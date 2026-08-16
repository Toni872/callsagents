import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { VoiceApi } from './voice.api';
import { apiUrl } from './api-base';
import { VoiceCall } from '../../shared/models/voice.model';

describe('VoiceApi', () => {
  let api: VoiceApi;
  let http: HttpTestingController;

  const voiceCall = (): VoiceCall => ({
    id: 'vc-1',
    leadId: null,
    appointmentId: null,
    userId: 'user-1',
    provider: 'RETELL',
    providerCallId: 'call-1',
    phoneNumber: '+5491112345678',
    status: 'RINGING',
    direction: 'OUTBOUND',
    startedAt: null,
    endedAt: null,
    durationSeconds: null,
    costUsd: null,
    transcript: null,
    recordingUrl: null,
    errorMessage: null,
    metadata: null,
    createdAt: '2026-01-01T00:00:00Z'
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    api = TestBed.inject(VoiceApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('startCall() sin campaignId envía solo provider y phoneNumber', () => {
    api.startCall('RETELL', '+5491112345678').subscribe();

    const req = http.expectOne(
      (r) =>
        r.method === 'POST' &&
        r.url === apiUrl('/voice/calls/start') &&
        r.params.get('provider') === 'RETELL' &&
        r.params.get('phoneNumber') === '+5491112345678' &&
        r.params.get('campaignId') === null
    );
    expect(req.request.method).toBe('POST');
    req.flush(voiceCall());
  });

  it('startCall() con campaignId lo añade a los query params', () => {
    const campaignId = '3f4a2b1c-1111-2222-3333-444455556666';

    api.startCall('RETELL', '+5491112345678', campaignId).subscribe();

    const req = http.expectOne(
      (r) =>
        r.method === 'POST' &&
        r.url === apiUrl('/voice/calls/start') &&
        r.params.get('provider') === 'RETELL' &&
        r.params.get('phoneNumber') === '+5491112345678' &&
        r.params.get('campaignId') === campaignId
    );
    expect(req.request.method).toBe('POST');
    req.flush(voiceCall());
  });
});
