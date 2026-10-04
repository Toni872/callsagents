package com.callsagents.backend.auth.security;

import com.callsagents.backend.common.exception.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brute-force protection for password login, keyed by IP + email.
 *
 * <p>Complements the generic {@code RateLimitFilter} (per-IP, per-minute): this
 * limiter tracks only <em>failed</em> attempts in a sliding window and resets on
 * success, so a legitimate user can never block herself by typing the wrong
 * password occasionally, while an attacker gets locked out of that email/IP
 * pair after {@code MAX_FAILED_ATTEMPTS} failures.
 *
 * <p>If the email is missing we fall back to IP-only keys so login failures
 * before validation still count toward a limit.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Cache<String, AtomicInteger> failures;

    public LoginRateLimiter() {
        this(MAX_FAILED_ATTEMPTS, WINDOW);
    }

    LoginRateLimiter(int maxFailedAttempts, Duration window) {
        this.maxFailedAttempts = maxFailedAttempts;
        this.failures = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(window)
            .build();
    }

    private final int maxFailedAttempts;

    /**
     * Returns the remaining allowed failures or throws 429 when exhausted.
     */
    public void checkAllowed(String ip, String email) {
        AtomicInteger count = failures.getIfPresent(key(ip, email));
        if (count != null && count.get() >= maxFailedAttempts) {
            throw new TooManyRequestsException(
                "Too many failed login attempts. Try again in a few minutes.");
        }
    }

    /**
     * Records one failed attempt for the key.
     */
    public void recordFailure(String ip, String email) {
        AtomicInteger counter = failures.get(key(ip, email), k -> new AtomicInteger(0));
        counter.incrementAndGet();
    }

    /**
     * Resets any accumulated failures for the key after a successful login.
     */
    public void recordSuccess(String ip, String email) {
        failures.invalidate(key(ip, email));
    }

    private static String key(String ip, String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return ip + "|" + normalized;
    }
}