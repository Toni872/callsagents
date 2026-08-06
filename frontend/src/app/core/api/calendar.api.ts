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
   * Returns the OAuth authorize URL. The browser must follow this URL
   * (it's a server-issued 302 redirect to Google).
   * In Angular we use `window.location.href` to navigate.
   */
  startConnectUrl(provider: CalendarProviderType): string {
    return apiUrl(`/calendar/integrations/${provider.toLowerCase()}/start`);
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
