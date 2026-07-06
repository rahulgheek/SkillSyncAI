package com.skillsync.skillsync_ai.userprofile.services;

import com.skillsync.skillsync_ai.common.events.UserVerifiedEvent;
import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final StudentProfileRepository profileRepository;

    /**
     * SPRING MODULITH BOUNDARY:
     * Listens for the UserVerifiedEvent from the Authentication module.
     * Automatically creates an empty profile for the new user.
     */
    @ApplicationModuleListener
    void onUserVerified(UserVerifiedEvent event) {
        log.info("Received UserVerifiedEvent for User ID: {}. Initializing Student Profile...", event.userId());

        // Extract a default name from the email (e.g., "john.doe@chitkara.edu.in" -> "John Doe")
        String defaultName = extractNameFromEmail(event.email());

        StudentProfile newProfile = StudentProfile.builder()
                .userId(event.userId())
                .fullName(defaultName)
                .build();

        profileRepository.save(newProfile);
        log.info("Student Profile initialized successfully for User ID: {}", event.userId());
    }

    @Transactional(readOnly = true)
    public StudentProfile getProfileByUserId(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Profile not found for User ID: " + userId));
    }

    @Transactional
    public StudentProfile updateProfileBasicInfo(UUID userId, String fullName, String bio, Short gradYear, String major) {
        StudentProfile profile = getProfileByUserId(userId);

        if (fullName != null && !fullName.isBlank()) profile.setFullName(fullName);
        if (bio != null) profile.setBio(bio);
        if (gradYear != null) profile.setGraduationYear(gradYear);
        if (major != null) profile.setMajor(major);

        return profileRepository.save(profile);
    }

    @Transactional
    public void updateResumeUrl(UUID userId, String secureUrl) {
        StudentProfile profile = getProfileByUserId(userId);
        profile.setResumeUrl(secureUrl);
        profile.setResumeUploadedAt(java.time.Instant.now());
        profileRepository.save(profile);

        // TODO: Publish a 'ResumeUploadedEvent' here for the AI module to consume via Kafka
    }

    @Transactional
    public void updateProfilePictureUrl(UUID userId, String secureUrl) {
        StudentProfile profile = getProfileByUserId(userId);
        profile.setProfilePictureUrl(secureUrl);
        profileRepository.save(profile);
    }

    private String extractNameFromEmail(String email) {
        if (email == null || !email.contains("@")) return "New Student";
        String localPart = email.split("@")[0];
        // Replace dots with spaces and capitalize (basic formatting)
        String[] parts = localPart.split("\\.");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                name.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return name.toString().trim();
    }
}