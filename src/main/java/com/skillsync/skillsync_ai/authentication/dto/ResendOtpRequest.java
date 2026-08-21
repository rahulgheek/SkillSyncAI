package com.skillsync.skillsync_ai.authentication.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email format")
        String email
) {}
