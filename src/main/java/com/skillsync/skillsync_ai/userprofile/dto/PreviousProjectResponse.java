package com.skillsync.skillsync_ai.userprofile.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PreviousProjectResponse(
        UUID id,
        String title,
        String description,
        String projectUrl,
        LocalDate startedOn,
        LocalDate endedOn,
        List<String> techStack,
        String githubUrl
) {}