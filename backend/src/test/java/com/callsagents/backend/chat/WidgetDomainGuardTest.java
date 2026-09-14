package com.callsagents.backend.chat;

import com.callsagents.backend.business.entity.BusinessProfile;
import com.callsagents.backend.business.service.BusinessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WidgetDomainGuardTest {

    @Mock BusinessService businessService;

    private WidgetDomainGuard guard;
    private final UUID businessId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        guard = new WidgetDomainGuard(businessService);
    }

    private void givenBusiness(String allowedDomains) {
        when(businessService.resolveOwnerUserId(businessId)).thenReturn(ownerId);
        when(businessService.getProfileEntityByUserId(ownerId))
            .thenReturn(BusinessProfile.builder()
                .id(businessId)
                .companyName("Acme")
                .allowedDomains(allowedDomains)
                .build());
    }

    @Test
    @DisplayName("exact host match is allowed")
    void exactDomain_allowed() {
        givenBusiness("script9.com");
        assertThat(guard.isAllowed(businessId, "https://script9.com/widget")).isTrue();
    }

    @Test
    @DisplayName("subdomain of the configured domain is allowed")
    void subdomain_allowed() {
        givenBusiness("script9.com");
        assertThat(guard.isAllowed(businessId, "https://api.script9.com/widget")).isTrue();
    }

    @Test
    @DisplayName("www prefix is stripped so www.script9.com matches script9.com")
    void wwwPrefix_stripped() {
        givenBusiness("script9.com");
        assertThat(guard.isAllowed(businessId, "https://www.script9.com/widget")).isTrue();
    }

    @Test
    @DisplayName("different domain is rejected")
    void unrelatedDomain_rejected() {
        givenBusiness("script9.com");
        assertThat(guard.isAllowed(businessId, "https://evil.example.com/widget")).isFalse();
    }

    @Test
    @DisplayName("suffix-only match is rejected (evil-script9.com is not a subdomain)")
    void suffixTrailingDot_rejected() {
        givenBusiness("script9.com");
        assertThat(guard.isAllowed(businessId, "https://evilscript9.com/widget")).isFalse();
    }

    @Test
    @DisplayName("missing Referer with configured domains is rejected")
    void missingReferer_rejected() {
        givenBusiness("script9.com");
        assertThat(guard.isAllowed(businessId, null)).isFalse();
        assertThat(guard.isAllowed(businessId, "")).isFalse();
    }

    @Test
    @DisplayName("comparison is case-insensitive")
    void caseInsensitive_allowed() {
        givenBusiness("Script9.COM");
        assertThat(guard.isAllowed(businessId, "https://SCRIPT9.COM/widget")).isTrue();
    }

    @Test
    @DisplayName("no allowed_domains configured -> always allowed (legacy compatibility)")
    void noDomainsConfigured_allowed() {
        givenBusiness(null);
        assertThat(guard.isAllowed(businessId, "https://anything.example.com")).isTrue();
        assertThat(guard.isAllowed(businessId, null)).isTrue();

        givenBusiness("   ");
        assertThat(guard.isAllowed(businessId, "https://anywhere.example.com")).isTrue();
    }

    @Test
    @DisplayName("unresolvable business or null businessId -> allowed")
    void unresolvableBusiness_allowed() {
        UUID unknown = UUID.randomUUID();
        when(businessService.resolveOwnerUserId(unknown)).thenReturn(null);
        assertThat(guard.isAllowed(unknown, "https://whatever.example.com")).isTrue();
        assertThat(guard.isAllowed(null, null)).isTrue();
    }

    @Test
    @DisplayName("multiple comma-separated domains: any matching host allowed")
    void commaSeparatedDomains_allowed() {
        givenBusiness("script9.com, client-2.example.com");
        assertThat(guard.isAllowed(businessId, "https://script9.com/widget")).isTrue();
        assertThat(guard.isAllowed(businessId, "https://www.client-2.example.com/widget")).isTrue();
        assertThat(guard.isAllowed(businessId, "https://other.example.com/widget")).isFalse();
    }
}