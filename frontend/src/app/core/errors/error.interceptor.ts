import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ErrorService } from './error.service';

interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
}

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const errorService = inject(ErrorService);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const body = err.error as ApiErrorBody | string | null;

      if (err.status === 0) {
        errorService.error('No se pudo conectar con el servidor');
      } else if (typeof body === 'string') {
        errorService.error(body || `Error ${err.status}`);
      } else if (body && typeof body === 'object') {
        const msg = body.message || body.error || `Error ${err.status}`;
        errorService.error(msg);
      } else {
        errorService.error(`Error ${err.status}: ${err.statusText || 'desconocido'}`);
      }

      return throwError(() => err);
    })
  );
};
