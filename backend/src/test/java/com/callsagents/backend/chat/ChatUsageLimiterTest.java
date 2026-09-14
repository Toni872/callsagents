package com.callsagents.backend.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatUsageLimiterTest {

    private final ChatUsageLimiter limiter = new ChatUsageLimiter();
    private final UUID businessId = UUID.randomUUID();

    @Test
    @DisplayName("per-minute limit: 60 calls allowed, the 61st is rejected")
    void minuteLimit_rejectsWhenExceeded() {
        for (int i = 0; i < ChatUsageLimiter.MINUTE_LIMIT; i++) {
            assertThat(limiter.tryAcquire(businessId))
                .as("call %d within minute limit", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire(businessId)).isFalse();
    }

    @Test
    @DisplayName("per-day limit: 500 calls allowed, the 501st is rejected")
    void dailyLimit_rejectsWhenExceeded() {
        for (int i = 0; i < ChatUsageLimiter.DAILY_LIMIT; i++) {
            assertThat(limiter.isDailyAllowed(businessId))
                .as("call %d within daily limit", i + 1).isTrue();
        }
        assertThat(limiter.isDailyAllowed(businessId)).isFalse();
    }

    @Test
    @DisplayName("null businessId is never limited (per-IP filter covers anonymous callers)")
    void nullBusinessId_isAlwaysAllowed() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(limiter.tryAcquire(null)).isTrue();
            assertThat(limiter.isMinuteAllowed(null)).isTrue();
            assertThat(limiter.isDailyAllowed(null)).isTrue();
        }
    }

    @Test
    @DisplayName("minute window resets when the time bucket rolls over")
    void minuteWindow_resetsOnBucketRollover() {
        MutableClockLimiter clocked = new MutableClockLimiter(0L);
        for (int i = 0; i < ChatUsageLimiter.MINUTE_LIMIT; i++) {
            clocked.tryAcquire(businessId);
        }
        assertThat(clocked.tryAcquire(businessId)).isFalse();

        clocked.advance(60_000L);
        assertThat(clocked.tryAcquire(businessId)).isTrue();
    }

    @Test
    @DisplayName("daily window resets when the day bucket rolls over")
    void dailyWindow_resetsOnBucketRollover() {
        MutableClockLimiter clocked = new MutableClockLimiter(0L);
        for (int i = 0; i < ChatUsageLimiter.DAILY_LIMIT; i++) {
            clocked.isDailyAllowed(businessId);
        }
        assertThat(clocked.isDailyAllowed(businessId)).isFalse();

        clocked.advance(86_400_000L);
        assertThat(clocked.isDailyAllowed(businessId)).isTrue();
    }

    private static final class MutableClockLimiter extends ChatUsageLimiter {
        private long now;

        MutableClockLimiter(long now) {
            this.now = now;
        }

        void advance(long millis) {
            this.now += millis;
        }

        @Override
        long currentTimeMillis() {
            return now;
        }
    }
}