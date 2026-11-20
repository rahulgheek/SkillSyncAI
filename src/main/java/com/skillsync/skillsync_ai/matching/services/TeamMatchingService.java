package com.skillsync.skillsync_ai.matching.services;

import com.skillsync.skillsync_ai.common.events.TeamMemberAcceptedEvent;
import com.skillsync.skillsync_ai.common.events.ApplicationSubmittedEvent;
import com.skillsync.skillsync_ai.common.events.InvitationSentEvent;
import com.skillsync.skillsync_ai.matching.dto.ApplicationResponse;
import com.skillsync.skillsync_ai.matching.dto.InvitationResponse;
import com.skillsync.skillsync_ai.matching.entity.Application;
import com.skillsync.skillsync_ai.matching.entity.Invitation;
import com.skillsync.skillsync_ai.matching.entity.enums.ApplicationStatus;
import com.skillsync.skillsync_ai.matching.entity.enums.InvitationStatus;
import com.skillsync.skillsync_ai.matching.repo.ApplicationRepository;
import com.skillsync.skillsync_ai.matching.repo.InvitationRepository;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository; // Cross-module import for Auth checks!
import com.skillsync.skillsync_ai.project.repo.ProjectRoleRepository;
import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import lombok.RequiredArgsConstructor;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamMatchingService {

    private final ApplicationRepository applicationRepository;
    private final InvitationRepository invitationRepository;
    private final ProjectRepository projectRepository; // Used ONLY for security lookups
    private final ProjectRoleRepository projectRoleRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final ApplicationEventPublisher eventPublisher;

    // --- SECURITY HELPER ---
    public void verifyProjectOwnership(UUID projectId, UUID requestedOwnerId) {
        UUID actualOwnerId = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found."))
                .getOwnerId();
        if (!actualOwnerId.equals(requestedOwnerId)) {
            throw new SecurityException("You are not the owner of this project.");
        }
    }

    // ==========================================
    // APPLICATIONS
    // ==========================================

    @Transactional
    public ApplicationResponse applyToProject(UUID studentId, UUID projectId, UUID roleId, String coverMessage) {
        // COLLISION CHECK
        UUID actualOwnerId = projectRepository.findById(projectId).orElseThrow().getOwnerId();
        if (actualOwnerId.equals(studentId)) {
            throw new IllegalStateException("You cannot apply to your own project.");
        }
        if (invitationRepository.existsByProjectIdAndInvitedUserIdAndStatus(projectId, studentId, InvitationStatus.PENDING)) {
            throw new IllegalStateException("You already have a pending invitation to this project. Please accept it instead.");
        }
        if (applicationRepository.existsByProjectIdAndApplicantUserIdAndStatus(projectId, studentId, ApplicationStatus.PENDING)) {
            throw new IllegalStateException("You already have a pending application for this project.");
        }

        Application application = Application.builder()
                .projectId(projectId).projectRoleId(roleId).applicantUserId(studentId)
                .status(ApplicationStatus.PENDING).coverMessage(coverMessage)
                .build();
        Application saved = applicationRepository.save(application);
        
        String applicantName = studentProfileRepository.findByUserId(studentId)
            .map(com.skillsync.skillsync_ai.userprofile.entity.StudentProfile::getFullName)
            .orElse("A student");
        String projectTitle = projectRepository.findById(projectId)
            .map(com.skillsync.skillsync_ai.project.entity.Project::getTitle)
            .orElse("your project");
            
        eventPublisher.publishEvent(new ApplicationSubmittedEvent(
                studentId, projectId, actualOwnerId, 
                applicantName, 
                projectTitle
        ));
        
        return mapToAppResponse(saved);
    }

    @Transactional
    public ApplicationResponse decideOnApplication(UUID ownerId, UUID applicationId, boolean accept) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));

        verifyProjectOwnership(application.getProjectId(), ownerId); // SECURITY CHECK

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Application has already been decided.");
        }

        application.setStatus(accept ? ApplicationStatus.ACCEPTED : ApplicationStatus.REJECTED);
        application.setDecidedAt(Instant.now());
        application.setDecidedBy(ownerId);

        Application savedApp = applicationRepository.save(application);

        if (accept) {
            eventPublisher.publishEvent(new TeamMemberAcceptedEvent(savedApp.getProjectId(), savedApp.getProjectRoleId(), savedApp.getApplicantUserId()));
        }
        return mapToAppResponse(savedApp);
    }

    @Transactional
    public void withdrawApplication(UUID studentId, UUID applicationId) {
        Application application = applicationRepository.findById(applicationId).orElseThrow();
        if (!application.getApplicantUserId().equals(studentId)) throw new SecurityException("Unauthorized.");
        if (application.getStatus() != ApplicationStatus.PENDING) throw new IllegalStateException("Can only withdraw pending applications.");

        application.setStatus(ApplicationStatus.WITHDRAWN);
        applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> getStudentApplications(UUID studentId, Pageable pageable) {
        Page<Application> page = applicationRepository.findAllByApplicantUserId(studentId, pageable);
        MatchLookup lookup = buildLookup(
                page.getContent().stream().map(Application::getApplicantUserId).toList(),
                page.getContent().stream().map(Application::getProjectRoleId).filter(java.util.Objects::nonNull).toList()
        );
        return page.map(a -> mapToAppResponse(a, lookup));
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> getProjectApplications(UUID ownerId, UUID projectId, ApplicationStatus status, Pageable pageable) {
        verifyProjectOwnership(projectId, ownerId);
        Page<Application> page = applicationRepository.findAllByProjectIdAndStatus(projectId, status, pageable);
        MatchLookup lookup = buildLookup(
                page.getContent().stream().map(Application::getApplicantUserId).toList(),
                page.getContent().stream().map(Application::getProjectRoleId).filter(java.util.Objects::nonNull).toList()
        );
        return page.map(a -> mapToAppResponse(a, lookup));
    }

    // ==========================================
    // INVITATIONS
    // ==========================================

    @Transactional
    public InvitationResponse inviteStudent(UUID ownerId, UUID projectId, UUID roleId, UUID studentId, String message) {
        verifyProjectOwnership(projectId, ownerId); // SECURITY CHECK

        // COLLISION CHECK
        if (ownerId.equals(studentId)) {
            throw new IllegalStateException("You cannot invite yourself to your own project.");
        }
        if (applicationRepository.existsByProjectIdAndApplicantUserIdAndStatus(projectId, studentId, ApplicationStatus.PENDING)) {
            throw new IllegalStateException("This student has already applied to this project. Please review their application.");
        }
        if (invitationRepository.existsByProjectIdAndInvitedUserIdAndStatus(projectId, studentId, InvitationStatus.PENDING)) {
            throw new IllegalStateException("This student already has a pending invitation.");
        }

        Invitation invitation = Invitation.builder()
                .projectId(projectId).projectRoleId(roleId).invitedUserId(studentId).invitedBy(ownerId)
                .status(InvitationStatus.PENDING).message(message)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        Invitation saved = invitationRepository.save(invitation);
        
        String inviterName = studentProfileRepository.findByUserId(ownerId)
            .map(com.skillsync.skillsync_ai.userprofile.entity.StudentProfile::getFullName)
            .orElse("A project owner");
        String projectTitle = projectRepository.findById(projectId)
            .map(com.skillsync.skillsync_ai.project.entity.Project::getTitle)
            .orElse("a project");
            
        eventPublisher.publishEvent(new InvitationSentEvent(
                studentId, projectId, ownerId,
                inviterName,
                projectTitle
        ));
        
        return mapToInvResponse(saved);
    }

    @Transactional
    public InvitationResponse respondToInvitation(UUID studentId, UUID invitationId, boolean accept) {
        Invitation invitation = invitationRepository.findById(invitationId).orElseThrow();
        if (!invitation.getInvitedUserId().equals(studentId)) throw new SecurityException("Unauthorized.");

        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new IllegalStateException("This invitation has expired.");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) throw new IllegalStateException("Invitation is no longer valid.");

        invitation.setStatus(accept ? InvitationStatus.ACCEPTED : InvitationStatus.DECLINED);
        invitation.setRespondedAt(Instant.now());

        Invitation savedInv = invitationRepository.save(invitation);

        if (accept) {
            eventPublisher.publishEvent(new TeamMemberAcceptedEvent(savedInv.getProjectId(), savedInv.getProjectRoleId(), savedInv.getInvitedUserId()));
        }
        return mapToInvResponse(savedInv);
    }

    @Transactional
    public void revokeInvitation(UUID ownerId, UUID invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId).orElseThrow();
        verifyProjectOwnership(invitation.getProjectId(), ownerId);
        if (invitation.getStatus() != InvitationStatus.PENDING) throw new IllegalStateException("Can only revoke pending invitations.");

        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);
    }

    @Transactional(readOnly = true)
    public Page<InvitationResponse> getStudentInvitations(UUID studentId, InvitationStatus status, Pageable pageable) {
        Page<Invitation> page = invitationRepository.findAllByInvitedUserIdAndStatus(studentId, status, pageable);
        MatchLookup lookup = buildLookup(
                page.getContent().stream().map(Invitation::getInvitedUserId).toList(),
                page.getContent().stream().map(Invitation::getProjectRoleId).filter(java.util.Objects::nonNull).toList()
        );
        return page.map(i -> mapToInvResponse(i, lookup));
    }

    @Transactional(readOnly = true)
    public Page<InvitationResponse> getProjectInvitations(UUID ownerId, UUID projectId, Pageable pageable) {
        verifyProjectOwnership(projectId, ownerId);
        Page<Invitation> page = invitationRepository.findAllByProjectId(projectId, pageable);
        MatchLookup lookup = buildLookup(
                page.getContent().stream().map(Invitation::getInvitedUserId).toList(),
                page.getContent().stream().map(Invitation::getProjectRoleId).filter(java.util.Objects::nonNull).toList()
        );
        return page.map(i -> mapToInvResponse(i, lookup));
    }

    // Invitations are expired lazily in respondToInvitation(). There is no
    // scheduled sweep because a periodic database query wakes Neon and keeps
    // the compute from scaling to zero.

    // --- MAPPERS ---

    /**
     * Batch context for a page of applications/invitations. Loading it once
     * turns 3N queries into 3 queries.
     */
    private record MatchLookup(
            Map<UUID, StudentProfile> profiles,
            Map<UUID, com.skillsync.skillsync_ai.project.entity.ProjectRole> roles,
            Map<UUID, List<String>> topSkillsByProfile
    ) {
        List<String> topSkills(UUID userId) {
            StudentProfile profile = profiles.get(userId);
            if (profile == null) {
                return List.of();
            }
            return topSkillsByProfile.getOrDefault(profile.getId(), List.of())
                    .stream().limit(3).toList();
        }
    }

    private MatchLookup buildLookup(Collection<UUID> userIds, Collection<UUID> roleIds) {
        List<UUID> distinctUserIds = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, StudentProfile> profiles = distinctUserIds.isEmpty() ? Map.of()
                : studentProfileRepository.findAllByUserIdIn(distinctUserIds).stream()
                .collect(Collectors.toMap(StudentProfile::getUserId, Function.identity()));

        Map<UUID, com.skillsync.skillsync_ai.project.entity.ProjectRole> roles =
                roleIds == null || roleIds.isEmpty() ? Map.of()
                        : projectRoleRepository.findAllByIdIn(roleIds).stream()
                        .collect(Collectors.toMap(com.skillsync.skillsync_ai.project.entity.ProjectRole::getId, Function.identity()));

        Map<UUID, List<String>> topSkills = profiles.isEmpty() ? Map.of()
                : studentSkillRepository.findAllByStudentProfileIdIn(
                        profiles.values().stream().map(StudentProfile::getId).toList()).stream()
                .collect(Collectors.groupingBy(
                        ss -> ss.getStudentProfile().getId(),
                        Collectors.mapping(ss -> ss.getSkill().getName(), Collectors.toList())
                ));

        return new MatchLookup(profiles, roles, topSkills);
    }

    private ApplicationResponse mapToAppResponse(Application a) {
        return mapToAppResponse(a, buildLookup(
                List.of(a.getApplicantUserId()),
                a.getProjectRoleId() == null ? List.of() : List.of(a.getProjectRoleId())
        ));
    }

    private ApplicationResponse mapToAppResponse(Application a, MatchLookup lookup) {
        StudentProfile profile = lookup.profiles().get(a.getApplicantUserId());
        var role = a.getProjectRoleId() != null ? lookup.roles().get(a.getProjectRoleId()) : null;

        return new ApplicationResponse(
                a.getId(), a.getProjectId(), a.getProjectRoleId(), role != null ? role.getTitle() : null,
                a.getApplicantUserId(), a.getStatus(), a.getCoverMessage(), a.getCreatedAt(), a.getDecidedAt(),
                profile != null ? profile.getFullName() : "Unknown User",
                profile != null ? profile.getProfilePictureUrl() : null,
                profile != null ? profile.getMajor() : null,
                profile != null ? profile.getGraduationYear() : null,
                lookup.topSkills(a.getApplicantUserId())
        );
    }

    private InvitationResponse mapToInvResponse(Invitation i) {
        return mapToInvResponse(i, buildLookup(
                List.of(i.getInvitedUserId()),
                i.getProjectRoleId() == null ? List.of() : List.of(i.getProjectRoleId())
        ));
    }

    private InvitationResponse mapToInvResponse(Invitation i, MatchLookup lookup) {
        StudentProfile profile = lookup.profiles().get(i.getInvitedUserId());
        var role = i.getProjectRoleId() != null ? lookup.roles().get(i.getProjectRoleId()) : null;

        return new InvitationResponse(
                i.getId(), i.getProjectId(), i.getProjectRoleId(), role != null ? role.getTitle() : null,
                i.getInvitedUserId(), i.getInvitedBy(), i.getStatus(), i.getMessage(), i.getCreatedAt(),
                i.getExpiresAt(), i.getRespondedAt(),
                profile != null ? profile.getFullName() : "Unknown User",
                profile != null ? profile.getProfilePictureUrl() : null,
                profile != null ? profile.getMajor() : null,
                profile != null ? profile.getGraduationYear() : null,
                lookup.topSkills(i.getInvitedUserId())
        );
    }
}