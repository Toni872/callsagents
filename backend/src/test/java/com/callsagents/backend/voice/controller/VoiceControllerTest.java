package com.callsagents.backend.voice.controller;

import com.callsagents.backend.auth.entity.User;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(VoiceController.class)
class VoiceControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private VoiceCallService service;
    @MockBean private WebhookSignatureValidator signatureValidator;
    @MockBean private RetellProvider retellProvider;
    @MockBean private UserRepository userRepository;
    @MockBean private JwtService jwtService;

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
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
        User user = User.builder().id(USER_ID).email("agent@callsagents.com").build();
        when(userRepository.findByEmail("agent@callsagents.com")).thenReturn(Optional.of(user));
        when(service.placeCall(eq(VoiceProviderType.VAPI), any(VoiceProvider.StartCallRequest.class),
            eq(USER_ID), isNull())).thenReturn(call());

        mvc.perform(post("/voice/calls/start")
                .param("provider", "VAPI")
                .param("phoneNumber", "+5491112345678"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerCallId").value("vapi-call-1"));

        verify(service).placeCall(eq(VoiceProviderType.VAPI), any(VoiceProvider.StartCallRequest.class),
            eq(USER_ID), isNull());
    }

    @Test
    @DisplayName("startCall: agent with campaignId delegates it")
    @WithMockUser(username = "agent@callsagents.com", roles = "AGENT")
    void startCall_withCampaignId() throws Exception {
        UUID campaignId = UUID.randomUUID();
        User user = User.builder().id(USER_ID).email("agent@callsagents.com").build();
        when(userRepository.findByEmail("agent@callsagents.com")).thenReturn(Optional.of(user));
        when(service.placeCall(eq(VoiceProviderType.RETELL), any(VoiceProvider.StartCallRequest.class),
            eq(USER_ID), eq(campaignId))).thenReturn(call());

        mvc.perform(post("/voice/calls/start")
                .param("provider", "RETELL")
                .param("phoneNumber", "+5491112345678")
                .param("campaignId", campaignId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerCallId").value("vapi-call-1"));

        verify(service).placeCall(eq(VoiceProviderType.RETELL), any(VoiceProvider.StartCallRequest.class),
            eq(USER_ID), eq(campaignId));
    }

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
