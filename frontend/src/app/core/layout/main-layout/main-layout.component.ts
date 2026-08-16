import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LoadingService } from '../../loading/loading.service';
import { ToastHostComponent } from '../../errors/toast-host/toast-host.component';
import { AuthService } from '../../auth/auth.service';
import { ThemeService } from '../../theme/theme.service';

interface NavItem {
  label: string;
  path: string;
  icon: string;
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
          <span class="sidebar__wordmark" aria-label="Callsagents">
            <span class="sidebar__wordmark-calls">CALLS</span><span class="sidebar__wordmark-agents">AGENTS</span>
          </span>
        </div>
        <nav class="sidebar__nav" aria-label="Navegación principal">
          @for (item of navItems; track item.path) {
            <a
              [routerLink]="item.path"
              routerLinkActive="sidebar__link--active"
              [routerLinkActiveOptions]="{ exact: item.path === '/dashboard' }"
              class="sidebar__link"
            >
              <span class="sidebar__icon" aria-hidden="true">{{ item.icon }}</span>
              <span>{{ item.label }}</span>
            </a>
          }
        </nav>
      </aside>

      <div class="main">
        <header class="header">
          <h1 class="header__title">Panel de control</h1>
          <div class="header__actions">
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
        font-size: 1rem;
        width: 20px;
        text-align: center;
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
export class MainLayoutComponent implements OnInit {
  protected readonly loading = inject(LoadingService);
  protected readonly auth = inject(AuthService);
  protected readonly themeService = inject(ThemeService);

  protected readonly theme = this.themeService.theme;

  ngOnInit(): void {
    this.themeService.initialize();
  }

  protected readonly navItems: NavItem[] = [
    { label: 'Dashboard', path: '/dashboard', icon: '◉' },
    { label: 'Leads', path: '/leads', icon: '◐' },
    { label: 'Campañas', path: '/campaigns', icon: '◑' },
    { label: 'Llamadas', path: '/calls', icon: '◓' },
    { label: 'Voz', path: '/voice-calls', icon: '☎' },
    { label: 'Citas', path: '/appointments', icon: '◒' },
    { label: 'Usuarios', path: '/users', icon: '◈' },
    { label: 'Calendario', path: '/settings/calendar', icon: '◔' }
  ];

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
