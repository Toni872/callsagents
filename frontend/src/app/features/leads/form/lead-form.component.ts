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
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LeadApi } from '../../../core/api/lead.api';
import { UserApi } from '../../../core/api/user.api';
import { ErrorService } from '../../../core/errors/error.service';
import {
  CreateLeadRequest,
  LeadAssignedUser,
  LeadResponse,
  LeadSource,
  LeadStatus,
  UpdateLeadRequest
} from '../../../shared/models/lead.model';

type LeadSourceValue = LeadSource;
type LeadStatusValue = LeadStatus;
type CustomFieldRow = FormGroup<{
  key: FormControl<string>;
  value: FormControl<string>;
}>;

@Component({
  selector: 'app-lead-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>{{ isEdit() ? 'Editar lead' : 'Nuevo lead' }}</h2>
          <p class="muted">
            {{
              isEdit()
                ? 'Modifica los datos del lead y guarda los cambios.'
                : 'Rellena los datos mínimos (nombre y origen) para crear el lead.'
            }}
          </p>
        </div>
        <div class="page__actions">
          <a class="secondary" [routerLink]="['/leads']">← Volver al listado</a>
        </div>
      </header>

      @if (loadingLead()) {
        <div class="card muted">Cargando lead...</div>
      } @else if (loadError()) {
        <div class="card error-text">Error: {{ loadError() }}</div>
      } @else {
        <form class="card form" [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
          <div class="form__grid">
            <label class="field">
              <span class="field__label">Nombre *</span>
              <input
                type="text"
                autocomplete="off"
                formControlName="firstName"
                [class.field__input--invalid]="isInvalid('firstName')"
              />
              @if (isInvalid('firstName')) {
                <small class="field__error">El nombre es obligatorio (máx. 100 caracteres).</small>
              }
            </label>

            <label class="field">
              <span class="field__label">Apellidos *</span>
              <input
                type="text"
                autocomplete="off"
                formControlName="lastName"
                [class.field__input--invalid]="isInvalid('lastName')"
              />
              @if (isInvalid('lastName')) {
                <small class="field__error">Los apellidos son obligatorios (máx. 100).</small>
              }
            </label>

            <label class="field">
              <span class="field__label">Email</span>
              <input
                type="email"
                autocomplete="off"
                formControlName="email"
                [class.field__input--invalid]="isInvalid('email')"
              />
              @if (isInvalid('email')) {
                <small class="field__error">Email inválido.</small>
              }
            </label>

            <label class="field">
              <span class="field__label">Teléfono</span>
              <input
                type="tel"
                autocomplete="off"
                formControlName="phone"
                placeholder="+34600000000"
                [class.field__input--invalid]="isInvalid('phone')"
              />
              @if (isInvalid('phone')) {
                <small class="field__error">Máx. 32 caracteres.</small>
              }
            </label>

            <label class="field">
              <span class="field__label">Empresa</span>
              <input
                type="text"
                autocomplete="off"
                formControlName="company"
                [class.field__input--invalid]="isInvalid('company')"
              />
            </label>

            <label class="field">
              <span class="field__label">Origen *</span>
              <select
                formControlName="source"
                [class.field__input--invalid]="isInvalid('source')"
              >
                <option value="" disabled>Seleccionar...</option>
                @for (s of sources; track s) {
                  <option [value]="s">{{ s }}</option>
                }
              </select>
              @if (isInvalid('source')) {
                <small class="field__error">Selecciona un origen.</small>
              }
            </label>

            @if (isEdit()) {
              <label class="field">
                <span class="field__label">Estado</span>
                <select formControlName="status">
                  @for (s of statuses; track s) {
                    <option [value]="s">{{ s }}</option>
                  }
                </select>
              </label>
            }

            <label class="field">
              <span class="field__label">Asignado a</span>
              <select formControlName="assignedToId">
                <option [ngValue]="null">— Sin asignar —</option>
                @for (u of assignableUsers(); track u.id) {
                  <option [ngValue]="u.id">{{ u.fullName }} ({{ u.email }})</option>
                }
              </select>
            </label>

            <label class="field form__full">
              <span class="field__label">Notas</span>
              <textarea
                rows="3"
                formControlName="notes"
                [class.field__input--invalid]="isInvalid('notes')"
              ></textarea>
            </label>

            <div class="field form__full">
              <label class="field__check">
                <input type="checkbox" formControlName="doNotCall" />
                <span>No llamar (RGPD / do-not-call)</span>
              </label>
            </div>
          </div>

          <fieldset class="custom-fields">
            <legend>Campos personalizados</legend>
            <p class="muted custom-fields__hint">
              Añade pares clave-valor para datos específicos del lead (origen externo,
              segmento, etc.). Se almacenan como JSON.
            </p>

            <div formArrayName="customFields" class="custom-fields__rows">
              @for (row of customFieldsArray.controls; track $index) {
                <div class="custom-fields__row" [formGroupName]="$index">
                  <input
                    type="text"
                    formControlName="key"
                    placeholder="clave"
                    [class.field__input--invalid]="customFieldKeyInvalid($index)"
                  />
                  <input
                    type="text"
                    formControlName="value"
                    placeholder="valor"
                  />
                  <button
                    type="button"
                    class="secondary"
                    (click)="removeCustomField($index)"
                    aria-label="Eliminar campo"
                  >
                    ×
                  </button>
                </div>
              } @empty {
                <p class="muted">Sin campos personalizados.</p>
              }
            </div>

            <button type="button" class="secondary" (click)="addCustomField()">
              + Añadir campo
            </button>
          </fieldset>

          @if (submitError()) {
            <div class="card error-text">{{ submitError() }}</div>
          }

          <footer class="form__footer">
            <a class="secondary" [routerLink]="['/leads']">Cancelar</a>
            <button type="submit" [disabled]="submitting() || form.invalid">
              @if (submitting()) {
                Guardando...
              } @else {
                {{ isEdit() ? 'Guardar cambios' : 'Crear lead' }}
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
      .field__check {
        display: flex;
        align-items: center;
        gap: var(--spacing-2);
        font-size: 0.875rem;
      }

      .custom-fields {
        border: 1px dashed var(--color-border);
        border-radius: var(--radius);
        padding: var(--spacing-4);
        display: flex;
        flex-direction: column;
        gap: var(--spacing-3);
      }
      .custom-fields legend {
        font-weight: 500;
        padding: 0 var(--spacing-2);
      }
      .custom-fields__hint {
        margin: 0;
        font-size: 0.8125rem;
      }
      .custom-fields__rows {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-2);
      }
      .custom-fields__row {
        display: grid;
        grid-template-columns: 1fr 1fr auto;
        gap: var(--spacing-2);
        align-items: center;
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
export class LeadFormComponent implements OnInit {
  private readonly api = inject(LeadApi);
  private readonly userApi = inject(UserApi);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly errors = inject(ErrorService);

  protected readonly sources: LeadSourceValue[] = ['MANUAL', 'IMPORT', 'API'];
  protected readonly statuses: LeadStatusValue[] = [
    'NEW',
    'ASSIGNED',
    'IN_PROGRESS',
    'QUALIFIED',
    'NOT_QUALIFIED',
    'CONVERTED',
    'DISQUALIFIED'
  ];

  protected readonly loadingLead = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly assignableUsers = signal<LeadAssignedUser[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    firstName: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(100)]),
    lastName: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(100)]),
    email: this.fb.nonNullable.control<string>('', [Validators.email, Validators.maxLength(255)]),
    phone: this.fb.nonNullable.control('', [Validators.maxLength(32)]),
    company: this.fb.nonNullable.control('', [Validators.maxLength(255)]),
    source: this.fb.nonNullable.control<LeadSourceValue | ''>('', [Validators.required]),
    status: this.fb.nonNullable.control<LeadStatusValue>('NEW'),
    assignedToId: this.fb.control<string | null>(null),
    notes: this.fb.nonNullable.control('', [Validators.maxLength(4096)]),
    doNotCall: this.fb.nonNullable.control(false),
    customFields: this.fb.array<CustomFieldRow>([])
  });

  protected readonly isEdit = computed(() => !!this.route.snapshot.paramMap.get('id'));

  get customFieldsArray(): FormArray<CustomFieldRow> {
    return this.form.controls.customFields;
  }

  ngOnInit(): void {
    this.loadAssignableUsers();
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadLead(id);
    }
  }

  protected isInvalid(name: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.dirty || c.touched);
  }

  protected customFieldKeyInvalid(index: number): boolean {
    const row = this.customFieldsArray.at(index);
    const keyCtrl = row?.controls.key;
    return !!(keyCtrl && keyCtrl.invalid && (keyCtrl.dirty || keyCtrl.touched));
  }

  protected addCustomField(): void {
    this.customFieldsArray.push(
      this.fb.nonNullable.group({
        key: this.fb.nonNullable.control('', [Validators.required]),
        value: this.fb.nonNullable.control('')
      })
    );
  }

  protected removeCustomField(index: number): void {
    this.customFieldsArray.removeAt(index);
  }

  protected onSubmit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const customFields = this.buildCustomFields();
    const id = this.route.snapshot.paramMap.get('id');

    this.submitting.set(true);
    this.submitError.set(null);

    if (id) {
      const req: UpdateLeadRequest = {
        firstName: raw.firstName.trim(),
        lastName: raw.lastName.trim(),
        email: raw.email.trim() || null,
        phone: raw.phone.trim() || null,
        company: raw.company.trim() || null,
        status: raw.status,
        source: raw.source || undefined,
        assignedToId: raw.assignedToId,
        notes: raw.notes.trim() || null,
        customFields
      };
      this.api.update(id, req).subscribe({
        next: () => this.onSaved(id),
        error: (err) => this.onSaveError(err)
      });
    } else {
      const req: CreateLeadRequest = {
        firstName: raw.firstName.trim(),
        lastName: raw.lastName.trim(),
        email: raw.email.trim() || null,
        phone: raw.phone.trim() || null,
        company: raw.company.trim() || null,
        source: raw.source || 'MANUAL',
        notes: raw.notes.trim() || null,
        customFields
      };
      this.api.create(req).subscribe({
        next: (created) => this.onSaved(created.id),
        error: (err) => this.onSaveError(err)
      });
    }
  }

  private onSaved(id: string): void {
    this.submitting.set(false);
    this.errors.success('Lead guardado correctamente');
    void this.router.navigate(['/leads', id]);
  }

  private onSaveError(err: { error?: { message?: string }; message?: string }): void {
    this.submitting.set(false);
    const msg = err?.error?.message || err?.message || 'No se pudo guardar el lead';
    this.submitError.set(msg);
    this.errors.error(msg);
  }

  private buildCustomFields(): Record<string, unknown> | null {
    const rows = this.customFieldsArray.value;
    const trimmed: Record<string, unknown> = {};
    for (const r of rows) {
      const key = (r.key ?? '').trim();
      if (!key) {
        continue;
      }
      trimmed[key] = r.value ?? '';
    }
    return Object.keys(trimmed).length > 0 ? trimmed : null;
  }

  private loadAssignableUsers(): void {
    this.userApi.list({ page: 0, size: 200 }).subscribe({
      next: (res) => this.assignableUsers.set(res.content),
      error: () => {
        // Non-fatal: assignment dropdown just stays empty.
        this.assignableUsers.set([]);
      }
    });
  }

  private loadLead(id: string): void {
    this.loadingLead.set(true);
    this.loadError.set(null);
    firstValueFrom(this.api.getById(id))
      .then((lead) => {
        this.populateForm(lead);
        this.loadingLead.set(false);
      })
      .catch((err: { error?: { message?: string }; message?: string }) => {
        this.loadingLead.set(false);
        const msg = err?.error?.message || err?.message || 'No se pudo cargar el lead';
        this.loadError.set(msg);
      });
  }

  private populateForm(lead: LeadResponse): void {
    this.form.patchValue({
      firstName: lead.firstName,
      lastName: lead.lastName,
      email: lead.email ?? '',
      phone: lead.phone ?? '',
      company: lead.company ?? '',
      source: lead.source,
      status: lead.status,
      assignedToId: lead.assignedTo?.id ?? null,
      notes: lead.notes ?? '',
      doNotCall: lead.doNotCall
    });

    this.customFieldsArray.clear();
    if (lead.customFields) {
      for (const [k, v] of Object.entries(lead.customFields)) {
        this.customFieldsArray.push(
          this.fb.nonNullable.group({
            key: this.fb.nonNullable.control(k, [Validators.required]),
            value: this.fb.nonNullable.control(this.stringifyValue(v))
          })
        );
      }
    }
  }

  private stringifyValue(v: unknown): string {
    if (v === null || v === undefined) {
      return '';
    }
    if (typeof v === 'string') {
      return v;
    }
    return JSON.stringify(v);
  }
}