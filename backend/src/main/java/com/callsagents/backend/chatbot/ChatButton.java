package com.callsagents.backend.chatbot;

/**
 * A single interactive button presented to the user.
 *
 * @param id    machine-readable button id sent back to the server when pressed
 *              (e.g. "intent_ventas", "timing_now", "confirm_yes")
 * @param label human-readable label rendered on the button (e.g. "Ventas")
 */
public record ChatButton(String id, String label) {}
