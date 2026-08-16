import { BadgeTone } from '../../shared/components/badge.component';
import { VoiceCallStatus } from '../../shared/models/voice.model';

export interface VoiceCallStatusPresentation {
  tone: BadgeTone;
  live: boolean;
  label: string;
}

const STATUS_PRESENTATION: Record<
  VoiceCallStatus,
  VoiceCallStatusPresentation
> = {
  RINGING: { tone: 'warning', live: true, label: 'Sonando' },
  IN_PROGRESS: { tone: 'success', live: true, label: 'En curso' },
  FORWARDING: { tone: 'info', live: true, label: 'Desviando' },
  SCHEDULED: { tone: 'info', live: false, label: 'Programada' },
  ENDED: { tone: 'neutral', live: false, label: 'Finalizada' },
  FAILED: { tone: 'error', live: false, label: 'Fallida' },
  NO_ANSWER: { tone: 'warning', live: false, label: 'Sin respuesta' }
};

export const LIVE_CALL_STATUSES: ReadonlySet<VoiceCallStatus> = new Set([
  'RINGING',
  'IN_PROGRESS',
  'FORWARDING'
]);

export function voiceCallStatusTone(status: VoiceCallStatus): {
  tone: BadgeTone;
  live: boolean;
} {
  const { tone, live } = STATUS_PRESENTATION[status];
  return { tone, live };
}

export function voiceCallStatusLabel(status: VoiceCallStatus): string {
  return STATUS_PRESENTATION[status].label;
}

export function voiceCallStatusPresentation(
  status: VoiceCallStatus
): VoiceCallStatusPresentation {
  return STATUS_PRESENTATION[status];
}

export function isLiveStatus(status: VoiceCallStatus): boolean {
  return LIVE_CALL_STATUSES.has(status);
}
