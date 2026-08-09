import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import { VoiceCall, VoiceProviderType } from '../../shared/models/voice.model';

@Injectable({ providedIn: 'root' })
export class VoiceApi {
  private readonly http = inject(HttpClient);

  list(): Observable<VoiceCall[]> {
    return this.http.get<VoiceCall[]>(apiUrl('/voice/calls'));
  }

  getById(id: string): Observable<VoiceCall> {
    return this.http.get<VoiceCall>(apiUrl(`/voice/calls/${id}`));
  }

  startCall(
    provider: VoiceProviderType,
    phoneNumber: string
  ): Observable<VoiceCall> {
    const params = new HttpParams()
      .set('provider', provider)
      .set('phoneNumber', phoneNumber);
    return this.http.post<VoiceCall>(apiUrl('/voice/calls/start'), null, {
      params
    });
  }

  logManualCall(call: Partial<VoiceCall>): Observable<VoiceCall> {
    return this.http.post<VoiceCall>(apiUrl('/voice/calls/log'), call);
  }
}
