import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import { PageResponse } from './http.types';
import {
  AppointmentFilter,
  AppointmentResponse,
  CreateAppointmentRequest,
  UpdateAppointmentRequest
} from '../../shared/models/appointment.model';

@Injectable({ providedIn: 'root' })
export class AppointmentApi {
  private readonly http = inject(HttpClient);

  list(filter: AppointmentFilter = {}): Observable<PageResponse<AppointmentResponse>> {
    let params = new HttpParams()
      .set('page', String(filter.page ?? 0))
      .set('size', String(filter.size ?? 20))
      .set('sort', filter.sort ?? 'scheduledAt,asc');

    if (filter.leadId) {
      params = params.set('leadId', filter.leadId);
    }
    if (filter.userId) {
      params = params.set('userId', filter.userId);
    }
    if (filter.status) {
      params = params.set('status', filter.status);
    }

    return this.http.get<PageResponse<AppointmentResponse>>(apiUrl('/appointments'), { params });
  }

  getById(id: string): Observable<AppointmentResponse> {
    return this.http.get<AppointmentResponse>(apiUrl(`/appointments/${id}`));
  }

  create(req: CreateAppointmentRequest): Observable<AppointmentResponse> {
    return this.http.post<AppointmentResponse>(apiUrl('/appointments'), req);
  }

  update(id: string, req: UpdateAppointmentRequest): Observable<AppointmentResponse> {
    return this.http.put<AppointmentResponse>(apiUrl(`/appointments/${id}`), req);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(apiUrl(`/appointments/${id}`));
  }
}
