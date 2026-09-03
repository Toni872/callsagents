package com.callsagents.backend.voice.config;

import com.callsagents.backend.voice.service.RetellProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Exposes the Retell voice gate on {@code /actuator/health}. Reports DOWN (with
 * the missing {@code RETELL_*} vars) so operators can see at a glance why
 * outbound calls would fail, without throwing on startup.
 */
@Component("voiceGateHealthIndicator")
public class VoiceHealthIndicator implements HealthIndicator {

    private final RetellProvider retellProvider;

    public VoiceHealthIndicator(RetellProvider retellProvider) {
        this.retellProvider = retellProvider;
    }

    @Override
    public Health health() {
        List<String> missing = new ArrayList<>();
        if (!retellProvider.isConfigured()) {
            missing.add("RETELL_API_KEY");
        }
        if (!retellProvider.isFromNumberConfigured()) {
            missing.add("RETELL_FROM_NUMBER");
        }
        if (missing.isEmpty()) {
            return Health.up().build();
        }
        return Health.down().withDetail("missingConfigVars", missing).build();
    }
}