import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import {
  CalendarInfo,
  CalendarIntegration,
  CalendarProviderStatus,
  CalendarProviderType,
  CalendarSelectionPayload
} from '../../shared/models/calendar.model';

@Injectable({ providedIn: 'root' })
export class CalendarApi {
  private readonly http = inject(HttpClient);

  /** Returns current user's integrations. NEVER returns decrypted tokens (server side never sends them). */
  list(): Observable<CalendarIntegration[]> {
    return this.http.get<CalendarIntegration[]>(apiUrl('/calendar/integrations'));
  }

  /** Provider configuration status — used to show/hide connect buttons. */
  providers(): Observable<CalendarProviderStatus[]> {
    return this.http.get<CalendarProviderStatus[]>(apiUrl('/calendar/providers'));
  }

  /**
   * Admin-only: create Google events for existing future appointments that were
   * never synced (created before the integration existed, or sync failed).
   * Returns { scanned, created, failed }.
   */
  backfill(): Observable<{ scanned: number; created: number; failed: number }> {
    return this.http.post<{ scanned: number; created: number; failed: number }>(
      apiUrl('/calendar/integrations/backfill'),
      {}
    );
  }

  /**
   * Asks the backend (with the JWT) for the Google OAuth authorize URL.
   * Returns { authorizeUrl } — the browser must follow it with
   * window.location.href after this authenticated call succeeds.
   */
  startConnect(provider: CalendarProviderType): Observable<{ authorizeUrl: string }> {
    return this.http.get<{ authorizeUrl: string }>(
      apiUrl(`/calendar/integrations/${provider.toLowerCase()}/start`)
    );
  }

  /** Lists the calendars accessible with the user's integration token. */
  listCalendars(provider: CalendarProviderType): Observable<CalendarInfo[]> {
    return this.http.get<CalendarInfo[]>(
      apiUrl(`/calendar/integrations/${provider.toLowerCase()}/calendars`)
    );
  }

  /** Persists the destination calendar + conflict-check calendars selection. */
  saveCalendars(
    id: string,
    payload: CalendarSelectionPayload
  ): Observable<CalendarIntegration> {
    return this.http.put<CalendarIntegration>(
      apiUrl(`/calendar/integrations/${id}/calendars`),
      payload
    );
  }

  disconnect(id: string): Observable<void> {
    return this.http.delete<void>(apiUrl(`/calendar/integrations/${id}`));
  }

  toggleSync(id: string): Observable<CalendarIntegration> {
    return this.http.post<CalendarIntegration>(
      apiUrl(`/calendar/integrations/${id}/sync-toggle`),
      {}
    );
  }
}
