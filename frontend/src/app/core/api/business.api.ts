import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import { BusinessProfile, BusinessProfileRequest } from '../../shared/models/business-profile.model';

interface ApiResponse<T> {
  success: boolean;
  data: T;
}

@Injectable({ providedIn: 'root' })
export class BusinessApi {
  private readonly http = inject(HttpClient);

  createProfile(): Observable<ApiResponse<BusinessProfile>> {
    return this.http.post<ApiResponse<BusinessProfile>>(apiUrl('/business/profile'), {});
  }

  getProfile(): Observable<ApiResponse<BusinessProfile>> {
    return this.http.get<ApiResponse<BusinessProfile>>(apiUrl('/business/profile'));
  }

  updateProfile(req: BusinessProfileRequest): Observable<ApiResponse<BusinessProfile>> {
    return this.http.put<ApiResponse<BusinessProfile>>(apiUrl('/business/profile'), req);
  }

}
