import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import {
  CalendarIntegration,
  CalendarProviderType
} from '../../shared/models/calendar.model';

@Injectable({ providedIn: 'root' })
export class CalendarApi {
  private readonly http = inject(HttpClient);

  /** Returns current user's integrations. NEVER returns decrypted tokens (server side never sends them). */
  list(): Observable<CalendarIntegration[]> {
    return this.http.get<CalendarIntegration[]>(apiUrl('/calendar/integrations'));
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
