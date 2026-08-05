export type CallStatus =
  | 'CONNECTED'
  | 'VOICEMAIL'
  | 'NO_ANSWER'
  | 'BUSY'
  | 'FAILED';

export type CallOutcome =
  | 'INTERESTED'
  | 'NOT_INTERESTED'
  | 'CALLBACK'
  | 'APPOINTMENT_SET'
  | 'NOT_REACHED';

export interface CallResponse {
  id: string;
  campaignId: string;
  leadId: string;
  userId: string;
  startedAt: string | null;
  endedAt: string | null;
  durationSeconds: number | null;
  status: CallStatus;
  outcome: CallOutcome | null;
  recordingUrl: string | null;
  providerCallId: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCallRequest {
  campaignId: string;
  leadId: string;
  userId: string;
  startedAt?: string | null;
  endedAt?: string | null;
  durationSeconds?: number | null;
  status?: string;
  outcome?: string | null;
  recordingUrl?: string | null;
  providerCallId?: string | null;
  notes?: string | null;
}

export interface UpdateCallRequest {
  startedAt?: string | null;
  endedAt?: string | null;
  durationSeconds?: number | null;
  status?: CallStatus;
  outcome?: CallOutcome | null;
  recordingUrl?: string | null;
  providerCallId?: string | null;
  notes?: string | null;
}

export interface CallFilter {
  page?: number;
  size?: number;
  campaignId?: string;
  userId?: string;
  leadId?: string;
  status?: CallStatus;
  outcome?: CallOutcome;
  sort?: string;
}
