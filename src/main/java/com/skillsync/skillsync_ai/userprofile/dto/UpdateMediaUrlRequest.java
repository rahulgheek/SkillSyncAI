package com.skillsync.skillsync_ai.userprofile.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record UpdateMediaUrlRequest(
        @NotBlank(message = "URL cannot be blank")
        @URL(message = "Must be a valid URL")
        String url
) {}