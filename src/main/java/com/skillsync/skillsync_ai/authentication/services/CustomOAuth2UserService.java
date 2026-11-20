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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository; // You will need to create this basic JpaRepository
    private final TransactionTemplate transactionTemplate;
    private static final String CAMPUS_DOMAIN = "chitkara.edu.in";

    /**
     * Google's userinfo call is an external HTTP round-trip, so it runs before
     * any transaction starts. Only the local user lookup/upsert is wrapped in a
     * short transaction, keeping Neon connections short-lived.
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        String googleSubjectId = (String) attributes.get("sub");

        // Domain validation removed per user request
        User user = transactionTemplate.execute(status -> {
            // Fetch or create user
            Optional<User> userOptional = userRepository.findByEmail(email);
            User localUser;

            if (userOptional.isPresent()) {
                localUser = userOptional.get();
                // Link account if they previously signed up with password
                if (localUser.getAuthProvider() == User.AuthProvider.LOCAL) {
                    localUser.setAuthProvider(User.AuthProvider.BOTH);
                    localUser.setGoogleSubjectId(googleSubjectId);
                    localUser.setEmailVerified(true); // Google verified it
                    userRepository.save(localUser);
                }
            } else {
                // First time login via Google -> Create account
                College college = collegeRepository.findFirstByIsActiveTrueOrderByCreatedAtAsc()
                        .orElseThrow(() -> new RuntimeException("System configuration error: No college seeded."));

                localUser = User.builder()
                        .email(email)
                        .college(college)
                        .authProvider(User.AuthProvider.GOOGLE)
                        .googleSubjectId(googleSubjectId)
                        .role(User.Role.STUDENT)
                        .isEmailVerified(true)
                        .build();
                userRepository.save(localUser);
            }

            return localUser;
        });

        return new DefaultOAuth2User(user.getAuthorities(), attributes, "email");
    }
}