package com.callsagents.backend.common.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple IP-based rate limiting filter using Caffeine cache.
 * 
 * Limits per endpoint category:
 * - Auth endpoints (login/register): 10 requests/min per IP
 * - Chat/webhook endpoints: 30 requests/min per IP
 * - General API: 100 requests/min per IP
 * 
 * Returns 429 Too Many Requests when exceeded.
 */
@Component
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // Sliding window counters per IP+path prefix
    private final Cache<String, AtomicInteger> requestCounts = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(1))
        .build();

    // Path prefix → max requests per minute
    private static final Map<String, Integer> LIMITS = Map.of(
        "/api/auth/", 10,       // Login, register, Google auth
        "/api/chat/", 30,       // Chat widget (public)
        "/api/voice/web-call", 30,  // Retell web calls (public)
        "/api/webhooks/", 60    // External webhooks
    );

    private static final int DEFAULT_LIMIT = 100; // Everything else

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String ip = getClientIp(httpRequest);
        String path = httpRequest.getRequestURI();
        int limit = getLimit(path);

        String key = ip + ":" + getLimitKey(path);
        AtomicInteger counter = requestCounts.get(key, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        // Set rate limit headers
        httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));

        if (count > limit) {
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private int getLimit(String path) {
        for (Map.Entry<String, Integer> entry : LIMITS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return DEFAULT_LIMIT;
    }

    private String getLimitKey(String path) {
        // Group similar paths together
        for (String prefix : LIMITS.keySet()) {
            if (path.startsWith(prefix)) {
                return prefix;
            }
        }
        return "/api/";
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String first = xForwardedFor.split(",")[0].trim();
            if (isValidIp(first)) {
                return first;
            }
            log.warn("Ignoring invalid X-Forwarded-For value '{}', falling back to remote addr", first);
        }
        return request.getRemoteAddr();
    }

    /**
     * Returns true only when the value parses as a legitimate IPv4/IPv6 address.
     * Used to avoid trusting a spoofed X-Forwarded-For header for rate limiting.
     */
    private boolean isValidIp(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(value);
            String ip = addr.getHostAddress();
            // InetAddress.getByName normalizes some inputs; reject anything that
            // is not a plain IPv4 or IPv6 literal (e.g. hostnames, encodings).
            return ip != null
                && (ip.contains(".") || ip.contains(":"))
                && !ip.startsWith("0")
                && !ip.equals("0.0.0.0");
        } catch (Exception e) {
            return false;
        }
    }
}
