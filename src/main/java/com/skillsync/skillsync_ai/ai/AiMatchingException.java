package com.skillsync.skillsync_ai.ai;

/**
 * Raised when the LLM cannot produce a usable scoring response. The matching
 * worker catches this and persists deterministic scores instead, so a Groq
 * outage degrades ranking quality rather than breaking the feature.
 */
public class AiMatchingException extends RuntimeException {

    public AiMatchingException(String message) {
        super(message);
    }

    public AiMatchingException(String message, Throwable cause) {
        super(message, cause);
    }
}
