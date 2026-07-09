package com.skillsync.skillsync_ai.userprofile.dto;

import java.util.UUID;

public record StudentSkillResponse(UUID skillId, String name, String level, String source,Integer endorsedCount) {}
