package com.skillsync.skillsync_ai.authentication.config;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.authentication.repo.UserRepository;
import com.skillsync.skillsync_ai.authentication.services.JwtService;
import com.skillsync.skillsync_ai.authentication.store.ExchangeCodeStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ExchangeCodeStore exchangeCodeStore;

    @Value("${app.frontend.oauth2-redirect-url:http://localhost:5173/oauth2/callback}")
    private String frontendRedirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found after OAuth2 success"));

        // 1. Mint the JWT Access Token
        java.util.Map<String, Object> extraClaims = new java.util.HashMap<>();
        extraClaims.put("userId", user.getId().toString());
        String accessToken = jwtService.generateToken(extraClaims, user);

        // 2. Generate a secure, one-time exchange code
        String exchangeCode = UUID.randomUUID().toString();

        // 3. Store the token mapped to the exchange code (valid for exactly 30 seconds)
        exchangeCodeStore.store(exchangeCode, accessToken);

        // 4. Redirect frontend with ONLY the exchange code in the URL
        String redirectUrl = frontendRedirectUrl + "?code=" + exchangeCode;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}