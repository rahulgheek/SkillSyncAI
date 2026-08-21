package com.skillsync.skillsync_ai.authentication.controller;

import com.skillsync.skillsync_ai.authentication.dto.AuthResponse;
import com.skillsync.skillsync_ai.authentication.dto.LoginRequest;
import com.skillsync.skillsync_ai.authentication.dto.RegisterRequest;
import com.skillsync.skillsync_ai.authentication.dto.ResendOtpRequest;
import com.skillsync.skillsync_ai.authentication.dto.VerifyRequest;
import com.skillsync.skillsync_ai.authentication.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        String response = authService.registerStudent(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<String> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        String response = authService.resendOtp(request.email());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<String> verifyOtp(@Valid @RequestBody VerifyRequest request) {
        String response = authService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.loginUser(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    // Add this to AuthController.java

    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate; // Inject this via constructor

    @GetMapping("/oauth2/exchange")
    public ResponseEntity<AuthResponse> exchangeCodeForToken(@RequestParam String code) {
        String redisKey = "oauth2_exchange:" + code;
        String token = redisTemplate.opsForValue().get(redisKey);

        if (token == null) {
            return ResponseEntity.status(400).body(new AuthResponse(null, "Invalid or expired exchange code."));
        }

        // Delete it immediately so it can only be used ONCE
        redisTemplate.delete(redisKey);

        return ResponseEntity.ok(new AuthResponse(token, "OAuth2 login successful"));
    }
}