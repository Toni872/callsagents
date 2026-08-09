export type VoiceProviderType = 'VAPI' | 'RETELL';

export type VoiceCallStatus =
  | 'SCHEDULED'
  | 'RINGING'
  | 'IN_PROGRESS'
  | 'FORWARDING'
  | 'ENDED'
  | 'FAILED'
  | 'NO_ANSWER';

export type VoiceCallDirection = 'OUTBOUND' | 'INBOUND';

export interface VoiceCall {
  id: string;
  leadId: string | null;
  appointmentId: string | null;
  userId: string;
  provider: VoiceProviderType | null;
  providerCallId: string | null;
  phoneNumber: string;
  status: VoiceCallStatus;
  direction: VoiceCallDirection;
  startedAt: string | null;
  endedAt: string | null;
  durationSeconds: number | null;
  /** Backend serializa BigDecimal como number o string. */
  costUsd: number | string | null;
  transcript: string | null;
  recordingUrl: string | null;
  errorMessage: string | null;
  metadata: Record<string, unknown> | null;
  createdAt: string;
}
