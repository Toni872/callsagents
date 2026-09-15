package com.callsagents.backend.voice.controller;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.auth.security.JwtService;
import com.callsagents.backend.voice.domain.VoiceCall;
import com.callsagents.backend.voice.domain.VoiceCallStatus;
import com.callsagents.backend.voice.domain.VoiceProviderType;
import com.callsagents.backend.voice.service.RetellProvider;
import com.callsagents.backend.voice.service.VoiceCallService;
import com.callsagents.backend.voice.service.VoiceProvider;
import com.callsagents.backend.voice.service.WebhookSignatureValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(VoiceController.class)
class VoiceControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private VoiceCallService service;
    @MockitoBean private WebhookSignatureValidator signatureValidator;
    @MockitoBean private RetellProvider retellProvider;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private JwtService jwtService;

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/voice/webhook/**", "/voice/web-call").permitAll()
                    .anyRequest().authenticated())
                .httpBasic(org.springframework.security.config.Customizer.withDefaults());
            return http.build();
        }
    }

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("startCall: unauthenticated request is rejected with 401")
    void startCall_unauthorized() throws Exception {
        mvc.perform(post("/voice/calls/start")
                .param("provider", "VAPI")
                .param("phoneNumber", "+5491112345678"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("startCall: agent without campaignId delegates with null campaignId")
    @WithMockUser(username = "agent@callsagents.com", roles = "AGENT")
    void startCall_withoutCampaignId() throws Exception {
        User user = User.builder().id(USER_ID).email("agent@callsagents.com").role(UserRole.AGENT).build();
        when(userRepository.findByEmail("agent@callsagents.com")).thenReturn(Optional.of(user));
        when(service.placeCall(eq(VoiceProviderType.VAPI), any(VoiceProvider.StartCallRequest.class),
            eq(USER_ID), isNull(), eq(UserRole.AGENT))).thenReturn(call());

        mvc.perform(post("/voice/calls/start")
                .param("provider", "VAPI")
                .param("phoneNumber", "+5491112345678"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerCallId").value("vapi-call-1"));

        verify(service).placeCall(eq(VoiceProviderType.VAPI), any(VoiceProvider.StartCallRequest.class),
            eq(USER_ID), isNull(), eq(UserRole.AGENT));
    }

    @Test
    @DisplayName("startCall: agent with campaignId delegates it")
    @WithMockUser(username = "agent@callsagents.com", roles = "AGENT")
    void startCall_withCampaignId() throws Exception {
        UUID campaignId = UUID.randomUUID();
        User user = User.builder().id(USER_ID).email("agent@callsagents.com").role(UserRole.AGENT).build();
        when(userRepository.findByEmail("agent@callsagents.com")).thenReturn(Optional.of(user));
        when(service.placeCall(eq(VoiceProviderType.RETELL), any(VoiceProvider.StartCallRequest.class),
            eq(USER_ID), eq(campaignId), eq(UserRole.AGENT))).thenReturn(call());

        mvc.perform(post("/voice/calls/start")
                .param("provider", "RETELL")
                .param("phoneNumber", "+5491112345678")
                .param("campaignId", campaignId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerCallId").value("vapi-call-1"));

        verify(service).placeCall(eq(VoiceProviderType.RETELL), any(VoiceProvider.StartCallRequest.class),
            eq(USER_ID), eq(campaignId), eq(UserRole.AGENT));
    }

    @Test
    @DisplayName("webhook: invalid signature is rejected with 401 and no state is touched")
    void webhook_badSignature_rejected() throws Exception {
        when(signatureValidator.verify(any(), any(), any(), any())).thenReturn(false);

        mvc.perform(post("/voice/webhook/retell")
                .header("X-Retell-Signature", "forged")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"call_started\"}"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("webhook: Retell nested call shape parses call_id, status, duration_ms, cost, reason")
    void webhook_retell_nestedCallShape() throws Exception {
        when(signatureValidator.verify(any(), any(), any(), any())).thenReturn(true);
        String payload = """
            {"event":"call_ended",
             "call":{"call_id":"call_abc","call_type":"web_call","agent_id":"agent_1",
                     "call_status":"ongoing","start_timestamp":1,"end_timestamp":2,
                     "duration_ms":130843,"recording_url":"https://rec.example/x",
                     "transcript":"Hola","disconnection_reason":"agent_hangup",
                     "call_cost":{"combined_cost":31.33,"total_duration_seconds":131,"product_costs":[]}}}""";

        mvc.perform(post("/voice/webhook/retell")
                .header("X-Retell-Signature", "sig")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        verify(service).applyWebhook(eq("retell"), eq("call_abc"),
            eq(VoiceCallStatus.IN_PROGRESS), eq(130), eq(new BigDecimal("31.33")),
            eq("Hola"), eq("https://rec.example/x"), eq("agent_hangup"), isNull());
    }

    @Test
    @DisplayName("webhook: Retell flat legacy shape still works (status/cost_total_cost/end_reason fallbacks)")
    void webhook_retell_flatLegacyShape() throws Exception {
        when(signatureValidator.verify(any(), any(), any(), any())).thenReturn(true);
        String payload = """
            {"event":"call_started","call_id":"call_flat","status":"ended",
             "duration_ms":5000,"end_reason":"customer_hangup",
             "call_cost":{"total_cost":"5.5"}}""";

        mvc.perform(post("/voice/webhook/retell")
                .header("X-Retell-Signature", "sig")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        verify(service).applyWebhook(eq("retell"), eq("call_flat"),
            eq(VoiceCallStatus.ENDED), eq(5), eq(new BigDecimal("5.5")),
            isNull(), isNull(), eq("customer_hangup"), isNull());
    }

    @Test
    @DisplayName("webhook: Vapi root shape is untouched")
    void webhook_vapi_rootShape() throws Exception {
        when(signatureValidator.verify(any(), any(), any(), any())).thenReturn(true);
        String payload = """
            {"id":"vapi-1","status":"ended","duration":90,"cost":"1.20",
             "transcript":"Hola","recordingUrl":"https://rec.example/v","endedReason":"customer_end"}""";

        mvc.perform(post("/voice/webhook/vapi")
                .header("X-Vapi-Secret", "secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        verify(service).applyWebhook(eq("vapi"), eq("vapi-1"),
            eq(VoiceCallStatus.ENDED), eq(90), eq(new BigDecimal("1.20")),
            eq("Hola"), eq("https://rec.example/v"), eq("customer_end"), isNull());
    }

    @Test
    @DisplayName("web-call: persists the call and returns access_token, call_id and voice_call_id")
    void webCall_persistsAndReturnsTokens() throws Exception {
        when(retellProvider.createWebCall(null))
            .thenReturn(new RetellProvider.WebCallResult("retell-call-1", "access_123"));
        VoiceCall saved = mock(VoiceCall.class);
        when(saved.getId()).thenReturn(CALL_ID);
        when(service.recordWebCall(any(), eq("retell-call-1"), any()))
            .thenReturn(Optional.of(saved));

        mvc.perform(post("/voice/web-call")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"business_id\":\"biz-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").value("access_123"))
            .andExpect(jsonPath("$.call_id").value("retell-call-1"))
            .andExpect(jsonPath("$.voice_call_id").value(CALL_ID.toString()));
    }

    @Test
    @DisplayName("web-call: agent_id override is forwarded in metadata")
    void webCall_agentIdOverride() throws Exception {
        when(retellProvider.createWebCall("agent_x"))
            .thenReturn(new RetellProvider.WebCallResult("retell-call-2", "access_456"));
        when(service.recordWebCall(any(), eq("retell-call-2"), eq(Map.of("agentId", "agent_x"))))
            .thenReturn(Optional.empty());

        mvc.perform(post("/voice/web-call")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agent_id\":\"agent_x\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").value("access_456"))
            .andExpect(jsonPath("$.call_id").value("retell-call-2"))
            .andExpect(jsonPath("$.voice_call_id").doesNotExist());

        verify(service).recordWebCall(isNull(), eq("retell-call-2"), eq(Map.of("agentId", "agent_x")));
    }

    private static final UUID CALL_ID = UUID.randomUUID();

    private static VoiceCall call() {
        return VoiceCall.builder()
            .id(UUID.randomUUID())
            .userId(USER_ID)
            .provider(VoiceProviderType.VAPI)
            .providerCallId("vapi-call-1")
            .phoneNumber("+5491112345678")
            .status(VoiceCallStatus.RINGING)
            .direction("OUTBOUND")
            .metadata(Map.of())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
}
