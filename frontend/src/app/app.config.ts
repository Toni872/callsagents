import { ApplicationConfig, APP_INITIALIZER, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { errorInterceptor } from './core/errors/error.interceptor';
import { loadingInterceptor } from './core/loading/loading.interceptor';
import { authInterceptor } from './core/auth/auth.interceptor';
import { tokenRefreshInterceptor } from './core/auth/token-refresh.interceptor';
import { AuthService } from './core/auth/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    /**
     * Order matters:
     * 1. loadingInterceptor — counts requests for global loading indicator
     * 2. errorInterceptor — shows toast on errors
     * 3. authInterceptor — adds Bearer token
     * 4. tokenRefreshInterceptor — handles 401 by refreshing + retrying
     *
     * The last registered is the closest to the network call.
     */
    provideHttpClient(
      withInterceptors([
        loadingInterceptor,
        errorInterceptor,
        authInterceptor,
        tokenRefreshInterceptor
      ])
    ),
    {
      provide: APP_INITIALIZER,
      useFactory: (auth: AuthService) => () => auth.initFromStorage(),
      deps: [AuthService],
      multi: true
    }
  ]
};
