package com.skillsync.skillsync_ai.project.services;

import com.skillsync.skillsync_ai.common.exception.ResourceNotFoundException;
import com.skillsync.skillsync_ai.project.ProjectGateway;
import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.enums.MemberStatus;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository;
import com.skillsync.skillsync_ai.project.repo.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectGatewayImpl implements ProjectGateway {

    private final ProjectRepository projectRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Override
    @Transactional(readOnly = true)
    public ProjectMatchingContext getProjectContext(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(p -> new ProjectMatchingContext(p.getId(), p.getOwnerId(), p.getCollegeId()))
                .orElseThrow(() -> new IllegalArgumentException("Project not found for matching context"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserActiveMember(UUID projectId, UUID userId) {
        // 1. Fetch the project to check ownership
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found for messaging context"));

        // 2. If the user is the owner, they are authorized
        if (project.getOwnerId().equals(userId)) {
            return true;
        }

        // 3. If they are not the owner, verify they have an ACTIVE member record
        return teamMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, userId, MemberStatus.ACTIVE)
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<ProjectChatContext> getUserActiveProjects(UUID userId) {
        java.util.List<ProjectChatContext> contexts = new java.util.ArrayList<>();
        
        // 1. Projects the user owns
        projectRepository.findAllByOwnerId(userId, org.springframework.data.domain.Pageable.unpaged())
            .forEach(p -> contexts.add(new ProjectChatContext(
                p.getId(), p.getTitle(), java.time.LocalDateTime.ofInstant(p.getCreatedAt(), java.time.ZoneId.systemDefault())
            )));
            
        // 2. Projects the user is a member of
        teamMemberRepository.findAllByUserIdAndStatus(userId, MemberStatus.ACTIVE)
            .forEach(tm -> {
                projectRepository.findById(tm.getProject().getId()).ifPresent(p -> {
                    // Avoid duplicates if owner is also a member somehow
                    if (!p.getOwnerId().equals(userId)) {
                        contexts.add(new ProjectChatContext(
                            p.getId(), p.getTitle(), java.time.LocalDateTime.ofInstant(p.getCreatedAt(), java.time.ZoneId.systemDefault())
                        ));
                    }
                });
            });
            
        return contexts;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDiscoveryContext getProjectDiscoveryContext(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(p -> new ProjectDiscoveryContext(p.getId(), p.getTitle(), p.getDescription()))
                .orElseThrow(() -> new IllegalArgumentException("Project not found for discovery context"));
    }
}