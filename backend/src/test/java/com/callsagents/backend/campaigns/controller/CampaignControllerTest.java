package com.callsagents.backend.campaigns.controller;

import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.auth.security.JwtService;
import com.callsagents.backend.campaigns.dto.VoicePromptPreviewRequest;
import com.callsagents.backend.campaigns.dto.VoicePromptPreviewResponse;
import com.callsagents.backend.campaigns.service.CampaignService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(CampaignController.class)
class CampaignControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private CampaignService campaignService;
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

    @Test
    @DisplayName("preview: unauthenticated request is rejected with 401")
    void preview_unauthorized() throws Exception {
        mvc.perform(post("/campaigns/voice-prompt/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
        verify(campaignService, never()).previewVoicePrompt(any());
    }

    @Test
    @DisplayName("preview: non-admin role is rejected with 403")
    @WithMockUser(username = "agent@callsagents.com", roles = "AGENT")
    void preview_forbiddenForNonAdmin() throws Exception {
        mvc.perform(post("/campaigns/voice-prompt/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
        verify(campaignService, never()).previewVoicePrompt(any());
    }

    @Test
    @DisplayName("preview: invalid body fails validation with 400 before touching the service")
    @WithMockUser(username = "admin@callsagents.com", roles = "ADMIN")
    void preview_invalidBody_returns400() throws Exception {
        mvc.perform(post("/campaigns/voice-prompt/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":\"Acme\",\"website\":\"not-a-url\"}"))
            .andExpect(status().isBadRequest());
        verify(campaignService, never()).previewVoicePrompt(any());
    }

    @Test
    @DisplayName("preview: valid body as ADMIN returns the composed prompt")
    @WithMockUser(username = "admin@callsagents.com", roles = "ADMIN")
    void preview_validBody_returnsPrompt() throws Exception {
        when(campaignService.previewVoicePrompt(any(VoicePromptPreviewRequest.class)))
            .thenReturn(new VoicePromptPreviewResponse("Eres el asistente virtual de Acme..."));

        mvc.perform(post("/campaigns/voice-prompt/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":\"Acme\",\"website\":\"https://acme.com\","
                    + "\"industry\":\"SaaS\",\"services\":\"CRM\",\"tone\":\"cercano\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"prompt\":\"Eres el asistente virtual de Acme...\"}"));
    }
}
