import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { VoiceCallsComponent } from './voice-calls.component';
import { apiUrl } from '../../core/api/api-base';
import { PageResponse } from '../../core/api/http.types';
import { CampaignResponse } from '../../shared/models/campaign.model';
import { VoiceCall } from '../../shared/models/voice.model';

describe('VoiceCallsComponent', () => {
  let fixture: ComponentFixture<VoiceCallsComponent>;
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

  const campaign = (id: string, name: string): CampaignResponse => ({
    id,
    name,
    description: null,
    status: 'DRAFT',
    startAt: null,
    endAt: null,
    script: null,
    createdBy: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    company: null,
    website: null,
    industry: null,
    services: null,
    tone: null
  });

  const page = (content: CampaignResponse[]): PageResponse<CampaignResponse> => ({
    content,
    page: 0,
    size: 100,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VoiceCallsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    fixture = TestBed.createComponent(VoiceCallsComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    // carga inicial del historial
    http.expectOne((r) => r.method === 'GET' && r.url === apiUrl('/voice/calls')).flush([]);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  function buttonByText(text: string): HTMLButtonElement {
    const buttons = fixture.debugElement.queryAll(By.css('button'));
    const match = buttons.find((b) =>
      b.nativeElement.textContent?.trim().includes(text)
    );
    expect(match).withContext(`button "${text}" not found`).toBeTruthy();
    return match!.nativeElement as HTMLButtonElement;
  }

  function openStartDialog(): void {
    buttonByText('+ Iniciar llamada').click();
    fixture.detectChanges();
  }

  function flushVoiceCampaigns(content: CampaignResponse[]): void {
    const req = http.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === apiUrl('/campaigns') &&
        r.params.get('hasVoiceConfig') === 'true' &&
        r.params.get('size') === '100'
    );
    req.flush(page(content));
    fixture.detectChanges();
  }

  function setSelect(controlName: string, value: string): void {
    const select = fixture.nativeElement.querySelector(
      `select[formControlName="${controlName}"]`
    ) as HTMLSelectElement;
    expect(select).withContext(`select ${controlName} not found`).toBeTruthy();
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function setInput(controlName: string, value: string): void {
    const input = fixture.nativeElement.querySelector(
      `input[formControlName="${controlName}"]`
    ) as HTMLInputElement;
    expect(input).withContext(`input ${controlName} not found`).toBeTruthy();
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function startSubmitButton(): HTMLButtonElement {
    // el primer <dialog> del DOM es el de "Iniciar llamada"
    const el = fixture.debugElement.query(
      By.css('dialog button[type="submit"]')
    );
    expect(el).withContext('start dialog submit not found').toBeTruthy();
    return el.nativeElement as HTMLButtonElement;
  }

  it('muestra el selector de campaña solo cuando provider es RETELL', () => {
    openStartDialog();
    flushVoiceCampaigns([campaign('c1', 'Campaña A')]);

    // sin provider → sin selector
    expect(
      fixture.nativeElement.querySelector('select[formControlName="campaignId"]')
    ).toBeNull();

    // VAPI → sin selector
    setSelect('provider', 'VAPI');
    expect(
      fixture.nativeElement.querySelector('select[formControlName="campaignId"]')
    ).toBeNull();

    // RETELL → selector visible con opción por defecto + campañas con voz
    setSelect('provider', 'RETELL');
    const select = fixture.nativeElement.querySelector(
      'select[formControlName="campaignId"]'
    ) as HTMLSelectElement;
    expect(select).toBeTruthy();
    expect(select.textContent).toContain('Sin campaña (config por defecto)');
    expect(select.textContent).toContain('Campaña A');
  });

  it('pide al backend solo campañas con voz configurada (hasVoiceConfig=true)', () => {
    openStartDialog();
    flushVoiceCampaigns([campaign('c1', 'Campaña A'), campaign('c2', 'Campaña B')]);

    setSelect('provider', 'RETELL');
    const select = fixture.nativeElement.querySelector(
      'select[formControlName="campaignId"]'
    ) as HTMLSelectElement;
    const options = Array.from(select.options).map((o) => o.textContent?.trim());
    expect(options).toContain('Sin campaña (config por defecto)');
    expect(options).toContain('Campaña A');
    expect(options).toContain('Campaña B');
  });

  it('startCall envía campaignId cuando hay campaña seleccionada', () => {
    openStartDialog();
    flushVoiceCampaigns([
      campaign('3f4a2b1c-1111-2222-3333-444455556666', 'Campaña A')
    ]);

    setSelect('provider', 'RETELL');
    setSelect('campaignId', '3f4a2b1c-1111-2222-3333-444455556666');
    setInput('phoneNumber', '+5491112345678');

    startSubmitButton().click();

    const req = http.expectOne(
      (r) =>
        r.method === 'POST' &&
        r.url === apiUrl('/voice/calls/start') &&
        r.params.get('provider') === 'RETELL' &&
        r.params.get('phoneNumber') === '+5491112345678' &&
        r.params.get('campaignId') === '3f4a2b1c-1111-2222-3333-444455556666'
    );
    expect(req.request.method).toBe('POST');
    req.flush(voiceCall());
    fixture.detectChanges();

    // refetch del historial tras iniciar la llamada
    http.expectOne((r) => r.method === 'GET' && r.url === apiUrl('/voice/calls')).flush([]);
    fixture.detectChanges();
  });

  it('startCall omite campaignId cuando no hay selección', () => {
    openStartDialog();
    flushVoiceCampaigns([campaign('c1', 'Campaña A')]);

    setSelect('provider', 'RETELL');
    setInput('phoneNumber', '+5491112345678');

    startSubmitButton().click();

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
    fixture.detectChanges();

    http.expectOne((r) => r.method === 'GET' && r.url === apiUrl('/voice/calls')).flush([]);
    fixture.detectChanges();
  });
});
