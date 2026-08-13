import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { CallApi } from './call.api';
import { apiUrl } from './api-base';
import { PageResponse } from './http.types';
import { CallResponse, UpdateCallRequest } from '../../shared/models/call.model';

describe('CallApi', () => {
  let api: CallApi;
  let http: HttpTestingController;

  const call = (id: string): CallResponse => ({
    id,
    campaignId: 'camp-1',
    leadId: 'lead-1',
    userId: 'user-1',
    startedAt: '2026-01-01T10:00:00Z',
    endedAt: '2026-01-01T10:05:00Z',
    durationSeconds: 300,
    status: 'CONNECTED',
    outcome: 'INTERESTED',
    recordingUrl: null,
    providerCallId: null,
    notes: null,
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T10:05:00Z'
  });

  const page = (content: CallResponse[]): PageResponse<CallResponse> => ({
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
    api = TestBed.inject(CallApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('list() sends GET /calls with page/size/sort and filters', () => {
    api
      .list({
        page: 1,
        size: 15,
        campaignId: 'camp-2',
        userId: 'user-2',
        status: 'CONNECTED'
      })
      .subscribe();

    const req = http.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === apiUrl('/calls') &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '15' &&
        r.params.get('sort') === 'createdAt,desc' &&
        r.params.get('campaignId') === 'camp-2' &&
        r.params.get('userId') === 'user-2' &&
        r.params.get('status') === 'CONNECTED'
    );
    expect(req.request.method).toBe('GET');
    req.flush(page([call('1')]));
  });

  it('getById() sends GET /calls/{id}', () => {
    let received: CallResponse | undefined;

    api.getById('77').subscribe((res) => (received = res));

    const req = http.expectOne((r) => r.method === 'GET' && r.url === apiUrl('/calls/77'));
    req.flush(call('77'));

    expect(received?.id).toBe('77');
  });

  it('create() sends POST /calls with the request body', () => {
    const body = {
      campaignId: 'camp-1',
      leadId: 'lead-1',
      userId: 'user-1',
      status: 'CONNECTED',
      notes: 'nota'
    };

    api.create(body).subscribe();

    const req = http.expectOne((r) => r.method === 'POST' && r.url === apiUrl('/calls'));
    expect(req.request.body).toEqual(body);
    req.flush(call('1'));
  });

  it('update() sends PUT /calls/{id} with the request body', () => {
    const body: UpdateCallRequest = { status: 'VOICEMAIL', notes: null };

    api.update('5', body).subscribe();

    const req = http.expectOne((r) => r.method === 'PUT' && r.url === apiUrl('/calls/5'));
    expect(req.request.body).toEqual(body);
    req.flush(call('5'));
  });
});
