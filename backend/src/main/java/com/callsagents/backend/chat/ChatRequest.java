package com.callsagents.backend.chat;

/**
 * Request body for the chat API.
 */
public record ChatRequest(
    String sessionId,
    String message
) {}
