package com.callsagents.backend.voice.config;

import com.callsagents.backend.voice.service.RetellProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies the Retell voice gate registers as the {@code voiceGate} component in
 * Spring Actuator's {@link HealthEndpoint} (the same component exposed under
 * {@code /actuator/health} → {@code $.components.voiceGate}).
 */
@SpringBootTest(classes = VoiceHealthIndicatorIntegrationTest.HealthSliceConfig.class)
class VoiceHealthIndicatorIntegrationTest {

    @Autowired HealthEndpoint healthEndpoint;

    @MockBean RetellProvider retellProvider;

    @SpringBootConfiguration
    @Import({
        HealthContributorAutoConfiguration.class,
        HealthEndpointAutoConfiguration.class,
        VoiceHealthIndicator.class
    })
    static class HealthSliceConfig {
    }

    @Test
    @DisplayName("HealthEndpoint aggregates voiceGate as DOWN when Retell is not configured")
    void health_slice_containsVoiceGateDown() {
        when(retellProvider.isConfigured()).thenReturn(false);
        when(retellProvider.isFromNumberConfigured()).thenReturn(false);

        var health = (CompositeHealth) healthEndpoint.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        var voiceGate = health.getComponents().get("voiceGate");
        assertThat(voiceGate).isNotNull();
        assertThat(voiceGate.getStatus()).isEqualTo(Status.DOWN);
        var voiceGateHealth = (org.springframework.boot.actuate.health.Health) voiceGate;
        assertThat(voiceGateHealth.getDetails()).containsKey("missingConfigVars");
    }

    @Test
    @DisplayName("HealthEndpoint reports voiceGate UP when Retell is fully configured")
    void health_slice_voiceGateUp_whenConfigured() {
        when(retellProvider.isConfigured()).thenReturn(true);
        when(retellProvider.isFromNumberConfigured()).thenReturn(true);

        var health = (CompositeHealth) healthEndpoint.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        var voiceGate = health.getComponents().get("voiceGate");
        assertThat(voiceGate).isNotNull();
        assertThat(voiceGate.getStatus()).isEqualTo(Status.UP);
    }
}