package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RecommendationPromptFactory {

    public static final String CURRENT_VERSION = "v1.1-batch-skills"; // Bumped version for the new schema

    public String buildBatchMatchPrompt(String requiredSkills, List<CandidateProfileForAi> candidates) {
        String candidateData = candidates.stream()
                .map(c -> "Candidate ID: " + c.userId() + " | Skills: " + formatSkills(c.skills()))
                .collect(Collectors.joining("\n"));

        return String.format("""
            You are an expert technical recruiter AI. 
            Evaluate the semantic match between the project's required skills and the candidates' skills.
            
            Project Required Skills: %s
            
            Candidates:
            %s
            
            Return the evaluation EXACTLY as a JSON array using this schema:
            [
              {
                "userId": "UUID string",
                "semanticScore": 85.5,
                "confidence": "HIGH",
                "matchedSkills": ["Java", "Spring Boot"],
                "rationale": "2-sentence explanation of the skill overlap."
              }
            ]
            """, requiredSkills, candidateData);
    }

    private String formatSkills(List<com.skillsync.skillsync_ai.common.events.AiSkill> skills) {
        return skills.stream().map(s -> s.name() + "(" + s.level() + ")").collect(Collectors.joining(", "));
    }
}