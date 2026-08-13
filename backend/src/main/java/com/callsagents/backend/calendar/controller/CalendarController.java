package com.callsagents.backend.calendar.controller;

import com.callsagents.backend.calendar.domain.CalendarIntegration;
import com.callsagents.backend.calendar.domain.CalendarProviderType;
import com.callsagents.backend.calendar.dto.CalendarIntegrationDto;
import com.callsagents.backend.calendar.service.CalendarProvider;
import com.callsagents.backend.calendar.service.CalendarSyncService;
import com.callsagents.backend.calendar.service.EncryptionService;
import com.callsagents.backend.calendar.repo.CalendarIntegrationRepository;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calendar OAuth + integration management.
 *
 * Endpoints (MVP):
 *   GET  /api/calendar/integrations/google/start       -> 200 { authorizeUrl } (SPA navigates to Google consent screen)
 *   GET  /api/calendar/integrations/google/callback    -> exchange code, persist integration, redirect to /settings
 *   GET  /api/calendar/integrations                    -> current user's integrations
 *   DELETE /api/calendar/integrations/{id}             -> disconnect (revoke + delete)
 *   POST /api/calendar/integrations/{id}/sync-toggle   -> enable/disable without disconnecting
 */
@RestController
@RequestMapping("/calendar")
@Tag(name = "Calendar", description = "Integración con calendarios externos (Google, Outlook)")
public class CalendarController {

    private static final Logger log = LoggerFactory.getLogger(CalendarController.class);

    private final CalendarIntegrationRepository integrationRepo;
    private final CalendarSyncService syncService;
    private final EncryptionService encryption;
    private final List<CalendarProvider> providers;
    private final com.callsagents.backend.auth.repository.UserRepository userRepository;

    /** Base URL of the SPA, e.g. http://localhost:80. OAuth callback redirects the browser back here. */
    @Value("${app.frontend-base-url:http://localhost}")
    private String frontendBaseUrl;

    /** Secret used to HMAC-sign the OAuth state so the callback can't be tampered with. */
    @Value("${app.jwt.secret}")
    private String stateSecret;

    public CalendarController(
        CalendarIntegrationRepository integrationRepo,
        CalendarSyncService syncService,
        EncryptionService encryption,
        List<CalendarProvider> providers,
        com.callsagents.backend.auth.repository.UserRepository userRepository
    ) {
        this.integrationRepo = integrationRepo;
        this.syncService = syncService;
        this.encryption = encryption;
        this.providers = providers;
        this.userRepository = userRepository;
    }

    /** Initiates the OAuth flow for the given provider. */
    @GetMapping("/integrations/{provider}/start")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','AGENT')")
    public ResponseEntity<?> start(
        @PathVariable String provider,
        Authentication authentication
    ) {
        var type = parseProvider(provider);
        var calProvider = syncService.providerOf(type);
        if (!calProvider.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of(
                "error", "provider_not_configured",
                "message", "Calendar provider " + type + " is not configured on this server. " +
                           "Set the appropriate env vars (see RUNBOOK)."
            ));
        }
        // State = signed email so the callback can attribute the integration to the
        // authenticated user AND detect tampering. Plain email alone would let an
        // attacker connect THEIR calendar to someone else's account.
        String state = signState(authentication.getName());
        String url = calProvider.buildAuthorizationUrl(state);
        // Return the Google URL as JSON instead of a raw 302: browser navigation
        // cannot send the Authorization header, but HttpClient can. The SPA then
        // navigates with window.location after receiving the authenticated response.
        return ResponseEntity.ok(Map.of(
            "authorizeUrl", url,
            "provider", type.name()
        ));
    }

    /** OAuth callback — exchanges the code and persists the integration. */
    @GetMapping("/integrations/{provider}/callback")
    public RedirectView callback(
        @PathVariable String provider,
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String error
    ) {
        // In production this would redirect to the SPA with success/failure in query params.
        // For MVP we redirect to a simple frontend route.
        if (error != null) {
            log.warn("Calendar OAuth callback error: {}", error);
            return frontendRedirect("?status=error&reason=" + error);
        }
        if (code == null || code.isBlank()) {
            return frontendRedirect("?status=missing_code");
        }

        // State is signed (HMAC) by /start: reject tampered/expired/foreign states.
        // This prevents an attacker from connecting their own Google account to
        // another user's Callsagents account (which would leak that user's
        // appointments into the attacker's calendar).
        String stateEmail = verifyState(state);
        if (stateEmail == null) {
            return frontendRedirect("?status=error&reason=invalid_state");
        }

        try {
            var type = parseProvider(provider);
            var calProvider = syncService.providerOf(type);
            var tokens = calProvider.exchangeCode(code);

            UUID userId = resolveUserIdByEmail(stateEmail);
            if (userId == null) {
                return frontendRedirect("?status=error&reason=user_not_found");
            }

            var existing = integrationRepo.findByUserIdAndProvider(userId, type);
            // CRITICAL: do NOT set the id manually. @UuidGenerator treats a non-null
            // id as "already persistent" (unsaved-value = null), so save() issues an
            // UPDATE on a non-existent row -> StaleObjectStateException. @PrePersist
            // assigns the UUID when id is null.
            CalendarIntegration integration = existing.orElseGet(() -> CalendarIntegration.builder()
                .userId(userId)
                .provider(type)
                .syncEnabled(true)
                .build());

            integration.setAccessTokenEncrypted(encryption.encrypt(tokens.accessToken()));
            if (tokens.refreshToken() != null) {
                integration.setRefreshTokenEncrypted(encryption.encrypt(tokens.refreshToken()));
            }
            integration.setAccessTokenExpiresAt(tokens.accessTokenExpiresAt());
            integration.setScopes(tokens.scope());
            integration.setExternalCalendarId("primary");
            // Best-effort: resolve the connected Google account email (userinfo).
            // Display-only — failure to fetch never fails the connection.
            integration.setExternalAccountEmail(calProvider.fetchAccountEmail(tokens.accessToken()));
            integrationRepo.save(integration);

            return frontendRedirect("?status=connected&provider=" + type);
        } catch (Exception e) {
            log.warn("Calendar callback error", e);
            return frontendRedirect("?status=error&reason=" + e.getMessage());
        }
    }

    /** List current user's integrations. */
    @GetMapping("/integrations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','AGENT')")
    @Operation(summary = "Listar integraciones del usuario actual")
    public List<CalendarIntegrationDto> list(Authentication authentication) {
        UUID userId = resolveUserIdByEmail(authentication.getName());
        if (userId == null) return List.of();
        return syncService.listForUser(userId).stream()
            .map(CalendarIntegrationDto::from)
            .toList();
    }

    /** Disconnect an integration. */
    @DeleteMapping("/integrations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','AGENT')")
    public ResponseEntity<Void> disconnect(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        var integration = integrationRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Integration not found"));
        UUID userId = resolveUserIdByEmail(authentication.getName());
        if (userId == null || !integration.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        // Best-effort revoke
        if (integration.getRefreshTokenEncrypted() != null) {
            try {
                String refresh = encryption.decrypt(integration.getRefreshTokenEncrypted());
                syncService.providerOf(integration.getProvider()).revokeTokens(refresh);
            } catch (Exception ignored) {}
        }
        integrationRepo.delete(integration);
        return ResponseEntity.noContent().build();
    }

    /** Toggle sync enabled without disconnecting. */
    @PostMapping("/integrations/{id}/sync-toggle")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','AGENT')")
    public CalendarIntegrationDto toggle(@PathVariable UUID id, Authentication authentication) {
        var integration = integrationRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Integration not found"));
        UUID userId = resolveUserIdByEmail(authentication.getName());
        if (userId == null || !integration.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Integration not found");
        }
        integration.setSyncEnabled(!Boolean.TRUE.equals(integration.getSyncEnabled()));
        return CalendarIntegrationDto.from(integrationRepo.save(integration));
    }

    /**
     * One-time backfill: create Google events for existing future appointments
     * (PENDING/CONFIRMED) that were never synced. Safe to re-run — already-synced
     * appointments are skipped.
     */
    @PostMapping("/integrations/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Backfill: crear eventos Google para citas futuras nunca sincronizadas")
    public Map<String, Object> backfill() {
        var result = syncService.backfillUnsynced();
        return Map.of(
            "scanned", result.scanned(),
            "created", result.created(),
            "failed", result.failed()
        );
    }

    // -------- helpers --------

    /** Redirect the browser back to the SPA (/settings/calendar) after OAuth. */
    private RedirectView frontendRedirect(String query) {
        return new RedirectView(frontendBaseUrl + "/settings/calendar" + query);
    }

    private static CalendarProviderType parseProvider(String s) {
        try {
            return CalendarProviderType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Unknown provider: " + s);
        }
    }

    private UUID resolveUserIdByEmail(String email) {
        return userRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }

    // -------- OAuth state signing (HMAC-SHA256) --------

    /** How long a signed state stays valid — enough for the Google consent round-trip. */
    private static final long STATE_TTL_SECONDS = 600;

    /** Format: <email>|<epoch-seconds>|<hex-hmac>. The '|' separator is safe: emails can't contain it. */
    private String signState(String email) {
        long ts = Instant.now().getEpochSecond();
        return email + "|" + ts + "|" + hmacHex(email + "|" + ts);
    }

    /** Verifies signature + expiry and returns the embedded email, or null if invalid. */
    private String verifyState(String state) {
        if (state == null) return null;
        String[] parts = state.split("\\|", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[2].isBlank()) return null;
        String email = parts[0];
        String ts = parts[1];
        String providedSig = parts[2];
        String expectedSig = hmacHex(email + "|" + ts);
        // Constant-time compare — plain equals() leaks timing on the signature.
        if (!MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                providedSig.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        long issuedAt;
        try {
            issuedAt = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return null;
        }
        long age = Instant.now().getEpochSecond() - issuedAt;
        if (age < 0 || age > STATE_TTL_SECONDS) return null;
        return email;
    }

    private String hmacHex(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                stateSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("OAuth state signing failed", e);
        }
    }
}
