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
    private final com.skillsync.skillsync_ai.ai.services.GeminiApiService geminiApiService;

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
        
        if (request.achievements() != null) {
            profile.setAchievements(request.achievements());
        }
        if (request.isPublic() != null) {
            profile.setPublic(request.isPublic());
        }

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
        
        if (request.achievements() != null) {
            profile.setAchievements(request.achievements());
        }
        if (request.isPublic() != null) {
            profile.setPublic(request.isPublic());
        }

        if (request.graduationYear() != null) {
            profile.setGraduationYear(request.graduationYear().shortValue());
        }
        profileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ProfileResponse> searchPublicProfiles(String query, UUID currentUserId, org.springframework.data.domain.Pageable pageable) {
        return profileRepository.searchPublicProfiles(query != null ? query : "", currentUserId, pageable)
                .map(p -> getAggregatedProfile(p.getUserId()));
    }

    @Transactional(readOnly = true)
    public ProfileResponse getPublicProfile(UUID targetUserId) {
        StudentProfile profile = getProfileOrThrow(targetUserId);
        if (!profile.isPublic()) {
            throw new com.skillsync.skillsync_ai.common.exception.UnauthorizedActionException("This profile is private.");
        }
        return getAggregatedProfile(targetUserId);
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

        Object aiInsightsObj = null;
        if (profile.getAiInsights() != null && !profile.getAiInsights().isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                aiInsightsObj = mapper.readValue(profile.getAiInsights(), Object.class);
            } catch (Exception e) {
                // Ignore parse errors, just return null
            }
        }

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
                skills, // Return them to the frontend
                profile.getAchievements(),
                profile.isPublic(),
                aiInsightsObj,
                profile.isInsightsOutdated()
        );
    }

    @Transactional
    public ProfileResponse generateAiInsights(UUID userId) {
        ProfileResponse profile = getAggregatedProfile(userId);
        
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Bio: ").append(profile.bio()).append("\n");
        contextBuilder.append("Major: ").append(profile.major()).append("\n");
        
        contextBuilder.append("Skills: ");
        if (profile.skills() != null) {
            profile.skills().forEach(s -> contextBuilder.append(s.name()).append(" (").append(s.level()).append("), "));
        }
        contextBuilder.append("\n");
        
        contextBuilder.append("Projects: ");
        if (profile.projects() != null) {
            profile.projects().forEach(p -> contextBuilder.append(p.title()).append(" - ").append(p.description()).append("; "));
        }
        contextBuilder.append("\n");

        String context = contextBuilder.toString();
        
        var response = geminiApiService.generateProfileInsights(context).block();

        if (response != null && response.getExtractedText() != null) {
            String insightsJson = response.getExtractedText();
            StudentProfile studentProfile = profileRepository.findByUserId(userId).orElseThrow();
            studentProfile.setAiInsights(insightsJson);
            studentProfile.setInsightsOutdated(false);
            profileRepository.save(studentProfile);
        }

        return getAggregatedProfile(userId);
    }
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