import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import {
  LoginRequest,
  LoginResponse,
  RefreshResponse,
  RegisterRequest,
  UserDto
} from '../../shared/models/auth.model';

/**
 * Auth API. Las llamadas de sesión (login/register/google/refresh/logout) usan
 * `withCredentials: true`: el navegador acepta y envía la cookie httpOnly
 * `callsagents_refresh` (Path=/api/auth). En producción es same-origin vía nginx
 * y no hace falta; en dev (localhost:4200 → localhost:8080) es imprescindible.
 */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  login(req: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(apiUrl('/auth/login'), req, { withCredentials: true });
  }

  register(req: RegisterRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(apiUrl('/auth/register'), req, { withCredentials: true });
  }

  /** Rota el refresh: sin body — la cookie httpOnly viaja sola. */
  refresh(): Observable<RefreshResponse> {
    return this.http.post<RefreshResponse>(apiUrl('/auth/refresh'), null, { withCredentials: true });
  }

  logout(): Observable<void> {
    return this.http.post<void>(apiUrl('/auth/logout'), null, { withCredentials: true });
  }

  googleLogin(credential: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(apiUrl('/auth/google'), { credential }, { withCredentials: true });
  }

  me(): Observable<UserDto> {
    return this.http.get<UserDto>(apiUrl('/auth/me'));
  }
}