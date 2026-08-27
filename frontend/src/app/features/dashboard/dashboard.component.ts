import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { DashboardApi } from '../../core/api/dashboard.api';
import { BusinessApi } from '../../core/api/business.api';
import { AuthService } from '../../core/auth/auth.service';
import { Router } from '@angular/router';
import { DashboardSummary } from '../../shared/models/dashboard.model';
import { CardComponent } from '../../shared/components/card.component';
import { BadgeComponent } from '../../shared/components/badge.component';
import { SkeletonComponent } from '../../shared/components/skeleton.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CardComponent,
    BadgeComponent,
    SkeletonComponent,
    EmptyStateComponent,
    PageHeaderComponent
  ],
  template: `
    <div class="dashboard-page">
      <app-page-header
        title="Dashboard ejecutivo"
        [subtitle]="lastUpdated() ? 'Actualizado ' + lastUpdated() : undefined"
      >
        <button class="btn btn--secondary" type="button" (click)="refresh()" [disabled]="loading()">
          {{ loading() ? 'Actualizando…' : 'Actualizar' }}
        </button>
      </app-page-header>

      @if (!chatbotConfigured() && !loadingProfile()) {
        <div class="setup-banner">
          <div class="setup-banner__content">
            <div class="setup-banner__icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                <line x1="12" y1="19" x2="12" y2="23"/>
                <line x1="8" y1="23" x2="16" y2="23"/>
              </svg>
            </div>
            <div class="setup-banner__text">
              <h3 class="setup-banner__title">Configura tu chatbot para empezar</h3>
              <p class="setup-banner__desc">Personaliza el nombre, tono y color de tu asistente virtual para que responda a tus leads.</p>
            </div>
          </div>
          <button class="btn btn--primary" type="button" (click)="goToSettings()">
            Configurar ahora
          </button>
        </div>
      }

      @if (loading() && summary() === null) {
        <div class="dashboard-page__grid" role="status" aria-label="Cargando métricas">
          <app-skeleton class="sk-hero" [height]="'220px'" [radius]="'var(--radius-lg)'"></app-skeleton>
          <div class="sk-rail">
            <app-skeleton [height]="'120px'" [radius]="'var(--radius-lg)'"></app-skeleton>
            <app-skeleton [height]="'120px'" [radius]="'var(--radius-lg)'"></app-skeleton>
          </div>
          <app-skeleton class="sk-wide" [height]="'140px'" [radius]="'var(--radius-lg)'"></app-skeleton>
          <app-skeleton class="sk-wide" [height]="'140px'" [radius]="'var(--radius-lg)'"></app-skeleton>
        </div>
      } @else if (summary() === null) {
        <app-card>
          <app-empty-state
            title="Sin datos todavía"
            message="El dashboard mostrará las métricas cuando haya leads, campañas y llamadas en el sistema."
          >
            <span slot="icon" class="empty-icon">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="36"
                height="36"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path
                  d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"
                />
              </svg>
            </span>
          </app-empty-state>
        </app-card>
      } @else {
        <div class="dashboard-page__grid">
          <app-card class="hero-card" padding="lg">
            <div class="hero-card__header">
              <h2 class="kpi-title">Llamadas Hoy</h2>
              <app-badge class="hero-card__live" tone="success">
                <span class="live-dot" aria-hidden="true"></span>
                En vivo
              </app-badge>
            </div>
            <p class="hero-card__value tabular-nums">{{ summary()!.callsToday }}</p>
            <p class="hero-card__sub">hoy</p>
            <p class="hero-card__connected tabular-nums">
              {{ summary()!.callsTodayConnected }} de {{ summary()!.callsToday }} conectadas
            </p>
            <div class="hero-card__rate">
              <div class="hero-card__rate-label">
                <span>Tasa de conexión</span>
                <span class="tabular-nums">{{ connectionRatePercent() }}%</span>
              </div>
              <div
                class="hero-card__track"
                role="progressbar"
                [attr.aria-label]="'Tasa de conexión: ' + connectionRatePercent() + '%'"
                [attr.aria-valuenow]="connectionRatePercent()"
                aria-valuemin="0"
                aria-valuemax="100"
              >
                <div class="hero-card__fill" [style.width.%]="connectionRatePercent()"></div>
              </div>
            </div>
          </app-card>

          <div class="dashboard-page__rail">
            <app-card class="kpi-card">
              <h3 class="kpi-title">Campañas Activas</h3>
              <p class="kpi-value tabular-nums">{{ summary()!.activeCampaigns }}</p>
              <p class="kpi-hint">en curso</p>
            </app-card>

            <app-card class="kpi-card">
              <h3 class="kpi-title">Citas Pendientes</h3>
              <p class="kpi-value tabular-nums">{{ summary()!.upcomingAppointments }}</p>
              <p class="kpi-hint">próximas</p>
            </app-card>
          </div>

          <app-card class="kpi-card kpi-card--wide">
            <h3 class="kpi-title">Total Leads</h3>
            <p class="kpi-value tabular-nums">{{ summary()!.totalLeads }}</p>
            <p class="kpi-hint">en el sistema</p>
          </app-card>

          <app-card class="kpi-card kpi-card--wide">
            <h3 class="kpi-title">Leads Asignados</h3>
            <div class="kpi-card__value-row">
              <p class="kpi-value tabular-nums">{{ summary()!.assignedLeads }}</p>
              @if (percentOfAssigned() !== '0%') {
                <app-badge tone="accent">{{ percentOfAssigned() }}</app-badge>
              }
            </div>
            <p class="kpi-hint">de {{ summary()!.totalLeads }} totales</p>
          </app-card>
        </div>
      }
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .dashboard-page {
        max-width: 1400px;
        margin: 0 auto;
      }
      .dashboard-page__grid {
        display: grid;
        grid-template-columns: repeat(12, 1fr);
        gap: var(--spacing-4);
        align-items: start;
      }

      /* ---------- Hero ---------- */
      .hero-card {
        grid-column: span 8;
      }
      :host ::ng-deep .hero-card .app-card {
        background: color-mix(in srgb, var(--color-primary), var(--color-bg) 94%);
        border-color: color-mix(in srgb, var(--color-primary), var(--color-border) 30%);
        transition: border-color 0.15s ease, box-shadow 0.15s ease;
      }
      :host ::ng-deep .hero-card:hover .app-card {
        border-color: var(--color-primary);
        box-shadow:
          0 0 0 1px color-mix(in srgb, var(--color-primary), transparent 55%),
          var(--shadow-sm);
      }
      .hero-card__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: var(--spacing-3);
        margin-bottom: var(--spacing-3);
      }
      .hero-card__live {
        display: inline-flex;
        align-items: center;
      }
      .live-dot {
        display: inline-block;
        width: 0.5rem;
        height: 0.5rem;
        margin-right: 0.375rem;
        border-radius: var(--radius-full);
        background: var(--color-success);
        vertical-align: middle;
        animation: live-pulse 1.6s ease-in-out infinite;
      }
      @keyframes live-pulse {
        0%,
        100% {
          opacity: 1;
          transform: scale(1);
        }
        50% {
          opacity: 0.35;
          transform: scale(0.7);
        }
      }
      @media (prefers-reduced-motion: reduce) {
        .live-dot {
          animation: none;
        }
      }
      .hero-card__value {
        margin: 0;
        font-size: clamp(2.5rem, 4vw, 3rem);
        font-weight: 700;
        line-height: 1.1;
        color: var(--color-text-strong);
      }
      .hero-card__sub {
        margin: var(--spacing-1) 0 0;
        font-size: 0.875rem;
        color: var(--color-text-muted);
      }
      .hero-card__connected {
        margin: var(--spacing-4) 0 0;
        font-size: 0.875rem;
        color: var(--color-text);
      }
      .hero-card__rate {
        margin-top: var(--spacing-3);
        max-width: 420px;
      }
      .hero-card__rate-label {
        display: flex;
        justify-content: space-between;
        margin-bottom: var(--spacing-1);
        font-size: 0.75rem;
        color: var(--color-text-muted);
      }
      .hero-card__track {
        height: 0.375rem;
        border-radius: var(--radius-full);
        background: var(--color-bg-alt);
        overflow: hidden;
      }
      .hero-card__fill {
        height: 100%;
        border-radius: var(--radius-full);
        background: linear-gradient(
          90deg,
          var(--color-primary),
          var(--color-primary-soft)
        );
        transition: width 0.3s ease;
      }

      /* ---------- Secondary KPI cards ---------- */
      .dashboard-page__rail {
        grid-column: span 4;
        display: grid;
        gap: var(--spacing-4);
        align-content: start;
      }
      .kpi-card {
        transition: box-shadow 0.15s ease, transform 0.15s ease;
      }
      .kpi-card:hover {
        box-shadow: var(--shadow-sm);
        transform: translateY(-1px);
      }
      .kpi-card--wide {
        grid-column: span 6;
      }
      .kpi-title {
        margin: 0 0 var(--spacing-2);
        font-size: 0.75rem;
        font-weight: 600;
        letter-spacing: 0.05em;
        text-transform: uppercase;
        color: var(--color-text-muted);
      }
      .kpi-value {
        margin: 0 0 var(--spacing-1);
        font-size: 2rem;
        font-weight: 700;
        line-height: 1.15;
        color: var(--color-text-strong);
      }
      .kpi-hint {
        margin: 0;
        font-size: 0.75rem;
        color: var(--color-text-muted);
      }
      .kpi-card__value-row {
        display: flex;
        align-items: center;
        gap: var(--spacing-2);
        margin-bottom: var(--spacing-1);
      }
      .kpi-card__value-row .kpi-value {
        margin-bottom: 0;
      }

      /* ---------- Loading skeletons ---------- */
      .sk-hero {
        grid-column: span 8;
      }
      .sk-rail {
        grid-column: span 4;
        display: grid;
        gap: var(--spacing-4);
        align-content: start;
      }
      .sk-wide {
        grid-column: span 6;
      }

      /* ---------- Buttons ---------- */
      .btn {
        padding: var(--spacing-2) var(--spacing-4);
        border-radius: var(--radius);
        border: 1px solid var(--color-border);
        background: var(--color-surface);
        color: var(--color-text);
        cursor: pointer;
        font-size: 0.875rem;
        transition: background-color 0.15s ease, border-color 0.15s ease;
      }
      .btn:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .btn--primary {
        background: var(--color-primary);
        color: var(--color-on-primary);
        border-color: var(--color-primary);
      }
      .btn--secondary {
        background: var(--color-surface);
        border-color: var(--color-border);
      }
      .btn--secondary:hover:not(:disabled) {
        background: var(--color-bg-alt);
        border-color: var(--color-border-strong);
      }

      .empty-icon {
        display: inline-block;
      }

      /* ---------- Setup banner ---------- */
      .setup-banner {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--spacing-4);
        margin-bottom: var(--spacing-4);
        padding: var(--spacing-4) var(--spacing-5);
        background: color-mix(in srgb, var(--color-primary), var(--color-bg) 93%);
        border: 1px solid color-mix(in srgb, var(--color-primary), var(--color-border) 30%);
        border-radius: var(--radius-lg);
      }
      .setup-banner__content {
        display: flex;
        align-items: center;
        gap: var(--spacing-4);
      }
      .setup-banner__icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 48px;
        height: 48px;
        border-radius: var(--radius);
        background: var(--color-primary);
        color: #fff;
        flex-shrink: 0;
      }
      .setup-banner__title {
        margin: 0 0 var(--spacing-1);
        font-size: 1rem;
        font-weight: 600;
        color: var(--color-text-strong);
      }
      .setup-banner__desc {
        margin: 0;
        font-size: 0.85rem;
        color: var(--color-text-muted);
      }
      .setup-banner .btn {
        flex-shrink: 0;
      }

      /* ---------- Responsive ---------- */
      @media (max-width: 1023px) {
        .hero-card,
        .sk-hero {
          grid-column: span 12;
        }
        .dashboard-page__rail,
        .sk-rail {
          grid-column: span 12;
          grid-template-columns: repeat(2, 1fr);
        }
        .kpi-card--wide,
        .sk-wide {
          grid-column: span 6;
        }
      }
      @media (max-width: 639px) {
        .dashboard-page__rail,
        .sk-rail {
          grid-template-columns: 1fr;
        }
        .kpi-card--wide,
        .sk-wide {
          grid-column: span 12;
        }
      }
    `
  ]
})
export class DashboardComponent implements OnInit {
  private readonly dashboardApi = inject(DashboardApi);
  private readonly businessApi = inject(BusinessApi);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly summary = signal<DashboardSummary | null>(null);
  protected readonly loading = signal(false);
  protected readonly chatbotConfigured = signal(false);
  protected readonly loadingProfile = signal(true);

  protected readonly connectionRatePercent = computed(() =>
    Math.round((this.summary()?.connectionRateToday ?? 0) * 100)
  );

  protected readonly percentOfAssigned = computed(() => {
    const s = this.summary();
    if (!s || s.totalLeads === 0) {
      return '0%';
    }
    return `${Math.round((s.assignedLeads / s.totalLeads) * 100)}%`;
  });

  protected readonly lastUpdated = computed(() => {
    const g = this.summary()?.generatedAt;
    if (!g) {
      return null;
    }
    const seconds = Math.round((Date.now() - new Date(g).getTime()) / 1000);
    if (seconds < 0) {
      return null;
    }
    if (seconds < 60) {
      return `hace ${seconds}s`;
    }
    if (seconds < 3600) {
      return `hace ${Math.round(seconds / 60)}min`;
    }
    return `hace ${Math.round(seconds / 3600)}h`;
  });

  ngOnInit(): void {
    this.loadSummary();
    this.checkChatbotConfig();
  }

  protected refresh(): void {
    this.loadSummary();
  }

  protected goToSettings(): void {
    this.router.navigateByUrl('/settings/profile');
  }

  private checkChatbotConfig(): void {
    this.businessApi.getProfile().subscribe({
      next: (res) => {
        // Chatbot is configured if companyName exists
        this.chatbotConfigured.set(!!res.data.companyName);
        this.loadingProfile.set(false);
      },
      error: () => {
        this.chatbotConfigured.set(false);
        this.loadingProfile.set(false);
      }
    });
  }

  private loadSummary(): void {
    this.loading.set(true);
    this.dashboardApi.getSummary().subscribe({
      next: (data) => {
        this.summary.set(data);
        this.loading.set(false);
      },
      error: () => {
        // errorInterceptor already shows a toast. Don't duplicate it.
        this.loading.set(false);
      }
    });
  }
}
