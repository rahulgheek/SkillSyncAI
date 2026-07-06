package com.skillsync.skillsync_ai.userprofile.services;

import com.cloudinary.Cloudinary;
import com.skillsync.skillsync_ai.userprofile.dto.UploadSignatureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinarySignatureService {

    private final Cloudinary cloudinary;

    @Value("${CLOUDINARY_CLOUD_NAME}")
    private String cloudName;

    @Value("${CLOUDINARY_API_KEY}")
    private String apiKey;

    public UploadSignatureResponse generateSignature(String folderName) {
        long timestamp = System.currentTimeMillis() / 1000L;

        // Parameters that MUST be signed
        Map<String, Object> paramsToSign = new HashMap<>();
        paramsToSign.put("timestamp", timestamp);
        paramsToSign.put("folder", folderName);

        // Generate the cryptographic signature using your API Secret
        String signature = cloudinary.apiSignRequest(paramsToSign, cloudinary.config.apiSecret);

        log.info("Generated Cloudinary upload signature for folder: {}", folderName);

        return new UploadSignatureResponse(
                signature,
                timestamp,
                cloudName,
                apiKey,
                folderName
        );
    }
}