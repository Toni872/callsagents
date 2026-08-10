import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import { PageResponse } from './http.types';

export interface CampaignLeadResponse {
  campaignId: string;
  leadId: string;
  leadFirstName: string | null;
  leadLastName: string | null;
  leadEmail: string | null;
  leadPhone: string | null;
  leadCompany: string | null;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';
  attempts: number;
  lastAttemptAt: string | null;
  nextAttemptAt: string | null;
  assignedTo: { id: string; email: string; fullName: string; role: string } | null;
  createdAt: string;
  updatedAt: string;
}

export interface AddLeadRequest {
  leadId: string;
  status?: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';
  assignedToId?: string | null;
}

@Injectable({ providedIn: 'root' })
export class CampaignLeadApi {
  private readonly http = inject(HttpClient);

  list(
    campaignId: string,
    page = 0,
    size = 50
  ): Observable<PageResponse<CampaignLeadResponse>> {
    const params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));
    return this.http.get<PageResponse<CampaignLeadResponse>>(
      apiUrl(`/campaigns/${campaignId}/leads`),
      { params }
    );
  }

  add(campaignId: string, req: AddLeadRequest): Observable<CampaignLeadResponse> {
    return this.http.post<CampaignLeadResponse>(
      apiUrl(`/campaigns/${campaignId}/leads`),
      req
    );
  }

  remove(campaignId: string, leadId: string): Observable<void> {
    return this.http.delete<void>(
      apiUrl(`/campaigns/${campaignId}/leads/${leadId}`)
    );
  }
}