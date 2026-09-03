package com.callsagents.backend.voice.config;

import com.callsagents.backend.voice.service.RetellProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoiceHealthIndicatorTest {

    private RetellProvider retellProvider = mock(RetellProvider.class);

    @Test
    @DisplayName("health: fully configured -> UP")
    void health_configured_isUp() {
        when(retellProvider.isConfigured()).thenReturn(true);
        when(retellProvider.isFromNumberConfigured()).thenReturn(true);

        Health health = new VoiceHealthIndicator(retellProvider).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("health: api key missing -> DOWN with missing RETELL_API_KEY detail")
    void health_missingApiKey_isDown() {
        when(retellProvider.isConfigured()).thenReturn(false);
        when(retellProvider.isFromNumberConfigured()).thenReturn(true);

        Health health = new VoiceHealthIndicator(retellProvider).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("missingConfigVars");
        assertThat(String.valueOf(health.getDetails().get("missingConfigVars")))
            .contains("RETELL_API_KEY");
    }

    @Test
    @DisplayName("health: from number missing -> DOWN with missing RETELL_FROM_NUMBER detail")
    void health_missingFromNumber_isDown() {
        when(retellProvider.isConfigured()).thenReturn(true);
        when(retellProvider.isFromNumberConfigured()).thenReturn(false);

        Health health = new VoiceHealthIndicator(retellProvider).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(String.valueOf(health.getDetails().get("missingConfigVars")))
            .contains("RETELL_FROM_NUMBER");
    }
}