package com.skillsync.skillsync_ai.ai;

import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import java.util.List;
import java.util.UUID;

public interface AiMatchingGateway {
    void analyzeCandidatesBatch(UUID projectId, String requiredSkillsJson, List<CandidateProfileForAi> candidates);
    void analyzeProjectsBatch(UUID studentUserId, String studentProfileJson, List<com.skillsync.skillsync_ai.common.events.ProjectProfileForAi> projects);
}