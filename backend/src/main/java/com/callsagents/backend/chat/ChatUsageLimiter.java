package com.callsagents.backend.chat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Per-business usage limits for the public chat widget, in addition to the
 * IP-based limits enforced by {@code RateLimitFilter}.
 *
 * <p>IP limits alone are evadible with botnets/proxies; since the widget sends a
 * {@code businessId} on every call, this limiter bounds how much paid Groq
 * traffic and lead creation a single business can trigger:
 * <ul>
 *   <li>60 calls/minute per businessId</li>
 *   <li>500 calls/day per businessId</li>
 * </ul>
 * When {@code businessId} is {@code null} the call is always allowed here (the
 * IP limit keeps covering anonymous callers).
 *
 * <p>Windows are time-bucket based (not fixed TTL + counter), so a continuously
 * active business still gets a fresh window every minute/day. Caffeine only
 * bounds memory.
 */
@Component
public class ChatUsageLimiter {

    static final int MINUTE_LIMIT = 60;
    static final int DAILY_LIMIT = 500;

    private static final long MINUTE_MILLIS = 60_000L;
    private static final long DAY_MILLIS = 86_400_000L;

    private final Cache<String, Window> minuteWindows = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(5))
        .build();

    private final Cache<String, Window> dailyWindows = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofDays(2))
        .build();

    /**
     * Records one call for the business (if any) and returns whether it is still
     * within both the per-minute and per-day limits.
     */
    public boolean tryAcquire(UUID businessId) {
        if (businessId == null) {
            return true;
        }
        return isMinuteAllowed(businessId) && isDailyAllowed(businessId);
    }

    /**
     * Increments the per-minute window for the business. Package-private so unit
     * tests can exercise the daily window independently without hitting the
     * minute cap first.
     */
    boolean isMinuteAllowed(UUID businessId) {
        if (businessId == null) {
            return true;
        }
        long bucket = currentTimeMillis() / MINUTE_MILLIS;
        Window window = minuteWindows.asMap().compute(keyFor(businessId) + ":min", (k, w) ->
            w == null || w.bucket != bucket ? new Window(bucket, 1) : w.increment());
        return window.count <= MINUTE_LIMIT;
    }

    /**
     * Increments the per-day window for the business.
     */
    boolean isDailyAllowed(UUID businessId) {
        if (businessId == null) {
            return true;
        }
        long bucket = currentTimeMillis() / DAY_MILLIS;
        Window window = dailyWindows.asMap().compute(keyFor(businessId) + ":day", (k, w) ->
            w == null || w.bucket != bucket ? new Window(bucket, 1) : w.increment());
        return window.count <= DAILY_LIMIT;
    }

    private static String keyFor(UUID businessId) {
        return businessId.toString().toLowerCase() + ":/api/chat/";
    }

    long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private static final class Window {
        final long bucket;
        final int count;

        Window(long bucket, int count) {
            this.bucket = bucket;
            this.count = count;
        }

        Window increment() {
            return new Window(bucket, count + 1);
        }
    }
}