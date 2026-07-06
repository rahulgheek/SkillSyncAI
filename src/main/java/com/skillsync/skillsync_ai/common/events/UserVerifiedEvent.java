package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

// This record is the contract between the Authentication and UserProfile modules
public record UserVerifiedEvent(
        UUID userId,
        String email
) {}