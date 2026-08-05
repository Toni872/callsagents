import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import { PageResponse } from './http.types';
import {
  CampaignFilter,
  CampaignResponse,
  CreateCampaignRequest,
  UpdateCampaignRequest
} from '../../shared/models/campaign.model';

@Injectable({ providedIn: 'root' })
export class CampaignApi {
  private readonly http = inject(HttpClient);

  list(filter: CampaignFilter = {}): Observable<PageResponse<CampaignResponse>> {
    let params = new HttpParams()
      .set('page', String(filter.page ?? 0))
      .set('size', String(filter.size ?? 20))
      .set('sort', filter.sort ?? 'createdAt,desc');

    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.createdById) {
      params = params.set('createdById', filter.createdById);
    }

    return this.http.get<PageResponse<CampaignResponse>>(apiUrl('/campaigns'), { params });
  }

  getById(id: string): Observable<CampaignResponse> {
    return this.http.get<CampaignResponse>(apiUrl(`/campaigns/${id}`));
  }

  create(req: CreateCampaignRequest): Observable<CampaignResponse> {
    return this.http.post<CampaignResponse>(apiUrl('/campaigns'), req);
  }

  update(id: string, req: UpdateCampaignRequest): Observable<CampaignResponse> {
    return this.http.put<CampaignResponse>(apiUrl(`/campaigns/${id}`), req);
  }

  launch(id: string): Observable<CampaignResponse> {
    return this.http.post<CampaignResponse>(apiUrl(`/campaigns/${id}/launch`), {});
  }

  pause(id: string): Observable<CampaignResponse> {
    return this.http.post<CampaignResponse>(apiUrl(`/campaigns/${id}/pause`), {});
  }
}
