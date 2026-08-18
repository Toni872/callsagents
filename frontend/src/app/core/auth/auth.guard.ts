import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Protects a route: if there is no authenticated user, redirect to /login
 * carrying the original URL as ?redirect= so login can return the user there.
 *
 * Demo fast-access: any protected URL with ?demo=1 logs in with the public demo
 * account and lands directly on the dashboard (used by the Script9 landing CTA).
 * TEMPORARY until the real self-service onboarding exists.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  const params = new URLSearchParams(state.url.split('?')[1] ?? '');
  if (params.get('demo') === '1') {
    return new Promise<boolean | ReturnType<typeof router.createUrlTree>>((resolve) => {
      auth
        .login(
          { email: 'demo@callsagents.com', password: 'demo12345' },
          state.url.split('?')[0] ?? '/dashboard'
        )
        .subscribe({
          next: () => resolve(true),
          error: () =>
            resolve(router.createUrlTree(['/login'], { queryParams: { redirect: state.url } }))
        });
    });
  }
  return router.createUrlTree(['/login'], { queryParams: { redirect: state.url } });
};
