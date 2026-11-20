package com.skillsync.skillsync_ai.userprofile.services;

import com.skillsync.skillsync_ai.common.events.ResumeUploadedEvent;
import com.skillsync.skillsync_ai.userprofile.dto.*;
import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import lombok.RequiredArgsConstructor;
import com.skillsync.skillsync_ai.authentication.repo.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.common.config.KafkaConfig;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.skillsync.skillsync_ai.common.exception.ResourceNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final StudentProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PreviousProjectService previousProjectService;
    private final AchievementService achievementService;
    private final StudentSkillRepository studentSkillRepository;
    private final com.skillsync.skillsync_ai.ai.services.GeminiApiService geminiApiService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    private StudentProfile getProfileOrThrow(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    StudentProfile profile = new StudentProfile();
                    profile.setUserId(userId);
                    profile.setFullName("New User");
                    profile.setBio("Update your bio here!");
                    profile.setPublic(true);
                    return profileRepository.save(profile);
                });
    }

    @Transactional
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
        org.springframework.data.domain.Page<StudentProfile> page =
                profileRepository.searchPublicProfiles(query != null ? query : "", currentUserId, pageable);

        // Load profiles, emails, projects, skills and achievements in batches.
        // The previous implementation ran five queries per profile, which is
        // catastrophic on a remote Neon connection.
        Map<UUID, ProfileResponse> responses = buildProfileResponses(page.getContent());
        return page.map(p -> responses.get(p.getUserId()));
    }

    @Transactional
    public ProfileResponse getPublicProfile(UUID targetUserId) {
        StudentProfile profile = getProfileOrThrow(targetUserId);
        if (!profile.isPublic()) {
            throw new com.skillsync.skillsync_ai.common.exception.UnauthorizedActionException("This profile is private.");
        }
        ProfileResponse full = buildProfileResponses(List.of(profile)).get(targetUserId);
        // Strip email — only the owner should see their own email
        return new ProfileResponse(
                full.userId(), full.fullName(), null, full.bio(),
                full.profilePictureUrl(), full.resumeUrl(), full.linkedinUrl(),
                full.githubUrl(), full.portfolioUrl(), full.major(),
                full.graduationYear(), full.projects(), full.skills(),
                full.achievements(), full.isPublic(), full.aiInsights(),
                full.insightsOutdated(), full.resumeSummary()
        );
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

    @Transactional
    public ProfileResponse getAggregatedProfile(UUID userId) {
        StudentProfile profile = getProfileOrThrow(userId);
        return buildProfileResponses(List.of(profile)).get(userId);
    }

    /**
     * Builds profile responses with a fixed number of queries regardless of
     * how many profiles are on the page.
     */
    private Map<UUID, ProfileResponse> buildProfileResponses(List<StudentProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return Map.of();
        }

        List<UUID> userIds = profiles.stream().map(StudentProfile::getUserId).distinct().toList();
        List<UUID> profileIds = profiles.stream().map(StudentProfile::getId).distinct().toList();

        Map<UUID, String> emails = userRepository.findEmailsByIdIn(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.skillsync.skillsync_ai.authentication.repo.UserRepository.UserEmailProjection::getId,
                        com.skillsync.skillsync_ai.authentication.repo.UserRepository.UserEmailProjection::getEmail
                ));

        Map<UUID, List<PreviousProjectResponse>> projectsByUser =
                previousProjectService.getProjectsForUsers(userIds);

        Map<UUID, List<StudentSkillResponse>> skillsByProfile = studentSkillRepository
                .findAllByStudentProfileIdIn(profileIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ss -> ss.getStudentProfile().getId(),
                        java.util.stream.Collectors.mapping(this::mapStudentSkill, java.util.stream.Collectors.toList())
                ));

        Map<UUID, List<AchievementResponse>> achievementsByProfile =
                achievementService.getAchievementsByProfiles(profileIds);

        return profiles.stream().collect(java.util.stream.Collectors.toMap(
                StudentProfile::getUserId,
                profile -> mapProfileResponse(
                        profile,
                        emails.getOrDefault(profile.getUserId(), ""),
                        projectsByUser.getOrDefault(profile.getUserId(), List.of()),
                        skillsByProfile.getOrDefault(profile.getId(), List.of()),
                        achievementsByProfile.getOrDefault(profile.getId(), List.of())
                )
        ));
    }

    private StudentSkillResponse mapStudentSkill(com.skillsync.skillsync_ai.userprofile.entity.StudentSkill ss) {
        return new StudentSkillResponse(
                ss.getId(),
                ss.getSkill().getId(),
                ss.getSkill().getName(),
                ss.getLevel().name(),
                ss.getSource().name(),
                ss.getEndorsedCount()
        );
    }

    private ProfileResponse mapProfileResponse(
            StudentProfile profile,
            String userEmail,
            List<PreviousProjectResponse> projects,
            List<StudentSkillResponse> skills,
            List<AchievementResponse> achievements) {

        Object aiInsightsObj = null;
        if (profile.getAiInsights() != null && !profile.getAiInsights().isBlank()) {
            try {
                aiInsightsObj = objectMapper.readValue(profile.getAiInsights(), Object.class);
            } catch (Exception e) {
                log.warn("Failed to parse AI Insights JSON for profile {}: {}", profile.getId(), e.getMessage());
            }
        }

        return new ProfileResponse(
                profile.getUserId(),
                profile.getFullName(),
                userEmail,
                profile.getBio(),
                profile.getProfilePictureUrl(),
                profile.getResumeUrl(),
                profile.getLinkedinUrl(),
                profile.getGithubUrl(),
                profile.getPortfolioUrl(),
                profile.getMajor(),
                profile.getGraduationYear() != null ? (int) profile.getGraduationYear() : null,
                projects,
                skills,
                achievements,
                profile.isPublic(),
                aiInsightsObj,
                profile.isInsightsOutdated(),
                profile.getResumeSummary()
        );
    }

    /**
     * Gemini is an external, potentially slow call. Keep it outside any
     * database transaction so Neon is not kept awake for the duration.
     */
    public ProfileResponse generateAiInsights(UUID userId) {
        ProfileResponse profile = transactionTemplate.execute(status -> getAggregatedProfile(userId));

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
            String finalInsightsJson = insightsJson;

            transactionTemplate.executeWithoutResult(status -> {
                StudentProfile studentProfile = profileRepository.findByUserId(userId).orElseThrow();
                studentProfile.setAiInsights(finalInsightsJson);
                studentProfile.setInsightsOutdated(false);
                profileRepository.save(studentProfile);
            });
        }

        return transactionTemplate.execute(status -> getAggregatedProfile(userId));
    }
    public Map<UUID, String> getAllUserSkillsForMatching() {
        // Projection query: no lazy profile/skill initialisation and no N+1.
        return studentSkillRepository.findAllActiveStudentSkills().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        com.skillsync.skillsync_ai.userprofile.repo.StudentSkillProjection::getUserId,
                        java.util.stream.Collectors.mapping(
                                ss -> ss.getSkillName() + " (" + ss.getSkillLevel() + ")",
                                java.util.stream.Collectors.joining(", ")
                        )
                ));
    }
}