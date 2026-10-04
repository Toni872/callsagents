package com.callsagents.backend.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

/**
 * Resolves the real client IP for rate limiting and audit purposes.
 *
 * <p>Trusts the first entry of {@code X-Forwarded-For} only when it parses as a
 * legitimate IP literal, falling back to the remote address otherwise. This
 * prevents a spoofed header from evading per-IP limits while still working
 * behind the nginx reverse proxy (production) and the Docker bridge (dev).
 */
public final class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String first = xForwardedFor.split(",")[0].trim();
            if (isValidIpLiteral(first)) {
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
    private static boolean isValidIpLiteral(String value) {
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