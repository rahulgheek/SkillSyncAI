package com.skillsync.skillsync_ai.project.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.project.dto.CreateProjectRequest;
import com.skillsync.skillsync_ai.project.dto.CreateProjectRoleRequest;
import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.ProjectRole;
import com.skillsync.skillsync_ai.project.entity.TeamMember;
import com.skillsync.skillsync_ai.project.entity.enums.MemberStatus;
import com.skillsync.skillsync_ai.project.entity.enums.ProjectStatus;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository;
import com.skillsync.skillsync_ai.project.repo.ProjectRoleRepository;
import com.skillsync.skillsync_ai.project.repo.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectManagementService {

    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public Project createDraftProject(UUID ownerId, UUID collegeId, CreateProjectRequest request) {
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

        // The owner is automatically the first active team member
        TeamMember ownerMember = TeamMember.builder()
                .project(savedProject)
                .userId(ownerId)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        teamMemberRepository.save(ownerMember);

        log.info("Draft project created with ID: {} by Owner ID: {}", savedProject.getId(), ownerId);
        return savedProject;
    }

    @Transactional
    public ProjectRole addRoleToProject(UUID ownerId, UUID projectId, CreateProjectRoleRequest request) {
        Project project = getProjectByIdAndOwner(projectId, ownerId);

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new IllegalStateException("Can only add roles while project is in DRAFT status.");
        }

        ProjectRole role = ProjectRole.builder()
                .project(project)
                .title(request.title())
                .description(request.description())
                .requiredSkills(request.requiredSkills() != null ? request.requiredSkills() : java.util.Collections.emptyList())
                .headcount(request.headcount())
                .filledCount((short) 0)
                .build();

        return projectRoleRepository.save(role);
    }

    @Transactional
    public Project publishProject(UUID ownerId, UUID projectId) {
        Project project = getProjectByIdAndOwner(projectId, ownerId);

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new IllegalStateException("Project is already published or cannot be published from current state.");
        }

        project.setStatus(ProjectStatus.RECRUITING);
        Project publishedProject = projectRepository.save(project);

        log.info("Project ID {} published for recruiting.", projectId);

        // Convert the required skills list to a JSON string for the AI module
        String requiredSkillsJson = "[]";
        try {
            requiredSkillsJson = objectMapper.writeValueAsString(publishedProject.getRequiredSkills());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse required skills to JSON for AI matching", e);
        }

        // Fire the exact event the AI module is listening for!
        eventPublisher.publishEvent(new com.skillsync.skillsync_ai.common.events.ProjectPublishedEvent(
                publishedProject.getId(),
                publishedProject.getTitle(),
                requiredSkillsJson
        ));

        return publishedProject;
    }

    @Transactional(readOnly = true)
    public Project getProjectByIdAndOwner(UUID projectId, UUID ownerId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found."));

        if (!project.getOwnerId().equals(ownerId)) {
            throw new SecurityException("You do not have permission to modify this project.");
        }
        return project;
    }

    /**
     * SPRING MODULITH BOUNDARY:
     * Listens for accepted applications/invitations from the Matching module.
     * Automatically creates the official TeamMember row.
     */
    @org.springframework.modulith.events.ApplicationModuleListener
    void onTeamMemberAccepted(com.skillsync.skillsync_ai.common.events.TeamMemberAcceptedEvent event) {
        log.info("Received TeamMemberAcceptedEvent for Project: {}, User: {}. Creating Team Member record...",
                event.projectId(), event.userId());

        Project project = projectRepository.findById(event.projectId())
                .orElseThrow(() -> new IllegalStateException("Project not found for accepted member."));

        ProjectRole role = null;
        if (event.projectRoleId() != null) {
            role = projectRoleRepository.findById(event.projectRoleId()).orElse(null);
        }

        TeamMember newMember = TeamMember.builder()
                .project(project)
                .projectRole(role)
                .userId(event.userId())
                .status(MemberStatus.ACTIVE)
                .joinedAt(java.time.Instant.now())
                .build();

        teamMemberRepository.save(newMember);

        // If they filled a specific role, increment the filled_count
        if (role != null) {
            role.setFilledCount((short) (role.getFilledCount() + 1));
            projectRoleRepository.save(role);
        }

        log.info("Successfully added User: {} to Project: {}", event.userId(), event.projectId());
    }
}