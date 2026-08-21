package com.callsagents.backend.whatsapp.domain;

import java.time.LocalDateTime;

public record ConversationState(
    String phone,
    ConversationStep step,
    String serviceInterest,
    String name,
    String email,
    LocalDateTime startedAt
) {
    public static ConversationState initial(String phone) {
        return new ConversationState(phone, ConversationStep.INITIAL, null, null, null, LocalDateTime.now());
    }

    public ConversationState withStep(ConversationStep step) {
        return new ConversationState(this.phone, step, this.serviceInterest, this.name, this.email, this.startedAt);
    }

    public ConversationState withService(String serviceInterest) {
        return new ConversationState(this.phone, ConversationStep.SERVICE_RECEIVED, serviceInterest, this.name, this.email, this.startedAt);
    }

    public ConversationState withName(String name) {
        return new ConversationState(this.phone, ConversationStep.NAME_RECEIVED, this.serviceInterest, name, this.email, this.startedAt);
    }

    public ConversationState withEmail(String email) {
        return new ConversationState(this.phone, ConversationStep.EMAIL_RECEIVED, this.serviceInterest, this.name, email, this.startedAt);
    }
}
