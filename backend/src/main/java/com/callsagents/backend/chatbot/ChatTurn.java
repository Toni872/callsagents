package com.callsagents.backend.chatbot;

import java.util.List;

/**
 * A single "turn" produced by the shared chatbot engine, completely
 * channel-agnostic: it carries only data (reply text + optional buttons), never
 * a reference to Vonage, Retell or any channel.
 *
 * <p>Semantics of the two nullable fields:
 * <ul>
 *   <li>{@code reply} may be {@code null} when the turn only sends interactive
 *       buttons without an accompanying standalone text (e.g. the timing
 *       buttons), or non-null to render a text bubble (e.g. the greeting body
 *       or the confirmation summary).</li>
 *   <li>{@code buttons} is non-null only when the turn presents interactive
 *       buttons; the WhatsApp adapter sends them as Vonage reply buttons, the
 *       web adapter returns them in JSON and the frontend renders them.</li>
 * </ul>
 *
 * @param reply        optional reply text (null when only buttons are sent with no text)
 * @param buttons      optional interactive buttons (null when the turn is pure text)
 * @param leadCaptured whether a lead was captured/saved during this turn
 * @param contactForm  when true the web widget renders inline name/email fields instead of the free-text input
 */
public record ChatTurn(String reply, List<ChatButton> buttons, boolean leadCaptured, boolean contactForm) {
    public static ChatTurn text(String reply) {
        return new ChatTurn(reply, null, false, false);
    }

    public static ChatTurn text(String reply, boolean leadCaptured) {
        return new ChatTurn(reply, null, leadCaptured, false);
    }

    public static ChatTurn textContact(String reply) {
        return new ChatTurn(reply, null, false, true);
    }

    public static ChatTurn buttons(String reply, List<ChatButton> buttons) {
        return new ChatTurn(reply, buttons, false, false);
    }

    public static ChatTurn buttons(String reply, List<ChatButton> buttons, boolean leadCaptured) {
        return new ChatTurn(reply, buttons, leadCaptured, false);
    }
}
