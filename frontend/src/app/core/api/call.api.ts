import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import { PageResponse } from './http.types';
import {
  CallFilter,
  CallResponse,
  CreateCallRequest,
  UpdateCallRequest
} from '../../shared/models/call.model';

@Injectable({ providedIn: 'root' })
export class CallApi {
  private readonly http = inject(HttpClient);

  list(filter: CallFilter = {}): Observable<PageResponse<CallResponse>> {
    let params = new HttpParams()
      .set('page', String(filter.page ?? 0))
      .set('size', String(filter.size ?? 20))
      .set('sort', filter.sort ?? 'createdAt,desc');

    if (filter.campaignId) {
      params = params.set('campaignId', filter.campaignId);
    }
    if (filter.userId) {
      params = params.set('userId', filter.userId);
    }
    if (filter.leadId) {
      params = params.set('leadId', filter.leadId);
    }
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.outcome) {
      params = params.set('outcome', filter.outcome);
    }

    return this.http.get<PageResponse<CallResponse>>(apiUrl('/calls'), { params });
  }

  getById(id: string): Observable<CallResponse> {
    return this.http.get<CallResponse>(apiUrl(`/calls/${id}`));
  }

  create(req: CreateCallRequest): Observable<CallResponse> {
    return this.http.post<CallResponse>(apiUrl('/calls'), req);
  }

  update(id: string, req: UpdateCallRequest): Observable<CallResponse> {
    return this.http.put<CallResponse>(apiUrl(`/calls/${id}`), req);
  }
}
