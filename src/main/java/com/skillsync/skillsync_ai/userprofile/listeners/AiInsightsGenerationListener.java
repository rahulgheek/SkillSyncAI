package com.skillsync.skillsync_ai.userprofile.listeners;

import com.skillsync.skillsync_ai.ai.services.GeminiApiService;
import com.skillsync.skillsync_ai.common.events.ProfileUpdatedEvent;
import com.skillsync.skillsync_ai.userprofile.dto.ProfileResponse;
import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import com.skillsync.skillsync_ai.userprofile.services.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiInsightsGenerationListener {

    private final UserProfileService userProfileService;
    private final StudentProfileRepository profileRepository;
    private final GeminiApiService geminiApiService;

    @Async
    @EventListener
    public void handleProfileUpdatedEvent(ProfileUpdatedEvent event) {
        log.info("Received ProfileUpdatedEvent for user: {}. Generating AI Insights...", event.userId());
        try {
            StudentProfile studentProfile = profileRepository.findByUserId(event.userId()).orElseThrow();
            studentProfile.setInsightsOutdated(true);
            profileRepository.save(studentProfile);
            log.info("Flagged AI Insights as outdated for user: {}", event.userId());
        } catch (Exception e) {
            log.error("Failed to flag AI insights as outdated for user: {}", event.userId(), e);
        }
    }
}
