import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { CampaignApi } from './campaign.api';
import { apiUrl } from './api-base';
import { PageResponse } from './http.types';
import {
  CampaignResponse,
  UpdateCampaignRequest,
  VoicePromptPreviewRequest,
  VoicePromptPreviewResponse
} from '../../shared/models/campaign.model';

describe('CampaignApi', () => {
  let api: CampaignApi;
  let http: HttpTestingController;

  const campaign = (id: string): CampaignResponse => ({
    id,
    name: `Campaña ${id}`,
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
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    api = TestBed.inject(CampaignApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('list() sends GET /campaigns with page/size/sort and status filter', () => {
    api.list({ page: 3, size: 25, status: 'RUNNING' }).subscribe();

    const req = http.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === apiUrl('/campaigns') &&
        r.params.get('page') === '3' &&
        r.params.get('size') === '25' &&
        r.params.get('sort') === 'createdAt,desc' &&
        r.params.get('status') === 'RUNNING'
    );
    expect(req.request.method).toBe('GET');
    req.flush(page([campaign('1')]));
  });

  it('getById() sends GET /campaigns/{id}', () => {
    let received: CampaignResponse | undefined;

    api.getById('10').subscribe((res) => (received = res));

    const req = http.expectOne(
      (r) => r.method === 'GET' && r.url === apiUrl('/campaigns/10')
    );
    req.flush(campaign('10'));

    expect(received?.id).toBe('10');
  });

  it('create() sends POST /campaigns with the request body', () => {
    const body = { name: 'Campaña nueva', description: null, script: null };

    api.create(body).subscribe();

    const req = http.expectOne((r) => r.method === 'POST' && r.url === apiUrl('/campaigns'));
    expect(req.request.body).toEqual(body);
    req.flush(campaign('2'));
  });

  it('update() sends PUT /campaigns/{id} with the request body', () => {
    const body: UpdateCampaignRequest = { name: 'Renombrada', status: 'SCHEDULED' };

    api.update('3', body).subscribe();

    const req = http.expectOne(
      (r) => r.method === 'PUT' && r.url === apiUrl('/campaigns/3')
    );
    expect(req.request.body).toEqual(body);
    req.flush(campaign('3'));
  });

  it('launch() sends POST /campaigns/{id}/launch', () => {
    api.launch('4').subscribe();

    const req = http.expectOne(
      (r) => r.method === 'POST' && r.url === apiUrl('/campaigns/4/launch')
    );
    expect(req.request.method).toBe('POST');
    req.flush(campaign('4'));
  });

  it('list() envía hasVoiceConfig=true y size cuando el filtro lo pide', () => {
    api.list({ hasVoiceConfig: true, size: 100 }).subscribe();

    const req = http.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === apiUrl('/campaigns') &&
        r.params.get('hasVoiceConfig') === 'true' &&
        r.params.get('size') === '100'
    );
    expect(req.request.method).toBe('GET');
    req.flush(page([campaign('1')]));
  });

  it('list() omite hasVoiceConfig cuando el filtro no lo define', () => {
    api.list().subscribe();

    const req = http.expectOne(
      (r) => r.method === 'GET' && r.url === apiUrl('/campaigns')
    );
    expect(req.request.params.get('hasVoiceConfig')).toBeNull();
    req.flush(page([]));
  });

  it('previewVoicePrompt() hace POST /campaigns/voice-prompt/preview con el body', () => {
    let received: VoicePromptPreviewResponse | undefined;
    const reqBody: VoicePromptPreviewRequest = { company: 'Acme', tone: 'cercano' };

    api.previewVoicePrompt(reqBody).subscribe((res) => (received = res));

    const req = http.expectOne(
      (r) =>
        r.method === 'POST' && r.url === apiUrl('/campaigns/voice-prompt/preview')
    );
    expect(req.request.body).toEqual(reqBody);
    req.flush({ prompt: 'Eres el asistente virtual de Acme...' });

    expect(received?.prompt).toContain('Acme');
  });
});
