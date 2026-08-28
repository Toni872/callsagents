import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApi } from '../../../core/api/business.api';
import { AuthService } from '../../../core/auth/auth.service';
import { ErrorService } from '../../../core/errors/error.service';
import { BusinessProfileRequest } from '../../../shared/models/business-profile.model';
import { PageHeaderComponent } from '../../../shared/components/page-header.component';
import { CardComponent } from '../../../shared/components/card.component';

@Component({
  selector: 'app-profile-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, PageHeaderComponent, CardComponent],
  template: `
    <app-page-header title="Perfil de negocio" subtitle="Configura los datos de tu empresa y chatbot.">
      <button class="btn btn--primary" type="button" (click)="save()" [disabled]="saving()">
        {{ saving() ? 'Guardando...' : 'Guardar cambios' }}
      </button>
    </app-page-header>

    @if (loading()) {
      <app-card>
        <p class="muted">Cargando perfil...</p>
      </app-card>
    } @else {
      <app-card>
        <div class="settings-form">
          <h3 class="settings-form__section">Datos de la empresa</h3>

          <div class="field">
            <label class="field__label" for="companyName">Nombre de la empresa *</label>
            <input id="companyName" class="field__input" type="text" [ngModel]="companyName()" (ngModelChange)="companyName.set($event)" />
          </div>

          <div class="field">
            <label class="field__label" for="website">Sitio web</label>
            <input id="website" class="field__input" type="url" [ngModel]="website()" (ngModelChange)="website.set($event)" />
          </div>

          <div class="field">
            <label class="field__label" for="industry">Industria</label>
            <select id="industry" class="field__input" [ngModel]="industry()" (ngModelChange)="industry.set($event)">
              <option value="">Selecciona una industria</option>
              <option value="Tecnologia">Tecnología</option>
              <option value="Salud">Salud</option>
              <option value="Educacion">Educación</option>
              <option value="Finanzas">Finanzas</option>
              <option value="Retail">Retail</option>
              <option value="Servicios Profesionales">Servicios Profesionales</option>
              <option value="Inmobiliario">Inmobiliario</option>
              <option value="Manufactura">Manufactura</option>
              <option value="Otro">Otro</option>
            </select>
          </div>

          <div class="field">
            <label class="field__label" for="services">Servicios</label>
            <textarea id="services" class="field__input field__textarea" rows="3" [ngModel]="services()" (ngModelChange)="services.set($event)"></textarea>
          </div>

          <h3 class="settings-form__section">Configuración del chatbot</h3>

          <div class="field">
            <label class="field__label" for="botName">Nombre del chatbot</label>
            <input id="botName" class="field__input" type="text" [ngModel]="botName()" (ngModelChange)="botName.set($event)" />
          </div>

          <div class="field">
            <label class="field__label" for="tone">Tono</label>
            <select id="tone" class="field__input" [ngModel]="tone()" (ngModelChange)="tone.set($event)">
              <option value="profesional">Profesional</option>
              <option value="amigable">Amigable</option>
              <option value="casual">Casual</option>
            </select>
          </div>

          <div class="field">
            <label class="field__label" for="greeting">Saludo personalizado</label>
            <textarea id="greeting" class="field__input field__textarea" rows="2" [ngModel]="greeting()" (ngModelChange)="greeting.set($event)"></textarea>
          </div>

          <div class="field">
            <label class="field__label" for="chatColor">Color del widget</label>
            <div class="color-row">
              <input id="chatColor" class="color-picker" type="color" [ngModel]="chatColor()" (ngModelChange)="chatColor.set($event)" />
              <span class="color-value">{{ chatColor() }}</span>
            </div>
          </div>
        </div>
      </app-card>

      @if (businessId()) {
        <app-card>
          <div class="settings-form">
            <h3 class="settings-form__section">Widget en tu web</h3>
            <p class="widget-info">Copia este codigo y pegalo en tu sitio web para activar el chatbot.</p>

            <div class="embed-code">
              <code class="embed-code__text">{{ embedCode() }}</code>
              <button class="btn btn--secondary btn--sm" type="button" (click)="copyEmbedCode()">
                {{ copied() ? 'Copiado!' : 'Copiar' }}
              </button>
            </div>

            <p class="widget-info">O prueba el widget directamente:</p>
            <a class="widget-link" [href]="widgetUrl()" target="_blank" rel="noopener noreferrer">
              Abrir widget en nueva pestaña
            </a>
          </div>
        </app-card>
      }
    }
  `,
  styles: [`
    :host { display: block; max-width: 720px; margin: 0 auto; }
    .settings-form { display: flex; flex-direction: column; gap: var(--spacing-4); }
    .settings-form__section {
      margin: var(--spacing-4) 0 var(--spacing-2); font-size: 0.9rem; font-weight: 600;
      color: var(--color-text-strong); border-bottom: 1px solid var(--color-border); padding-bottom: var(--spacing-2);
    }
    .field { display: flex; flex-direction: column; gap: var(--spacing-1); }
    .field__label { font-size: 0.8rem; font-weight: 600; color: var(--color-text); }
    .field__input {
      padding: 10px 14px; background: var(--color-bg); border: 1px solid var(--color-border);
      border-radius: var(--radius); color: var(--color-text); font-size: 0.9rem; font-family: inherit; outline: none;
      transition: border-color 0.2s;
    }
    .field__input:focus { border-color: var(--color-primary); }
    .field__textarea { resize: vertical; min-height: 60px; }
    .color-row { display: flex; align-items: center; gap: var(--spacing-3); }
    .color-picker { width: 48px; height: 40px; border: 1px solid var(--color-border); border-radius: var(--radius); cursor: pointer; padding: 2px; background: var(--color-surface); }
    .color-value { font-size: 0.85rem; color: var(--color-text-muted); font-family: monospace; }
    .muted { color: var(--color-text-muted); font-size: 0.9rem; }
    .btn {
      padding: 10px 24px; border-radius: var(--radius); border: 1px solid var(--color-border);
      background: var(--color-surface); color: var(--color-text); cursor: pointer; font-size: 0.875rem;
      font-family: inherit; transition: background-color 0.15s ease;
    }
    .btn--primary { background: var(--color-primary); color: var(--color-on-primary); border-color: var(--color-primary); }
    .btn--primary:hover:not(:disabled) { opacity: 0.9; }
    .btn--primary:disabled { opacity: 0.5; cursor: not-allowed; }
    .btn--secondary { background: var(--color-surface); border-color: var(--color-border); }
    .btn--secondary:hover { background: var(--color-bg-alt); }
    .btn--sm { padding: 6px 12px; font-size: 0.8rem; }
    .widget-info { margin: 0 0 var(--spacing-3); font-size: 0.85rem; color: var(--color-text-muted); }
    .embed-code {
      display: flex; align-items: center; gap: var(--spacing-3);
      padding: var(--spacing-3); background: var(--color-bg); border: 1px solid var(--color-border);
      border-radius: var(--radius); margin-bottom: var(--spacing-3);
    }
    .embed-code__text {
      flex: 1; font-size: 0.75rem; font-family: monospace; color: var(--color-text-muted);
      word-break: break-all; white-space: pre-wrap;
    }
    .widget-link {
      display: inline-block; font-size: 0.85rem; color: var(--color-primary);
      text-decoration: none; font-weight: 500;
    }
    .widget-link:hover { text-decoration: underline; }
  `]
})
export class ProfileSettingsComponent implements OnInit {
  private readonly businessApi = inject(BusinessApi);
  private readonly authService = inject(AuthService);
  private readonly errorService = inject(ErrorService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly copied = signal(false);

  readonly companyName = signal('');
  readonly website = signal('');
  readonly industry = signal('');
  readonly services = signal('');
  readonly botName = signal('Naiara');
  readonly tone = signal('profesional');
  readonly greeting = signal('');
  readonly chatColor = signal('#25D366');
  readonly businessId = signal('');

  readonly embedCode = signal('');
  readonly widgetUrl = signal('');

  ngOnInit(): void {
    this.businessApi.getProfile().subscribe({
      next: (res) => {
        const p = res.data;
        this.companyName.set(p.companyName);
        this.website.set(p.website || '');
        this.industry.set(p.industry || '');
        this.services.set(p.services || '');
        this.botName.set(p.botName);
        this.tone.set(p.tone);
        this.greeting.set(p.greeting || '');
        this.chatColor.set(p.chatColor);
        this.businessId.set(p.id || '');
        this.generateEmbedCode(p.id || '');
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  private generateEmbedCode(businessId: string): void {
    if (!businessId) return;
    const baseUrl = window.location.origin;
    this.widgetUrl.set(`${baseUrl}/widget?businessId=${businessId}`);
    this.embedCode.set(
      `<script src="${baseUrl}/widget.js" data-business-id="${businessId}"></script>`
    );
  }

  copyEmbedCode(): void {
    navigator.clipboard.writeText(this.embedCode());
    this.copied.set(true);
    setTimeout(() => this.copied.set(false), 2000);
  }

  save(): void {
    this.saving.set(true);
    const request: BusinessProfileRequest = {
      companyName: this.companyName(),
      website: this.website() || undefined,
      industry: this.industry() || undefined,
      services: this.services() || undefined,
      tone: this.tone(),
      botName: this.botName(),
      greeting: this.greeting() || undefined,
      chatColor: this.chatColor()
    };

    this.businessApi.updateProfile(request).subscribe({
      next: () => {
        this.saving.set(false);
        this.errorService.success('Perfil actualizado correctamente');
      },
      error: () => {
        this.saving.set(false);
      }
    });
  }
}
