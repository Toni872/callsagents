import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="login-page">
      <form
        class="card login-card"
        [formGroup]="form"
        (ngSubmit)="onSubmit()"
        novalidate
      >
        <h1 class="login-card__title">Callsagents</h1>
        <p class="login-card__subtitle muted">
          Inicia sesión para acceder al panel.
        </p>

        <div class="login-card__field">
          <label for="email">Email</label>
          <input
            id="email"
            type="email"
            formControlName="email"
            autocomplete="username"
            placeholder="tu&#64;empresa.com"
          />
          @if (form.controls.email.touched && form.controls.email.errors) {
            <p class="error-text">Email inválido.</p>
          }
        </div>

        <div class="login-card__field">
          <label for="password">Contraseña</label>
          <input
            id="password"
            type="password"
            formControlName="password"
            autocomplete="current-password"
            placeholder="Mínimo 8 caracteres"
          />
          @if (form.controls.password.touched && form.controls.password.errors) {
            <p class="error-text">
              La contraseña debe tener al menos 8 caracteres.
            </p>
          }
        </div>

        <button type="submit" [disabled]="form.invalid || loading()">
          {{ loading() ? 'Iniciando sesión…' : 'Iniciar sesión' }}
        </button>
      </form>
    </div>
  `,
  styles: [
    `
      .login-page {
        min-height: 100vh;
        display: grid;
        place-items: center;
        background: linear-gradient(135deg, #1e3a8a 0%, #0f172a 100%);
        padding: var(--spacing-4);
      }
      .login-card {
        width: 100%;
        max-width: 380px;
        display: flex;
        flex-direction: column;
        gap: var(--spacing-4);
      }
      .login-card__title {
        margin: 0;
        font-size: 1.5rem;
        text-align: center;
      }
      .login-card__subtitle {
        margin: 0;
        text-align: center;
      }
      .login-card__field {
        display: flex;
        flex-direction: column;
      }
    `
  ]
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  protected readonly loading = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  protected onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    const redirect =
      this.route.snapshot.queryParamMap.get('redirect') ?? undefined;
    // AuthService.login() does the navigation on success.
    // errorInterceptor already shows a toast on failure — no need to duplicate.
    this.auth.login(this.form.getRawValue(), redirect).subscribe({
      next: () => this.loading.set(false),
      error: () => this.loading.set(false)
    });
  }
}
