package com.callsagents.backend.chatbot;

/**
 * The conversation channel driving the shared chatbot engine.
 *
 * <p>Channel-specific behavior (voice offers, escalations, lead lookups by
 * phone) is decided inside the engine based on this enum; the engine itself
 * stays free of any channel SDK (Vonage, Retell, ...).
 */
public enum Channel {
    WHATSAPP,
    WEB
}
