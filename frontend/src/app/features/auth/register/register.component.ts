import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

declare const google: any;

@Component({
  selector: 'app-register',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="login-page">
      <form
        class="card login-card"
        [formGroup]="form"
        (ngSubmit)="onSubmit()"
        novalidate
      >
        <h1 class="login-card__title">Crea tu cuenta</h1>
        <p class="login-card__subtitle muted">
          7 días de prueba gratuita. Sin tarjeta.
        </p>

        <div #googleBtn class="google-btn-container"></div>

        <div class="divider">
          <span>o</span>
        </div>

        @if (serverError()) {
          <p class="error-text" role="alert">{{ serverError() }}</p>
        }

        <div class="login-card__field">
          <label for="fullName">Nombre completo</label>
          <input
            id="fullName"
            type="text"
            formControlName="fullName"
            autocomplete="name"
            placeholder="Ana García"
          />
          @if (form.controls.fullName.touched && form.controls.fullName.errors) {
            <p class="error-text">Introduce tu nombre.</p>
          }
        </div>

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
            autocomplete="new-password"
            placeholder="Mínimo 8 caracteres"
          />
          @if (form.controls.password.touched && form.controls.password.errors) {
            <p class="error-text">
              La contraseña debe tener al menos 8 caracteres.
            </p>
          }
        </div>

        <button type="submit" [disabled]="form.invalid || loading()">
          {{ loading() ? 'Creando cuenta…' : 'Crear cuenta y empezar' }}
        </button>

        <p class="login-card__footer muted">
          ¿Ya tienes cuenta?
          <a routerLink="/login" class="login-card__link">Inicia sesión</a>
        </p>
      </form>
    </div>
  `,
  styles: [
    `
      .login-page {
        min-height: 100vh;
        display: grid;
        place-items: center;
        background: linear-gradient(135deg, #003d26 0%, #0f172a 100%);
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
      .login-card__footer {
        margin: 0;
        text-align: center;
        font-size: 0.875rem;
      }
      .login-card__link {
        color: var(--color-primary);
        text-decoration: none;
        font-weight: 600;
      }
      .login-card__link:hover {
        text-decoration: underline;
      }
      .google-btn-container {
        display: flex;
        justify-content: center;
      }
      .divider {
        display: flex;
        align-items: center;
        gap: 12px;
        color: var(--color-text-muted, #6b7280);
        font-size: 0.875rem;
      }
      .divider::before,
      .divider::after {
        content: '';
        flex: 1;
        border-bottom: 1px solid var(--color-border, #374151);
      }
    `
  ]
})
export class RegisterComponent implements AfterViewInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  @ViewChild('googleBtn') googleBtn!: ElementRef;

  protected readonly loading = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  ngAfterViewInit(): void {
    this.initGoogle();
  }

  private initGoogle(): void {
    if (typeof google === 'undefined' || !google.accounts?.id) {
      setTimeout(() => this.initGoogle(), 500);
      return;
    }

    google.accounts.id.initialize({
      client_id: '557204149721-ounh5q7phakk0ln1auer314bi9rj839v.apps.googleusercontent.com',
      callback: (response: any) => this.handleGoogleResponse(response)
    });

    google.accounts.id.renderButton(this.googleBtn.nativeElement, {
      theme: 'outline',
      size: 'large',
      width: 380,
      text: 'continue_with',
      locale: 'es'
    });
  }

  private handleGoogleResponse(response: any): void {
    this.loading.set(true);
    this.auth.googleLogin(response.credential).subscribe({
      next: () => this.loading.set(false),
      error: () => this.loading.set(false)
    });
  }

  protected onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.serverError.set(null);
    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => this.loading.set(false),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        if (err.status === 400) {
          const body = err.error as { message?: string } | null;
          this.serverError.set(
            body?.message ?? 'No se pudo crear la cuenta. Inténtalo de nuevo.'
          );
        }
      }
    });
  }
}