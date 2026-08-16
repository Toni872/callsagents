import {
  isLiveStatus,
  voiceCallStatusLabel,
  voiceCallStatusTone
} from './voice-call-status.util';

describe('voiceCallStatusTone', () => {
  it('maps live states to their tone with live flag', () => {
    expect(voiceCallStatusTone('RINGING')).toEqual({
      tone: 'warning',
      live: true
    });
    expect(voiceCallStatusTone('IN_PROGRESS')).toEqual({
      tone: 'success',
      live: true
    });
    expect(voiceCallStatusTone('FORWARDING')).toEqual({
      tone: 'info',
      live: true
    });
  });

  it('maps non-live states without live flag', () => {
    expect(voiceCallStatusTone('SCHEDULED')).toEqual({
      tone: 'info',
      live: false
    });
    expect(voiceCallStatusTone('ENDED')).toEqual({
      tone: 'neutral',
      live: false
    });
    expect(voiceCallStatusTone('FAILED')).toEqual({
      tone: 'error',
      live: false
    });
    expect(voiceCallStatusTone('NO_ANSWER')).toEqual({
      tone: 'warning',
      live: false
    });
  });
});

describe('isLiveStatus', () => {
  it('returns true only for live statuses', () => {
    expect(isLiveStatus('RINGING')).toBe(true);
    expect(isLiveStatus('IN_PROGRESS')).toBe(true);
    expect(isLiveStatus('FORWARDING')).toBe(true);
    expect(isLiveStatus('SCHEDULED')).toBe(false);
    expect(isLiveStatus('ENDED')).toBe(false);
    expect(isLiveStatus('FAILED')).toBe(false);
    expect(isLiveStatus('NO_ANSWER')).toBe(false);
  });
});

describe('voiceCallStatusLabel', () => {
  it('provides a Spanish label for every status', () => {
    expect(voiceCallStatusLabel('RINGING')).toBe('Sonando');
    expect(voiceCallStatusLabel('IN_PROGRESS')).toBe('En curso');
    expect(voiceCallStatusLabel('FORWARDING')).toBe('Desviando');
    expect(voiceCallStatusLabel('SCHEDULED')).toBe('Programada');
    expect(voiceCallStatusLabel('ENDED')).toBe('Finalizada');
    expect(voiceCallStatusLabel('FAILED')).toBe('Fallida');
    expect(voiceCallStatusLabel('NO_ANSWER')).toBe('Sin respuesta');
  });
});
