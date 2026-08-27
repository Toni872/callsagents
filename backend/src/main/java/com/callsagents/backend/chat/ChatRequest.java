package com.callsagents.backend.chat;

import java.util.UUID;

/**
 * Request body for the chat API.
 */
public record ChatRequest(
    String sessionId,
    String message,
    UUID businessId
) {}
