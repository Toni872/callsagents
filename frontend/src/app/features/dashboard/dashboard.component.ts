import { ChangeDetectionStrategy, Component } from '@angular/core';

interface KpiCard {
  label: string;
  value: string;
  hint: string;
  icon: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dashboard">
      <h2 class="dashboard__title">Resumen general</h2>
      <p class="dashboard__subtitle muted">Métricas placeholder — Fase 5 no consulta endpoints reales aquí.</p>

      <div class="dashboard__grid">
        @for (card of cards; track card.label) {
          <article class="card kpi-card">
            <header class="kpi-card__header">
              <span class="kpi-card__icon" aria-hidden="true">{{ card.icon }}</span>
              <span class="kpi-card__label">{{ card.label }}</span>
            </header>
            <div class="kpi-card__value">{{ card.value }}</div>
            <div class="kpi-card__hint muted">{{ card.hint }}</div>
          </article>
        }
      </div>

      <div class="card dashboard__panel">
        <h3>Próximos pasos</h3>
        <ul>
          <li>Fase 6: autenticación real con backend.</li>
          <li>Fase 7: gestión completa de leads (filtros, creación, edición).</li>
          <li>Fase 8: campañas con launch/pause.</li>
          <li>Fase 9: registro manual de llamadas y resultados.</li>
        </ul>
      </div>
    </section>
  `,
  styles: [
    `
      .dashboard {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-6);
      }
      .dashboard__title {
        margin: 0;
        font-size: 1.5rem;
      }
      .dashboard__subtitle {
        margin: 0;
      }
      .dashboard__grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: var(--spacing-4);
      }
      .kpi-card {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-2);
      }
      .kpi-card__header {
        display: flex;
        align-items: center;
        gap: var(--spacing-2);
      }
      .kpi-card__icon {
        font-size: 1.25rem;
      }
      .kpi-card__label {
        font-size: 0.875rem;
        color: var(--color-text-muted);
        text-transform: uppercase;
        letter-spacing: 0.04em;
      }
      .kpi-card__value {
        font-size: 2rem;
        font-weight: 700;
      }
      .kpi-card__hint {
        font-size: 0.75rem;
      }
      .dashboard__panel h3 {
        margin-top: 0;
      }
    `
  ]
})
export class DashboardComponent {
  protected readonly cards: KpiCard[] = [
    { label: 'Leads totales', value: '—', hint: 'Conteo global', icon: '◐' },
    { label: 'Campañas activas', value: '—', hint: 'Estados RUNNING/SCHEDULED', icon: '◑' },
    { label: 'Llamadas hoy', value: '—', hint: 'Últimas 24 h', icon: '◓' },
    { label: 'Citas agendadas', value: '—', hint: 'Próximos 7 días', icon: '◒' }
  ];
}
