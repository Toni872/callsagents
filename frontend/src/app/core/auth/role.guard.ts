import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { ErrorService } from '../errors/error.service';

/**
 * Factory for role-based route protection.
 *
 * Usage in a route config:
 *   { path: 'admin', canActivate: [authGuard, roleGuard(['ADMIN'])], ... }
 *
 * If the current user's role is not in `allowedRoles`, redirect to /dashboard
 * and show a toast.
 */
export function roleGuard(allowedRoles: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const errorService = inject(ErrorService);

    const role = auth.currentRole();
    if (role && allowedRoles.includes(role)) {
      return true;
    }
    errorService.error('No tienes permisos para acceder a esta sección');
    return router.createUrlTree(['/dashboard']);
  };
}
