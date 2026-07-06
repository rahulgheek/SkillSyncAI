package com.skillsync.skillsync_ai.userprofile.dto;

public record UploadSignatureResponse(
        String signature,
        long timestamp,
        String cloudName,
        String apiKey,
        String folder
) {}