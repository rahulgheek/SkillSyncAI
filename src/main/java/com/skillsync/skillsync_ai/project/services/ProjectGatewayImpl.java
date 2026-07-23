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
}