import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LoadingService } from '../../loading/loading.service';
import { ToastHostComponent } from '../../errors/toast-host/toast-host.component';
import { AuthService } from '../../auth/auth.service';

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
          <span class="sidebar__logo">CA</span>
          <span class="sidebar__title">Callsagents</span>
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
        height: 100%;
      }
      .layout {
        display: grid;
        grid-template-columns: 240px 1fr;
        min-height: 100vh;
      }
      .sidebar {
        background: #0f172a;
        color: #e2e8f0;
        display: flex;
        flex-direction: column;
        padding: var(--spacing-6) 0;
      }
      .sidebar__brand {
        display: flex;
        align-items: center;
        gap: var(--spacing-3);
        padding: 0 var(--spacing-6) var(--spacing-6);
        border-bottom: 1px solid #1e293b;
      }
      .sidebar__logo {
        width: 32px;
        height: 32px;
        background: var(--color-primary);
        color: #fff;
        border-radius: var(--radius);
        display: grid;
        place-items: center;
        font-weight: 700;
        font-size: 0.875rem;
      }
      .sidebar__title {
        font-weight: 600;
        font-size: 1rem;
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
        color: #cbd5e1;
        text-decoration: none;
        font-size: 0.875rem;
        transition: background 0.15s ease, color 0.15s ease;
      }
      .sidebar__link:hover {
        background: #1e293b;
        color: #fff;
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
        background: var(--color-bg-alt);
      }
      .header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: var(--spacing-4) var(--spacing-6);
        background: var(--color-surface);
        border-bottom: 1px solid var(--color-border);
      }
      .header__title {
        margin: 0;
        font-size: 1.125rem;
        font-weight: 600;
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
export class MainLayoutComponent {
  protected readonly loading = inject(LoadingService);
  protected readonly auth = inject(AuthService);

  protected readonly navItems: NavItem[] = [
    { label: 'Dashboard', path: '/dashboard', icon: '◉' },
    { label: 'Leads', path: '/leads', icon: '◐' },
    { label: 'Campañas', path: '/campaigns', icon: '◑' },
    { label: 'Llamadas', path: '/calls', icon: '◓' },
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
