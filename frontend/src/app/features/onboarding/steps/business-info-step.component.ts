import { Component, EventEmitter, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-business-info-step',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="step">
      <h2 class="step__title">Informacion de tu negocio</h2>
      <p class="step__subtitle">Cuentanos sobre tu empresa para personalizar tu chatbot.</p>

      <div class="step__form">
        <div class="field">
          <label class="field__label" for="companyName">Nombre de la empresa *</label>
          <input
            id="companyName"
            class="field__input"
            type="text"
            placeholder="Ej: Mi Empresa S.A."
            [ngModel]="companyName()"
            (ngModelChange)="companyName.set($event)"
            [class.field__input--error]="showError() && !companyName().trim()"
          />
          @if (showError() && !companyName().trim()) {
            <span class="field__error">El nombre de la empresa es obligatorio.</span>
          }
        </div>

        <div class="field">
          <label class="field__label" for="website">Sitio web</label>
          <input
            id="website"
            class="field__input"
            type="url"
            placeholder="https://miempresa.com"
            [ngModel]="website()"
            (ngModelChange)="website.set($event)"
          />
        </div>

        <div class="field">
          <label class="field__label" for="industry">Industria</label>
          <select
            id="industry"
            class="field__input"
            [ngModel]="industry()"
            (ngModelChange)="industry.set($event)"
          >
            <option value="">Selecciona una industria</option>
            <option value="Tecnologia">Tecnologia</option>
            <option value="Salud">Salud</option>
            <option value="Educacion">Educacion</option>
            <option value="Finanzas">Finanzas</option>
            <option value="Retail">Retail</option>
            <option value="Servicios Profesionales">Servicios Profesionales</option>
            <option value="Inmobiliario">Inmobiliario</option>
            <option value="Manufactura">Manufactura</option>
            <option value="Otro">Otro</option>
          </select>
        </div>

        <div class="field">
          <label class="field__label" for="services">Servicios que ofreces</label>
          <textarea
            id="services"
            class="field__input field__textarea"
            placeholder="Describe brevemente tus servicios principales..."
            rows="3"
            [ngModel]="services()"
            (ngModelChange)="services.set($event)"
          ></textarea>
        </div>
      </div>

      <div class="step__actions">
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
    .step__actions { margin-top: var(--spacing-6); display: flex; justify-content: flex-end; }
    .btn {
      padding: 10px 24px; border-radius: var(--radius); border: 1px solid var(--color-border);
      background: var(--color-surface); color: var(--color-text); cursor: pointer; font-size: 0.9rem;
      font-family: inherit; transition: background-color 0.15s ease;
    }
    .btn--primary { background: var(--color-primary); color: var(--color-on-primary); border-color: var(--color-primary); }
    .btn--primary:hover { opacity: 0.9; }
  `]
})
export class BusinessInfoStepComponent {
  readonly companyName = signal('');
  readonly website = signal('');
  readonly industry = signal('');
  readonly services = signal('');
  readonly showError = signal(false);

  @Output() next = new EventEmitter<void>();

  getData(): Record<string, string> {
    return {
      companyName: this.companyName(),
      website: this.website(),
      industry: this.industry(),
      services: this.services()
    };
  }

  validate(): boolean {
    this.showError.set(true);
    return this.companyName().trim().length > 0;
  }
}
