package com.skillsync.skillsync_ai.authentication.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.skillsync.skillsync_ai.authentication.store.OtpStore;

import lombok.RequiredArgsConstructor;

/**
 * Hashes OTPs before persistence and delegates storage to the OtpStore backend
 * (Redis protocol by default, or Cacheify over HTTP when app.otp.store=cacheify).
 * Email keys are normalized to lower-case so register/verify casing always
 * hits the same key regardless of the store's key-sensitivity.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    public static final int RESEND_COOLDOWN_SECONDS = 45;

    private final OtpStore otpStore;

    public void saveOtp(String email, String otp) {
        otpStore.storeOtpHash(normalize(email), hashOtp(otp));
    }

    /** Constant-time comparison of the submitted code against the stored hash. */
    public boolean verifyOtp(String email, String otp) {
        return otpStore.getOtpHash(normalize(email))
                .filter(stored -> MessageDigest.isEqual(
                        stored.getBytes(StandardCharsets.US_ASCII),
                        hashOtp(otp).getBytes(StandardCharsets.US_ASCII)))
                .isPresent();
    }

    public void deleteOtp(String email) {
        otpStore.deleteOtpHash(normalize(email));
    }

    /** Returns true if the user must wait before resending. */
    public boolean isOnCooldown(String email) {
        return otpStore.isOnCooldown(normalize(email));
    }

    /** Returns how many seconds remain in the cooldown, or 0 if not on cooldown. */
    public long getCooldownSeconds(String email) {
        return otpStore.getCooldownSeconds(normalize(email));
    }

    /** Starts the cooldown window after an OTP is sent. */
    public void startCooldown(String email) {
        otpStore.startCooldown(normalize(email));
    }

    static String normalize(String email) {
        return email == null ? "" : email.toLowerCase();
    }

    private static String hashOtp(String otp) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(otp.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }
}
