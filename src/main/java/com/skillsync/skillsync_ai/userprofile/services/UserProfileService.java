package com.skillsync.skillsync_ai.userprofile.services;

import com.skillsync.skillsync_ai.common.events.ResumeUploadedEvent; // Assuming you have this record
import com.skillsync.skillsync_ai.userprofile.dto.*;
import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final StudentProfileRepository profileRepository;
    private final ApplicationEventPublisher eventPublisher;

    private StudentProfile getProfileOrThrow(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Profile not found for user: " + userId));
    }

    @Transactional
    public void updateBasicInfo(UUID userId, UpdateProfileBasicInfoRequest request) {
        StudentProfile profile = getProfileOrThrow(userId);
        profile.setBio(request.bio());
        profile.setMajor(request.major());
        int year = request.graduationYear();
        profile.setGraduationYear((short)year);
        profileRepository.save(profile);
    }

    @Transactional
    public void updateSocialLinks(UUID userId, UpdateSocialLinksRequest request) {
        StudentProfile profile = getProfileOrThrow(userId);
        profile.setLinkedinUrl(request.linkedinUrl());
        profile.setGithubUrl(request.githubUrl());
        profile.setPortfolioUrl(request.portfolioUrl());
        profileRepository.save(profile);
    }

    @Transactional
    public void updateResumeUrl(UUID userId, UpdateMediaUrlRequest request) {
        StudentProfile profile = getProfileOrThrow(userId);

        // IDEMPOTENCY CHECK: Only trigger AI if the URL actually changed
        if (request.url().equals(profile.getResumeUrl())) {
            return;
        }

        profile.setResumeUrl(request.url());
        profileRepository.save(profile);

        // Shout into the void! The AI module will hear this and start parsing.
        eventPublisher.publishEvent(new ResumeUploadedEvent(userId, request.url()));
    }

    @Transactional
    public void updateAvatarUrl(UUID userId, UpdateMediaUrlRequest request) {
        StudentProfile profile = getProfileOrThrow(userId);
        profile.setProfilePictureUrl(request.url());
        profileRepository.save(profile);
    }
}