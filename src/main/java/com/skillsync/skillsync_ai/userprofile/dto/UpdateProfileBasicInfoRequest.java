package com.skillsync.skillsync_ai.userprofile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record UpdateProfileBasicInfoRequest(
        @NotBlank(message = "Name cannot be blank") String fullName,
        @Size(max = 500, message = "Bio cannot exceed 500 characters") String bio,
        String major,
        Integer graduationYear,
        String achievements,
        Boolean isPublic
) {}
