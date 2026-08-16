import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="app-skeleton"
      aria-hidden="true"
      [style.width]="width()"
      [style.height]="height()"
      [style.border-radius]="radius()"
    ></span>
  `,
  styles: [
    `
      .app-skeleton {
        display: block;
        background: var(--color-bg-alt);
        background-image: linear-gradient(
          90deg,
          var(--color-bg-alt) 0%,
          var(--color-surface-hover) 50%,
          var(--color-bg-alt) 100%
        );
        background-size: 200% 100%;
        animation: app-skeleton-shimmer 1.4s ease-in-out infinite;
      }
      @keyframes app-skeleton-shimmer {
        from {
          background-position: 200% 0;
        }
        to {
          background-position: -200% 0;
        }
      }
      @media (prefers-reduced-motion: reduce) {
        .app-skeleton {
          animation: none;
        }
      }
    `
  ]
})
export class SkeletonComponent {
  readonly width = input<string>('100%');
  readonly height = input<string>('1rem');
  readonly radius = input<string | null>(null);
}
