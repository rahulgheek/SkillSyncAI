package com.skillsync.skillsync_ai.authentication.services;

import com.skillsync.skillsync_ai.authentication.dto.AuthResponse;
import com.skillsync.skillsync_ai.authentication.dto.LoginRequest;
import com.skillsync.skillsync_ai.authentication.dto.RegisterRequest;
import com.skillsync.skillsync_ai.authentication.dto.VerifyRequest;
import com.skillsync.skillsync_ai.authentication.entity.College;
import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.authentication.repo.CollegeRepository;
import com.skillsync.skillsync_ai.authentication.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository; // Make sure to create this interface
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final AuthenticationProvider authenticationProvider;
    private final MailService mailService;

    private static final String CAMPUS_DOMAIN = "chitkara.edu.in";

    @Transactional
    public String registerStudent(RegisterRequest request) {
        Optional<User> existingUser = userRepository.findByEmail(request.email());
        if (existingUser.isPresent()) {
            if (existingUser.get().isEmailVerified()) {
                throw new IllegalStateException("User already exists and is verified. Please login.");
            }

            // User exists but isn't verified -> Resend OTP
            if (otpService.isOnCooldown(request.email())) {
                long remaining = otpService.getCooldownSeconds(request.email());
                throw new IllegalStateException("Please wait " + remaining + " seconds before requesting another OTP.");
            }

            String otp = generateOTP();
            otpService.saveOtp(request.email(), otp);
            otpService.startCooldown(request.email());

            log.info("Resent OTP to unverified user: {}", request.email());
            mailService.sendMail(
                request.email(),
                "Your SkillSync AI Verification Code",
                "Your verification code is: " + otp + "\n\nThis code will expire in 5 minutes."
            );

            return "OTP resent. Please check your college email.";
        }

        // Fetch the seeded college for this deployment or auto-create a default one if the DB is completely empty
        College college = collegeRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    College newCollege = College.builder()
                            .name("SkillSync University")
                            .emailDomain(CAMPUS_DOMAIN)
                            .isActive(true)
                            .build();
                    return collegeRepository.save(newCollege);
                });

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .college(college)
                .role(User.Role.STUDENT)
                .authProvider(User.AuthProvider.LOCAL)
                .isEmailVerified(false)
                .isActive(true)
                .build();

        userRepository.save(user);

        String otp = generateOTP();
        otpService.saveOtp(request.email(), otp);
        otpService.startCooldown(request.email());

        log.info("Sending OTP to {}: {}", request.email(), otp);
        mailService.sendMail(
            request.email(),
            "Welcome to SkillSync AI! Verification Code",
            "Your verification code is: " + otp + "\n\nThis code will expire in 5 minutes."
        );

        return "Student registration initiated. Check your college email for the OTP.";
    }

    @Transactional
    public String resendOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist."));

        if (user.isEmailVerified()) {
            throw new IllegalStateException("User already verified. Please login.");
        }

        if (otpService.isOnCooldown(email)) {
            long remaining = otpService.getCooldownSeconds(email);
            throw new IllegalStateException("Please wait " + remaining + " seconds before requesting another OTP.");
        }

        String otp = generateOTP();
        otpService.saveOtp(email, otp);
        otpService.startCooldown(email);

        log.info("Resent OTP to unverified user: {}", email);
        mailService.sendMail(
            email,
            "Your SkillSync AI Verification Code",
            "Your verification code is: " + otp + "\n\nThis code will expire in 5 minutes."
        );

        return "OTP resent. Please check your college email.";
    }

    @Transactional
    public String verifyOtp(VerifyRequest request) {
        String storedOtp = otpService.getOtp(request.email());

        if (storedOtp == null || !storedOtp.equals(request.otp())) {
            throw new IllegalArgumentException("Invalid or Expired OTP.");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("User does not exist."));

        user.setEmailVerified(true);
        userRepository.save(user);

        // Clean up OTP from Redis
        otpService.deleteOtp(request.email());

        // TODO: (Modulith Integration) Publish UserVerifiedEvent here so the
        // UserProfile module can automatically create an empty StudentProfile row.

        return "Email verified successfully! You can now log in.";
    }

    public AuthResponse loginUser(LoginRequest request) {
        // This will throw BadCredentialsException if password doesn't match
        authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (!user.isEmailVerified()) {
            throw new IllegalStateException("College email is not verified. Please verify first.");
        }

        if (!user.isActive()) {
            throw new IllegalStateException("This account has been deactivated by an administrator.");
        }

        java.util.Map<String, Object> extraClaims = new java.util.HashMap<>();
        extraClaims.put("userId", user.getId().toString());
        String accessToken = jwtService.generateToken(extraClaims, user);

        return new AuthResponse(accessToken, "Login successful");
    }

    // Domain validation removed per user request
    private String generateOTP() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }
}