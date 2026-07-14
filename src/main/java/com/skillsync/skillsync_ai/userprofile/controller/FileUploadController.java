package com.skillsync.skillsync_ai.userprofile.controller;

import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.userprofile.dto.UploadSignatureResponse;
import com.skillsync.skillsync_ai.userprofile.services.CloudinarySignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class FileUploadController {

    private final CloudinarySignatureService signatureService;

    /**
     * Frontend calls this BEFORE uploading to Cloudinary.
     * @param type e.g., "resume" or "avatar"
     */
    @GetMapping("/signature")
    public ResponseEntity<ApiResponse<UploadSignatureResponse>> getUploadSignature(
            @RequestParam(defaultValue = "misc") String type) {

        String folder = type.equalsIgnoreCase("resume") ? "skillsync/resumes" : "skillsync/avatars";

        UploadSignatureResponse response = signatureService.generateSignature(folder);
        return ResponseEntity.ok(ApiResponse.success("Signature generated successfully", response));
    }
}