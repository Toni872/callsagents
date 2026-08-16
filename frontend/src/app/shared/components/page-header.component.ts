import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="app-page-header">
      <div class="app-page-header__text">
        <h1 class="app-page-header__title">{{ title() }}</h1>
        @if (subtitle()) {
          <p class="app-page-header__subtitle">{{ subtitle() }}</p>
        }
      </div>
      <div class="app-page-header__actions">
        <ng-content></ng-content>
      </div>
    </header>
  `,
  styles: [
    `
      .app-page-header {
        display: flex;
        align-items: flex-end;
        justify-content: space-between;
        gap: var(--spacing-4);
        flex-wrap: wrap;
        margin-bottom: var(--spacing-6);
      }
      .app-page-header__title {
        margin: 0;
        font-family: var(--font-display);
        font-size: 1.5rem;
      }
      .app-page-header__subtitle {
        margin: var(--spacing-1) 0 0;
        font-size: 0.875rem;
        color: var(--color-text-muted);
      }
      .app-page-header__actions {
        display: flex;
        align-items: center;
        gap: var(--spacing-2);
      }
    `
  ]
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
}
