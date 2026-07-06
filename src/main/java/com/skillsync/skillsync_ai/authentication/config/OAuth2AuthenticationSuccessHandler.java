package com.skillsync.skillsync_ai.authentication.config;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.authentication.repo.UserRepository;
import com.skillsync.skillsync_ai.authentication.services.JwtService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.frontend.oauth2-redirect-url:http://localhost:3000/oauth2/callback}")
    private String frontendRedirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found after OAuth2 success"));

        // 1. Mint the JWT Access Token
        String accessToken = jwtService.generateToken(user);

        // 2. Generate a secure, one-time exchange code
        String exchangeCode = UUID.randomUUID().toString();

        // 3. Store the token in Redis mapped to the exchange code (Valid for 30 seconds only!)
        redisTemplate.opsForValue().set("oauth2_exchange:" + exchangeCode, accessToken, Duration.ofSeconds(30));

        // 4. Redirect frontend with ONLY the exchange code in the URL
        String redirectUrl = frontendRedirectUrl + "?code=" + exchangeCode;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}