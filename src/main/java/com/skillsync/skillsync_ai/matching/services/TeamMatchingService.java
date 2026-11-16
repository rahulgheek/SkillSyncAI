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
import java.util.List;
import java.util.UUID;

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
        return applicationRepository.findAllByApplicantUserId(studentId, pageable).map(this::mapToAppResponse);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> getProjectApplications(UUID ownerId, UUID projectId, ApplicationStatus status, Pageable pageable) {
        verifyProjectOwnership(projectId, ownerId);
        return applicationRepository.findAllByProjectIdAndStatus(projectId, status, pageable).map(this::mapToAppResponse);
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
        return invitationRepository.findAllByInvitedUserIdAndStatus(studentId, status, pageable).map(this::mapToInvResponse);
    }

    @Transactional(readOnly = true)
    public Page<InvitationResponse> getProjectInvitations(UUID ownerId, UUID projectId, Pageable pageable) {
        verifyProjectOwnership(projectId, ownerId);
        return invitationRepository.findAllByProjectId(projectId, pageable).map(this::mapToInvResponse);
    }

    // Invitations are expired lazily in respondToInvitation(). There is no
    // scheduled sweep because a periodic database query wakes Neon and keeps
    // the compute from scaling to zero.

    // --- MAPPERS ---
    private ApplicationResponse mapToAppResponse(Application a) {
        StudentProfile profile = studentProfileRepository.findByUserId(a.getApplicantUserId()).orElse(null);
        String roleTitle = null;
        if (a.getProjectRoleId() != null) {
            roleTitle = projectRoleRepository.findById(a.getProjectRoleId()).map(r -> r.getTitle()).orElse(null);
        }
        
        List<String> topSkills = java.util.Collections.emptyList();
        if (profile != null) {
            topSkills = studentSkillRepository.findAllByStudentProfileId(profile.getId())
                    .stream().map(s -> s.getSkill().getName()).limit(3).collect(Collectors.toList());
        }

        return new ApplicationResponse(
                a.getId(), a.getProjectId(), a.getProjectRoleId(), roleTitle, a.getApplicantUserId(), 
                a.getStatus(), a.getCoverMessage(), a.getCreatedAt(), a.getDecidedAt(),
                profile != null ? profile.getFullName() : "Unknown User",
                profile != null ? profile.getProfilePictureUrl() : null,
                profile != null ? profile.getMajor() : null,
                profile != null ? profile.getGraduationYear() : null,
                topSkills
        );
    }
    private InvitationResponse mapToInvResponse(Invitation i) {
        StudentProfile profile = studentProfileRepository.findByUserId(i.getInvitedUserId()).orElse(null);
        String roleTitle = null;
        if (i.getProjectRoleId() != null) {
            roleTitle = projectRoleRepository.findById(i.getProjectRoleId()).map(r -> r.getTitle()).orElse(null);
        }
        
        List<String> topSkills = java.util.Collections.emptyList();
        if (profile != null) {
            topSkills = studentSkillRepository.findAllByStudentProfileId(profile.getId())
                    .stream().map(s -> s.getSkill().getName()).limit(3).collect(Collectors.toList());
        }

        return new InvitationResponse(
                i.getId(), i.getProjectId(), i.getProjectRoleId(), roleTitle, i.getInvitedUserId(), 
                i.getInvitedBy(), i.getStatus(), i.getMessage(), i.getCreatedAt(), i.getExpiresAt(), i.getRespondedAt(),
                profile != null ? profile.getFullName() : "Unknown User",
                profile != null ? profile.getProfilePictureUrl() : null,
                profile != null ? profile.getMajor() : null,
                profile != null ? profile.getGraduationYear() : null,
                topSkills
        );
    }
}