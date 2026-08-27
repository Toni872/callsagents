package com.callsagents.backend.escalation.entity;

/**
 * Lifecycle stage of an escalation for a qualified lead.
 *
 * <p>QUALIFIED       → lead confirmed a demo (flow started)
 * FOLLOWUP_SENT   → WhatsApp follow-up message delivered
 * WAITING_REPLY   → waiting for the lead to reply (timeout arms the voice call)
 * VOICE_CALLED    → Retell outbound voice call placed (fallback)
 * RESOLVED        → lead replied (or call succeeded) — end state
 * ABANDONED       → could not proceed (no phone, do-not-call) — end state
 * CANCELLED       → manually cancelled — end state
 */
public enum EscalationStage {
    QUALIFIED,
    FOLLOWUP_SENT,
    WAITING_REPLY,
    VOICE_CALLED,
    RESOLVED,
    ABANDONED,
    CANCELLED
}
