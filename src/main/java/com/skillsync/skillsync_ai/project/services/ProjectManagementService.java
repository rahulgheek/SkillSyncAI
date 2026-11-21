package com.skillsync.skillsync_ai.project.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.common.events.ProjectRecruitmentStatusChangedEvent;
import com.skillsync.skillsync_ai.common.events.TeamMemberAcceptedEvent;
import com.skillsync.skillsync_ai.common.events.TeamMemberRemovedEvent;
import com.skillsync.skillsync_ai.common.events.TeamMemberRoleChangedEvent;
import com.skillsync.skillsync_ai.matching.entity.Application;
import com.skillsync.skillsync_ai.matching.entity.Invitation;
import com.skillsync.skillsync_ai.matching.entity.enums.ApplicationStatus;
import com.skillsync.skillsync_ai.matching.entity.enums.InvitationStatus;
import com.skillsync.skillsync_ai.matching.repo.ApplicationRepository;
import com.skillsync.skillsync_ai.matching.repo.InvitationRepository;
import com.skillsync.skillsync_ai.project.dto.*;
import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.ProjectRole;
import com.skillsync.skillsync_ai.project.entity.TeamMember;
import com.skillsync.skillsync_ai.project.entity.enums.MemberStatus;
import com.skillsync.skillsync_ai.project.entity.enums.ProjectStatus;
import com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository;
import com.skillsync.skillsync_ai.project.repo.ProjectRoleRepository;
import com.skillsync.skillsync_ai.project.repo.TeamMemberRepository;
import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectManagementService {

    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final ApplicationRepository applicationRepository;
    private final InvitationRepository invitationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // --- PHASE 1: CRUD & PUBLISHING ---

    @Transactional
    public ProjectResponse createDraftProject(UUID ownerId, UUID collegeId, CreateProjectRequest request) {
        Project project = Project.builder()
                .ownerId(ownerId)
                .collegeId(collegeId)
                .title(request.title())
                .description(request.description())
                .status(ProjectStatus.DRAFT)
                .maxTeamSize(request.maxTeamSize())
                .applicationDeadline(request.applicationDeadline())
                .build();

        Project savedProject = projectRepository.save(project);

        TeamMember ownerMember = TeamMember.builder()
                .project(savedProject)
                .userId(ownerId)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();
        teamMemberRepository.save(ownerMember);

        return mapToProjectResponse(savedProject);
    }

    @Transactional
    public ProjectResponse updateProject(UUID ownerId, UUID projectId, UpdateProjectRequest request) {
        Project project = getProjectByIdAndOwnerForUpdate(projectId, ownerId);
        ensureProjectIsEditable(project);

        if (request.maxTeamSize() != null) {
            long activeMembers = teamMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
            if (request.maxTeamSize() < activeMembers) {
                throw new IllegalStateException(
                        "Max team size cannot be lower than the current active member count (" + activeMembers + ").");
            }

            int roleHeadcount = totalRoleHeadcount(projectId);
            if (1 + roleHeadcount > request.maxTeamSize()) {
                throw new IllegalStateException(
                        "Max team size must be at least the owner plus the total role headcount ("
                                + (1 + roleHeadcount) + "). Reduce role headcounts first.");
            }
        }

        project.setTitle(request.title());
        project.setDescription(request.description());
        project.setMaxTeamSize(request.maxTeamSize());
        project.setApplicationDeadline(request.applicationDeadline());

        return mapToProjectResponse(projectRepository.save(project));
    }

    @Transactional
    public void deleteProject(UUID ownerId, UUID projectId) {
        Project project = getProjectByIdAndOwner(projectId, ownerId);
        projectRepository.delete(project);
    }

    @Transactional
    public ProjectRoleResponse addRoleToProject(UUID ownerId, UUID projectId, CreateProjectRoleRequest request) {
        Project project = getProjectByIdAndOwnerForUpdate(projectId, ownerId);

        if (project.getStatus() != ProjectStatus.DRAFT && project.getStatus() != ProjectStatus.RECRUITING) {
            throw new IllegalStateException(
                    "Roles can only be added while the project is in DRAFT or RECRUITING status.");
        }

        short headcount = request.headcount() != null ? request.headcount() : 1;
        validateRoleHeadcountFits(project, headcount, null);

        ProjectRole role = ProjectRole.builder()
                .project(project)
                .title(request.title())
                .description(request.description())
                .requiredSkills(request.requiredSkills() != null
                        ? new ArrayList<>(request.requiredSkills())
                        : new ArrayList<>())
                .headcount(headcount)
                .filledCount((short) 0)
                .build();

        ProjectRole saved = projectRoleRepository.save(role);
        refreshProjectRequiredSkills(project);
        return mapToRoleResponse(saved);
    }

    @Transactional
    public ProjectResponse publishProject(UUID ownerId, UUID projectId) {
        Project project = getProjectByIdAndOwnerForUpdate(projectId, ownerId);

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new IllegalStateException("Project is already published or cannot be published from current state.");
        }

        List<ProjectRole> roles = projectRoleRepository.findAllByProjectId(projectId);
        if (roles.isEmpty()) {
            throw new IllegalStateException("A project must have at least one role before it can be published.");
        }

        if (project.getMaxTeamSize() != null) {
            int roleHeadcount = roles.stream()
                    .mapToInt(role -> role.getHeadcount() == null ? 0 : role.getHeadcount())
                    .sum();
            if (1 + roleHeadcount > project.getMaxTeamSize()) {
                throw new IllegalStateException(
                        "The owner plus the total role headcount (" + (1 + roleHeadcount)
                                + ") exceeds the max team size (" + project.getMaxTeamSize() + ").");
            }
        }

        if (project.getApplicationDeadline() != null
                && !project.getApplicationDeadline().isAfter(Instant.now())) {
            throw new IllegalStateException("The application deadline must be in the future before publishing.");
        }

        List<RequiredSkill> allRequiredSkills = roles.stream()
                .flatMap(role -> role.getRequiredSkills() == null
                        ? java.util.stream.Stream.empty()
                        : role.getRequiredSkills().stream())
                .distinct()
                .toList();

        project.setRequiredSkills(new ArrayList<>(allRequiredSkills));
        project.setStatus(ProjectStatus.RECRUITING);
        Project publishedProject = projectRepository.save(project);

        String requiredSkillsJson = "[]";
        try {
            requiredSkillsJson = objectMapper.writeValueAsString(allRequiredSkills);
        } catch (Exception e) {
            log.error("Failed to parse aggregated required skills to JSON for AI matching", e);
        }

        eventPublisher.publishEvent(new com.skillsync.skillsync_ai.common.events.ProjectPublishedEvent(
                publishedProject.getId(),
                publishedProject.getTitle(),
                requiredSkillsJson
        ));

        return mapToProjectResponse(publishedProject);
    }

    // --- PHASE 2: FEEDS & AGGREGATION ---

    @Transactional(readOnly = true)
    public ProjectDetailsResponse getProjectDetails(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found."));

        StudentProfile ownerProfile = studentProfileRepository.findByUserId(project.getOwnerId())
                .orElse(null);

        int activeMemberCount = (int) teamMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);

        return mapToProjectDetailsResponse(project, ownerProfile, activeMemberCount);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> getMyProjects(UUID ownerId, Pageable pageable) {
        return projectRepository.findAllByOwnerId(ownerId, pageable).map(this::mapToProjectResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> getRecruitingFeed(UUID collegeId, Pageable pageable) {
        return projectRepository.findAllByCollegeIdAndStatus(collegeId, ProjectStatus.RECRUITING, pageable)
                .map(this::mapToProjectResponse);
    }

    @Transactional(readOnly = true)
    public List<ProjectRoleResponse> getProjectRoles(UUID projectId) {
        return projectRoleRepository.findAllByProjectId(projectId).stream()
                .map(this::mapToRoleResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getProjectMembers(UUID projectId) {
        List<TeamMember> members = teamMemberRepository.findAllByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
        if (members.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds = members.stream().map(TeamMember::getUserId).distinct().toList();
        Map<UUID, StudentProfile> profiles = studentProfileRepository.findAllByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(StudentProfile::getUserId, p -> p));

        Map<UUID, List<String>> skillsByProfile = profiles.isEmpty() ? Map.of()
                : studentSkillRepository.findAllByStudentProfileIdIn(
                        profiles.values().stream().map(StudentProfile::getId).toList()).stream()
                .collect(Collectors.groupingBy(
                        ss -> ss.getStudentProfile().getId(),
                        Collectors.mapping(ss -> ss.getSkill().getName(), Collectors.toList())
                ));

        return members.stream()
                .map(m -> mapToMemberResponse(m, profiles.get(m.getUserId()), skillsByProfile))
                .collect(Collectors.toList());
    }

    /**
     * Owner-only dashboard: request funnels, capacity, and per-role fill state.
     * Counts are aggregate queries so a project with hundreds of requests does
     * not load every row into memory.
     */
    @Transactional(readOnly = true)
    public ProjectManagementResponse getProjectManagement(UUID ownerId, UUID projectId) {
        Project project = getProjectByIdAndOwner(projectId, ownerId);
        Instant now = Instant.now();

        int currentMembers = (int) teamMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
        Integer maxTeamSize = project.getMaxTeamSize() == null ? null : project.getMaxTeamSize().intValue();
        int openSlots = maxTeamSize == null ? 0 : Math.max(0, maxTeamSize - currentMembers);
        boolean full = maxTeamSize != null && currentMembers >= maxTeamSize;
        boolean deadlinePassed = project.getApplicationDeadline() != null
                && !project.getApplicationDeadline().isAfter(now);
        boolean acceptingRequests = project.getStatus() == ProjectStatus.RECRUITING
                && !full && !deadlinePassed;
        long daysRemaining = project.getApplicationDeadline() == null ? 0
                : Math.max(0, ChronoUnit.DAYS.between(now, project.getApplicationDeadline()));

        Map<ApplicationStatus, Long> applicationCounts = countApplications(projectId);
        Map<InvitationStatus, Long> invitationCounts = countInvitations(projectId);

        long applicationTotal = applicationCounts.values().stream().mapToLong(Long::longValue).sum();
        long invitationTotal = invitationCounts.values().stream().mapToLong(Long::longValue).sum();

        // Expired invitations are only flipped lazily when the student responds,
        // so report pending rows whose expiry has passed as expired here.
        long expiredPendingInvitations = invitationRepository
                .countByProjectIdAndStatusAndExpiresAtBefore(projectId, InvitationStatus.PENDING, now);
        long pendingInvitations = Math.max(0,
                invitationCounts.getOrDefault(InvitationStatus.PENDING, 0L) - expiredPendingInvitations);
        long expiredInvitations = invitationCounts.getOrDefault(InvitationStatus.EXPIRED, 0L)
                + expiredPendingInvitations;

        ProjectManagementResponse.ApplicationCounts applications =
                new ProjectManagementResponse.ApplicationCounts(
                        applicationCounts.getOrDefault(ApplicationStatus.PENDING, 0L),
                        applicationCounts.getOrDefault(ApplicationStatus.ACCEPTED, 0L),
                        applicationCounts.getOrDefault(ApplicationStatus.REJECTED, 0L),
                        applicationCounts.getOrDefault(ApplicationStatus.WITHDRAWN, 0L),
                        applicationTotal
                );

        ProjectManagementResponse.InvitationCounts invitations =
                new ProjectManagementResponse.InvitationCounts(
                        pendingInvitations,
                        invitationCounts.getOrDefault(InvitationStatus.ACCEPTED, 0L),
                        invitationCounts.getOrDefault(InvitationStatus.DECLINED, 0L),
                        expiredInvitations,
                        invitationCounts.getOrDefault(InvitationStatus.REVOKED, 0L),
                        invitationTotal
                );

        Map<UUID, Long> filledByRole = toCountMap(
                teamMemberRepository.countByProjectIdAndStatusGroupByRole(projectId, MemberStatus.ACTIVE));
        Map<UUID, Long> pendingApplicationsByRole = toCountMap(
                applicationRepository.countByProjectIdAndStatusGroupByRole(projectId, ApplicationStatus.PENDING));
        Map<UUID, Long> pendingInvitationsByRole = toCountMap(
                invitationRepository.countByProjectIdAndStatusGroupByRole(projectId, InvitationStatus.PENDING));

        List<ProjectManagementResponse.RoleManagement> roles = projectRoleRepository
                .findAllByProjectId(projectId).stream()
                .map(role -> {
                    int headcount = role.getHeadcount() == null ? 0 : role.getHeadcount();
                    int filled = filledByRole.getOrDefault(role.getId(), 0L).intValue();
                    return new ProjectManagementResponse.RoleManagement(
                            role.getId(),
                            role.getTitle(),
                            role.getDescription(),
                            role.getRequiredSkills() == null ? List.of() : role.getRequiredSkills(),
                            headcount,
                            filled,
                            Math.max(0, headcount - filled),
                            pendingApplicationsByRole.getOrDefault(role.getId(), 0L),
                            pendingInvitationsByRole.getOrDefault(role.getId(), 0L),
                            headcount > 0 && filled >= headcount
                    );
                })
                .toList();

        return new ProjectManagementResponse(
                project.getId(),
                project.getTitle(),
                project.getStatus(),
                maxTeamSize,
                currentMembers,
                openSlots,
                full,
                acceptingRequests,
                deadlinePassed,
                project.getApplicationDeadline(),
                daysRemaining,
                applications,
                invitations,
                roles
        );
    }

    // --- PHASE 3: TEAM LIFECYCLE ---

    @Transactional
    public ProjectResponse closeRecruitment(UUID ownerId, UUID projectId) {
        Project project = getProjectByIdAndOwnerForUpdate(projectId, ownerId);
        if (project.getStatus() != ProjectStatus.RECRUITING) {
            throw new IllegalStateException("Recruitment can only be closed while the project is recruiting.");
        }
        project.setStatus(ProjectStatus.IN_PROGRESS);
        return mapToProjectResponse(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse reopenRecruitment(UUID ownerId, UUID projectId) {
        Project project = getProjectByIdAndOwnerForUpdate(projectId, ownerId);
        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only a project with closed recruitment can be reopened.");
        }

        long activeMembers = teamMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
        if (project.getMaxTeamSize() != null && activeMembers >= project.getMaxTeamSize()) {
            throw new IllegalStateException("The team is already full. Remove a member before reopening recruitment.");
        }
        if (project.getApplicationDeadline() != null
                && !project.getApplicationDeadline().isAfter(Instant.now())) {
            throw new IllegalStateException("The application deadline has passed. Update the deadline before reopening recruitment.");
        }

        project.setStatus(ProjectStatus.RECRUITING);
        Project saved = projectRepository.save(project);
        eventPublisher.publishEvent(new ProjectRecruitmentStatusChangedEvent(
                saved.getId(), saved.getOwnerId(), saved.getTitle(), false));
        return mapToProjectResponse(saved);
    }

    @Transactional
    public void removeMember(UUID ownerId, UUID projectId, UUID memberUserId) {
        Project project = getProjectByIdAndOwnerForUpdate(projectId, ownerId);

        TeamMember member = teamMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, memberUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active team member not found."));

        if (member.getUserId().equals(ownerId)) {
            throw new IllegalStateException("Owner cannot remove themselves.");
        }

        deactivateMember(projectId, member, MemberStatus.REMOVED);
        handleCapacityAfterRemoval(project);
        eventPublisher.publishEvent(new TeamMemberRemovedEvent(
                projectId, memberUserId, project.getTitle(), true));
    }

    @Transactional
    public void leaveProject(UUID userId, UUID projectId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found."));

        TeamMember member = teamMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("You are not an active member of this project."));

        if (project.getOwnerId().equals(userId)) {
            throw new IllegalStateException("Owner cannot leave their own project. Transfer ownership or cancel the project.");
        }

        deactivateMember(projectId, member, MemberStatus.LEFT);
        handleCapacityAfterRemoval(project);
        eventPublisher.publishEvent(new TeamMemberRemovedEvent(
                projectId, userId, project.getTitle(), false));
    }

    /**
     * Synchronous, transactional membership insertion used by the matching
     * module when an application or invitation is accepted. Throwing here
     * rolls the whole decision back, so a request can never be marked ACCEPTED
     * without a corresponding active team member.
     */
    @Transactional
    public void addMemberToTeam(UUID projectId, UUID projectRoleId, UUID userId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found."));
        addMemberInternal(project, projectRoleId, userId, project.getOwnerId(), false);
    }

    /**
     * Direct owner action. Unlike an invitation, this does not require the
     * student to accept. Any pending application or invitation for the same
     * user is marked accepted so the management screen does not keep showing
     * a request for someone who is already on the team.
     */
    @Transactional
    public TeamMemberResponse addMemberDirectly(UUID ownerId, UUID projectId, UUID userId, UUID roleId) {
        Project project = getProjectByIdAndOwnerForUpdate(projectId, ownerId);
        TeamMember member = addMemberInternal(project, roleId, userId, ownerId, true);
        return mapToMemberResponse(member);
    }

    @Transactional
    public TeamMemberResponse updateMemberRole(UUID ownerId, UUID projectId, UUID memberUserId, UUID newRoleId) {
        Project project = getProjectByIdAndOwnerForUpdate(projectId, ownerId);
        ensureProjectIsEditable(project);

        TeamMember member = teamMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, memberUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active team member not found."));

        if (member.getProjectRole() != null && member.getProjectRole().getId().equals(newRoleId)) {
            return mapToMemberResponse(member);
        }

        ProjectRole newRole = projectRoleRepository.findById(newRoleId)
                .orElseThrow(() -> new IllegalArgumentException("New role not found."));

        if (!newRole.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Role does not belong to this project.");
        }

        long currentInNewRole = teamMemberRepository.countByProjectIdAndProjectRoleIdAndStatus(
                projectId, newRoleId, MemberStatus.ACTIVE);
        if (newRole.getHeadcount() != null && currentInNewRole >= newRole.getHeadcount()) {
            throw new IllegalStateException("The new role is already at maximum capacity.");
        }

        ProjectRole oldRole = member.getProjectRole();
        String oldRoleTitle = oldRole != null ? oldRole.getTitle() : null;

        member.setProjectRole(newRole);
        TeamMember saved = teamMemberRepository.save(member);

        recalculateRoleFilledCount(projectId, oldRole);
        recalculateRoleFilledCount(projectId, newRole);

        eventPublisher.publishEvent(new TeamMemberRoleChangedEvent(
                projectId, memberUserId, project.getTitle(), oldRoleTitle, newRole.getTitle()));

        return mapToMemberResponse(saved);
    }

    @Transactional
    public ProjectRoleResponse updateProjectRole(
            UUID ownerId, UUID projectId, UUID roleId, UpdateProjectRoleRequest request) {
        Project project = getProjectByIdAndOwnerForUpdate(projectId, ownerId);
        ensureProjectIsEditable(project);

        ProjectRole role = projectRoleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Project role not found."));
        if (!role.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Role does not belong to this project.");
        }

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new IllegalArgumentException("Role title cannot be blank.");
            }
            role.setTitle(request.title());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        if (request.requiredSkills() != null) {
            role.setRequiredSkills(new ArrayList<>(request.requiredSkills()));
        }
        if (request.headcount() != null) {
            long filled = teamMemberRepository.countByProjectIdAndProjectRoleIdAndStatus(
                    projectId, roleId, MemberStatus.ACTIVE);
            if (request.headcount() < filled) {
                throw new IllegalStateException(
                        "Headcount cannot be lower than the " + filled + " active member(s) currently in this role.");
            }
            validateRoleHeadcountFits(project, request.headcount(), roleId);
            role.setHeadcount(request.headcount());
        }

        ProjectRole saved = projectRoleRepository.save(role);
        refreshProjectRequiredSkills(project);
        return mapToRoleResponse(saved);
    }

    @Transactional
    public void deleteProjectRole(UUID ownerId, UUID projectId, UUID roleId) {
        Project project = getProjectByIdAndOwnerForUpdate(projectId, ownerId);
        ensureProjectIsEditable(project);

        ProjectRole role = projectRoleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Project role not found."));
        if (!role.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Role does not belong to this project.");
        }

        long activeMembers = teamMemberRepository.countByProjectIdAndProjectRoleIdAndStatus(
                projectId, roleId, MemberStatus.ACTIVE);
        if (activeMembers > 0) {
            throw new IllegalStateException(
                    "Reassign the " + activeMembers + " active member(s) in this role before deleting it.");
        }

        long pendingApplications = applicationRepository.countByProjectIdAndProjectRoleIdAndStatus(
                projectId, roleId, ApplicationStatus.PENDING);
        long pendingInvitations = invitationRepository.countByProjectIdAndProjectRoleIdAndStatus(
                projectId, roleId, InvitationStatus.PENDING);
        if (pendingApplications > 0 || pendingInvitations > 0) {
            throw new IllegalStateException(
                    "Resolve pending applications or invitations for this role before deleting it.");
        }

        if (project.getStatus() != ProjectStatus.DRAFT
                && projectRoleRepository.findAllByProjectId(projectId).size() <= 1) {
            throw new IllegalStateException("A published project must keep at least one role.");
        }

        projectRoleRepository.delete(role);
        refreshProjectRequiredSkills(project);
    }

    @Transactional
    public ProjectResponse transferOwnership(UUID currentOwnerId, UUID projectId, UUID newOwnerId) {
        Project project = getProjectByIdAndOwnerForUpdate(projectId, currentOwnerId);

        if (currentOwnerId.equals(newOwnerId)) {
            throw new IllegalArgumentException("You are already the owner of this project.");
        }

        teamMemberRepository.findByProjectIdAndUserIdAndStatus(projectId, newOwnerId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "The new owner must be an active member of the team."));

        project.setOwnerId(newOwnerId);
        Project savedProject = projectRepository.save(project);

        eventPublisher.publishEvent(new com.skillsync.skillsync_ai.common.events.ProjectOwnershipTransferredEvent(
                projectId, project.getTitle(), currentOwnerId, newOwnerId
        ));

        return mapToProjectResponse(savedProject);
    }

    // --- INTERNAL HELPERS & MAPPERS ---

    private TeamMember addMemberInternal(
            Project project,
            UUID roleId,
            UUID userId,
            UUID actorId,
            boolean resolvePendingRequests) {

        if (project.getStatus() == ProjectStatus.DRAFT || isTerminal(project.getStatus())) {
            throw new IllegalStateException("This project cannot accept new members in its current state.");
        }
        if (project.getOwnerId().equals(userId)) {
            throw new IllegalStateException("The project owner is already a member of this project.");
        }
        if (teamMemberRepository.existsByProjectIdAndUserIdAndStatus(
                project.getId(), userId, MemberStatus.ACTIVE)) {
            throw new IllegalStateException("This user is already an active member of the project.");
        }

        long activeMembers = teamMemberRepository.countByProjectIdAndStatus(
                project.getId(), MemberStatus.ACTIVE);
        if (project.getMaxTeamSize() != null && activeMembers >= project.getMaxTeamSize()) {
            throw new IllegalStateException("The team is already full.");
        }

        ProjectRole role = null;
        if (roleId != null) {
            role = projectRoleRepository.findById(roleId)
                    .orElseThrow(() -> new IllegalArgumentException("Project role not found."));
            if (!role.getProject().getId().equals(project.getId())) {
                throw new IllegalArgumentException("Role does not belong to this project.");
            }

            long roleMembers = teamMemberRepository.countByProjectIdAndProjectRoleIdAndStatus(
                    project.getId(), roleId, MemberStatus.ACTIVE);
            if (role.getHeadcount() != null && roleMembers >= role.getHeadcount()) {
                throw new IllegalStateException("The selected role is already full.");
            }
        }

        TeamMember newMember = TeamMember.builder()
                .project(project)
                .projectRole(role)
                .userId(userId)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();
        TeamMember saved = teamMemberRepository.save(newMember);

        if (role != null) {
            recalculateRoleFilledCount(project.getId(), role);
        }

        if (resolvePendingRequests) {
            resolvePendingRequests(project, userId, actorId);
        }

        long newActiveMembers = teamMemberRepository.countByProjectIdAndStatus(
                project.getId(), MemberStatus.ACTIVE);
        if (project.getStatus() == ProjectStatus.RECRUITING
                && project.getMaxTeamSize() != null
                && newActiveMembers >= project.getMaxTeamSize()) {
            project.setStatus(ProjectStatus.IN_PROGRESS);
            projectRepository.save(project);
            eventPublisher.publishEvent(new ProjectRecruitmentStatusChangedEvent(
                    project.getId(), project.getOwnerId(), project.getTitle(), true));
        }

        eventPublisher.publishEvent(new TeamMemberAcceptedEvent(
                project.getId(), roleId, userId));
        return saved;
    }

    private void resolvePendingRequests(Project project, UUID userId, UUID actorId) {
        List<Application> pendingApplications = applicationRepository
                .findAllByProjectIdAndApplicantUserIdAndStatus(
                        project.getId(), userId, ApplicationStatus.PENDING);
        if (!pendingApplications.isEmpty()) {
            Instant now = Instant.now();
            for (Application application : pendingApplications) {
                application.setStatus(ApplicationStatus.ACCEPTED);
                application.setDecidedAt(now);
                application.setDecidedBy(actorId);
            }
            applicationRepository.saveAll(pendingApplications);
        }

        List<Invitation> pendingInvitations = invitationRepository
                .findAllByProjectIdAndInvitedUserIdAndStatus(
                        project.getId(), userId, InvitationStatus.PENDING);
        if (!pendingInvitations.isEmpty()) {
            Instant now = Instant.now();
            for (Invitation invitation : pendingInvitations) {
                invitation.setStatus(InvitationStatus.ACCEPTED);
                invitation.setRespondedAt(now);
            }
            invitationRepository.saveAll(pendingInvitations);
        }
    }

    private void deactivateMember(UUID projectId, TeamMember member, MemberStatus newStatus) {
        ProjectRole role = member.getProjectRole();
        member.setStatus(newStatus);
        member.setLeftAt(Instant.now());
        teamMemberRepository.save(member);
        recalculateRoleFilledCount(projectId, role);
    }

    private void handleCapacityAfterRemoval(Project project) {
        long activeMembers = teamMemberRepository.countByProjectIdAndStatus(
                project.getId(), MemberStatus.ACTIVE);
        if (project.getStatus() == ProjectStatus.IN_PROGRESS
                && project.getMaxTeamSize() != null
                && activeMembers < project.getMaxTeamSize()) {
            project.setStatus(ProjectStatus.RECRUITING);
            projectRepository.save(project);
            eventPublisher.publishEvent(new ProjectRecruitmentStatusChangedEvent(
                    project.getId(), project.getOwnerId(), project.getTitle(), false));
        }
    }

    private void recalculateRoleFilledCount(UUID projectId, ProjectRole role) {
        if (role == null) {
            return;
        }
        long filled = teamMemberRepository.countByProjectIdAndProjectRoleIdAndStatus(
                projectId, role.getId(), MemberStatus.ACTIVE);
        role.setFilledCount((short) filled);
        projectRoleRepository.save(role);
    }

    private void validateRoleHeadcountFits(Project project, short newHeadcount, UUID excludedRoleId) {
        if (project.getMaxTeamSize() == null) {
            return;
        }

        int otherHeadcount = projectRoleRepository.findAllByProjectId(project.getId()).stream()
                .filter(role -> excludedRoleId == null || !role.getId().equals(excludedRoleId))
                .mapToInt(role -> role.getHeadcount() == null ? 0 : role.getHeadcount())
                .sum();

        int required = 1 + otherHeadcount + newHeadcount;
        if (required > project.getMaxTeamSize()) {
            throw new IllegalArgumentException(
                    "This role would require " + required + " team slots including the owner, but the max team size is "
                            + project.getMaxTeamSize() + ".");
        }
    }

    private int totalRoleHeadcount(UUID projectId) {
        return projectRoleRepository.findAllByProjectId(projectId).stream()
                .mapToInt(role -> role.getHeadcount() == null ? 0 : role.getHeadcount())
                .sum();
    }

    private void refreshProjectRequiredSkills(Project project) {
        if (project.getStatus() == ProjectStatus.DRAFT) {
            return;
        }

        List<RequiredSkill> skills = projectRoleRepository.findAllByProjectId(project.getId()).stream()
                .flatMap(role -> role.getRequiredSkills() == null
                        ? java.util.stream.Stream.empty()
                        : role.getRequiredSkills().stream())
                .distinct()
                .toList();

        project.setRequiredSkills(new ArrayList<>(skills));
        projectRepository.save(project);
    }

    private boolean isTerminal(ProjectStatus status) {
        return status == ProjectStatus.COMPLETED
                || status == ProjectStatus.ARCHIVED
                || status == ProjectStatus.CANCELLED;
    }

    private void ensureProjectIsEditable(Project project) {
        if (isTerminal(project.getStatus())) {
            throw new IllegalStateException("This project can no longer be edited.");
        }
    }

    private Map<ApplicationStatus, Long> countApplications(UUID projectId) {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : applicationRepository.countByProjectIdGroupByStatus(projectId)) {
            counts.put((ApplicationStatus) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private Map<InvitationStatus, Long> countInvitations(UUID projectId) {
        Map<InvitationStatus, Long> counts = new EnumMap<>(InvitationStatus.class);
        for (InvitationStatus status : InvitationStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : invitationRepository.countByProjectIdGroupByStatus(projectId)) {
            counts.put((InvitationStatus) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private Project getProjectByIdAndOwner(UUID projectId, UUID ownerId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found."));
        if (!project.getOwnerId().equals(ownerId)) {
            throw new SecurityException("Permission denied.");
        }
        return project;
    }

    private Project getProjectByIdAndOwnerForUpdate(UUID projectId, UUID ownerId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found."));
        if (!project.getOwnerId().equals(ownerId)) {
            throw new SecurityException("Permission denied.");
        }
        return project;
    }

    private ProjectResponse mapToProjectResponse(Project p) {
        return new ProjectResponse(p.getId(), p.getOwnerId(), p.getCollegeId(), p.getTitle(), p.getDescription(),
                p.getStatus(), p.getRequiredSkills(), p.getMaxTeamSize(), p.getApplicationDeadline(), p.getCreatedAt());
    }

    private ProjectDetailsResponse mapToProjectDetailsResponse(Project p, StudentProfile owner, int memberCount) {
        ProjectDetailsResponse.OwnerInfo ownerInfo = null;
        if (owner != null) {
            ownerInfo = new ProjectDetailsResponse.OwnerInfo(
                    owner.getFullName(),
                    owner.getProfilePictureUrl(),
                    owner.getMajor(),
                    owner.getGraduationYear()
            );
        }
        return new ProjectDetailsResponse(
                p.getId(), p.getOwnerId(), p.getCollegeId(), p.getTitle(), p.getDescription(),
                p.getStatus(), p.getRequiredSkills(), p.getMaxTeamSize(), memberCount,
                p.getApplicationDeadline(), p.getCreatedAt(), ownerInfo
        );
    }

    private ProjectRoleResponse mapToRoleResponse(ProjectRole r) {
        return new ProjectRoleResponse(r.getId(), r.getTitle(), r.getDescription(), r.getRequiredSkills(),
                r.getHeadcount(), r.getFilledCount());
    }

    private TeamMemberResponse mapToMemberResponse(TeamMember m) {
        StudentProfile profile = studentProfileRepository.findByUserId(m.getUserId()).orElse(null);
        List<String> topSkills = profile == null ? java.util.Collections.emptyList()
                : studentSkillRepository.findAllByStudentProfileId(profile.getId()).stream()
                .map(skill -> skill.getSkill().getName())
                .limit(3)
                .collect(Collectors.toList());

        return mapToMemberResponse(m, profile, topSkills);
    }

    private TeamMemberResponse mapToMemberResponse(
            TeamMember m,
            StudentProfile profile,
            Map<UUID, List<String>> skillsByProfile) {
        List<String> topSkills = profile == null ? java.util.Collections.emptyList()
                : skillsByProfile.getOrDefault(profile.getId(), List.of()).stream().limit(3).toList();
        return mapToMemberResponse(m, profile, topSkills);
    }

    private TeamMemberResponse mapToMemberResponse(TeamMember m, StudentProfile profile, List<String> topSkills) {
        return new TeamMemberResponse(m.getId(), m.getUserId(),
                m.getProjectRole() != null ? m.getProjectRole().getId() : null,
                m.getProjectRole() != null ? m.getProjectRole().getTitle() : null,
                m.getStatus(), m.getJoinedAt(),
                profile != null ? profile.getFullName() : "Unknown User",
                profile != null ? profile.getProfilePictureUrl() : null,
                profile != null ? profile.getMajor() : null,
                profile != null ? profile.getGraduationYear() : null,
                topSkills);
    }
}
