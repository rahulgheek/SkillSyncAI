package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RecommendationPromptFactory {

    public static final String CURRENT_VERSION = "v2.0-groq-achievements"; // Bumped version for Groq + Achievements

    public String buildBatchMatchPrompt(String projectDataJson, List<CandidateProfileForAi> candidates) {
        String candidateData = candidates.stream()
                .map(c -> "Candidate ID: " + c.userId() + " | Skills: " + formatSkills(c.skills()) + " | Achievements: " + formatAchievements(c.achievements()))
                .collect(Collectors.joining("\n"));

        return String.format("""
            You are an expert technical recruiter AI. 
            Evaluate the semantic match between the project's required skills and the candidates' skills and achievements.
            IMPORTANT: The project may have multiple specific roles (e.g. Frontend, Backend). 
            Do NOT penalize a candidate if they only fit ONE role. Score them based on how well they fit their BEST matching role.
            Consider professional certifications, hackathons, and valid achievements as highly relevant evidence of skill mastery.
            
            Project Data (Overall Skills and Roles): %s
            
            Candidates:
            %s
            
            Return the evaluation EXACTLY as a JSON object using this schema. Do not output anything else.
            {
              "matches": [
                {
                  "userId": "UUID string",
                  "semanticScore": 85.5,
                  "confidence": "HIGH",
                  "matchedSkills": ["Java", "AWS Certified"],
                  "rationale": "2-sentence explanation of why they fit their best matching role (mention which role)."
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

    public String buildBatchProjectsPrompt(String studentProfileJson, List<com.skillsync.skillsync_ai.common.events.ProjectProfileForAi> projects) {
        String projectData = projects.stream()
                .map(p -> "Project ID: " + p.projectId() + " | Title: " + p.title() + " | Required Skills: " + String.join(", ", p.requiredSkills()))
                .collect(Collectors.joining("\n"));

        return String.format("""
            You are an expert career and project advisor AI. 
            Evaluate the semantic match between the student's skills/achievements and each project's required skills.
            Consider the student's professional certifications and valid achievements as highly relevant evidence of skill mastery.
            
            Student Profile: %s
            
            Projects:
            %s
            
            Return the evaluation EXACTLY as a JSON object using this schema. Do not output anything else.
            {
              "matches": [
                {
                  "projectId": "UUID string",
                  "semanticScore": 85.5,
                  "confidence": "HIGH",
                  "matchedSkills": ["Java", "AWS Certified"],
                  "rationale": "2-sentence explanation of why the student is a great fit for this project."
                }
              ]
            }
            """, studentProfileJson, projectData);
    }
}