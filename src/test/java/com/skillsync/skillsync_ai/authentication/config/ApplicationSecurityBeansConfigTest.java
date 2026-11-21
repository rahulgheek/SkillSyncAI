package com.skillsync.skillsync_ai.authentication.config;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.authentication.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The login flow builds its own manager around the application's
 * UserDetailsService. These tests guard that wiring: no separate
 * AuthenticationProvider bean, and passwords are still checked with the
 * configured encoder.
 */
class ApplicationSecurityBeansConfigTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);

        ApplicationSecurityBeansConfig config = new ApplicationSecurityBeansConfig(userRepository);
        passwordEncoder = config.passwordEncoder();
        authenticationManager = config.authenticationManager();

        user = User.builder()
                .email("student@example.com")
                .password(passwordEncoder.encode("secret"))
                .role(User.Role.STUDENT)
                .isEmailVerified(true)
                .isActive(true)
                .build();

        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void authenticatesThroughUserDetailsService() {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("student@example.com", "secret"));

        assertTrue(authentication.isAuthenticated());
        assertSame(user, authentication.getPrincipal());
    }

    @Test
    void rejectsWrongPassword() {
        assertThrows(BadCredentialsException.class, () -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("student@example.com", "wrong")));
    }

    @Test
    void hidesUnknownEmailAsBadCredentials() {
        assertThrows(BadCredentialsException.class, () -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("missing@example.com", "secret")));
    }
}
