import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TokenStorageService } from './token-storage.service';

/**
 * Adds Authorization: Bearer header to every request EXCEPT the public auth
 * endpoints /auth/login, /auth/register and /auth/refresh (they must NOT carry
 * a token because they issue the tokens).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(TokenStorageService);

  const isPublicAuthEndpoint =
    req.url.includes('/auth/login') ||
    req.url.includes('/auth/register') ||
    req.url.includes('/auth/refresh');

  const access = storage.getAccess();
  if (access && !isPublicAuthEndpoint) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${access}` }
    });
  }
  return next(req);
};
