import { Component, EventEmitter, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-chatbot-config-step',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="step">
      <h2 class="step__title">Configura tu chatbot</h2>
      <p class="step__subtitle">Personaliza como se vera y hablara tu asistente virtual.</p>

      <div class="step__form">
        <div class="field">
          <label class="field__label" for="botName">Nombre del chatbot *</label>
          <input
            id="botName"
            class="field__input"
            type="text"
            placeholder="Naiara"
            [ngModel]="botName()"
            (ngModelChange)="botName.set($event)"
            [class.field__input--error]="showError() && !botName().trim()"
          />
          @if (showError() && !botName().trim()) {
            <span class="field__error">El nombre del chatbot es obligatorio.</span>
          }
        </div>

        <div class="field">
          <label class="field__label" for="tone">Tono de personalidad</label>
          <select
            id="tone"
            class="field__input"
            [ngModel]="tone()"
            (ngModelChange)="tone.set($event)"
          >
            <option value="profesional">Profesional</option>
            <option value="amigable">Amigable</option>
            <option value="casual">Casual</option>
          </select>
        </div>

        <div class="field">
          <label class="field__label" for="greeting">Mensaje de saludo personalizado</label>
          <textarea
            id="greeting"
            class="field__input field__textarea"
            placeholder="Hola! Soy tu asistente virtual. En que puedo ayudarte hoy?"
            rows="3"
            [ngModel]="greeting()"
            (ngModelChange)="greeting.set($event)"
          ></textarea>
        </div>

        <div class="field">
          <label class="field__label" for="chatColor">Color del widget</label>
          <div class="color-row">
            <input
              id="chatColor"
              class="color-picker"
              type="color"
              [ngModel]="chatColor()"
              (ngModelChange)="chatColor.set($event)"
            />
            <span class="color-value">{{ chatColor() }}</span>
          </div>
        </div>
      </div>

      <div class="step__actions">
        <button class="btn btn--secondary" type="button" (click)="prev.emit()">Atras</button>
        <button class="btn btn--primary" type="button" (click)="next.emit()">Siguiente</button>
      </div>
    </div>
  `,
  styles: [`
    .step { max-width: 480px; margin: 0 auto; }
    .step__title { margin: 0 0 var(--spacing-2); font-size: 1.5rem; font-weight: 700; color: var(--color-text-strong); }
    .step__subtitle { margin: 0 0 var(--spacing-6); font-size: 0.9rem; color: var(--color-text-muted); }
    .step__form { display: flex; flex-direction: column; gap: var(--spacing-4); }
    .field { display: flex; flex-direction: column; gap: var(--spacing-1); }
    .field__label { font-size: 0.8rem; font-weight: 600; color: var(--color-text); }
    .field__input {
      padding: 10px 14px; background: var(--color-surface); border: 1px solid var(--color-border);
      border-radius: var(--radius); color: var(--color-text); font-size: 0.9rem; font-family: inherit; outline: none;
      transition: border-color 0.2s;
    }
    .field__input:focus { border-color: var(--color-primary); }
    .field__input--error { border-color: #ef4444; }
    .field__textarea { resize: vertical; min-height: 80px; }
    .field__error { font-size: 0.75rem; color: #ef4444; }
    .color-row { display: flex; align-items: center; gap: var(--spacing-3); }
    .color-picker { width: 48px; height: 40px; border: 1px solid var(--color-border); border-radius: var(--radius); cursor: pointer; padding: 2px; background: var(--color-surface); }
    .color-value { font-size: 0.85rem; color: var(--color-text-muted); font-family: monospace; }
    .step__actions { margin-top: var(--spacing-6); display: flex; justify-content: space-between; }
    .btn {
      padding: 10px 24px; border-radius: var(--radius); border: 1px solid var(--color-border);
      background: var(--color-surface); color: var(--color-text); cursor: pointer; font-size: 0.9rem;
      font-family: inherit; transition: background-color 0.15s ease;
    }
    .btn--primary { background: var(--color-primary); color: var(--color-on-primary); border-color: var(--color-primary); }
    .btn--primary:hover { opacity: 0.9; }
    .btn--secondary:hover { background: var(--color-bg-alt); }
  `]
})
export class ChatbotConfigStepComponent {
  readonly botName = signal('Naiara');
  readonly tone = signal('profesional');
  readonly greeting = signal('');
  readonly chatColor = signal('#25D366');
  readonly showError = signal(false);

  @Output() next = new EventEmitter<void>();
  @Output() prev = new EventEmitter<void>();

  getData(): Record<string, string> {
    return {
      botName: this.botName(),
      tone: this.tone(),
      greeting: this.greeting(),
      chatColor: this.chatColor()
    };
  }

  validate(): boolean {
    this.showError.set(true);
    return this.botName().trim().length > 0;
  }
}
