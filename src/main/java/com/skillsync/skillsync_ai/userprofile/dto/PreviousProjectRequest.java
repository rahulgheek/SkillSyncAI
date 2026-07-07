package com.skillsync.skillsync_ai.userprofile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

public record PreviousProjectRequest(
        @NotBlank(message = "Title is required") String title,
        @Size(max = 1000) String description,
        @URL(message = "Must be a valid URL") String projectUrl,
        LocalDate startedOn,
        LocalDate endedOn
) {}