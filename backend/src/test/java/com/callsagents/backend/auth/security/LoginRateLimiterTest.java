package com.callsagents.backend.auth.security;

import com.callsagents.backend.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTest {

    private static final String IP = "203.0.113.10";
    private static final String EMAIL = "agent@example.com";

    @Test
    void allowsAttemptsBelowTheLimit() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        // Each attempt first checks, then records the failure after a bad login.
        for (int i = 0; i < LoginRateLimiter.MAX_FAILED_ATTEMPTS; i++) {
            assertDoesNotThrow(() -> limiter.checkAllowed(IP, EMAIL),
                "Attempt " + (i + 1) + " must still be allowed");
            limiter.recordFailure(IP, EMAIL);
        }
    }

    @Test
    void blocksWhenTheLimitIsReached() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < LoginRateLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure(IP, EMAIL);
        }

        assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(IP, EMAIL));
    }

    @Test
    void successResetsTheCounter() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < LoginRateLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure(IP, EMAIL);
        }

        limiter.recordSuccess(IP, EMAIL);
        assertDoesNotThrow(() -> limiter.checkAllowed(IP, EMAIL));
    }

    @Test
    void keysAreScopedByIpAndEmail() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < LoginRateLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure(IP, EMAIL);
        }

        // Same IP + different email is a different key and must not be blocked.
        assertDoesNotThrow(() -> limiter.checkAllowed(IP, "other@example.com"));
        // Same email + different IP must not be blocked either (attacker on one
        // IP must not lock a user out from another device).
        assertDoesNotThrow(() -> limiter.checkAllowed("198.51.100.42", EMAIL));
    }

    @Test
    void emailComparisonIsCaseInsensitiveAndTrimmed() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < LoginRateLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure(IP, "  Agent@Example.com  ");
        }

        assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(IP, "agent@example.com"));
    }

    @Test
    void windowExpiryReleasesTheKey() throws InterruptedException {
        LoginRateLimiter limiter = new LoginRateLimiter(1, Duration.ofMillis(50));

        limiter.recordFailure(IP, EMAIL);
        assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(IP, EMAIL));

        Thread.sleep(120);
        assertDoesNotThrow(() -> limiter.checkAllowed(IP, EMAIL));
    }
}