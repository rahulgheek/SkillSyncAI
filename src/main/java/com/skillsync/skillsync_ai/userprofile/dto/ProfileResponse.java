package com.skillsync.skillsync_ai.userprofile.dto;

import java.util.List;
import java.util.UUID;

public record ProfileResponse(
        UUID userId,
        String fullName,
        String bio,
        String profilePictureUrl,
        String resumeUrl,
        String linkedinUrl,
        String githubUrl,
        String portfolioUrl,
        String major,
        Integer graduationYear,
        List<PreviousProjectResponse> projects, // Skills list can be added here depending on your Skill DTOs
        List<StudentSkillResponse> skills
) {}