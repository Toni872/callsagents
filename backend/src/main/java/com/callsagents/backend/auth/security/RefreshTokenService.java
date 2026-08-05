package com.callsagents.backend.auth.security;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String REVOKED_ACCESS_PREFIX = "revoked:";

    private final RedisTemplate<String, String> redisTemplate;

    public RefreshTokenService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeRefreshToken(UUID userId, String tokenId, Duration ttl) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(ttl, "ttl");
        String key = refreshKey(userId, tokenId);
        redisTemplate.opsForValue().set(key, "1", ttl);
    }

    public boolean isRefreshTokenValid(UUID userId, String tokenId) {
        if (userId == null || tokenId == null) {
            return false;
        }
        Boolean exists = redisTemplate.hasKey(refreshKey(userId, tokenId));
        return Boolean.TRUE.equals(exists);
    }

    public void revokeRefreshToken(UUID userId, String tokenId) {
        if (userId == null || tokenId == null) {
            return;
        }
        redisTemplate.delete(refreshKey(userId, tokenId));
    }

    public void revokeAllRefreshTokens(UUID userId) {
        if (userId == null) {
            return;
        }
        String pattern = REFRESH_KEY_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete((Collection<String>) new HashSet<>(keys));
        }
    }

    public void revokeAccessToken(String jti, Duration remainingTtl) {
        if (jti == null || jti.isBlank() || remainingTtl == null || remainingTtl.isNegative() || remainingTtl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(REVOKED_ACCESS_PREFIX + jti, "1", remainingTtl);
    }

    public boolean isAccessTokenRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Boolean exists = redisTemplate.hasKey(REVOKED_ACCESS_PREFIX + jti);
        return Boolean.TRUE.equals(exists);
    }

    private static String refreshKey(UUID userId, String tokenId) {
        return REFRESH_KEY_PREFIX + userId + ":" + tokenId;
    }
}
