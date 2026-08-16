import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type BadgeTone =
  | 'neutral'
  | 'success'
  | 'warning'
  | 'error'
  | 'info'
  | 'accent';

@Component({
  selector: 'app-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="app-badge"
      [class.app-badge--rect]="!pill()"
      [class]="'app-badge--' + tone()"
    >
      <ng-content></ng-content>
    </span>
  `,
  styles: [
    `
      .app-badge {
        display: inline-block;
        padding: 0.125rem 0.5rem;
        border-radius: var(--radius-full);
        font-size: 0.75rem;
        font-weight: 500;
        line-height: 1.4;
      }
      .app-badge--rect {
        border-radius: var(--radius);
      }
      .app-badge--neutral {
        background: var(--color-bg-alt);
        color: var(--color-text-muted);
      }
      .app-badge--success {
        background: var(--color-success-bg);
        color: var(--color-success);
      }
      .app-badge--warning {
        background: var(--color-warning-bg);
        color: var(--color-warning);
      }
      .app-badge--error {
        background: var(--color-error-bg);
        color: var(--color-error);
      }
      .app-badge--info {
        background: var(--color-info-bg);
        color: var(--color-info);
      }
      .app-badge--accent {
        background: var(--color-primary-soft);
        color: var(--color-primary);
      }
    `
  ]
})
export class BadgeComponent {
  readonly tone = input<BadgeTone>('neutral');
  readonly pill = input(true);
}
