package com.skillsync.skillsync_ai.userprofile.dto;

import jakarta.validation.constraints.Size;


public record UpdateProfileBasicInfoRequest(
        @Size(max = 500, message = "Bio cannot exceed 500 characters") String bio,
        String major,
        Integer graduationYear
) {}
