import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { apiUrl } from './api-base';
import { WebCallResponse } from '../../shared/models/web-call.model';

@Injectable({ providedIn: 'root' })
export class VoiceWebApi {
  private readonly http = inject(HttpClient);

  /**
   * Creates a Retell web call against the backend. The backend applies the
   * global RETELL_AGENT_ID when no agent_id is sent, so we send an empty body.
   */
  createWebCall(): Observable<WebCallResponse> {
    return this.http.post<WebCallResponse>(apiUrl('/voice/web-call'), {}).pipe(
      catchError((err: HttpErrorResponse) => {
        const message = this.extractErrorMessage(err);
        return throwError(() => new Error(message));
      })
    );
  }

  private extractErrorMessage(err: HttpErrorResponse): string {
    if (err.error && typeof err.error === 'object' && typeof err.error['error'] === 'string') {
      return err.error['error'];
    }
    if (err.status === 400) {
      return 'No se pudo iniciar la llamada. Verifica la configuración del agente de voz.';
    }
    return 'Error de conexión. Intenta de nuevo.';
  }
}
