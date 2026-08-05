import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-lead-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <h2>Detalle de lead</h2>
      <div class="card muted">
        Vista placeholder. Se implementa en Fase 7 contra <code>GET /api/leads/{{ '{' }}id{{ '}' }}</code>.
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
export class LeadDetailComponent {}
