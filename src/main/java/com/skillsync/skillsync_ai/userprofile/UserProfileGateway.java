package com.skillsync.skillsync_ai.userprofile;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileGateway {
    
    record UserProfileContext(UUID userId, String fullName, String profilePictureUrl) {}

    Optional<UserProfileContext> getUserProfile(UUID userId);

    record UserProfileWorkContext(UUID profileId, java.util.List<String> skills, java.util.List<String> projects) {}
    
    Optional<UserProfileWorkContext> getUserWorkContext(UUID userId);
}
