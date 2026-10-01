import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { TokenStorageService } from './token-storage.service';
import { AuthApi } from '../api/auth.api';
import { AuthService } from './auth.service';

/**
 * Module-scoped flag prevents concurrent 401s from triggering parallel refresh calls.
 * If two requests fail with 401 at the same time, only the first triggers refresh;
 * the second sees isRefreshing=true and propagates the original error to errorInterceptor.
 */
let isRefreshing = false;

/**
 * When a protected request gets 401 (token expired), try to refresh exactly once,
 * then retry the original request with the new access token. If refresh fails,
 * clear the session and redirect to /login.
 *
 * The refresh token travels in the HttpOnly cookie `callsagents_refresh`; the
 * SPA never reads it. `authApi.refresh()` therefore sends no body: the cookie
 * (with credentials) is the whole auth mechanism.
 *
 * /auth/* endpoints are excluded: their 401 means "bad credentials", not "expired token".
 */
export const tokenRefreshInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(TokenStorageService);
  const authApi = inject(AuthApi);
  const authService = inject(AuthService);

  const isAuthEndpoint = req.url.includes('/auth/');

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || isAuthEndpoint || isRefreshing) {
        return throwError(() => err);
      }
      isRefreshing = true;
      return authApi.refresh().pipe(
        switchMap((res) => {
          storage.setAccess(res.accessToken);
          isRefreshing = false;
          const retried = req.clone({
            setHeaders: { Authorization: `Bearer ${res.accessToken}` }
          });
          return next(retried);
        }),
        catchError((refreshErr) => {
          isRefreshing = false;
          authService.logout(false);
          return throwError(() => refreshErr);
        })
      );
    })
  );
};