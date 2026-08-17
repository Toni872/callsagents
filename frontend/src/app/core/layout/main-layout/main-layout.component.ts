import { ChangeDetectionStrategy, Component, inject, OnInit, OnDestroy } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet, NavigationEnd } from '@angular/router';
import { LoadingService } from '../../loading/loading.service';
import { ToastHostComponent } from '../../errors/toast-host/toast-host.component';
import { AuthService } from '../../auth/auth.service';
import { ThemeService } from '../../theme/theme.service';
import { TourService } from '../../tour/tour.service';
import { Subscription, filter } from 'rxjs';

interface NavItem {
  label: string;
  path: string;
  id: string;
}

@Component({
  selector: 'app-main-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, ToastHostComponent],
  template: `
    <div class="layout">
      <aside class="sidebar">
        <div class="sidebar__brand">
          <a routerLink="/landing" class="sidebar__wordmark" aria-label="Callsagents — Ver sitio web">
            <span class="sidebar__wordmark-calls">CALLS</span><span class="sidebar__wordmark-agents">AGENTS</span>
          </a>
        </div>
        <nav class="sidebar__nav" aria-label="Navegación principal">
          <a routerLink="/dashboard" routerLinkActive="sidebar__link--active" [routerLinkActiveOptions]="{ exact: true }" class="sidebar__link">
            <span class="sidebar__icon" aria-hidden="true"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg></span>
            <span>Dashboard</span>
          </a>
          <a routerLink="/leads" routerLinkActive="sidebar__link--active" class="sidebar__link">
            <span class="sidebar__icon" aria-hidden="true"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/></svg></span>
            <span>Leads</span>
          </a>
          <a routerLink="/campaigns" routerLinkActive="sidebar__link--active" class="sidebar__link">
            <span class="sidebar__icon" aria-hidden="true"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 2L11 13"/><path d="M22 2L15 22L11 13L2 9L22 2Z"/></svg></span>
            <span>Campañas</span>
          </a>
          <a routerLink="/calls" routerLinkActive="sidebar__link--active" class="sidebar__link">
            <span class="sidebar__icon" aria-hidden="true"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/></svg></span>
            <span>Llamadas</span>
          </a>
          <a routerLink="/voice-calls" routerLinkActive="sidebar__link--active" class="sidebar__link">
            <span class="sidebar__icon" aria-hidden="true"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/></svg></span>
            <span>Voz</span>
          </a>
          <a routerLink="/appointments" routerLinkActive="sidebar__link--active" class="sidebar__link">
            <span class="sidebar__icon" aria-hidden="true"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/><path d="M9 16l2 2l4-4"/></svg></span>
            <span>Citas</span>
          </a>
          <a routerLink="/users" routerLinkActive="sidebar__link--active" class="sidebar__link">
            <span class="sidebar__icon" aria-hidden="true"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg></span>
            <span>Usuarios</span>
          </a>
          <a routerLink="/settings/calendar" routerLinkActive="sidebar__link--active" class="sidebar__link">
            <span class="sidebar__icon" aria-hidden="true"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg></span>
            <span>Calendario</span>
          </a>
        </nav>
      </aside>

      <div class="main">
        <header class="header">
          <h1 class="header__title">Panel de control</h1>
          <div class="header__actions">
            <button
              type="button"
              class="header__tour-btn"
              (click)="startTour()"
              aria-label="Iniciar tour guiado"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
              Tour
            </button>
            <button
              type="button"
              class="header__theme-toggle"
              [attr.aria-label]="theme() === 'dark' ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'"
              (click)="themeService.toggle()"
            >
              @if (theme() === 'dark') {
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="20"
                  height="20"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  <circle cx="12" cy="12" r="4"></circle>
                  <path d="M12 2v2"></path>
                  <path d="M12 20v2"></path>
                  <path d="m4.93 4.93 1.41 1.41"></path>
                  <path d="m17.66 17.66 1.41 1.41"></path>
                  <path d="M2 12h2"></path>
                  <path d="M20 12h2"></path>
                  <path d="m6.34 17.66-1.41 1.41"></path>
                  <path d="m19.07 4.93-1.41 1.41"></path>
                </svg>
              } @else {
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="20"
                  height="20"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"></path>
                </svg>
              }
            </button>
            <div class="header__user">
              <div class="header__user-info">
                <span class="header__user-name">{{ userName() }}</span>
                @if (userRole()) {
                  <span class="header__user-role">{{ userRole() }}</span>
                }
              </div>
              <span class="header__user-avatar" aria-hidden="true">{{ userInitial() }}</span>
              <button
                type="button"
                class="header__logout"
                (click)="auth.logout()"
                aria-label="Cerrar sesión"
              >
                Salir
              </button>
            </div>
          </div>
        </header>

        <main class="content">
          @if (loading.isLoading()) {
            <div class="loading-bar" aria-hidden="true"></div>
          }
          <router-outlet></router-outlet>
        </main>
      </div>

      <app-toast-host></app-toast-host>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        min-height: 100%;
      }
      .layout {
        display: grid;
        grid-template-columns: 240px 1fr;
        min-height: 100vh;
      }
      .sidebar {
        background: var(--color-bg);
        color: var(--color-text);
        display: flex;
        flex-direction: column;
        padding: var(--spacing-6) 0;
        border-right: 1px solid var(--color-border);
      }
      .sidebar__brand {
        display: flex;
        align-items: center;
        padding: 0 var(--spacing-6) var(--spacing-6);
        border-bottom: 1px solid var(--color-border);
      }
      .sidebar__wordmark {
        font-family: var(--font-display), 'Inter', system-ui, sans-serif;
        font-size: 1.15rem;
        font-weight: 800;
        letter-spacing: -0.02em;
        line-height: 1;
        display: inline-flex;
        text-decoration: none;
        cursor: pointer;
        transition: opacity 0.15s ease;
      }
      .sidebar__wordmark:hover {
        opacity: 0.8;
      }
      .sidebar__wordmark-calls {
        color: var(--color-text-strong);
      }
      .sidebar__wordmark-agents {
        color: var(--color-primary);
      }
      .sidebar__nav {
        display: flex;
        flex-direction: column;
        padding: var(--spacing-4) var(--spacing-3);
        gap: var(--spacing-1);
      }
      .sidebar__link {
        display: flex;
        align-items: center;
        gap: var(--spacing-3);
        padding: var(--spacing-2) var(--spacing-3);
        border-radius: var(--radius);
        color: var(--color-text-muted);
        text-decoration: none;
        font-size: 0.875rem;
        transition: background 0.15s ease, color 0.15s ease;
      }
      .sidebar__link:hover {
        background: var(--color-bg-alt);
        color: var(--color-text);
        text-decoration: none;
      }
      .sidebar__link--active {
        background: var(--color-primary);
        color: #fff;
      }
      .sidebar__icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 20px;
        height: 20px;
        flex-shrink: 0;
      }
      .sidebar__icon svg {
        width: 18px;
        height: 18px;
      }
      .main {
        display: flex;
        flex-direction: column;
        background: var(--color-bg);
      }
      .header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: var(--spacing-4) var(--spacing-6);
        background: var(--color-bg);
        border-bottom: none;
      }
      .header__title {
        margin: 0;
        font-size: 1.125rem;
        font-weight: 600;
      }
      .header__actions {
        display: flex;
        align-items: center;
        gap: var(--spacing-3);
      }
      .header__theme-toggle {
        display: grid;
        place-items: center;
        width: 36px;
        height: 36px;
        padding: 0;
        background: transparent;
        border: 1px solid var(--color-border);
        border-radius: var(--radius);
        color: var(--color-text-muted);
        transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
      }
      .header__theme-toggle:hover {
        background: var(--color-bg-alt);
        border-color: var(--color-border-strong);
        color: var(--color-text);
      }
      .header__tour-btn {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 6px 12px;
        background: var(--color-bg-alt);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        color: var(--color-text);
        font-size: 0.8rem;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s ease;
      }
      .header__tour-btn:hover {
        background: var(--color-primary);
        color: white;
        border-color: var(--color-primary);
      }
      @media (prefers-reduced-motion: reduce) {
        .header__theme-toggle {
          transition: none;
        }
      }
      .header__user {
        display: flex;
        align-items: center;
        gap: var(--spacing-3);
      }
      .header__user-info {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        line-height: 1.2;
      }
      .header__user-name {
        font-size: 0.875rem;
        color: var(--color-text);
        font-weight: 500;
      }
      .header__user-role {
        font-size: 0.7rem;
        color: var(--color-text-muted);
        text-transform: uppercase;
        letter-spacing: 0.04em;
      }
      .header__user-avatar {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        background: var(--color-primary);
        color: #fff;
        display: grid;
        place-items: center;
        font-weight: 600;
        font-size: 0.875rem;
      }
      .header__logout {
        padding: var(--spacing-1) var(--spacing-3);
        background: transparent;
        border: 1px solid var(--color-border);
        border-radius: var(--radius);
        color: var(--color-text);
        font-size: 0.8125rem;
        cursor: pointer;
        transition: background 0.15s ease, border-color 0.15s ease;
      }
      .header__logout:hover {
        background: var(--color-bg-alt);
        border-color: var(--color-text-muted);
      }
      .content {
        flex: 1;
        padding: var(--spacing-6);
        padding-bottom: var(--spacing-8);
        position: relative;
      }
      .loading-bar {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        height: 3px;
        background: linear-gradient(90deg, transparent, var(--color-primary), transparent);
        background-size: 200% 100%;
        animation: loading-slide 1.2s linear infinite;
      }
      @keyframes loading-slide {
        from {
          background-position: 200% 0;
        }
        to {
          background-position: -200% 0;
        }
      }
    `
  ]
})
export class MainLayoutComponent implements OnInit, OnDestroy {
  protected readonly loading = inject(LoadingService);
  protected readonly auth = inject(AuthService);
  protected readonly themeService = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly tourService = inject(TourService);

  protected readonly theme = this.themeService.theme;
  private routerSub?: Subscription;

  ngOnInit(): void {
    this.routerSub = this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event) => {
      const navEnd = event as NavigationEnd;
      if (this.tourService.shouldShowTour(navEnd.urlAfterRedirects || navEnd.url)) {
        setTimeout(() => this.tourService.startTour(navEnd.urlAfterRedirects || navEnd.url), 500);
      }
    });
  }

  ngOnDestroy(): void {
    this.routerSub?.unsubscribe();
  }

  protected startTour(): void {
    this.tourService.startTour(this.router.url);
  }

  protected userName(): string {
    return this.auth.currentUser()?.fullName ?? 'Sin sesión';
  }

  protected userRole(): string | null {
    return this.auth.currentRole();
  }

  protected userInitial(): string {
    const name = this.auth.currentUser()?.fullName ?? '';
    const first = name.trim().charAt(0).toUpperCase();
    return first || '?';
  }
}
