package com.callsagents.backend.voice.domain;

public enum VoiceCallStatus {
    SCHEDULED,
    RINGING,
    IN_PROGRESS,
    FORWARDING,
    ENDED,
    FAILED,
    NO_ANSWER
}
