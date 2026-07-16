package com.skillsync.skillsync_ai.project.services;

import com.skillsync.skillsync_ai.project.ProjectGateway;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectGatewayImpl implements ProjectGateway {

    private final ProjectRepository projectRepository;

    @Override
    @Transactional(readOnly = true)
    public ProjectMatchingContext getProjectContext(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(p -> new ProjectMatchingContext(p.getId(), p.getOwnerId(), p.getCollegeId()))
                .orElseThrow(() -> new IllegalArgumentException("Project not found for matching context"));
    }
}