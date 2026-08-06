import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { CalendarApi } from '../../../core/api/calendar.api';
import { ErrorService } from '../../../core/errors/error.service';
import {
  CalendarIntegration,
  CalendarSyncStatus
} from '../../../shared/models/calendar.model';

@Component({
  selector: 'app-calendar-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>Integraciones de calendario</h2>
          <p class="muted">
            Conecta tu calendario para que las citas se sincronicen automáticamente.
          </p>
        </div>
        <button
          type="button"
          class="secondary"
          (click)="load()"
          [disabled]="loading()"
        >
          Recargar
        </button>
      </header>

      @if (!googleConfigured()) {
        <div class="card warning-panel" role="alert">
          <h3>Google Calendar no está configurado</h3>
          <p class="muted">
            Google Calendar no está configurado en este servidor. Configurá las
            variables de entorno <code>GOOGLE_CLIENT_ID</code> y
            <code>GOOGLE_CLIENT_SECRET</code> (ver RUNBOOK).
          </p>
        </div>
      }

      <h3 class="section-title">Proveedores</h3>
      <div class="providers-grid">
        <article class="provider-card">
          <div class="provider-card__head">
            <span class="provider-card__icon provider-card__icon--google" aria-hidden="true">G</span>
            <div>
              <h3 class="provider-card__title">Google Calendar</h3>
              <p class="provider-card__desc">
                Sincroniza tus citas con Google Calendar en tiempo real.
              </p>
            </div>
          </div>
          @if (googleIntegration(); as g) {
            <span class="badge badge--ok">Conectado</span>
            <button
              type="button"
              class="btn btn--danger"
              (click)="disconnect(g)"
            >
              Desconectar
            </button>
          } @else {
            <button
              type="button"
              class="btn btn--primary"
              [disabled]="!googleConfigured()"
              (click)="connectGoogle()"
            >
              Conectar Google Calendar
            </button>
          }
        </article>

        <article class="provider-card provider-card--disabled">
          <div class="provider-card__head">
            <span class="provider-card__icon provider-card__icon--outlook" aria-hidden="true">O</span>
            <div>
              <h3 class="provider-card__title">Microsoft Outlook</h3>
              <p class="provider-card__desc">
                Próximamente. La integración con Outlook Calendar estará disponible pronto.
              </p>
            </div>
          </div>
          <button type="button" class="btn" disabled>Próximamente</button>
        </article>
      </div>

      <h3 class="section-title">Cuentas conectadas</h3>
      @if (loading() && integrations().length === 0) {
        <div class="empty-state">Cargando integraciones...</div>
      } @else if (integrations().length === 0) {
        <div class="empty-state">
          No hay cuentas de calendario conectadas. Conectá Google Calendar para empezar.
        </div>
      } @else {
        <div class="integrations-list">
          @for (integration of integrations(); track integration.id) {
            <div class="integration-item">
              <div class="integration-item__meta">
                <div class="integration-item__email">
                  <span class="integration-item__provider">{{ integration.provider }}</span>
                  {{ integration.externalAccountEmail || '(sin email)' }}
                  <span class="badge" [class]="badgeClass(integration.lastSyncStatus)">
                    {{ integration.lastSyncStatus || 'NUNCA' }}
                  </span>
                </div>
                <div class="integration-item__status">
                  {{ integration.syncEnabled ? 'Sincronización activada' : 'Sincronización desactivada' }}
                  · Última sync: {{ integration.lastSyncAt ? (integration.lastSyncAt | date: 'short') : 'Nunca' }}
                </div>
                @if (integration.lastSyncError) {
                  <div class="integration-item__error">
                    {{ integration.lastSyncError }}
                  </div>
                }
              </div>
              <div class="integration-item__actions">
                <button
                  type="button"
                  class="btn"
                  (click)="toggle(integration)"
                >
                  {{ integration.syncEnabled ? 'Pausar sync' : 'Reanudar sync' }}
                </button>
                <button
                  type="button"
                  class="btn btn--danger"
                  (click)="disconnect(integration)"
                >
                  Desconectar
                </button>
              </div>
            </div>
          }
        </div>
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
      .section-title {
        margin: 0;
        font-size: 1rem;
        font-weight: 600;
      }
      .warning-panel {
        border-color: var(--color-warning);
        background: var(--color-bg-alt);
      }
      .warning-panel h3 {
        margin: 0 0 var(--spacing-2);
        font-size: 0.95rem;
        color: var(--color-warning);
      }
      .warning-panel p {
        margin: 0;
      }

      .providers-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
        gap: var(--spacing-4);
      }

      .provider-card {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: var(--spacing-4);
        box-shadow: var(--shadow-sm);
        display: flex;
        flex-direction: column;
        gap: var(--spacing-3);
      }
      .provider-card--disabled {
        opacity: 0.7;
      }
      .provider-card__head {
        display: flex;
        gap: var(--spacing-3);
        align-items: flex-start;
      }
      .provider-card__icon {
        width: 40px;
        height: 40px;
        border-radius: var(--radius);
        display: grid;
        place-items: center;
        font-weight: 700;
        font-size: 1.1rem;
        color: #fff;
        flex-shrink: 0;
      }
      .provider-card__icon--google {
        background: #4285f4;
      }
      .provider-card__icon--outlook {
        background: #0078d4;
      }
      .provider-card__title {
        margin: 0 0 var(--spacing-1);
        font-size: 1rem;
      }
      .provider-card__desc {
        margin: 0;
        color: var(--color-text-muted);
        font-size: 0.875rem;
      }

      .integrations-list {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-3);
      }
      .integration-item {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: var(--spacing-4);
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--spacing-4);
        flex-wrap: wrap;
      }
      .integration-item__meta {
        flex: 1;
        min-width: 0;
      }
      .integration-item__email {
        font-weight: 500;
        display: flex;
        align-items: center;
        gap: var(--spacing-2);
        flex-wrap: wrap;
      }
      .integration-item__provider {
        font-size: 0.7rem;
        font-weight: 600;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: var(--color-text-muted);
        background: var(--color-bg-alt);
        padding: 2px var(--spacing-2);
        border-radius: var(--radius);
      }
      .integration-item__status {
        font-size: 0.875rem;
        color: var(--color-text-muted);
        margin-top: var(--spacing-1);
      }
      .integration-item__error {
        font-size: 0.75rem;
        color: var(--color-error);
        margin-top: var(--spacing-1);
      }
      .integration-item__actions {
        display: flex;
        gap: var(--spacing-2);
        flex-wrap: wrap;
      }

      .badge--ok {
        background: var(--color-success-bg);
        color: var(--color-success);
      }
      .badge--warn {
        background: #fef3c7;
        color: var(--color-warning);
      }
      .badge--err {
        background: var(--color-error-bg);
        color: var(--color-error);
      }

      .empty-state {
        padding: var(--spacing-6);
        text-align: center;
        color: var(--color-text-muted);
        background: var(--color-bg-alt);
        border-radius: var(--radius-lg);
      }

      .btn {
        padding: var(--spacing-2) var(--spacing-4);
        border-radius: var(--radius);
        border: 1px solid var(--color-border-strong);
        background: var(--color-surface);
        color: var(--color-text);
        cursor: pointer;
        font-size: 0.875rem;
      }
      .btn:hover:not(:disabled) {
        background: var(--color-bg-alt);
      }
      .btn:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .btn--primary {
        background: var(--color-primary);
        color: #fff;
        border-color: var(--color-primary);
      }
      .btn--primary:hover:not(:disabled) {
        background: var(--color-primary-hover);
        border-color: var(--color-primary-hover);
      }
      .btn--danger {
        background: var(--color-error);
        color: #fff;
        border-color: var(--color-error);
      }
      .btn--danger:hover:not(:disabled) {
        background: #b91c1c;
        border-color: #b91c1c;
      }
    `
  ]
})
export class CalendarSettingsComponent implements OnInit {
  private readonly api = inject(CalendarApi);
  private readonly error = inject(ErrorService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly integrations = signal<CalendarIntegration[]>([]);
  protected readonly loading = signal(false);
  protected readonly googleConfigured = signal(true);

  protected readonly googleIntegration = computed(
    () => this.integrations().find((i) => i.provider === 'GOOGLE') ?? null
  );

  ngOnInit(): void {
    this.handleOAuthCallback();
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (list) => {
        this.integrations.set(list);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        if (err.status === 503) {
          this.googleConfigured.set(false);
        }
        // errorInterceptor ya muestra el toast; el componente solo
        // agrega el contexto adicional (panel de Google no configurado).
      }
    });
  }

  protected connectGoogle(): void {
    window.location.href = this.api.startConnectUrl('GOOGLE');
  }

  protected toggle(integration: CalendarIntegration): void {
    this.api.toggleSync(integration.id).subscribe({
      next: (updated) => {
        this.integrations.update((list) =>
          list.map((i) => (i.id === updated.id ? updated : i))
        );
        this.error.success(
          updated.syncEnabled ? 'Sync activado' : 'Sync pausado'
        );
      }
      // errorInterceptor maneja el toast de error
    });
  }

  protected disconnect(integration: CalendarIntegration): void {
    const ok = confirm(
      `¿Desconectar ${integration.provider}? Las citas futuras NO se sincronizarán.`
    );
    if (!ok) {
      return;
    }
    this.api.disconnect(integration.id).subscribe({
      next: () => {
        this.integrations.update((list) =>
          list.filter((i) => i.id !== integration.id)
        );
        this.error.success('Desconectado');
      }
      // errorInterceptor maneja el toast de error
    });
  }

  protected badgeClass(status: CalendarSyncStatus | string | null): string {
    switch (status) {
      case 'SYNCED':
        return 'badge--ok';
      case 'PENDING':
        return 'badge--warn';
      case 'FAILED':
        return 'badge--err';
      default:
        return '';
    }
  }

  private handleOAuthCallback(): void {
    const params = this.route.snapshot.queryParamMap;
    const status = params.get('status');
    const reason = params.get('reason');

    if (status === 'connected') {
      this.error.success('Calendar conectado correctamente');
    } else if (status === 'error') {
      this.error.error(
        `No se pudo conectar: ${reason ?? 'error desconocido'}`
      );
    }

    if (status) {
      // Quitamos los query params para que un refresh no vuelva a mostrar el toast.
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: {},
        replaceUrl: true
      });
    }
  }
}
