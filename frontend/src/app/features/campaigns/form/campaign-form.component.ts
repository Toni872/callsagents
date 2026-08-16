import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  FormControl,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, firstValueFrom } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CampaignApi } from '../../../core/api/campaign.api';
import { ErrorService } from '../../../core/errors/error.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  CampaignResponse,
  CampaignStatus,
  CreateCampaignRequest,
  UpdateCampaignRequest,
  VoicePromptPreviewRequest
} from '../../../shared/models/campaign.model';

@Component({
  selector: 'app-campaign-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>{{ isEdit() ? 'Editar campaña' : 'Nueva campaña' }}</h2>
          <p class="muted">
            {{
              isEdit()
                ? 'Modifica los datos de la campaña. Para lanzarla o pausarla, ve al detalle.'
                : 'Crea una campaña en estado DRAFT. Luego podrás añadirle leads y lanzarla.'
            }}
          </p>
        </div>
        <div class="page__actions">
          <a class="secondary" [routerLink]="['/campaigns']">← Volver al listado</a>
        </div>
      </header>

      @if (loadingCampaign()) {
        <div class="card muted">Cargando campaña...</div>
      } @else if (loadError()) {
        <div class="card error-text">Error: {{ loadError() }}</div>
      } @else {
        <form class="card form" [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
          <div class="form__grid">
            <label class="field form__full">
              <span class="field__label">Nombre *</span>
              <input
                type="text"
                autocomplete="off"
                formControlName="name"
                [class.field__input--invalid]="isInvalid('name')"
              />
              @if (isInvalid('name')) {
                <small class="field__error">El nombre es obligatorio (máx. 255).</small>
              }
            </label>

            <label class="field form__full">
              <span class="field__label">Descripción</span>
              <textarea
                rows="3"
                formControlName="description"
                [class.field__input--invalid]="isInvalid('description')"
              ></textarea>
              @if (isInvalid('description')) {
                <small class="field__error">Máx. 4096 caracteres.</small>
              }
            </label>

            <label class="field">
              <span class="field__label">Fecha de inicio</span>
              <input type="datetime-local" formControlName="startAt" />
            </label>

            <label class="field">
              <span class="field__label">Fecha de fin</span>
              <input type="datetime-local" formControlName="endAt" />
            </label>

            @if (isEdit()) {
              <label class="field form__full">
                <span class="field__label">Estado</span>
                <select formControlName="status">
                  @for (s of statuses; track s) {
                    <option [value]="s">{{ s }}</option>
                  }
                </select>
                <small class="muted">
                  Para lanzar o pausar la campaña, usa los botones en la vista de detalle.
                </small>
              </label>
            }

            <label class="field form__full">
              <span class="field__label">Script / prompt</span>
              <textarea
                rows="6"
                formControlName="script"
                placeholder="Texto que se usará como guion para la IA..."
                [class.field__input--invalid]="isInvalid('script')"
              ></textarea>
              @if (isInvalid('script')) {
                <small class="field__error">Máx. 65535 caracteres.</small>
              }
            </label>

            @if (isVoiceAdmin()) {
              <h3 class="form__full voice-section__title">Agente de voz</h3>

              <label class="field">
                <span class="field__label">Empresa</span>
                <input
                  type="text"
                  autocomplete="off"
                  formControlName="company"
                  [class.field__input--invalid]="isInvalid('company')"
                />
                @if (isInvalid('company')) {
                  <small class="field__error">Máx. 255 caracteres.</small>
                }
              </label>

              <label class="field">
                <span class="field__label">Sitio web</span>
                <input
                  type="text"
                  autocomplete="off"
                  formControlName="website"
                  [class.field__input--invalid]="isInvalid('website')"
                />
                @if (isInvalid('website')) {
                  <small class="field__error">URL válida (máx. 255 caracteres).</small>
                }
              </label>

              <label class="field">
                <span class="field__label">Industria</span>
                <input
                  type="text"
                  autocomplete="off"
                  formControlName="industry"
                  [class.field__input--invalid]="isInvalid('industry')"
                />
                @if (isInvalid('industry')) {
                  <small class="field__error">Máx. 255 caracteres.</small>
                }
              </label>

              <label class="field form__full">
                <span class="field__label">Servicios</span>
                <textarea
                  rows="4"
                  formControlName="services"
                  [class.field__input--invalid]="isInvalid('services')"
                ></textarea>
                @if (isInvalid('services')) {
                  <small class="field__error">Máx. 65535 caracteres.</small>
                }
              </label>

              <label class="field">
                <span class="field__label">Tono</span>
                <input
                  type="text"
                  autocomplete="off"
                  formControlName="tone"
                  [class.field__input--invalid]="isInvalid('tone')"
                />
                @if (isInvalid('tone')) {
                  <small class="field__error">Máx. 255 caracteres.</small>
                }
              </label>

              @if (previewLoading()) {
                <div class="form__full muted">Generando preview del prompt…</div>
              } @else if (preview()) {
                <div class="form__full">
                  <span class="field__label">Preview del prompt</span>
                  <pre class="voice-preview">{{ preview() }}</pre>
                </div>
              }
            }
          </div>

          @if (submitError()) {
            <div class="card error-text">{{ submitError() }}</div>
          }

          <footer class="form__footer">
            <a class="secondary" [routerLink]="['/campaigns']">Cancelar</a>
            <button type="submit" [disabled]="submitting() || form.invalid">
              @if (submitting()) {
                Guardando...
              } @else {
                {{ isEdit() ? 'Guardar cambios' : 'Crear campaña' }}
              }
            </button>
          </footer>
        </form>
      }
    </section>
  `,
  styles: [
    `
      .page {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-4);
      }
      .page__header {
        display: flex;
        align-items: flex-end;
        justify-content: space-between;
        gap: var(--spacing-4);
        flex-wrap: wrap;
      }
      .page__actions {
        display: flex;
        gap: var(--spacing-2);
      }
      .form {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-6);
      }
      .form__grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: var(--spacing-4);
      }
      .form__full {
        grid-column: 1 / -1;
      }
      @media (max-width: 720px) {
        .form__grid {
          grid-template-columns: 1fr;
        }
      }
      .field {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-1);
      }
      .field__label {
        font-size: 0.8125rem;
        font-weight: 500;
      }
      .field__error {
        color: var(--color-error);
        font-size: 0.75rem;
      }
      .field__input--invalid {
        border-color: var(--color-error);
      }
      .voice-section__title {
        margin: var(--spacing-2) 0 0;
        font-size: 0.9375rem;
        font-weight: 600;
      }
      .voice-preview {
        margin: 0;
        padding: var(--spacing-3);
        background: var(--color-bg-alt);
        border: 1px solid var(--color-border);
        border-radius: var(--radius);
        font-size: 0.8125rem;
        white-space: pre-wrap;
        max-height: 320px;
        overflow-y: auto;
      }
      .form__footer {
        display: flex;
        justify-content: flex-end;
        gap: var(--spacing-2);
        padding-top: var(--spacing-3);
        border-top: 1px solid var(--color-border);
      }
    `
  ]
})
export class CampaignFormComponent implements OnInit {
  private readonly api = inject(CampaignApi);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly errors = inject(ErrorService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly statuses: CampaignStatus[] = [
    'DRAFT',
    'SCHEDULED',
    'RUNNING',
    'PAUSED',
    'FINISHED',
    'CANCELLED'
  ];

  protected readonly loadingCampaign = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly isVoiceAdmin = computed(
    () => this.auth.currentRole() === 'ADMIN'
  );
  protected readonly preview = signal<string | null>(null);
  protected readonly previewLoading = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    name: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(255)]),
    description: this.fb.nonNullable.control('', [Validators.maxLength(4096)]),
    startAt: this.fb.nonNullable.control<string>(''),
    endAt: this.fb.nonNullable.control<string>(''),
    status: this.fb.nonNullable.control<CampaignStatus>('DRAFT'),
    script: this.fb.nonNullable.control('', [Validators.maxLength(65535)]),
    company: this.fb.nonNullable.control('', [Validators.maxLength(255)]),
    website: this.fb.nonNullable.control('', [Validators.maxLength(255)]),
    industry: this.fb.nonNullable.control('', [Validators.maxLength(255)]),
    services: this.fb.nonNullable.control('', [Validators.maxLength(65535)]),
    tone: this.fb.nonNullable.control('', [Validators.maxLength(255)])
  });

  protected readonly isEdit = computed(() => !!this.route.snapshot.paramMap.get('id'));

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadCampaign(id);
    }
    // El preview en vivo solo tiene sentido donde la sección es visible
    // (AGENT no debe llamar al endpoint ADMIN del preview).
    if (this.isVoiceAdmin()) {
      this.form.valueChanges
        .pipe(debounceTime(400), takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.refreshPreview());
    }
  }

  protected isInvalid(
    name:
      | 'name'
      | 'description'
      | 'script'
      | 'company'
      | 'website'
      | 'industry'
      | 'services'
      | 'tone'
  ): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.dirty || c.touched);
  }

  protected onSubmit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const startAt = this.toIsoOrNull(raw.startAt);
    const endAt = this.toIsoOrNull(raw.endAt);
    const voice = {
      company: raw.company.trim() || null,
      website: raw.website.trim() || null,
      industry: raw.industry.trim() || null,
      services: raw.services.trim() || null,
      tone: raw.tone.trim() || null
    };
    const id = this.route.snapshot.paramMap.get('id');

    this.submitting.set(true);
    this.submitError.set(null);

    if (id) {
      const req: UpdateCampaignRequest = {
        name: raw.name.trim(),
        description: raw.description.trim() || null,
        startAt,
        endAt,
        script: raw.script.trim() || null,
        status: raw.status,
        ...voice
      };
      this.api.update(id, req).subscribe({
        next: () => this.onSaved(id),
        error: (err) => this.onSaveError(err)
      });
    } else {
      const req: CreateCampaignRequest = {
        name: raw.name.trim(),
        description: raw.description.trim() || null,
        startAt,
        endAt,
        script: raw.script.trim() || null,
        ...voice
      };
      this.api.create(req).subscribe({
        next: (created) => this.onSaved(created.id),
        error: (err) => this.onSaveError(err)
      });
    }
  }

  private onSaved(id: string): void {
    this.submitting.set(false);
    this.errors.success('Campaña guardada correctamente');
    void this.router.navigate(['/campaigns', id]);
  }

  private onSaveError(err: { error?: { message?: string }; message?: string }): void {
    this.submitting.set(false);
    const msg = err?.error?.message || err?.message || 'No se pudo guardar la campaña';
    this.submitError.set(msg);
  }

  /**
   * Convert `<input type="datetime-local">` value (`YYYY-MM-DDTHH:mm`)
   * to an ISO string the backend accepts, or null when empty.
   */
  private toIsoOrNull(local: string): string | null {
    if (!local) {
      return null;
    }
    const d = new Date(local);
    if (Number.isNaN(d.getTime())) {
      return null;
    }
    return d.toISOString();
  }

  /**
   * Convert backend ISO string into the format expected by
   * `<input type="datetime-local">` (`YYYY-MM-DDTHH:mm`).
   */
  private fromIsoToLocal(iso: string | null): string {
    if (!iso) {
      return '';
    }
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
      return '';
    }
    const pad = (n: number): string => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  /**
   * Preview en vivo del prompt de voz: pide al backend la composición del
   * template (el frontend NO duplica el template). Errores → toast del
   * errorInterceptor; el preview se oculta sin romper el form.
   */
  private refreshPreview(): void {
    const raw = this.form.getRawValue();
    const req: VoicePromptPreviewRequest = {
      company: raw.company.trim(),
      website: raw.website.trim(),
      industry: raw.industry.trim(),
      services: raw.services.trim(),
      tone: raw.tone.trim()
    };
    this.previewLoading.set(true);
    this.api.previewVoicePrompt(req).subscribe({
      next: (res) => {
        this.preview.set(res.prompt);
        this.previewLoading.set(false);
      },
      error: () => {
        this.preview.set(null);
        this.previewLoading.set(false);
      }
    });
  }

  private loadCampaign(id: string): void {
    this.loadingCampaign.set(true);
    this.loadError.set(null);
    firstValueFrom(this.api.getById(id))
      .then((c) => {
        this.form.patchValue({
          name: c.name,
          description: c.description ?? '',
          startAt: this.fromIsoToLocal(c.startAt),
          endAt: this.fromIsoToLocal(c.endAt),
          status: c.status,
          script: c.script ?? '',
          company: c.company ?? '',
          website: c.website ?? '',
          industry: c.industry ?? '',
          services: c.services ?? '',
          tone: c.tone ?? ''
        });
        this.loadingCampaign.set(false);
      })
      .catch((err: { error?: { message?: string }; message?: string }) => {
        this.loadingCampaign.set(false);
        const msg = err?.error?.message || err?.message || 'No se pudo cargar la campaña';
        this.loadError.set(msg);
      });
  }
}