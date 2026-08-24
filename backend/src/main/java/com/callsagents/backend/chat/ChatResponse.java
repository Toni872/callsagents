package com.callsagents.backend.chat;

/**
 * Response body for the chat API.
 */
public record ChatResponse(
    String sessionId,
    String reply,
    boolean leadCaptured
) {}
