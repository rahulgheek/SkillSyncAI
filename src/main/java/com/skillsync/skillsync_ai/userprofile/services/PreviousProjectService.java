package com.skillsync.skillsync_ai.userprofile.services;

import com.skillsync.skillsync_ai.userprofile.dto.PreviousProjectRequest;
import com.skillsync.skillsync_ai.userprofile.dto.PreviousProjectResponse;
import com.skillsync.skillsync_ai.userprofile.entity.PreviousProject;
import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import com.skillsync.skillsync_ai.userprofile.repo.PreviousProjectRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreviousProjectService {

    private final PreviousProjectRepository projectRepository;
    private final StudentProfileRepository profileRepository;

    @Transactional
    public PreviousProjectResponse addProject(UUID userId, PreviousProjectRequest request) {
        StudentProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        // Validation: Ensure time isn't flowing backward
        if (request.endedOn() != null && request.startedOn() != null && request.endedOn().isBefore(request.startedOn())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        PreviousProject project = PreviousProject.builder()
                .studentProfile(profile)
                .title(request.title())
                .description(request.description())
                .projectUrl(request.projectUrl())
                .startedOn(request.startedOn())
                .endedOn(request.endedOn())
                .build();

        PreviousProject saved = projectRepository.save(project);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteProject(UUID userId, UUID projectId) {
        // We use the custom repository method to guarantee this user owns this project
        PreviousProject project = projectRepository.findByIdAndStudentProfile_UserId(projectId, userId)
                .orElseThrow(() -> new RuntimeException("Project not found or unauthorized"));

        projectRepository.delete(project);
    }

    @Transactional(readOnly = true)
    public List<PreviousProjectResponse> getProjectsForUser(UUID userId) {
        return projectRepository.findByStudentProfile_UserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private PreviousProjectResponse mapToResponse(PreviousProject project) {
        return new PreviousProjectResponse(
                project.getId(), project.getTitle(), project.getDescription(),
                project.getProjectUrl(), project.getStartedOn(), project.getEndedOn()
        );
    }

    @Transactional
    public PreviousProjectResponse updateProject(UUID userId, UUID projectId, PreviousProjectRequest request) {
        PreviousProject project = projectRepository.findByIdAndStudentProfile_UserId(projectId, userId)
                .orElseThrow(() -> new RuntimeException("Project not found or unauthorized"));

        if (request.endedOn() != null && request.startedOn() != null && request.endedOn().isBefore(request.startedOn())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        project.setTitle(request.title());
        project.setDescription(request.description());
        project.setProjectUrl(request.projectUrl());
        project.setStartedOn(request.startedOn());
        project.setEndedOn(request.endedOn());

        return mapToResponse(projectRepository.save(project));
    }
}