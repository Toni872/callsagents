import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ErrorService } from '../error.service';

@Component({
  selector: 'app-toast-host',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toast-host" aria-live="polite" aria-atomic="true">
      @for (toast of errorService.toasts(); track toast.id) {
        <div class="toast" [class]="'toast--' + toast.type" role="alert">
          <span class="toast__text">{{ toast.text }}</span>
          <button
            type="button"
            class="toast__close"
            aria-label="Cerrar notificación"
            (click)="errorService.dismiss(toast.id)"
          >
            &times;
          </button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .toast-host {
        position: fixed;
        top: var(--spacing-4);
        right: var(--spacing-4);
        z-index: 1000;
        display: flex;
        flex-direction: column;
        gap: var(--spacing-2);
        pointer-events: none;
      }
      .toast {
        pointer-events: auto;
        min-width: 280px;
        max-width: 420px;
        padding: var(--spacing-3) var(--spacing-4);
        border-radius: var(--radius);
        box-shadow: var(--shadow-md);
        background: var(--color-surface);
        border-left: 4px solid var(--color-primary);
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: var(--spacing-3);
        animation: slide-in 0.2s ease-out;
      }
      .toast--error {
        border-left-color: var(--color-error);
        background: var(--color-error-bg);
        color: var(--color-error);
      }
      .toast--success {
        border-left-color: var(--color-success);
        background: var(--color-success-bg);
        color: var(--color-success);
      }
      .toast--info {
        border-left-color: var(--color-info);
        background: var(--color-info-bg);
        color: var(--color-info);
      }
      .toast--warning {
        border-left-color: var(--color-warning);
        background: #fffbeb;
        color: var(--color-warning);
      }
      .toast__text {
        flex: 1;
        font-size: 0.875rem;
      }
      .toast__close {
        background: transparent;
        color: inherit;
        border: none;
        padding: 0;
        font-size: 1.25rem;
        line-height: 1;
        cursor: pointer;
      }
      @keyframes slide-in {
        from {
          transform: translateX(20px);
          opacity: 0;
        }
        to {
          transform: translateX(0);
          opacity: 1;
        }
      }
    `
  ]
})
export class ToastHostComponent {
  protected readonly errorService = inject(ErrorService);
}
