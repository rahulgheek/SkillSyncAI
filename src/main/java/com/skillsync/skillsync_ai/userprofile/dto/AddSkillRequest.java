package com.skillsync.skillsync_ai.userprofile.dto;

import com.skillsync.skillsync_ai.userprofile.entity.enums.SkillLevel;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddSkillRequest(
        @NotNull(message = "Skill ID is required")
        UUID skillId,

        @NotNull(message = "Skill level is required")
        SkillLevel level
) {}