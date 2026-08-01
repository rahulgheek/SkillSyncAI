package com.skillsync.skillsync_ai.common.events;

import java.util.List;
import java.util.UUID;

public record ProjectProfileForAi(
        UUID projectId,
        String title,
        String description,
        List<String> requiredSkills
) {}
