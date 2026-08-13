import {
  ChangeDetectionStrategy,
  Component,
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
import { firstValueFrom } from 'rxjs';
import { CallApi } from '../../../core/api/call.api';
import { CampaignApi } from '../../../core/api/campaign.api';
import { LeadApi } from '../../../core/api/lead.api';
import { UserApi } from '../../../core/api/user.api';
import { ErrorService } from '../../../core/errors/error.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  CallOutcome,
  CallResponse,
  CallStatus,
  CreateCallRequest,
  UpdateCallRequest
} from '../../../shared/models/call.model';
import { CampaignResponse } from '../../../shared/models/campaign.model';
import { LeadResponse } from '../../../shared/models/lead.model';
import { UserListItem } from '../../../shared/models/user.model';

@Component({
  selector: 'app-call-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>{{ isEdit() ? 'Editar llamada' : 'Nueva llamada' }}</h2>
          <p class="muted">
            {{
              isEdit()
                ? 'Actualiza el resultado, notas o tiempos de la llamada.'
                : 'Registra una llamada de la campaña seleccionada, asociando el lead y el agente que la realizó.'
            }}
          </p>
        </div>
        <div class="page__actions">
          <a class="secondary" [routerLink]="['/calls']">← Volver al listado</a>
        </div>
      </header>

      @if (loadingCall()) {
        <div class="card muted">Cargando llamada...</div>
      } @else if (loadError()) {
        <div class="card error-text">Error: {{ loadError() }}</div>
      } @else if (loadingRefs()) {
        <div class="card muted">Cargando catálogos (campañas, leads, usuarios)...</div>
      } @else {
        <form class="card form" [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
          <div class="form__grid">
            <label class="field">
              <span class="field__label">Campaña *</span>
              <select
                formControlName="campaignId"
                [class.field__input--invalid]="isInvalid('campaignId')"
              >
                <option value="" disabled>Seleccionar...</option>
                @for (c of campaigns(); track c.id) {
                  <option [value]="c.id">{{ c.name }}</option>
                }
              </select>
              @if (isInvalid('campaignId')) {
                <small class="field__error">Selecciona una campaña.</small>
              }
            </label>

            <label class="field">
              <span class="field__label">Lead *</span>
              <select
                formControlName="leadId"
                [class.field__input--invalid]="isInvalid('leadId')"
              >
                <option value="" disabled>Seleccionar...</option>
                @for (l of leads(); track l.id) {
                  <option [value]="l.id">
                    {{ l.firstName }} {{ l.lastName }}{{ l.email ? ' (' + l.email + ')' : '' }}
                  </option>
                }
              </select>
              @if (isInvalid('leadId')) {
                <small class="field__error">Selecciona un lead.</small>
              }
            </label>

            <label class="field">
              <span class="field__label">Agente (usuario) *</span>
              <select
                formControlName="userId"
                [disabled]="!canAssign()"
                [class.field__input--invalid]="isInvalid('userId')"
              >
                <option value="" disabled>Seleccionar...</option>
                @for (u of users(); track u.id) {
                  <option [value]="u.id">
                    {{ u.fullName }} ({{ u.email }})
                  </option>
                }
              </select>
              @if (!canAssign()) {
                <small class="muted">Solo administradores y supervisores pueden asignar a otro agente.</small>
              }
              @if (isInvalid('userId')) {
                <small class="field__error">Selecciona el agente que hizo la llamada.</small>
              }
            </label>

            <label class="field">
              <span class="field__label">Inicio</span>
              <input type="datetime-local" formControlName="startedAt" />
            </label>

            <label class="field">
              <span class="field__label">Fin</span>
              <input type="datetime-local" formControlName="endedAt" />
            </label>

            <label class="field">
              <span class="field__label">Duración (segundos)</span>
              <input type="number" min="0" formControlName="durationSeconds" />
            </label>

            <label class="field">
              <span class="field__label">Estado</span>
              <select formControlName="status">
                <option [ngValue]="null">— Sin estado —</option>
                @for (s of statuses; track s) {
                  <option [ngValue]="s">{{ s }}</option>
                }
              </select>
            </label>

            <label class="field">
              <span class="field__label">Resultado</span>
              <select formControlName="outcome">
                <option [ngValue]="null">— Sin resultado —</option>
                @for (o of outcomes; track o) {
                  <option [ngValue]="o">{{ o }}</option>
                }
              </select>
            </label>

            <label class="field form__full">
              <span class="field__label">Notas</span>
              <textarea
                rows="4"
                formControlName="notes"
                placeholder="Resumen, próximos pasos, observaciones..."
              ></textarea>
            </label>
          </div>

          @if (submitError()) {
            <div class="card error-text">{{ submitError() }}</div>
          }

          <footer class="form__footer">
            <a class="secondary" [routerLink]="['/calls']">Cancelar</a>
            <button type="submit" [disabled]="submitting() || form.invalid">
              @if (submitting()) {
                Guardando...
              } @else {
                {{ isEdit() ? 'Guardar cambios' : 'Registrar llamada' }}
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
export class CallFormComponent implements OnInit {
  private readonly api = inject(CallApi);
  private readonly campaignApi = inject(CampaignApi);
  private readonly leadApi = inject(LeadApi);
  private readonly userApi = inject(UserApi);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly errors = inject(ErrorService);
  private readonly auth = inject(AuthService);

  protected readonly canAssign = computed(() => {
    const r = this.auth.currentRole();
    return r === 'ADMIN' || r === 'SUPERVISOR';
  });

  protected readonly statuses: CallStatus[] = [
    'CONNECTED',
    'VOICEMAIL',
    'NO_ANSWER',
    'BUSY',
    'FAILED'
  ];
  protected readonly outcomes: CallOutcome[] = [
    'INTERESTED',
    'NOT_INTERESTED',
    'CALLBACK',
    'APPOINTMENT_SET',
    'NOT_REACHED'
  ];

  protected readonly loadingCall = signal(false);
  protected readonly loadingRefs = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);

  protected readonly campaigns = signal<CampaignResponse[]>([]);
  protected readonly leads = signal<LeadResponse[]>([]);
  protected readonly users = signal<UserListItem[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    campaignId: this.fb.nonNullable.control('', [Validators.required]),
    leadId: this.fb.nonNullable.control('', [Validators.required]),
    userId: this.fb.nonNullable.control('', [Validators.required]),
    startedAt: this.fb.nonNullable.control<string>(''),
    endedAt: this.fb.nonNullable.control<string>(''),
    durationSeconds: this.fb.control<number | null>(null),
    status: this.fb.control<CallStatus | null>(null),
    outcome: this.fb.control<CallOutcome | null>(null),
    notes: this.fb.nonNullable.control('', [Validators.maxLength(65535)])
  });

  protected readonly isEdit = computed(() => !!this.route.snapshot.paramMap.get('id'));

  ngOnInit(): void {
    this.loadReferences();
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadCall(id);
    } else if (!this.canAssign()) {
      this.form.patchValue({ userId: this.auth.currentUser()?.id ?? '' });
    }
  }

  protected isInvalid(name: 'campaignId' | 'leadId' | 'userId'): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.dirty || c.touched);
  }

  protected onSubmit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const startedAt = this.toIsoOrNull(raw.startedAt);
    const endedAt = this.toIsoOrNull(raw.endedAt);
    const id = this.route.snapshot.paramMap.get('id');

    this.submitting.set(true);
    this.submitError.set(null);

    if (id) {
      const req: UpdateCallRequest = {
        startedAt,
        endedAt,
        durationSeconds: raw.durationSeconds,
        status: raw.status ?? undefined,
        outcome: raw.outcome ?? null,
        notes: raw.notes.trim() || null
      };
      this.api.update(id, req).subscribe({
        next: () => this.onSaved(id),
        error: (err) => this.onSaveError(err)
      });
    } else {
      const req: CreateCallRequest = {
        campaignId: raw.campaignId,
        leadId: raw.leadId,
        userId: raw.userId,
        startedAt,
        endedAt,
        durationSeconds: raw.durationSeconds,
        status: (raw.status ?? undefined) as string | undefined,
        outcome: raw.outcome ?? null,
        notes: raw.notes.trim() || null
      };
      this.api.create(req).subscribe({
        next: (created) => this.onSaved(created.id),
        error: (err) => this.onSaveError(err)
      });
    }
  }

  private onSaved(id: string): void {
    this.submitting.set(false);
    this.errors.success('Llamada guardada correctamente');
    void this.router.navigate(['/calls', id]);
  }

  private onSaveError(err: { error?: { message?: string }; message?: string }): void {
    this.submitting.set(false);
    const msg = err?.error?.message || err?.message || 'No se pudo guardar la llamada';
    this.submitError.set(msg);
  }

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

  private loadReferences(): void {
    this.loadingRefs.set(true);
    Promise.all([
      firstValueFrom(this.campaignApi.list({ page: 0, size: 200 })),
      firstValueFrom(this.leadApi.list({ page: 0, size: 200 })),
      firstValueFrom(this.userApi.list({ page: 0, size: 200 }))
    ])
      .then(([camps, lds, us]) => {
        this.campaigns.set(camps.content);
        this.leads.set(lds.content);
        this.users.set(us.content);
        this.loadingRefs.set(false);
      })
      .catch((err: { error?: { message?: string }; message?: string }) => {
        this.loadingRefs.set(false);
        this.loadError.set(
          err?.error?.message || err?.message || 'No se pudieron cargar los catálogos'
        );
      });
  }

  private loadCall(id: string): void {
    this.loadingCall.set(true);
    this.loadError.set(null);
    firstValueFrom(this.api.getById(id))
      .then((c) => {
        this.form.patchValue({
          campaignId: c.campaignId,
          leadId: c.leadId,
          userId: c.userId,
          startedAt: this.fromIsoToLocal(c.startedAt),
          endedAt: this.fromIsoToLocal(c.endedAt),
          durationSeconds: c.durationSeconds ?? null,
          status: c.status ?? null,
          outcome: c.outcome ?? null,
          notes: c.notes ?? ''
        });
        this.loadingCall.set(false);
      })
      .catch((err: { error?: { message?: string }; message?: string }) => {
        this.loadingCall.set(false);
        const msg = err?.error?.message || err?.message || 'No se pudo cargar la llamada';
        this.loadError.set(msg);
      });
  }
}