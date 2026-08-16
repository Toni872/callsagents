package com.callsagents.backend.voice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetellProviderTest {

    private RetellProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RetellProvider();
    }

    @Test
    @DisplayName("startCall: without API key, fails with explicit RETELL_API_KEY message")
    void startCall_noApiKey() {
        var req = new VoiceProvider.StartCallRequest("+15551234567", null, Map.of(), Map.of());

        assertThatThrownBy(() -> provider.startCall(req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("RETELL_API_KEY");
    }

    @Test
    @DisplayName("startCall: with API key but no agent id nor from number, names both missing vars")
    void startCall_missingAgentAndNumber() {
        ReflectionTestUtils.setField(provider, "apiKey", "key_test");
        var req = new VoiceProvider.StartCallRequest("+15551234567", null, Map.of(), Map.of());

        assertThatThrownBy(() -> provider.startCall(req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("RETELL_AGENT_ID")
            .hasMessageContaining("RETELL_FROM_NUMBER");
    }

    @Test
    @DisplayName("startCall: with agent id but missing from number, names only the missing var")
    void startCall_missingOnlyFromNumber() {
        ReflectionTestUtils.setField(provider, "apiKey", "key_test");
        ReflectionTestUtils.setField(provider, "defaultAgentId", "agent_123");
        var req = new VoiceProvider.StartCallRequest("+15551234567", null, Map.of(), Map.of());

        assertThatThrownBy(() -> provider.startCall(req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("RETELL_FROM_NUMBER")
            .hasMessageNotContaining("RETELL_AGENT_ID");
    }

    @Test
    @DisplayName("buildBodyJson: full golden — stable key order, dynamic variables last, metadata always present")
    void buildBodyJson_fullGolden() throws Exception {
        ReflectionTestUtils.setField(provider, "apiKey", "key_test");
        ReflectionTestUtils.setField(provider, "defaultAgentId", "agent_123");
        ReflectionTestUtils.setField(provider, "defaultFromNumber", "+15550000000");
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("campaign_prompt", "prompt");
        vars.put("company", "Acme");
        var req = new VoiceProvider.StartCallRequest(
            "+15551234567", "agent_override", Map.of("lead", "L1"), vars);

        String json = provider.buildBodyJson(req);

        assertThat(json).isEqualTo("{\"from_number\":\"+15550000000\",\"to_number\":\"+15551234567\","
            + "\"override_agent_id\":\"agent_override\",\"metadata\":{\"lead\":\"L1\"},"
            + "\"retell_llm_dynamic_variables\":{\"campaign_prompt\":\"prompt\",\"company\":\"Acme\"}}");
    }

    @Test
    @DisplayName("buildBodyJson: empty dynamic variables omitted, empty metadata serialized as {}")
    void buildBodyJson_noDynamicVariables() throws Exception {
        ReflectionTestUtils.setField(provider, "apiKey", "key_test");
        ReflectionTestUtils.setField(provider, "defaultAgentId", "agent_123");
        ReflectionTestUtils.setField(provider, "defaultFromNumber", "+15550000000");
        var req = new VoiceProvider.StartCallRequest("+15551234567", null, Map.of(), Map.of());

        String json = provider.buildBodyJson(req);

        assertThat(json).isEqualTo("{\"from_number\":\"+15550000000\",\"to_number\":\"+15551234567\","
            + "\"override_agent_id\":\"agent_123\",\"metadata\":{}}");
    }

    @Test
    @DisplayName("buildBodyJson: null metadata and null dynamic variables are tolerated")
    void buildBodyJson_nullMaps() throws Exception {
        ReflectionTestUtils.setField(provider, "apiKey", "key_test");
        ReflectionTestUtils.setField(provider, "defaultAgentId", "agent_123");
        ReflectionTestUtils.setField(provider, "defaultFromNumber", "+15550000000");
        var req = new VoiceProvider.StartCallRequest("+15551234567", null, null, null);

        String json = provider.buildBodyJson(req);

        assertThat(json).isEqualTo("{\"from_number\":\"+15550000000\",\"to_number\":\"+15551234567\","
            + "\"override_agent_id\":\"agent_123\",\"metadata\":{}}");
    }
}
