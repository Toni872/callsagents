import { Component, EventEmitter, Input, Output, signal } from '@angular/core';

@Component({
  selector: 'app-widget-preview-step',
  standalone: true,
  template: `
    <div class="step">
      <h2 class="step__title">Vista previa del widget</h2>
      <p class="step__subtitle">Asi es como tus visitantes veran el chatbot.</p>

      <div class="preview">
        <div class="preview__widget">
          <header class="preview__header" [style.background]="chatColor">
            <div class="preview__brand">
              <span class="preview__brand-name">{{ companyName || 'CALLSAGENTS' }}</span>
              <span class="preview__dot"></span>
              <span class="preview__online">En linea</span>
            </div>
          </header>

          <div class="preview__messages">
            <div class="preview__bubble preview__bubble--bot">
              <span class="preview__text">{{ greeting || 'Hola! Soy ' + botName + '. En que puedo ayudarte hoy?' }}</span>
              <span class="preview__time">{{ now() }}</span>
            </div>
          </div>

          <div class="preview__input-row">
            <input class="preview__input" type="text" placeholder="Escribe un mensaje..." disabled />
            <button class="preview__send" [style.background]="chatColor" disabled>
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="22" y1="2" x2="11" y2="13"></line>
                <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
              </svg>
            </button>
          </div>
        </div>
      </div>

      <div class="step__info">
        <p class="step__info-text">
          <strong>Nombre:</strong> {{ botName }} &middot;
          <strong>Tono:</strong> {{ tone }} &middot;
          <strong>Color:</strong> <span class="color-dot" [style.background]="chatColor"></span> {{ chatColor }}
        </p>
      </div>

      <div class="step__actions">
        <button class="btn btn--secondary" type="button" (click)="prev.emit()">Atras</button>
        <button class="btn btn--ghost" type="button" (click)="skip.emit()">Omitir por ahora</button>
        <button class="btn btn--primary" type="button" (click)="complete.emit()" [disabled]="saving">
          {{ saving ? 'Guardando...' : 'Completar configuracion' }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    .step { max-width: 480px; margin: 0 auto; }
    .step__title { margin: 0 0 var(--spacing-2); font-size: 1.5rem; font-weight: 700; color: var(--color-text-strong); }
    .step__subtitle { margin: 0 0 var(--spacing-6); font-size: 0.9rem; color: var(--color-text-muted); }
    .preview { display: flex; justify-content: center; margin-bottom: var(--spacing-4); }
    .preview__widget {
      width: 320px; height: 380px; display: flex; flex-direction: column;
      background: #0f172a; border-radius: 12px; overflow: hidden; border: 1px solid #334155;
      box-shadow: 0 4px 24px rgba(0,0,0,0.3);
    }
    .preview__header { padding: 12px 16px; }
    .preview__brand { display: flex; align-items: center; gap: 8px; }
    .preview__brand-name { font-size: 0.8rem; font-weight: 700; letter-spacing: 0.06em; color: #fff; }
    .preview__dot { width: 7px; height: 7px; border-radius: 50%; background: #fff; opacity: 0.8; }
    .preview__online { font-size: 0.65rem; color: rgba(255,255,255,0.7); }
    .preview__messages { flex: 1; padding: 16px; display: flex; flex-direction: column; }
    .preview__bubble { max-width: 80%; padding: 10px 14px; border-radius: 12px; background: #1e293b; color: #e2e8f0; font-size: 0.8rem; line-height: 1.4; }
    .preview__text { display: block; white-space: pre-wrap; }
    .preview__time { display: block; margin-top: 4px; font-size: 0.6rem; color: #64748b; text-align: right; }
    .preview__input-row { display: flex; gap: 8px; padding: 10px 12px; background: #1e293b; border-top: 1px solid #334155; }
    .preview__input { flex: 1; padding: 8px 12px; background: #0f172a; border: 1px solid #334155; border-radius: 8px; color: #64748b; font-size: 0.8rem; }
    .preview__send { display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; border: none; border-radius: 8px; color: #fff; flex-shrink: 0; }
    .step__info { margin-bottom: var(--spacing-4); }
    .step__info-text { font-size: 0.8rem; color: var(--color-text-muted); }
    .color-dot { display: inline-block; width: 12px; height: 12px; border-radius: 50%; vertical-align: middle; margin-right: 4px; }
    .step__actions { margin-top: var(--spacing-6); display: flex; justify-content: space-between; align-items: center; gap: var(--spacing-3); }
    .btn {
      padding: 10px 20px; border-radius: var(--radius); border: 1px solid var(--color-border);
      background: var(--color-surface); color: var(--color-text); cursor: pointer; font-size: 0.85rem;
      font-family: inherit; transition: background-color 0.15s ease;
    }
    .btn--primary { background: var(--color-primary); color: var(--color-on-primary); border-color: var(--color-primary); }
    .btn--primary:hover:not(:disabled) { opacity: 0.9; }
    .btn--primary:disabled { opacity: 0.5; cursor: not-allowed; }
    .btn--ghost { background: transparent; border-color: transparent; color: var(--color-text-muted); }
    .btn--ghost:hover { color: var(--color-text); }
    .btn--secondary:hover { background: var(--color-bg-alt); }
  `]
})
export class WidgetPreviewStepComponent {
  @Input() companyName = '';
  @Input() botName = 'Naiara';
  @Input() tone = 'profesional';
  @Input() greeting = '';
  @Input() chatColor = '#25D366';
  @Input() saving = false;

  @Output() prev = new EventEmitter<void>();
  @Output() complete = new EventEmitter<void>();
  @Output() skip = new EventEmitter<void>();

  now(): string {
    const d = new Date();
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }
}
