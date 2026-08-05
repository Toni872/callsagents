import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-lead-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <h2>Formulario de lead</h2>
      <div class="card muted">
        Placeholder. Form reactivo contra <code>POST/PUT /api/leads</code> en Fase 7.
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
export class LeadFormComponent {}
