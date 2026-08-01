package com.skillsync.skillsync_ai.authentication.services;

import com.skillsync.skillsync_ai.authentication.entity.College;
import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.authentication.repo.CollegeRepository;
import com.skillsync.skillsync_ai.authentication.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository; // You will need to create this basic JpaRepository
    private static final String CAMPUS_DOMAIN = "chitkara.edu.in";

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        String googleSubjectId = (String) attributes.get("sub");

        // Domain validation removed per user request
        // Fetch or create user
        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            // Link account if they previously signed up with password
            if (user.getAuthProvider() == User.AuthProvider.LOCAL) {
                user.setAuthProvider(User.AuthProvider.BOTH);
                user.setGoogleSubjectId(googleSubjectId);
                user.setEmailVerified(true); // Google verified it
                userRepository.save(user);
            }
        } else {
            // First time login via Google -> Create account
            College college = collegeRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("System configuration error: No college seeded."));

            user = User.builder()
                    .email(email)
                    .college(college)
                    .authProvider(User.AuthProvider.GOOGLE)
                    .googleSubjectId(googleSubjectId)
                    .role(User.Role.STUDENT)
                    .isEmailVerified(true)
                    .build();
            userRepository.save(user);
        }

        return new DefaultOAuth2User(user.getAuthorities(), attributes, "email");
    }
}