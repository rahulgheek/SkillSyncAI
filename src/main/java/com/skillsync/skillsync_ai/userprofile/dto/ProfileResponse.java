package com.skillsync.skillsync_ai.userprofile.dto;

import java.util.List;
import java.util.UUID;

public record ProfileResponse(
        UUID userId,
        String fullName,
        String email,
        String bio,
        String profilePictureUrl,
        String resumeUrl,
        String linkedinUrl,
        String githubUrl,
        String portfolioUrl,
        String major,
        Integer graduationYear,
        List<PreviousProjectResponse> projects,
        List<StudentSkillResponse> skills,
        List<AchievementResponse> achievements,
        boolean isPublic,
        Object aiInsights,
        boolean insightsOutdated,
        String resumeSummary
) {}