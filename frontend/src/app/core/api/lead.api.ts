import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import { PageResponse } from './http.types';
import {
  CreateLeadRequest,
  LeadFilter,
  LeadResponse,
  UpdateLeadRequest
} from '../../shared/models/lead.model';

@Injectable({ providedIn: 'root' })
export class LeadApi {
  private readonly http = inject(HttpClient);

  list(filter: LeadFilter = {}): Observable<PageResponse<LeadResponse>> {
    let params = new HttpParams()
      .set('page', String(filter.page ?? 0))
      .set('size', String(filter.size ?? 20))
      .set('sort', filter.sort ?? 'createdAt,desc');

    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.source) {
      params = params.set('source', filter.source);
    }
    if (filter.assignedToId) {
      params = params.set('assignedToId', filter.assignedToId);
    }
    if (filter.search) {
      params = params.set('search', filter.search);
    }

    return this.http.get<PageResponse<LeadResponse>>(apiUrl('/leads'), { params });
  }

  getById(id: string): Observable<LeadResponse> {
    return this.http.get<LeadResponse>(apiUrl(`/leads/${id}`));
  }

  create(req: CreateLeadRequest): Observable<LeadResponse> {
    return this.http.post<LeadResponse>(apiUrl('/leads'), req);
  }

  update(id: string, req: UpdateLeadRequest): Observable<LeadResponse> {
    return this.http.put<LeadResponse>(apiUrl(`/leads/${id}`), req);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(apiUrl(`/leads/${id}`));
  }
}
