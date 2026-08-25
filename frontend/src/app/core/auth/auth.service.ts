import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthApi } from '../api/auth.api';
import { TokenStorageService } from './token-storage.service';
import { ErrorService } from '../errors/error.service';
import { LoginRequest, RegisterRequest, UserDto } from '../../shared/models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApi);
  private readonly storage = inject(TokenStorageService);
  private readonly router = inject(Router);
  private readonly errorService = inject(ErrorService);

  private readonly _currentUser = signal<UserDto | null>(null);
  readonly currentUser = this._currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this._currentUser() !== null);
  readonly currentRole = computed(() => this._currentUser()?.role ?? null);

  /** ISO date when the public 7-day trial ends, or null when there is no trial. */
  readonly trialEndsAt = computed(() => this._currentUser()?.trialEndsAt ?? null);

  /** Whole days left in the trial (Math.ceil), or null when there is no trial. */
  readonly trialDaysLeft = computed(() => {
    const ends = this.trialEndsAt();
    if (!ends) {
      return null;
    }
    return Math.max(0, Math.ceil((new Date(ends).getTime() - Date.now()) / 86_400_000));
  });

  /** True only when a trial exists AND it has ended. */
  readonly isTrialExpired = computed(() => {
    const ends = this.trialEndsAt();
    return ends !== null && new Date(ends).getTime() < Date.now();
  });

  readonly isDemoUser = computed(
    () => this._currentUser()?.email === 'demo@callsagents.com'
  );

  /**
   * Initialize from localStorage on app boot. Returns a Promise so APP_INITIALIZER awaits
   * the /auth/me call before the router activates. Without this, hard-refreshing on a
   * protected route would briefly redirect to /login before /auth/me resolves.
   */
  initFromStorage(): Promise<void> {
    const access = this.storage.getAccess();
    if (!access) {
      return Promise.resolve();
    }
    return new Promise<void>((resolve) => {
      this.api.me().subscribe({
        next: (user) => {
          this._currentUser.set(user);
          resolve();
        },
        error: () => {
          this.logout(false);
          resolve();
        }
      });
    });
  }

  login(req: LoginRequest, redirect?: string): Observable<unknown> {
    return new Observable((subscriber) => {
      this.api.login(req).subscribe({
        next: (res) => {
          this.handleAuthSuccess(res.accessToken, res.refreshToken, res.user);
          this.errorService.success(`Bienvenido, ${res.user.fullName}`);
          const target = redirect && redirect !== '/login' ? redirect : '/dashboard';
          this.router.navigateByUrl(target);
          subscriber.next(res);
          subscriber.complete();
        },
        error: (err) => {
          // errorInterceptor ya muestra el toast. No duplicar.
          subscriber.error(err);
        }
      });
    });
  }

  register(req: RegisterRequest, redirect?: string): Observable<unknown> {
    return new Observable((subscriber) => {
      this.api.register(req).subscribe({
        next: (res) => {
          this.handleAuthSuccess(res.accessToken, res.refreshToken, res.user);
          this.errorService.success(`Bienvenido, ${res.user.fullName}`);
          const target = redirect && redirect !== '/register' ? redirect : '/dashboard';
          this.router.navigateByUrl(target);
          subscriber.next(res);
          subscriber.complete();
        },
        error: (err) => {
          // errorInterceptor ya muestra el toast. No duplicar.
          subscriber.error(err);
        }
      });
    });
  }

  logout(showToast = true): void {
    const access = this.storage.getAccess();
    if (access) {
      this.api.logout().subscribe({ error: () => undefined });
    }
    this.storage.clear();
    this._currentUser.set(null);
    if (showToast) {
      this.errorService.info('Sesión cerrada');
    }
    this.router.navigateByUrl('/landing');
  }

  googleLogin(credential: string, redirect?: string): Observable<unknown> {
    return new Observable((subscriber) => {
      this.api.googleLogin(credential).subscribe({
        next: (res) => {
          this.handleAuthSuccess(res.accessToken, res.refreshToken, res.user);
          this.errorService.success(`Bienvenido, ${res.user.fullName}`);
          const target = redirect && redirect !== '/login' ? redirect : '/dashboard';
          this.router.navigateByUrl(target);
          subscriber.next(res);
          subscriber.complete();
        },
        error: (err) => {
          subscriber.error(err);
        }
      });
    });
  }

  private handleAuthSuccess(access: string, refresh: string, user: UserDto): void {
    this.storage.setTokens(access, refresh);
    this._currentUser.set(user);
  }
}
