import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-campaign-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <h2>Detalle de campaña</h2>
      <div class="card muted">
        Placeholder. Vista completa en Fase 8.
      </div>
    </section>
  `,
  styles: [
    `
      .page {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-4);
      }
    `
  ]
})
export class CampaignDetailComponent {}
