import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import {
  LoginRequest,
  LoginResponse,
  RefreshRequest,
  RefreshResponse,
  UserDto
} from '../../shared/models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  login(req: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(apiUrl('/auth/login'), req);
  }

  refresh(req: RefreshRequest): Observable<RefreshResponse> {
    return this.http.post<RefreshResponse>(apiUrl('/auth/refresh'), req);
  }

  logout(): Observable<void> {
    return this.http.post<void>(apiUrl('/auth/logout'), {});
  }

  me(): Observable<UserDto> {
    return this.http.get<UserDto>(apiUrl('/auth/me'));
  }
}
