package com.skillsync.skillsync_ai.common.events;

import com.skillsync.skillsync_ai.ai.entity.ExtractedSkill;
import java.util.List;
import java.util.UUID;

public record ResumeAnalysisCompletedEvent(
        UUID userId,
        List<ExtractedSkill> extractedSkills
) {}