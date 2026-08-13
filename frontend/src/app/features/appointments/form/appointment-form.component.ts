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
import { AppointmentApi } from '../../../core/api/appointment.api';
import { LeadApi } from '../../../core/api/lead.api';
import { UserApi } from '../../../core/api/user.api';
import { ErrorService } from '../../../core/errors/error.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  AppointmentResponse,
  AppointmentStatus,
  CreateAppointmentRequest,
  UpdateAppointmentRequest
} from '../../../shared/models/appointment.model';
import { LeadResponse } from '../../../shared/models/lead.model';
import { UserListItem } from '../../../shared/models/user.model';

@Component({
  selector: 'app-appointment-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>{{ isEdit() ? 'Editar cita' : 'Nueva cita' }}</h2>
          <p class="muted">
            {{
              isEdit()
                ? 'Modifica la fecha, duración, estado o notas de la cita.'
                : 'Programa una cita con un lead. Se enviará automáticamente al calendario del agente si tiene integración activa.'
            }}
          </p>
        </div>
        <div class="page__actions">
          <a class="secondary" [routerLink]="['/appointments']">← Volver al listado</a>
        </div>
      </header>

      @if (loadingAppointment()) {
        <div class="card muted">Cargando cita...</div>
      } @else if (loadError()) {
        <div class="card error-text">Error: {{ loadError() }}</div>
      } @else if (loadingRefs()) {
        <div class="card muted">Cargando catálogos (leads, usuarios)...</div>
      } @else {
        <form class="card form" [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
          <div class="form__grid">
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
                <small class="field__error">Selecciona el agente responsable.</small>
              }
            </label>

            <label class="field">
              <span class="field__label">Fecha y hora *</span>
              <input
                type="datetime-local"
                formControlName="scheduledAt"
                [class.field__input--invalid]="isInvalid('scheduledAt')"
              />
              @if (isInvalid('scheduledAt')) {
                <small class="field__error">La fecha es obligatoria.</small>
              }
            </label>

            <label class="field">
              <span class="field__label">Duración (minutos) *</span>
              <input
                type="number"
                min="1"
                formControlName="durationMinutes"
                [class.field__input--invalid]="isInvalid('durationMinutes')"
              />
              @if (isInvalid('durationMinutes')) {
                <small class="field__error">Mínimo 1 minuto.</small>
              }
            </label>

            @if (isEdit()) {
              <label class="field">
                <span class="field__label">Estado</span>
                <select formControlName="status">
                  @for (s of statuses; track s) {
                    <option [ngValue]="s">{{ s }}</option>
                  }
                </select>
              </label>
            }

            <label class="field form__full">
              <span class="field__label">Notas</span>
              <textarea
                rows="4"
                formControlName="notes"
                placeholder="Motivo de la cita, preparativos, etc."
              ></textarea>
            </label>
          </div>

          @if (submitError()) {
            <div class="card error-text">{{ submitError() }}</div>
          }

          <footer class="form__footer">
            <a class="secondary" [routerLink]="['/appointments']">Cancelar</a>
            <button type="submit" [disabled]="submitting() || form.invalid">
              @if (submitting()) {
                Guardando...
              } @else {
                {{ isEdit() ? 'Guardar cambios' : 'Crear cita' }}
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
export class AppointmentFormComponent implements OnInit {
  private readonly api = inject(AppointmentApi);
  private readonly leadApi = inject(LeadApi);
  private readonly userApi = inject(UserApi);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly errors = inject(ErrorService);

  protected readonly statuses: AppointmentStatus[] = [
    'PENDING',
    'CONFIRMED',
    'COMPLETED',
    'CANCELLED',
    'NO_SHOW'
  ];

  protected readonly canAssign = computed(() => {
    const r = this.auth.currentRole();
    return r === 'ADMIN' || r === 'SUPERVISOR';
  });

  protected readonly loadingAppointment = signal(false);
  protected readonly loadingRefs = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);

  protected readonly leads = signal<LeadResponse[]>([]);
  protected readonly users = signal<UserListItem[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    leadId: this.fb.nonNullable.control('', [Validators.required]),
    userId: this.fb.nonNullable.control('', [Validators.required]),
    scheduledAt: this.fb.nonNullable.control('', [Validators.required]),
    durationMinutes: this.fb.nonNullable.control(30, [Validators.required, Validators.min(1)]),
    status: this.fb.nonNullable.control<AppointmentStatus>('PENDING'),
    notes: this.fb.nonNullable.control('')
  });

  protected readonly isEdit = computed(() => !!this.route.snapshot.paramMap.get('id'));

  ngOnInit(): void {
    this.loadReferences();
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadAppointment(id);
    } else {
      // Default the agent to the current user
      const currentUserId = this.auth.currentUser()?.id;
      if (currentUserId) {
        this.form.patchValue({ userId: currentUserId });
      }
    }
  }

  protected isInvalid(name: 'leadId' | 'userId' | 'scheduledAt' | 'durationMinutes'): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.dirty || c.touched);
  }

  protected onSubmit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const scheduledAt = this.toIsoOrNull(raw.scheduledAt);
    if (!scheduledAt) {
      this.form.controls.scheduledAt.markAsTouched();
      return;
    }
    const id = this.route.snapshot.paramMap.get('id');

    this.submitting.set(true);
    this.submitError.set(null);

    if (id) {
      const req: UpdateAppointmentRequest = {
        scheduledAt,
        durationMinutes: raw.durationMinutes,
        status: raw.status,
        notes: raw.notes.trim() || null
      };
      this.api.update(id, req).subscribe({
        next: () => this.onSaved(id),
        error: (err) => this.onSaveError(err)
      });
    } else {
      const req: CreateAppointmentRequest = {
        leadId: raw.leadId,
        userId: raw.userId,
        scheduledAt,
        durationMinutes: raw.durationMinutes,
        status: raw.status,
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
    this.errors.success('Cita guardada correctamente');
    void this.router.navigate(['/appointments', id]);
  }

  private onSaveError(err: { error?: { message?: string }; message?: string }): void {
    this.submitting.set(false);
    const msg = err?.error?.message || err?.message || 'No se pudo guardar la cita';
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
      firstValueFrom(this.leadApi.list({ page: 0, size: 200 })),
      firstValueFrom(this.userApi.list({ page: 0, size: 200 }))
    ])
      .then(([lds, us]) => {
        this.leads.set(lds.content);
        this.users.set(us.content);
        this.loadingRefs.set(false);
      })
      .catch((err: { error?: { message?: string }; message?: string }) => {
        this.loadingRefs.set(false);
        this.loadError.set(err?.error?.message || err?.message || 'No se pudieron cargar los catálogos');
      });
  }

  private loadAppointment(id: string): void {
    this.loadingAppointment.set(true);
    this.loadError.set(null);
    firstValueFrom(this.api.getById(id))
      .then((a) => {
        this.form.patchValue({
          leadId: a.leadId,
          userId: a.userId,
          scheduledAt: this.fromIsoToLocal(a.scheduledAt),
          durationMinutes: a.durationMinutes,
          status: a.status,
          notes: a.notes ?? ''
        });
        this.loadingAppointment.set(false);
      })
      .catch((err: { error?: { message?: string }; message?: string }) => {
        this.loadingAppointment.set(false);
        const msg = err?.error?.message || err?.message || 'No se pudo cargar la cita';
        this.loadError.set(msg);
      });
  }
}