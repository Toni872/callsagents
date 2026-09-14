package com.callsagents.backend.chat;

import com.callsagents.backend.business.entity.BusinessProfile;
import com.callsagents.backend.business.service.BusinessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;

/**
 * Allow-list validation for the domains that may call the public chat widget on
 * behalf of a business.
 *
 * <p>Trust signal is the {@code Referer} header (never {@code Origin} alone):
 * it is set by the browser for the bidirectional calls the widget makes from
 * the page that embeds it. When the business stores no
 * {@code allowed_domains} (null/blank) any referer is permitted — legacy
 * clients keep working untouched.
 */
@Component
public class WidgetDomainGuard {

    private static final Logger log = LoggerFactory.getLogger(WidgetDomainGuard.class);

    private final BusinessService businessService;

    public WidgetDomainGuard(BusinessService businessService) {
        this.businessService = businessService;
    }

    /**
     * Whether the caller behind {@code referer} may use the widget for the given
     * business. Callers without a resolved business profile are always allowed.
     */
    public boolean isAllowed(UUID businessId, String referer) {
        BusinessProfile profile = resolveProfile(businessId);
        if (profile == null) {
            return true;
        }
        String allowedDomains = profile.getAllowedDomains();
        if (allowedDomains == null || allowedDomains.isBlank()) {
            return true;
        }
        String host = extractHost(referer);
        if (host == null || host.isBlank()) {
            log.warn("Widget domain guard: no Referer host; businessId={} has allowed_domains configured", businessId);
            return false;
        }
        String normalizedHost = normalize(host);
        for (String configured : allowedDomains.split(",")) {
            String normalizedDomain = normalize(configured);
            if (normalizedDomain.isEmpty()) {
                continue;
            }
            if (normalizedHost.equals(normalizedDomain) || normalizedHost.endsWith("." + normalizedDomain)) {
                return true;
            }
        }
        log.warn("Widget domain guard: referer host '{}' not in allowed_domains for businessId={}", host, businessId);
        return false;
    }

    private BusinessProfile resolveProfile(UUID businessId) {
        if (businessId == null) {
            return null;
        }
        UUID ownerId = businessService.resolveOwnerUserId(businessId);
        if (ownerId == null) {
            return null;
        }
        return businessService.getProfileEntityByUserId(ownerId);
    }

    /**
     * Lowercase host without scheme/path, and with a leading {@code www.}
     * dropped so {@code www.script9.com} matches a configured
     * {@code script9.com} (and {@code api.script9.com} still matches as a
     * subdomain).
     */
    static String normalize(String hostOrDomain) {
        String trimmed = hostOrDomain == null ? "" : hostOrDomain.trim().toLowerCase();
        if (trimmed.startsWith("www.")) {
            trimmed = trimmed.substring(4);
        }
        return trimmed;
    }

    private static String extractHost(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            return URI.create(referer.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}