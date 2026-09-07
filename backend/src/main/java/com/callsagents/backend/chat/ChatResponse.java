package com.callsagents.backend.chat;

import com.callsagents.backend.chatbot.ChatButton;

import java.util.List;

/**
 * Response body for the chat API.
 *
 * @param sessionId     the conversation session id
 * @param reply         optional reply text (null when only buttons are returned)
 * @param leadCaptured  whether a lead was captured during this turn
 * @param buttons       optional interactive buttons (null when the turn is pure text)
 * @param contactForm   when true the web widget renders inline name/email fields
 */
public record ChatResponse(
    String sessionId,
    String reply,
    boolean leadCaptured,
    List<ChatButton> buttons,
    boolean contactForm
) {}
