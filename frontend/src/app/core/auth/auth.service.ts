import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
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
    if (!ends) return null;
    return Math.max(0, Math.ceil((new Date(ends).getTime() - Date.now()) / 86_400_000));
  });

  /** True only when a trial exists AND it has ended. */
  readonly isTrialExpired = computed(() => {
    const ends = this.trialEndsAt();
    return ends !== null && new Date(ends).getTime() < Date.now();
  });

  /**
   * Initialize from localStorage on app boot. Returns a Promise so APP_INITIALIZER awaits
   * the /auth/me call before the router activates.
   */
  initFromStorage(): Promise<void> {
    const access = this.storage.getAccess();
    if (!access) return Promise.resolve();
    return new Promise<void>((resolve) => {
      this.api.me().subscribe({
        next: (user) => { this._currentUser.set(user); resolve(); },
        error: () => { this.logout(false); resolve(); }
      });
    });
  }

  login(req: LoginRequest, redirect?: string): Observable<unknown> {
    return this.api.login(req).pipe(
      tap((res) => {
        this.handleAuthSuccess(res.accessToken, res.refreshToken, res.user);
        this.errorService.success(`Bienvenido, ${res.user.fullName}`);
        this.router.navigateByUrl(redirect && redirect !== '/login' ? redirect : '/dashboard');
      })
    );
  }

  register(req: RegisterRequest): Observable<unknown> {
    return this.api.register(req).pipe(
      tap((res) => {
        this.handleAuthSuccess(res.accessToken, res.refreshToken, res.user);
        this.errorService.success(`Bienvenido, ${res.user.fullName}`);
        this.router.navigateByUrl('/dashboard');
      })
    );
  }

  logout(showToast = true): void {
    if (this.storage.getAccess()) {
      this.api.logout().subscribe({ error: () => undefined });
    }
    this.storage.clear();
    this._currentUser.set(null);
    if (showToast) this.errorService.info('Sesión cerrada');
    this.router.navigateByUrl('/landing');
  }

  googleLogin(credential: string, redirect?: string): Observable<unknown> {
    return this.api.googleLogin(credential).pipe(
      tap((res) => {
        this.handleAuthSuccess(res.accessToken, res.refreshToken, res.user);
        this.errorService.success(`Bienvenido, ${res.user.fullName}`);
        this.router.navigateByUrl(redirect && redirect !== '/login' ? redirect : '/dashboard');
      })
    );
  }

  private handleAuthSuccess(access: string, refresh: string, user: UserDto): void {
    this.storage.setTokens(access, refresh);
    this._currentUser.set(user);
  }
}
