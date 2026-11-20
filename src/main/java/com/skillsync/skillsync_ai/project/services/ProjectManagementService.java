package com.skillsync.skillsync_ai.project.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.project.dto.*;
import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.ProjectRole;
import com.skillsync.skillsync_ai.project.entity.TeamMember;
import com.skillsync.skillsync_ai.project.entity.enums.MemberStatus;
import com.skillsync.skillsync_ai.project.entity.enums.ProjectStatus;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository;
import com.skillsync.skillsync_ai.project.repo.ProjectRoleRepository;
import com.skillsync.skillsync_ai.project.repo.TeamMemberRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import com.skillsync.skillsync_ai.userprofile.entity.StudentSkill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
        Project project = getProjectByIdAndOwner(projectId, ownerId);

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
        Project project = getProjectByIdAndOwner(projectId, ownerId);

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new IllegalStateException("Can only add roles while project is in DRAFT status.");
        }

        // --- NEW VALIDATION: MAX TEAM SIZE ---
        List<ProjectRole> existingRoles = projectRoleRepository.findAllByProjectId(projectId);
        int currentHeadcount = existingRoles.stream().mapToInt(ProjectRole::getHeadcount).sum();

        // Note: We add 1 for the owner, as they take up a slot!
        if (1 + currentHeadcount + request.headcount() > project.getMaxTeamSize()) {
            throw new IllegalArgumentException(
                    "Adding this role exceeds the max team size of " + project.getMaxTeamSize() +
                            ". Current roles require " + currentHeadcount + " people (plus 1 owner)."
            );
        }
        // -------------------------------------

        ProjectRole role = ProjectRole.builder()
                .project(project)
                .title(request.title())
                .description(request.description())
                .requiredSkills(request.requiredSkills() != null ? request.requiredSkills() : java.util.Collections.emptyList())
                .headcount(request.headcount())
                .filledCount((short) 0)
                .build();

        return mapToRoleResponse(projectRoleRepository.save(role));
    }

    @Transactional
    public ProjectResponse publishProject(UUID ownerId, UUID projectId) {
        Project project = getProjectByIdAndOwner(projectId, ownerId);

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new IllegalStateException("Project is already published or cannot be published from current state.");
        }

        // --- NEW VALIDATION: MUST HAVE ROLES ---
        List<ProjectRole> roles = projectRoleRepository.findAllByProjectId(projectId);
        if (roles.isEmpty()) {
            throw new IllegalStateException("A project must have at least one role before it can be published.");
        }
        // ---------------------------------------

        // --- NEW BEHAVIOR: Aggregate all required skills from the roles for the AI ---
        List<com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill> allRequiredSkills = roles.stream()
                .flatMap(role -> role.getRequiredSkills().stream())
                .distinct() // Remove duplicates if multiple roles need the same skill
                .collect(Collectors.toList());
        
        project.setRequiredSkills(allRequiredSkills);
        project.setStatus(ProjectStatus.RECRUITING);
        Project publishedProject = projectRepository.save(project);

        String requiredSkillsJson = "[]";
        try {
            requiredSkillsJson = objectMapper.writeValueAsString(allRequiredSkills);
        } catch (Exception e) {
            log.error("Failed to parse aggregated required skills to JSON for AI matching", e);
        }
        // ------------------------------------------------------------------------------

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

    // --- PHASE 3: TEAM LIFECYCLE ---

    @Transactional
    public ProjectResponse closeRecruitment(UUID ownerId, UUID projectId) {
        Project project = getProjectByIdAndOwner(projectId, ownerId);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        return mapToProjectResponse(projectRepository.save(project));
    }

    @Transactional
    public void removeMember(UUID ownerId, UUID projectId, UUID memberUserId) {
        // Only the owner can remove someone
        Project project = getProjectByIdAndOwner(projectId, ownerId);

        TeamMember member = teamMemberRepository.findByProjectIdAndUserIdAndStatus(projectId, memberUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Active team member not found."));

        if (member.getUserId().equals(ownerId)) {
            throw new IllegalStateException("Owner cannot remove themselves.");
        }

        member.setStatus(MemberStatus.REMOVED);
        member.setLeftAt(Instant.now());
        decrementRoleCount(member);
        teamMemberRepository.save(member);

        long activeMembers = teamMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
        if (project.getStatus() == ProjectStatus.IN_PROGRESS && activeMembers < project.getMaxTeamSize()) {
            project.setStatus(ProjectStatus.RECRUITING);
            projectRepository.save(project);
        }
    }

    @Transactional
    public void leaveProject(UUID userId, UUID projectId) {
        TeamMember member = teamMemberRepository.findByProjectIdAndUserIdAndStatus(projectId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("You are not an active member of this project."));

        Project project = projectRepository.findById(projectId).orElseThrow();
        if (project.getOwnerId().equals(userId)) {
            throw new IllegalStateException("Owner cannot leave their own project. Transfer ownership or cancel the project.");
        }

        member.setStatus(MemberStatus.LEFT);
        member.setLeftAt(Instant.now());
        decrementRoleCount(member);
        teamMemberRepository.save(member);

        long activeMembers = teamMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
        if (project.getStatus() == ProjectStatus.IN_PROGRESS && activeMembers < project.getMaxTeamSize()) {
            project.setStatus(ProjectStatus.RECRUITING);
            projectRepository.save(project);
        }
    }

    // --- MODULITH LISTENER ---

    @org.springframework.modulith.events.ApplicationModuleListener
    void onTeamMemberAccepted(com.skillsync.skillsync_ai.common.events.TeamMemberAcceptedEvent event) {
        log.info("Received TeamMemberAcceptedEvent for Project: {}, User: {}", event.projectId(), event.userId());

        // FIX 1: Duplicate Event Protection (Idempotency)
        if (teamMemberRepository.findByProjectIdAndUserIdAndStatus(event.projectId(), event.userId(), MemberStatus.ACTIVE).isPresent()) {
            log.warn("User {} is already an active member of Project {}. Ignoring duplicate event.", event.userId(), event.projectId());
            return;
        }

        Project project = projectRepository.findById(event.projectId())
                .orElseThrow(() -> new IllegalStateException("Project not found for accepted member."));

        // FIX 3: Overall Team Size Validation
        long activeMembers = teamMemberRepository.countByProjectIdAndStatus(event.projectId(), MemberStatus.ACTIVE);
        if (activeMembers >= project.getMaxTeamSize()) {
            log.error("Project {} has reached max capacity ({}). Cannot add user {}.", event.projectId(), project.getMaxTeamSize(), event.userId());
            return;
        }

        ProjectRole role = event.projectRoleId() != null ? projectRoleRepository.findById(event.projectRoleId()).orElse(null) : null;

        // FIX 2: Role Capacity Validation
        if (role != null && role.getFilledCount() >= role.getHeadcount()) {
            log.error("Role '{}' in Project {} is already full. Cannot assign user {}.", role.getTitle(), event.projectId(), event.userId());
            return;
        }

        TeamMember newMember = TeamMember.builder()
                .project(project)
                .projectRole(role)
                .userId(event.userId())
                .status(MemberStatus.ACTIVE)
                .joinedAt(java.time.Instant.now())
                .build();
        teamMemberRepository.save(newMember);

        if (role != null) {
            role.setFilledCount((short) (role.getFilledCount() + 1));
            projectRoleRepository.save(role);
        }
        
        // FIX 4: Check if project is now full and update status to hide from discover feed
        long newActiveMembersCount = teamMemberRepository.countByProjectIdAndStatus(event.projectId(), MemberStatus.ACTIVE);
        if (newActiveMembersCount >= project.getMaxTeamSize() && project.getStatus() == ProjectStatus.RECRUITING) {
            project.setStatus(ProjectStatus.IN_PROGRESS);
            projectRepository.save(project);
            log.info("Project {} has reached max capacity and is now IN_PROGRESS", project.getId());
        }
        
        log.info("Successfully added User: {} to Project: {}", event.userId(), event.projectId());
    }

    // --- INTERNAL HELPERS & MAPPERS ---

    private Project getProjectByIdAndOwner(UUID projectId, UUID ownerId) {
        Project project = projectRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found."));
        if (!project.getOwnerId().equals(ownerId)) throw new SecurityException("Permission denied.");
        return project;
    }

    private void decrementRoleCount(TeamMember member) {
        if (member.getProjectRole() != null) {
            ProjectRole role = member.getProjectRole();
            role.setFilledCount((short) Math.max(0, role.getFilledCount() - 1));
            projectRoleRepository.save(role);
        }
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
        return new ProjectRoleResponse(r.getId(), r.getTitle(), r.getDescription(), r.getRequiredSkills(), r.getHeadcount(), r.getFilledCount());
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

    @Transactional
    public ProjectResponse transferOwnership(UUID currentOwnerId, UUID projectId, UUID newOwnerId) {
        Project project = getProjectByIdAndOwner(projectId, currentOwnerId);

        if (currentOwnerId.equals(newOwnerId)) {
            throw new IllegalArgumentException("You are already the owner of this project.");
        }

        TeamMember newOwnerMember = teamMemberRepository.findByProjectIdAndUserIdAndStatus(projectId, newOwnerId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("The new owner must be an active member of the team."));

        project.setOwnerId(newOwnerId);
        Project savedProject = projectRepository.save(project);

        // THE FIX: Publish the Modulith Event
        eventPublisher.publishEvent(new com.skillsync.skillsync_ai.common.events.ProjectOwnershipTransferredEvent(
                projectId,project.getTitle(), currentOwnerId, newOwnerId
        ));

        return mapToProjectResponse(savedProject);
    }

    @Transactional
    public TeamMemberResponse updateMemberRole(UUID ownerId, UUID projectId, UUID memberUserId, UUID newRoleId) {
        getProjectByIdAndOwner(projectId, ownerId);

        TeamMember member = teamMemberRepository.findByProjectIdAndUserIdAndStatus(projectId, memberUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active team member not found."));

        // THE FIX: Early return if they aren't actually changing roles
        if (member.getProjectRole() != null && member.getProjectRole().getId().equals(newRoleId)) {
            return mapToMemberResponse(member);
        }

        ProjectRole newRole = projectRoleRepository.findById(newRoleId)
                .orElseThrow(() -> new IllegalArgumentException("New role not found."));

        if (!newRole.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Role does not belong to this project.");
        }

        if (newRole.getFilledCount() >= newRole.getHeadcount()) {
            throw new IllegalStateException("The new role is already at maximum capacity.");
        }

        decrementRoleCount(member);

        member.setProjectRole(newRole);
        newRole.setFilledCount((short) (newRole.getFilledCount() + 1));
        projectRoleRepository.save(newRole);

        return mapToMemberResponse(teamMemberRepository.save(member));
    }
}