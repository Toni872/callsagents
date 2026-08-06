import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { DashboardApi } from '../../core/api/dashboard.api';
import { AuthService } from '../../core/auth/auth.service';
import { ErrorService } from '../../core/errors/error.service';
import { DashboardSummary } from '../../shared/models/dashboard.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dashboard-page">
      <header class="dashboard-page__header">
        <div>
          <h1>Dashboard ejecutivo</h1>
          @if (lastUpdated()) {
            <p class="muted">Actualizado {{ lastUpdated() }}</p>
          }
        </div>
        <div class="dashboard-page__actions">
          <button class="btn btn--secondary" type="button" (click)="refresh()" [disabled]="loading()">
            {{ loading() ? 'Actualizando…' : 'Actualizar' }}
          </button>
          @if (isAdmin() && !hasData()) {
            <button class="btn btn--primary" type="button" (click)="loadDemo()" [disabled]="seeding()">
              {{ seeding() ? 'Cargando…' : 'Cargar datos de demo' }}
            </button>
          }
        </div>
      </header>

      @if (loading() && summary() === null) {
        <div class="dashboard-page__loading">
          <p>Cargando métricas…</p>
        </div>
      } @else if (summary() === null) {
        <div class="dashboard-page__empty card">
          <h2>Sin datos todavía</h2>
          <p>El dashboard mostrará las métricas cuando haya leads, campañas y llamadas en el sistema.</p>
          @if (isAdmin()) {
            <button class="btn btn--primary" type="button" (click)="loadDemo()" [disabled]="seeding()">
              {{ seeding() ? 'Cargando…' : 'Cargar datos de demo' }}
            </button>
          }
        </div>
      } @else {
        <div class="dashboard-page__grid">
          <article class="kpi-card">
            <h3 class="kpi-card__title">Total Leads</h3>
            <p class="kpi-card__value">{{ summary()!.totalLeads }}</p>
            <p class="kpi-card__hint">en el sistema</p>
          </article>

          <article class="kpi-card">
            <h3 class="kpi-card__title">Leads Asignados</h3>
            <p class="kpi-card__value">{{ summary()!.assignedLeads }}</p>
            <p class="kpi-card__hint">
              de {{ summary()!.totalLeads }} totales ({{ percentOfAssigned() }})
            </p>
          </article>

          <article class="kpi-card">
            <h3 class="kpi-card__title">Campañas Activas</h3>
            <p class="kpi-card__value">{{ summary()!.activeCampaigns }}</p>
            <p class="kpi-card__hint">en curso</p>
          </article>

          <article class="kpi-card">
            <h3 class="kpi-card__title">Llamadas Hoy</h3>
            <p class="kpi-card__value">{{ summary()!.callsToday }}</p>
            <p class="kpi-card__hint">{{ summary()!.callsTodayConnected }} conectadas</p>
          </article>

          <article class="kpi-card kpi-card--highlight">
            <h3 class="kpi-card__title">Tasa de Conexión Hoy</h3>
            <p class="kpi-card__value">{{ connectionRatePercent() }}%</p>
            <p class="kpi-card__hint">de las llamadas de hoy</p>
          </article>

          <article class="kpi-card">
            <h3 class="kpi-card__title">Citas Pendientes</h3>
            <p class="kpi-card__value">{{ summary()!.upcomingAppointments }}</p>
            <p class="kpi-card__hint">próximas</p>
          </article>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .dashboard-page {
        padding: var(--spacing-6);
        max-width: 1400px;
        margin: 0 auto;
      }
      .dashboard-page__header {
        display: flex;
        justify-content: space-between;
        align-items: flex-end;
        margin-bottom: var(--spacing-6);
        gap: var(--spacing-4);
        flex-wrap: wrap;
      }
      .dashboard-page__header h1 {
        margin: 0;
        font-size: 1.5rem;
      }
      .dashboard-page__actions {
        display: flex;
        gap: var(--spacing-2);
      }
      .dashboard-page__grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: var(--spacing-4);
      }
      .kpi-card {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: var(--spacing-4);
        box-shadow: var(--shadow-sm);
      }
      .kpi-card--highlight {
        border-color: var(--color-primary);
        background: linear-gradient(
          135deg,
          var(--color-surface),
          color-mix(in srgb, var(--color-primary), white 90%)
        );
      }
      .kpi-card__title {
        margin: 0 0 var(--spacing-2);
        font-size: 0.875rem;
        color: var(--color-text-muted);
        text-transform: uppercase;
        letter-spacing: 0.04em;
      }
      .kpi-card__value {
        margin: 0 0 var(--spacing-1);
        font-size: 2rem;
        font-weight: 700;
        color: var(--color-text);
      }
      .kpi-card__hint {
        margin: 0;
        font-size: 0.75rem;
        color: var(--color-text-muted);
      }
      .dashboard-page__loading,
      .dashboard-page__empty {
        padding: var(--spacing-6);
        text-align: center;
        color: var(--color-text-muted);
      }
      .dashboard-page__empty h2 {
        margin: 0 0 var(--spacing-2);
      }
      .dashboard-page__empty .btn {
        margin-top: var(--spacing-3);
      }
      .btn {
        padding: var(--spacing-2) var(--spacing-4);
        border-radius: var(--radius);
        border: 1px solid var(--color-border);
        background: var(--color-surface);
        color: var(--color-text);
        cursor: pointer;
        font-size: 0.875rem;
      }
      .btn:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .btn--primary {
        background: var(--color-primary);
        color: white;
        border-color: var(--color-primary);
      }
      .btn--secondary {
        background: var(--color-surface);
        border-color: var(--color-border);
      }
      .muted {
        color: var(--color-text-muted);
      }
    `
  ]
})
export class DashboardComponent implements OnInit {
  private readonly dashboardApi = inject(DashboardApi);
  private readonly auth = inject(AuthService);
  private readonly errorService = inject(ErrorService);

  protected readonly summary = signal<DashboardSummary | null>(null);
  protected readonly loading = signal(false);
  protected readonly seeding = signal(false);

  protected readonly isAdmin = computed(() => this.auth.currentRole() === 'ADMIN');

  protected readonly hasData = computed(() => {
    const s = this.summary();
    return s !== null && s.totalLeads > 0;
  });

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
  }

  protected refresh(): void {
    this.loadSummary();
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

  protected loadDemo(): void {
    if (!this.isAdmin() || this.seeding()) {
      return;
    }
    this.seeding.set(true);
    this.dashboardApi.seedDemoData().subscribe({
      next: (result) => {
        this.seeding.set(false);
        if (result.seeded) {
          this.errorService.success(
            `Datos de demo cargados: ${result.leads} leads, ${result.campaigns} campañas, ${result.calls} llamadas, ${result.appointments} citas`
          );
        }
        this.loadSummary();
      },
      error: () => {
        // errorInterceptor already shows a toast. Don't duplicate it.
        this.seeding.set(false);
      }
    });
  }
}
