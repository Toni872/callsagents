import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-call-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <h2>Registrar llamada</h2>
      <div class="card muted">Placeholder. Form reactivo en Fase 9.</div>
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
export class CallFormComponent {}
