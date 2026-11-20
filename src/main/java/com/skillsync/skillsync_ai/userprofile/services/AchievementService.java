package com.skillsync.skillsync_ai.userprofile.services;

import com.skillsync.skillsync_ai.userprofile.dto.AchievementRequest;
import com.skillsync.skillsync_ai.userprofile.dto.AchievementResponse;
import com.skillsync.skillsync_ai.userprofile.entity.StudentAchievement;
import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import com.skillsync.skillsync_ai.userprofile.repo.StudentAchievementRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.common.config.KafkaConfig;
import com.skillsync.skillsync_ai.common.events.CertificateUploadedEvent;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final StudentAchievementRepository achievementRepository;
    private final StudentProfileRepository profileRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<AchievementResponse> getAchievementsByProfile(UUID profileId) {
        return achievementRepository.findAllByStudentProfileIdOrderByDateDesc(profileId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public java.util.Map<UUID, List<AchievementResponse>> getAchievementsByProfiles(java.util.Collection<UUID> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            return java.util.Map.of();
        }
        return achievementRepository.findAchievementsForProfiles(profileIds).stream()
                .collect(Collectors.groupingBy(
                        a -> a.getStudentProfile().getId(),
                        Collectors.mapping(this::mapToResponse, Collectors.toList())
                ));
    }

    @Transactional
    public AchievementResponse addAchievement(UUID userId, AchievementRequest request) {
        StudentProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        StudentAchievement achievement = StudentAchievement.builder()
                .studentProfile(profile)
                .title(request.title())
                .organization(request.organization())
                .date(request.date())
                .description(request.description())
                .type(request.type())
                .certificateUrl(request.certificateUrl())
                .build();

        achievement = achievementRepository.save(achievement);
        
        // Mark insights as outdated
        profile.setInsightsOutdated(true);
        profileRepository.save(profile);
        
        if (achievement.getCertificateUrl() != null && !achievement.getCertificateUrl().isBlank()) {
            publishCertificateEvent(userId, achievement);
        }
        
        return mapToResponse(achievement);
    }

    @Transactional
    public AchievementResponse updateAchievement(UUID userId, UUID achievementId, AchievementRequest request) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
                .orElseThrow(() -> new RuntimeException("Achievement not found"));

        if (!achievement.getStudentProfile().getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        achievement.setTitle(request.title());
        achievement.setOrganization(request.organization());
        achievement.setDate(request.date());
        achievement.setDescription(request.description());
        achievement.setType(request.type());
        if (request.certificateUrl() != null) {
            achievement.setCertificateUrl(request.certificateUrl());
        }

        achievement = achievementRepository.save(achievement);
        
        StudentProfile profile = achievement.getStudentProfile();
        profile.setInsightsOutdated(true);
        profileRepository.save(profile);

        if (achievement.getCertificateUrl() != null && !achievement.getCertificateUrl().isBlank()) {
            publishCertificateEvent(userId, achievement);
        }

        return mapToResponse(achievement);
    }

    @Transactional
    public void deleteAchievement(UUID userId, UUID achievementId) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
                .orElseThrow(() -> new RuntimeException("Achievement not found"));

        if (!achievement.getStudentProfile().getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        achievementRepository.delete(achievement);
        
        StudentProfile profile = achievement.getStudentProfile();
        profile.setInsightsOutdated(true);
        profileRepository.save(profile);
    }

    private AchievementResponse mapToResponse(StudentAchievement achievement) {
        return new AchievementResponse(
                achievement.getId(),
                achievement.getTitle(),
                achievement.getOrganization(),
                achievement.getDate(),
                achievement.getDescription(),
                achievement.getType(),
                achievement.getCertificateUrl()
        );
    }

    private void publishCertificateEvent(UUID userId, StudentAchievement achievement) {
        try {
            CertificateUploadedEvent event = new CertificateUploadedEvent(
                    userId,
                    achievement.getTitle(),
                    achievement.getCertificateUrl()
            );
            kafkaTemplate.send(KafkaConfig.CERTIFICATE_UPLOADED_TOPIC, userId.toString(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish certificate uploaded event to Kafka", e);
        }
    }
}
