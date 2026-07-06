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

    private static final String CAMPUS_DOMAIN = "chitkara.edu.in";

    @Transactional
    public String registerStudent(RegisterRequest request) {
        validateCollegeDomain(request.email());

        Optional<User> existingUser = userRepository.findByEmail(request.email());
        if (existingUser.isPresent()) {
            if (existingUser.get().isEmailVerified()) {
                throw new RuntimeException("User already exists and is verified. Please login.");
            }

            // User exists but isn't verified -> Resend OTP
            String otp = generateOTP();
            otpService.saveOtp(request.email(), otp);

            log.info("Resent OTP to unverified user: {}", request.email());
            log.info("DEV MODE OTP for {}: {}", request.email(), otp); // TODO: Remove in Prod

            return "OTP resent. Please verify your college email.";
        }

        // Fetch the seeded college for this deployment
        College college = collegeRepository.findByEmailDomain(CAMPUS_DOMAIN)
                .orElseThrow(() -> new RuntimeException("System Error: College domain " + CAMPUS_DOMAIN + " is not seeded in the database."));

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

        log.info("DEV MODE OTP for {}: {}", request.email(), otp); // TODO: Remove in Prod, send via Email service

        return "Student registration initiated. Check your college email for the OTP.";
    }

    @Transactional
    public String verifyOtp(VerifyRequest request) {
        String storedOtp = otpService.getOtp(request.email());

        if (storedOtp == null || !storedOtp.equals(request.otp())) {
            throw new RuntimeException("Invalid or Expired OTP.");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User does not exist."));

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
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (!user.isEmailVerified()) {
            throw new RuntimeException("College email is not verified. Please verify first.");
        }

        if (!user.isActive()) {
            throw new RuntimeException("This account has been deactivated by an administrator.");
        }

        String accessToken = jwtService.generateToken(user);

        return new AuthResponse(accessToken, "Login successful");
    }

    private void validateCollegeDomain(String email) {
        if (email == null || !email.toLowerCase().endsWith("@" + CAMPUS_DOMAIN)) {
            throw new RuntimeException("Registration restricted: Must use a valid university email address (@" + CAMPUS_DOMAIN + ").");
        }
    }

    private String generateOTP() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }
}