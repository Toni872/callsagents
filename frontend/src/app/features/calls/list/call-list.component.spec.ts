import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { CallListComponent } from './call-list.component';
import { apiUrl } from '../../../core/api/api-base';
import { PageResponse } from '../../../core/api/http.types';
import { CallResponse } from '../../../shared/models/call.model';

describe('CallListComponent', () => {
  let fixture: ComponentFixture<CallListComponent>;
  let http: HttpTestingController;

  const call = (id: string): CallResponse => ({
    id,
    campaignId: 'camp-1',
    leadId: 'lead-1',
    userId: 'user-1',
    startedAt: '2026-01-01T10:00:00Z',
    endedAt: null,
    durationSeconds: 120,
    status: 'CONNECTED',
    outcome: 'INTERESTED',
    recordingUrl: null,
    providerCallId: null,
    notes: null,
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T10:00:00Z'
  });

  const page = (content: CallResponse[], totalPages = 1): PageResponse<CallResponse> => ({
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages,
    first: true,
    last: totalPages <= 1
  });

  function flushCalls(content: CallResponse[], totalPages = 1): void {
    const req = http.expectOne(
      (r) => r.method === 'GET' && r.url === apiUrl('/calls')
    );
    req.flush(page(content, totalPages));
    fixture.detectChanges();
  }

  function buttonByText(text: string): HTMLButtonElement {
    const buttons = fixture.debugElement.queryAll(By.css('button'));
    const match = buttons.find((b) =>
      b.nativeElement.textContent?.trim().includes(text)
    );
    expect(match).withContext(`button "${text}" not found`).toBeTruthy();
    return match!.nativeElement as HTMLButtonElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CallListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CallListComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  it('loads and renders calls on init', () => {
    flushCalls([call('1'), call('2')]);

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('CONNECTED');
    expect(text).toContain('INTERESTED');
    expect(text).toContain('2m 00s');
  });

  it('pagination buttons request the next page', () => {
    flushCalls([call('1')], 3);

    buttonByText('Siguiente').click();

    const req = http.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === apiUrl('/calls') &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '20'
    );
    req.flush(page([call('2')], 3));
    fixture.detectChanges();
  });
});
