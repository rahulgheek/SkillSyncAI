package com.skillsync.skillsync_ai.userprofile.services;

import com.skillsync.skillsync_ai.userprofile.UserProfileGateway;
import com.skillsync.skillsync_ai.userprofile.repo.PreviousProjectRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileGatewayImpl implements UserProfileGateway {

    private final StudentProfileRepository studentProfileRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final PreviousProjectRepository previousProjectRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfileContext> getUserProfile(UUID userId) {
        return studentProfileRepository.findByUserId(userId)
                .map(profile -> new UserProfileContext(
                        profile.getUserId(),
                        profile.getFullName(),
                        profile.getProfilePictureUrl()
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfileWorkContext> getUserWorkContext(UUID userId) {
        return studentProfileRepository.findByUserId(userId).map(profile -> {
            java.util.List<String> skills = studentSkillRepository.findAllByStudentProfileId(profile.getId())
                    .stream()
                    .map(ss -> ss.getSkill().getName() + " (" + ss.getLevel() + ")")
                    .collect(Collectors.toList());

            java.util.List<String> projects = previousProjectRepository.findByStudentProfile_UserId(userId)
                    .stream()
                    .map(p -> p.getTitle() + ": " + p.getDescription() + " (Tech: " + String.join(", ", p.getTechStack()) + ")")
                    .collect(Collectors.toList());

            return new UserProfileWorkContext(profile.getId(), skills, projects);
        });
    }
}
