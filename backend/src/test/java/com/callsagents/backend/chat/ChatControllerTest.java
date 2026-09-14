package com.callsagents.backend.chat;

import com.callsagents.backend.chatbot.ChatTurn;
import com.callsagents.backend.chatbot.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock ChatService chatService;
    @Mock ChatUsageLimiter usageLimiter;
    @Mock WidgetDomainGuard domainGuard;

    private ChatController controller;
    private final UUID businessId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatService, usageLimiter, domainGuard);
    }

    private MockHttpServletRequest requestWithReferer(String referer) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (referer != null) {
            request.addHeader("Referer", referer);
        }
        return request;
    }

    @Test
    @DisplayName("start() passes when business is allowed and within limits")
    void start_passesWhenAllowed() {
        when(usageLimiter.tryAcquire(businessId)).thenReturn(true);
        when(domainGuard.isAllowed(businessId, "https://script9.com/widget"))
            .thenReturn(true);
        when(chatService.start(any(), any())).thenReturn(
            new ChatResponse("s1", "¡Hola!", false, null, false));

        ResponseEntity<?> res = controller.start("s1", businessId,
            requestWithReferer("https://script9.com/widget"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(chatService).start("s1", businessId);
    }

    @Test
    @DisplayName("message() with an unrelated Referer domain returns 403 JSON")
    void message_unrelatedDomain_forbidden() {
        when(usageLimiter.tryAcquire(businessId)).thenReturn(true);
        when(domainGuard.isAllowed(businessId, "https://evil.example.com/x")).thenReturn(false);

        ChatRequest request = new ChatRequest("s1", "hola", businessId);
        ResponseEntity<?> res = controller.sendMessage(request,
            requestWithReferer("https://evil.example.com/x"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).isEqualTo(java.util.Map.of("error", "Domain not allowed"));
        verify(chatService, never()).processMessage(any(), any(), any());
    }

    @Test
    @DisplayName("message() without Referer but with configured domains returns 403")
    void message_missingReferer_forbidden() {
        when(usageLimiter.tryAcquire(businessId)).thenReturn(true);
        when(domainGuard.isAllowed(businessId, null)).thenReturn(false);

        ChatRequest request = new ChatRequest("s1", "hola", businessId);
        ResponseEntity<?> res = controller.sendMessage(request, requestWithReferer(null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).isEqualTo(java.util.Map.of("error", "Domain not allowed"));
    }

    @Test
    @DisplayName("message() when the minute limit is exceeded returns 429 JSON")
    void message_rateLimited_returns429() {
        when(usageLimiter.tryAcquire(businessId)).thenReturn(false);

        ChatRequest request = new ChatRequest("s1", "hola", businessId);
        ResponseEntity<?> res = controller.sendMessage(request, requestWithReferer("https://script9.com/x"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(res.getBody())
            .isEqualTo(java.util.Map.of("error", "Too many requests. Please try again later."));
        verify(chatService, never()).processMessage(any(), any(), any());
    }

    @Test
    @DisplayName("start() when the daily limit is exceeded returns 429")
    void start_rateLimited_returns429() {
        when(usageLimiter.tryAcquire(businessId)).thenReturn(false);

        ResponseEntity<?> res = controller.start("s1", businessId,
            requestWithReferer("https://script9.com/x"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(chatService, never()).start(any(), any());
    }

    @Test
    @DisplayName("message() with null businessId is never rate limited or domain blocked")
    void message_nullBusinessId_notBlocked() {
        when(chatService.processMessage(any(), any(), any())).thenReturn(
            new ChatResponse("s2", "ok", false, null, false));

        ChatRequest request = new ChatRequest("s1", "hola", null);
        ResponseEntity<?> res = controller.sendMessage(request, requestWithReferer(null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(usageLimiter, never()).tryAcquire(any());
        verify(domainGuard, never()).isAllowed(any(), any());
    }
}