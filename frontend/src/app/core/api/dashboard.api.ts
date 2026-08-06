import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import { DashboardSummary, SeedResult } from '../../shared/models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);

  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(apiUrl('/dashboard/summary'));
  }

  seedDemoData(): Observable<SeedResult> {
    return this.http.post<SeedResult>(apiUrl('/admin/seed-demo-data'), {});
  }
}
