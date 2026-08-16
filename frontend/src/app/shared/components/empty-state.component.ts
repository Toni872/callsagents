import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="app-empty-state">
      <div class="app-empty-state__icon">
        <ng-content select="[slot=icon]"></ng-content>
      </div>
      @if (title()) {
        <h2 class="app-empty-state__title">{{ title() }}</h2>
      }
      @if (message()) {
        <p class="app-empty-state__message">{{ message() }}</p>
      }
      <div class="app-empty-state__actions">
        <ng-content select="[slot=actions]"></ng-content>
      </div>
    </div>
  `,
  styles: [
    `
      .app-empty-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        max-width: 420px;
        margin: 0 auto;
        padding: var(--spacing-8);
      }
      .app-empty-state__icon {
        margin-bottom: var(--spacing-3);
        color: var(--color-text-subtle);
      }
      .app-empty-state__title {
        margin: 0 0 var(--spacing-1);
        font-size: 1.125rem;
        color: var(--color-text-strong);
      }
      .app-empty-state__message {
        margin: 0;
        font-size: 0.875rem;
        color: var(--color-text-muted);
      }
      .app-empty-state__actions {
        margin-top: var(--spacing-4);
        display: flex;
        gap: var(--spacing-2);
      }
    `
  ]
})
export class EmptyStateComponent {
  readonly title = input('');
  readonly message = input('');
}
