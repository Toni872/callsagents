import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { UserApi } from '../../../core/api/user.api';
import { ErrorService } from '../../../core/errors/error.service';
import {
  CreateUserRequest,
  UserListItem,
  UserRole
} from '../../../shared/models/user.model';

@Component({
  selector: 'app-user-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>Usuarios</h2>
          <p class="muted">Listado paginado contra <code>GET /api/users</code> (ADMIN).</p>
        </div>
        <div class="page__actions">
          <button
            class="secondary"
            type="button"
            (click)="reload()"
            [disabled]="loading()"
          >
            Recargar
          </button>
          <button
            type="button"
            (click)="openCreateDialog()"
            [disabled]="loading()"
          >
            + Crear usuario
          </button>
        </div>
      </header>

      <div class="card">
        <table>
          <thead>
            <tr>
              <th>Email</th>
              <th>Nombre completo</th>
              <th>Rol</th>
              <th>Estado</th>
              <th>Último login</th>
              <th class="actions-col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (user of users(); track user.id) {
              <tr>
                <td>{{ user.email }}</td>
                <td>{{ user.fullName }}</td>
                <td><span class="badge">{{ user.role }}</span></td>
                <td>
                  <span class="badge" [class.badge--disabled]="user.status === 'DISABLED'">
                    {{ user.status }}
                  </span>
                </td>
                <td>{{ formatDate(user.lastLoginAt) }}</td>
                <td class="actions-col">
                  <span class="muted">—</span>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="6" class="muted" style="text-align: center; padding: 2rem;">
                  @if (loading()) {
                    Cargando...
                  } @else {
                    Sin resultados.
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>

        <footer class="pager">
          <button
            class="secondary"
            type="button"
            [disabled]="page() === 0 || loading()"
            (click)="goTo(page() - 1)"
          >
            ← Anterior
          </button>
          <span>
            Página {{ page() + 1 }} de {{ totalPages() || 1 }}
            ({{ totalElements() }} usuarios)
          </span>
          <button
            class="secondary"
            type="button"
            [disabled]="page() + 1 >= totalPages() || loading()"
            (click)="goTo(page() + 1)"
          >
            Siguiente →
          </button>
        </footer>
      </div>
    </section>

    <dialog #createDialog class="dialog" (close)="onDialogClose()">
      <form
        class="dialog__form"
        [formGroup]="form"
        (ngSubmit)="onSubmit()"
        novalidate
      >
        <header class="dialog__header">
          <h3>Crear usuario</h3>
          <button
            type="button"
            class="dialog__close"
            (click)="closeCreateDialog()"
            aria-label="Cerrar"
          >
            ×
          </button>
        </header>

        <div class="dialog__body">
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
            <span class="field__label">Nombre completo</span>
            <input
              type="text"
              autocomplete="off"
              formControlName="fullName"
              [class.field__input--invalid]="isInvalid('fullName')"
            />
            @if (isInvalid('fullName')) {
              <small class="field__error">El nombre es obligatorio.</small>
            }
          </label>

          <label class="field">
            <span class="field__label">Contraseña</span>
            <input
              type="password"
              autocomplete="new-password"
              formControlName="password"
              [class.field__input--invalid]="isInvalid('password')"
            />
            @if (isInvalid('password')) {
              <small class="field__error">Mínimo 8 caracteres.</small>
            }
          </label>

          <label class="field">
            <span class="field__label">Rol</span>
            <select
              formControlName="role"
              [class.field__input--invalid]="isInvalid('role')"
            >
              <option value="" disabled>Seleccionar...</option>
              @for (r of availableRoles; track r) {
                <option [value]="r">{{ r }}</option>
              }
            </select>
            @if (isInvalid('role')) {
              <small class="field__error">Seleccioná un rol.</small>
            }
          </label>
        </div>

        <footer class="dialog__footer">
          <button
            type="button"
            class="secondary"
            (click)="closeCreateDialog()"
            [disabled]="submitting()"
          >
            Cancelar
          </button>
          <button type="submit" [disabled]="submitting() || form.invalid">
            @if (submitting()) {
              Creando...
            } @else {
              Crear
            }
          </button>
        </footer>
      </form>
    </dialog>
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
        align-items: center;
      }
      .pager {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--spacing-3);
        margin-top: var(--spacing-4);
      }
      .actions-col {
        text-align: right;
        width: 1%;
        white-space: nowrap;
      }
      .badge--disabled {
        background: var(--color-bg-alt);
        color: var(--color-text-muted);
      }

      .dialog {
        border: none;
        border-radius: var(--radius-lg);
        padding: 0;
        background: var(--color-surface);
        color: var(--color-text);
        box-shadow: var(--shadow-md);
        width: min(480px, 92vw);
      }
      .dialog::backdrop {
        background: rgba(15, 23, 42, 0.55);
      }
      .dialog__form {
        display: flex;
        flex-direction: column;
      }
      .dialog__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: var(--spacing-4) var(--spacing-6);
        border-bottom: 1px solid var(--color-border);
      }
      .dialog__header h3 {
        margin: 0;
        font-size: 1rem;
      }
      .dialog__close {
        background: transparent;
        color: var(--color-text-muted);
        border: none;
        padding: 0 var(--spacing-2);
        font-size: 1.25rem;
        line-height: 1;
      }
      .dialog__close:hover:not(:disabled) {
        background: transparent;
        color: var(--color-text);
      }
      .dialog__body {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-3);
        padding: var(--spacing-6);
      }
      .dialog__footer {
        display: flex;
        justify-content: flex-end;
        gap: var(--spacing-2);
        padding: var(--spacing-4) var(--spacing-6);
        border-top: 1px solid var(--color-border);
        background: var(--color-bg-alt);
      }

      .field {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-1);
      }
      .field__label {
        font-size: 0.8125rem;
        font-weight: 500;
        color: var(--color-text);
      }
      .field__error {
        color: var(--color-error);
        font-size: 0.75rem;
      }
      .field__input--invalid {
        border-color: var(--color-error);
      }
    `
  ]
})
export class UserListComponent implements OnInit {
  private readonly api = inject(UserApi);
  private readonly fb = inject(FormBuilder);
  private readonly errors = inject(ErrorService);

  protected readonly availableRoles: UserRole[] = ['ADMIN', 'SUPERVISOR', 'AGENT'];

  protected readonly form = this.fb.nonNullable.group({
    email: this.fb.nonNullable.control('', [Validators.required, Validators.email]),
    fullName: this.fb.nonNullable.control('', [Validators.required, Validators.minLength(1)]),
    password: this.fb.nonNullable.control('', [Validators.required, Validators.minLength(8)]),
    role: this.fb.nonNullable.control<UserRole | ''>('', [Validators.required])
  });

  protected readonly users = signal<UserListItem[]>([]);
  protected readonly loading = signal(false);
  protected readonly submitting = signal(false);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly pageSize = 20;

  @ViewChild('createDialog', { static: true })
  private readonly dialogRef!: ElementRef<HTMLDialogElement>;

  ngOnInit(): void {
    this.fetch();
  }

  protected reload(): void {
    this.page.set(0);
    this.fetch();
  }

  protected goTo(p: number): void {
    if (p < 0) {
      return;
    }
    this.page.set(p);
    this.fetch();
  }

  protected formatDate(value: string | null): string {
    if (!value) {
      return '—';
    }
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) {
      return '—';
    }
    return d.toLocaleString();
  }

  protected openCreateDialog(): void {
    this.form.reset({ email: '', fullName: '', password: '', role: '' });
    this.dialogRef.nativeElement.showModal();
  }

  protected closeCreateDialog(): void {
    if (this.dialogRef.nativeElement.open) {
      this.dialogRef.nativeElement.close();
    }
  }

  protected onDialogClose(): void {
    // No-op: form reset happens in openCreateDialog
  }

  protected isInvalid(controlName: 'email' | 'fullName' | 'password' | 'role'): boolean {
    const c = this.form.controls[controlName];
    return c.invalid && (c.dirty || c.touched);
  }

  protected onSubmit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const req: CreateUserRequest = {
      email: raw.email.trim(),
      fullName: raw.fullName.trim(),
      password: raw.password,
      role: raw.role as UserRole
    };

    this.submitting.set(true);
    this.api.create(req).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closeCreateDialog();
        this.errors.success('Usuario creado correctamente');
        this.page.set(0);
        this.fetch();
      },
      error: (err) => {
        this.submitting.set(false);
        const msg =
          err?.error?.message ||
          err?.message ||
          'No se pudo crear el usuario';
        this.errors.error(msg);
      }
    });
  }

  private fetch(): void {
    this.loading.set(true);
    this.api
      .list({ page: this.page(), size: this.pageSize })
      .subscribe({
        next: (res) => {
          this.users.set(res.content);
          this.totalPages.set(res.totalPages);
          this.totalElements.set(res.totalElements);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          const msg =
            err?.error?.message ||
            err?.message ||
            'Error al cargar usuarios';
          this.errors.error(msg);
        }
      });
  }
}
