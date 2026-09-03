package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.whatsapp.config.GroqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroqServiceStructuredOutputTest {

    @Mock GroqConfig config;
    @Mock RestTemplate restTemplate;

    private GroqService groqService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        groqService = new GroqService(config, objectMapper);
        ReflectionTestUtils.setField(groqService, "restTemplate", restTemplate);
    }

    private void stubGroqResponse(String jsonContent) {
        @SuppressWarnings("unchecked")
        ResponseEntity<String> response = mock(ResponseEntity.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);
        when(response.getBody()).thenReturn(jsonContent);
        doReturn(response).when(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("chatStructured: sends response_format json_schema with correct schema and temp 0.3")
    void chatStructured_sendsCorrectSchemaAndTemp() {
        when(config.isConfigured()).thenReturn(true);
        when(config.getApiKey()).thenReturn("key_test");
        when(config.getApiUrl()).thenReturn("https://api.groq.com/openai/v1/chat/completions");
        when(config.getStructuredModel()).thenReturn("openai/gpt-oss-20b");

        stubGroqResponse("""
            {"choices":[{"message":{"content":"{\\"response\\":\\"Perfecto, te ayudo\\",\\"lead\\":null}"}}]}
            """);

        List<Map<String, String>> history = List.of();
        GroqService.LeadExtraction result = groqService.chatStructured(
            "You are a lead extractor", history, "Me llamo Juan, juan@test.com"
        );

        assertThat(result).isNotNull();
        assertThat(result.response()).isEqualTo("Perfecto, te ayudo");
        assertThat(result.lead()).isNull();

        // Verify the request body contains correct schema and temperature
        ArgumentCaptor<HttpEntity<Map>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        Map<String, Object> body = captor.getValue().getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("model")).isEqualTo("openai/gpt-oss-20b");
        assertThat(body.get("temperature")).isEqualTo(0.3);

        Map<String, Object> responseFormat = (Map<String, Object>) body.get("response_format");
        assertThat(responseFormat).isNotNull();
        assertThat(responseFormat.get("type")).isEqualTo("json_schema");

        Map<String, Object> jsonSchema = (Map<String, Object>) responseFormat.get("json_schema");
        assertThat(jsonSchema.get("name")).isEqualTo("lead_extraction");
        assertThat(jsonSchema.get("strict")).isEqualTo(true);
        assertThat(jsonSchema.get("schema")).isNotNull();
    }

    @Test
    @DisplayName("chatStructured: returns LeadExtraction with populated lead fields")
    void chatStructured_returnsPopulatedLead() {
        when(config.isConfigured()).thenReturn(true);
        when(config.getApiKey()).thenReturn("key_test");
        when(config.getApiUrl()).thenReturn("https://api.groq.com/openai/v1/chat/completions");
        when(config.getStructuredModel()).thenReturn("openai/gpt-oss-20b");

        stubGroqResponse("""
            {"choices":[{"message":{"content":"{\\"response\\":\\"Perfecto Juan\\",\\"lead\\":{\\"name\\":\\"Juan\\",\\"email\\":\\"juan@test.com\\",\\"service\\":\\"taxi\\",\\"timing\\":\\"now\\"}}}"}}]}
            """);

        List<Map<String, String>> history = List.of();
        GroqService.LeadExtraction result = groqService.chatStructured(
            "You are a lead extractor", history, "Me llamo Juan, juan@test.com"
        );

        assertThat(result).isNotNull();
        assertThat(result.response()).isEqualTo("Perfecto Juan");
        assertThat(result.lead()).isNotNull();
        assertThat(result.lead().name()).isEqualTo("Juan");
        assertThat(result.lead().email()).isEqualTo("juan@test.com");
        assertThat(result.lead().service()).isEqualTo("taxi");
        assertThat(result.lead().timing()).isEqualTo("now");
    }

    @Test
    @DisplayName("chatStructured: null lead when model returns partial data")
    void chatStructured_nullLeadOnPartialData() {
        when(config.isConfigured()).thenReturn(true);
        when(config.getApiKey()).thenReturn("key_test");
        when(config.getApiUrl()).thenReturn("https://api.groq.com/openai/v1/chat/completions");
        when(config.getStructuredModel()).thenReturn("openai/gpt-oss-20b");

        stubGroqResponse("""
            {"choices":[{"message":{"content":"{\\"response\\":\\"¿Cuál es tu email?\\",\\"lead\\":null}"}}]}
            """);

        List<Map<String, String>> history = List.of();
        GroqService.LeadExtraction result = groqService.chatStructured(
            "You are a lead extractor", history, "Me llamo Juan"
        );

        assertThat(result).isNotNull();
        assertThat(result.lead()).isNull();
    }

    @Test
    @DisplayName("chatStructured: uses system prompt in messages")
    void chatStructured_includesSystemPrompt() {
        when(config.isConfigured()).thenReturn(true);
        when(config.getApiKey()).thenReturn("key_test");
        when(config.getApiUrl()).thenReturn("https://api.groq.com/openai/v1/chat/completions");
        when(config.getStructuredModel()).thenReturn("openai/gpt-oss-20b");

        stubGroqResponse("""
            {"choices":[{"message":{"content":"{\\"response\\":\\"Ok\\",\\"lead\\":null}"}}]}
            """);

        List<Map<String, String>> history = List.of();
        groqService.chatStructured("System prompt here", history, "hello");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        Map<String, Object> body = captor.getValue().getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role")).isEqualTo("system");
        assertThat(messages.get(0).get("content")).isEqualTo("System prompt here");
        assertThat(messages.get(1).get("role")).isEqualTo("user");
        assertThat(messages.get(1).get("content")).isEqualTo("hello");
    }

    @Test
    @DisplayName("chatStructured: HTTP 400 falls back to unstructured chat()")
    void chatStructured_http400_fallsBackToChat() {
        when(config.isConfigured()).thenReturn(true);
        when(config.getApiKey()).thenReturn("key_test");
        when(config.getApiUrl()).thenReturn("https://api.groq.com/openai/v1/chat/completions");
        when(config.getStructuredModel()).thenReturn("openai/gpt-oss-20b");

        // Use a spy so we can stub chat() independently
        GroqService spyService = spy(groqService);
        doReturn("Fallback response text").when(spyService).chat(anyString(), anyList(), anyString());

        // First call (chatStructured) throws 400
        HttpClientErrorException badRequest = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request",
            HttpHeaders.EMPTY, "{\"error\":\"model does not support structured output\"}".getBytes(),
            java.nio.charset.StandardCharsets.UTF_8
        );
        doThrow(badRequest).when(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));

        List<Map<String, String>> history = List.of();
        GroqService.LeadExtraction result = spyService.chatStructured(
            "System prompt", history, "hello"
        );

        // Fallback returns response text with null lead
        assertThat(result).isNotNull();
        assertThat(result.response()).isEqualTo("Fallback response text");
        assertThat(result.lead()).isNull();
    }
}
