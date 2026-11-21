package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RecommendationPromptFactory {

    /**
     * Bumped when the prompt contract changes so stored recommendations can be
     * traced back to the prompt that produced them.
     */
    public static final String CURRENT_VERSION = "v3.0-shortlist-rerank";

    public String buildBatchMatchPrompt(String projectDataJson, List<CandidateProfileForAi> candidates) {
        String candidateData = candidates.stream()
                .map(c -> "Candidate ID: " + c.userId()
                        + " | Skills: " + formatSkills(c.skills())
                        + " | Achievements: " + formatAchievements(c.achievements()))
                .collect(Collectors.joining("\n"));

        return String.format("""
            You are an expert technical recruiter. Re-rank ONLY the candidates provided below.
            The candidate list is already a deterministic shortlist, so every candidate has at
            least one exact skill match.

            Rules:
            - Score each candidate 0-100 for the project's best matching role.
            - Never penalize a candidate for fitting only one role.
            - Treat certifications, hackathons and shipped projects as strong evidence.
            - Use only the candidate IDs listed below. Never invent an ID.
            - Keep each rationale to one short sentence.
            - Return every candidate exactly once.

            Project Data (overall skills and roles): %s

            Candidates:
            %s

            Return EXACTLY this JSON object and nothing else:
            {
              "matches": [
                {
                  "userId": "UUID string",
                  "semanticScore": 85.5,
                  "confidence": "HIGH",
                  "matchedSkills": ["Java", "AWS Certified"],
                  "rationale": "Strong backend fit for the API role."
                }
              ]
            }
            """, projectDataJson, candidateData);
    }

    private String formatSkills(List<com.skillsync.skillsync_ai.common.events.AiSkill> skills) {
        if (skills == null || skills.isEmpty()) return "None";
        return skills.stream().map(s -> s.name() + "(" + s.level() + ")").collect(Collectors.joining(", "));
    }

    private String formatAchievements(List<com.skillsync.skillsync_ai.common.events.AiAchievement> achievements) {
        if (achievements == null || achievements.isEmpty()) return "None";
        return achievements.stream()
                .map(a -> a.title() + " (" + a.type() + ")")
                .collect(Collectors.joining(", "));
    }

    public String buildBatchProjectsPrompt(
            String studentProfileJson,
            List<com.skillsync.skillsync_ai.common.events.ProjectProfileForAi> projects) {

        String projectData = projects.stream()
                .map(p -> "Project ID: " + p.projectId()
                        + " | Title: " + p.title()
                        + " | Description: " + safeDescription(p.description())
                        + " | Required Skills: " + String.join(", ", p.requiredSkills()))
                .collect(Collectors.joining("\n"));

        return String.format("""
            You are an expert career advisor. Re-rank ONLY the projects provided below for
            this student. The list is already a deterministic shortlist based on exact skill
            matches.

            Rules:
            - Score each project 0-100 for how well it fits the student's skills and goals.
            - Use only the project IDs listed below. Never invent an ID.
            - Keep each rationale to one short sentence.
            - Return every project exactly once.

            Student Profile: %s

            Projects:
            %s

            Return EXACTLY this JSON object and nothing else:
            {
              "matches": [
                {
                  "projectId": "UUID string",
                  "semanticScore": 85.5,
                  "confidence": "HIGH",
                  "matchedSkills": ["Java", "AWS Certified"],
                  "rationale": "The student's backend skills fit this project well."
                }
              ]
            }
            """, studentProfileJson, projectData);
    }

    private String safeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "No description";
        }
        return description.length() > 280 ? description.substring(0, 280) + "..." : description;
    }
}
