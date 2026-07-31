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
import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.common.config.KafkaConfig;

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
    private final AchievementService achievementService;
    private final StudentSkillRepository studentSkillRepository;
    private final com.skillsync.skillsync_ai.ai.services.GeminiApiService geminiApiService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

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

        try {
            ResumeUploadedEvent event = new ResumeUploadedEvent(userId, request.url());
            kafkaTemplate.send(KafkaConfig.RESUME_UPLOADED_TOPIC, userId.toString(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish resume uploaded event to Kafka", e);
        }
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
                aiInsightsObj = objectMapper.readValue(profile.getAiInsights(), Object.class);
            } catch (Exception e) {
                // Ignore parse errors, just return null
            }
        }

        List<AchievementResponse> achievements = achievementService.getAchievementsByProfile(profile.getId());

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
                achievements,
                profile.isPublic(),
                aiInsightsObj,
                profile.isInsightsOutdated(),
                profile.getResumeSummary()
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

        contextBuilder.append("Achievements: ");
        if (profile.achievements() != null) {
            profile.achievements().forEach(a -> contextBuilder.append(a.title()).append(" at ").append(a.organization()).append(" (").append(a.type()).append("); "));
        }
        contextBuilder.append("\n");

        String context = contextBuilder.toString();
        
        var response = geminiApiService.generateProfileInsights(context).block();

        if (response != null && response.getExtractedText() != null) {
            String insightsJson = response.getExtractedText().trim();
            if (insightsJson.startsWith("```json")) {
                insightsJson = insightsJson.substring(7);
            } else if (insightsJson.startsWith("```")) {
                insightsJson = insightsJson.substring(3);
            }
            if (insightsJson.endsWith("```")) {
                insightsJson = insightsJson.substring(0, insightsJson.length() - 3);
            }
            insightsJson = insightsJson.trim();
            
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