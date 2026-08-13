import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { AppointmentListComponent } from './appointment-list.component';
import { apiUrl } from '../../../core/api/api-base';
import { PageResponse } from '../../../core/api/http.types';
import { AppointmentResponse } from '../../../shared/models/appointment.model';

describe('AppointmentListComponent', () => {
  let fixture: ComponentFixture<AppointmentListComponent>;
  let http: HttpTestingController;

  const appointment = (id: string): AppointmentResponse => ({
    id,
    leadId: 'lead-1',
    userId: 'user-1',
    scheduledAt: '2026-01-05T15:00:00Z',
    durationMinutes: 30,
    status: 'CONFIRMED',
    notes: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    externalEventId: null,
    externalEventUrl: null
  });

  const page = (
    content: AppointmentResponse[],
    totalPages = 1
  ): PageResponse<AppointmentResponse> => ({
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages,
    first: true,
    last: totalPages <= 1
  });

  function flushAppointments(
    content: AppointmentResponse[],
    totalPages = 1
  ): void {
    const req = http.expectOne(
      (r) => r.method === 'GET' && r.url === apiUrl('/appointments')
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
      imports: [AppointmentListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AppointmentListComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  it('loads and renders appointments on init', () => {
    flushAppointments([appointment('1'), appointment('2')]);

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('CONFIRMED');
    expect(text).toContain('30 min');
    expect(text).toContain('2 citas');
  });

  it('pagination buttons request the next page', () => {
    flushAppointments([appointment('1')], 3);

    buttonByText('Siguiente').click();

    const req = http.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === apiUrl('/appointments') &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '20'
    );
    req.flush(page([appointment('2')], 3));
    fixture.detectChanges();
  });
});
