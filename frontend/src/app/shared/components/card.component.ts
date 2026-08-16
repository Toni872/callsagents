import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type CardPadding = 'md' | 'lg' | 'none';

@Component({
  selector: 'app-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section
      class="app-card"
      [class.app-card--lg]="padding() === 'lg'"
      [class.app-card--none]="padding() === 'none'"
    >
      <ng-content></ng-content>
    </section>
  `,
  styles: [
    `
      .app-card {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: var(--spacing-4);
      }
      .app-card--lg {
        padding: var(--spacing-6);
      }
      .app-card--none {
        padding: 0;
      }
    `
  ]
})
export class CardComponent {
  readonly padding = input<CardPadding>('md');
}
