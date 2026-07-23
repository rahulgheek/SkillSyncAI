package com.skillsync.skillsync_ai.userprofile.services;

import com.skillsync.skillsync_ai.common.events.ResumeUploadedEvent;
import com.skillsync.skillsync_ai.userprofile.dto.*;
import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.skillsync.skillsync_ai.common.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final StudentProfileRepository profileRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PreviousProjectService previousProjectService;
    private final StudentSkillRepository studentSkillRepository;

    private StudentProfile getProfileOrThrow(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for user: " + userId));
    }

    @Transactional(readOnly = true)
    public StudentProfile getProfileByUserId(UUID userId) {
        return getProfileOrThrow(userId);
    }

    @Transactional
    public ProfileResponse createProfile(UUID userId, UpdateProfileBasicInfoRequest request) {
        if (profileRepository.findByUserId(userId).isPresent()) {
            throw new RuntimeException("Profile already exists for user: " + userId);
        }
        StudentProfile profile = new StudentProfile();
        profile.setUserId(userId);
        profile.setFullName(request.fullName());
        profile.setBio(request.bio());
        profile.setMajor(request.major());

        if (request.graduationYear() != null) {
            profile.setGraduationYear(request.graduationYear().shortValue());
        }
        profileRepository.save(profile);
        return getAggregatedProfile(userId);
    }

    @Transactional
    public void updateBasicInfo(UUID userId, UpdateProfileBasicInfoRequest request) {
        StudentProfile profile = getProfileOrThrow(userId);
        profile.setFullName(request.fullName()); // Added Name Update
        profile.setBio(request.bio());
        profile.setMajor(request.major());

        if (request.graduationYear() != null) {
            profile.setGraduationYear(request.graduationYear().shortValue());
        }
        profileRepository.save(profile);
    }

    @Transactional
    public void updateSocialLinks(UUID userId, UpdateSocialLinksRequest request) {
        StudentProfile profile = getProfileOrThrow(userId);
        profile.setLinkedinUrl(request.linkedinUrl());
        profile.setGithubUrl(request.githubUrl());
        profile.setPortfolioUrl(request.portfolioUrl());
        profileRepository.save(profile);
    }

    @Transactional
    public void updateResumeUrl(UUID userId, UpdateMediaUrlRequest request) {
        StudentProfile profile = getProfileOrThrow(userId);

        if (request.url().equals(profile.getResumeUrl())) {
            return;
        }

        profile.setResumeUrl(request.url());
        profile.setResumeUploadedAt(java.time.Instant.now()); // Added Timestamp Update!
        profileRepository.save(profile);

        eventPublisher.publishEvent(new ResumeUploadedEvent(userId, request.url()));
    }

    @Transactional
    public void updateAvatarUrl(UUID userId, UpdateMediaUrlRequest request) {
        StudentProfile profile = getProfileOrThrow(userId);
        profile.setProfilePictureUrl(request.url());
        profileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getAggregatedProfile(UUID userId) {
        StudentProfile profile = getProfileOrThrow(userId);

        List<PreviousProjectResponse> projects = previousProjectService.getProjectsForUser(userId);

        // Fetch and map the skills!
        List<StudentSkillResponse> skills = studentSkillRepository.findAllByStudentProfileId(profile.getId())
                .stream()
                .map(ss -> new StudentSkillResponse(
                        ss.getId(),
                        ss.getSkill().getId(),
                        ss.getSkill().getName(),
                        ss.getLevel().name(),
                        ss.getSource().name(),
                        ss.getEndorsedCount()
                ))
                .toList();

        return new ProfileResponse(
                profile.getUserId(),
                profile.getFullName(),
                profile.getBio(),
                profile.getProfilePictureUrl(),
                profile.getResumeUrl(),
                profile.getLinkedinUrl(),
                profile.getGithubUrl(),
                profile.getPortfolioUrl(),
                profile.getMajor(),
                profile.getGraduationYear() != null ? (int) profile.getGraduationYear() : null,
                projects,
                skills // Return them to the frontend
        );
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> getAllUserSkillsForMatching() {
        return studentSkillRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ss -> ss.getStudentProfile().getUserId(),
                        java.util.stream.Collectors.mapping(
                                ss -> ss.getSkill().getName() + " (" + ss.getLevel().name() + ")",
                                java.util.stream.Collectors.joining(", ")
                        )
                ));
    }
}