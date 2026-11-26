package com.skillsync.skillsync_ai.common.exception;

/**
 * Raised when an upstream AI provider (Gemini or Groq) cannot produce a usable
 * response. The message is safe to show to the user: it explains what failed
 * without leaking provider payloads or credentials.
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
