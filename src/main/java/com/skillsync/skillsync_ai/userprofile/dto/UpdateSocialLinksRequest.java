package com.skillsync.skillsync_ai.userprofile.dto;

import org.hibernate.validator.constraints.URL;

public record UpdateSocialLinksRequest(
        @URL(message = "Must be a valid URL") String linkedinUrl,
        @URL(message = "Must be a valid URL") String githubUrl,
        @URL(message = "Must be a valid URL") String portfolioUrl
) {}
