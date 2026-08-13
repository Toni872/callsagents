import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AppointmentApi } from '../../../core/api/appointment.api';
import { LeadApi } from '../../../core/api/lead.api';
import { UserApi } from '../../../core/api/user.api';
import { ErrorService } from '../../../core/errors/error.service';
import { AuthService } from '../../../core/auth/auth.service';
import { AppointmentResponse } from '../../../shared/models/appointment.model';

@Component({
  selector: 'app-appointment-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>{{ appointment() ? 'Detalle de cita' : 'Cargando...' }}</h2>
          @if (appointment(); as a) {
            <p class="muted">
              <span class="badge">{{ a.status }}</span>
              · {{ formatDate(a.scheduledAt) }} ({{ a.durationMinutes }} min)
            </p>
          }
        </div>
        <div class="page__actions">
          <a class="secondary" [routerLink]="['/appointments']">← Volver</a>
          @if (appointment()) {
            <a class="secondary" [routerLink]="['/appointments', appointment()!.id, 'edit']">Editar</a>
          }
          @if (canDelete() && appointment()) {
            <button
              type="button"
              class="danger"
              (click)="onDelete()"
              [disabled]="deleting()"
            >
              @if (deleting()) {
                Eliminando...
              } @else {
                Eliminar
              }
            </button>
          }
        </div>
      </header>

      @if (appointment(); as a) {
          <div class="card detail">
            <dl class="detail__grid">
              <dt>Estado</dt>
              <dd><span class="badge">{{ a.status }}</span></dd>

              <dt>Fecha y hora</dt>
              <dd>{{ formatDate(a.scheduledAt) }}</dd>

              <dt>Duración</dt>
              <dd>{{ a.durationMinutes }} min</dd>

              <dt>Lead</dt>
              <dd>
                @if (leadLabel()) {
                  {{ leadLabel() }}
                } @else {
                  <span class="muted">cargando...</span>
                }
              </dd>

              <dt>Agente</dt>
              <dd>
                @if (userLabel()) {
                  {{ userLabel() }}
                } @else {
                  <span class="muted">cargando...</span>
                }
              </dd>

              @if (a.externalEventUrl) {
                <dt>Calendario</dt>
                <dd>
                  <a
                    class="calendar-link"
                    [href]="a.externalEventUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                  >Ver en Google Calendar</a>
                </dd>
              }

              <dt>Notas</dt>
              <dd class="detail__multiline">{{ a.notes || '—' }}</dd>

              <dt>Creada</dt>
              <dd>{{ formatDate(a.createdAt) }}</dd>

              <dt>Actualizada</dt>
              <dd>{{ formatDate(a.updatedAt) }}</dd>
            </dl>
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
      .page__actions {
        display: flex;
        gap: var(--spacing-2);
        align-items: center;
      }
      .detail {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-6);
      }
      .detail__grid {
        display: grid;
        grid-template-columns: 180px 1fr;
        gap: var(--spacing-3) var(--spacing-4);
        margin: 0;
      }
      .detail__grid dt {
        font-weight: 500;
        color: var(--color-text-muted);
      }
      .detail__grid dd {
        margin: 0;
        word-break: break-word;
      }
      .detail__multiline {
        white-space: pre-wrap;
      }
      .calendar-link {
        color: var(--color-primary);
        font-weight: 500;
        text-decoration: none;
      }
      .calendar-link:hover {
        text-decoration: underline;
      }
      @media (max-width: 720px) {
        .detail__grid {
          grid-template-columns: 1fr;
        }
        .detail__grid dt {
          margin-top: var(--spacing-2);
        }
      }
      .danger {
        background: var(--color-error);
        color: white;
      }
      .danger:hover:not(:disabled) {
        background: var(--color-error-bg);
        color: var(--color-error);
      }
    `
  ]
})
export class AppointmentDetailComponent implements OnInit {
  private readonly api = inject(AppointmentApi);
  private readonly leadApi = inject(LeadApi);
  private readonly userApi = inject(UserApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly errors = inject(ErrorService);

  protected readonly appointment = signal<AppointmentResponse | null>(null);
  protected readonly deleting = signal(false);
  protected readonly leadLabel = signal<string | null>(null);
  protected readonly userLabel = signal<string | null>(null);

  protected readonly canDelete = (): boolean => this.auth.currentRole() === 'ADMIN';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errors.error('ID de cita no proporcionado');
      return;
    }
    this.load(id);
  }

  protected formatDate(value: string | null): string {
    if (!value) {
      return '—';
    }
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) {
      return value;
    }
    return d.toLocaleString();
  }

  protected onDelete(): void {
    const a = this.appointment();
    if (!a || this.deleting()) {
      return;
    }
    const confirmed = confirm('¿Eliminar esta cita? Esta acción no se puede deshacer.');
    if (!confirmed) {
      return;
    }
    this.deleting.set(true);
    this.api.delete(a.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.errors.success('Cita eliminada');
        void this.router.navigate(['/appointments']);
      },
      error: () => {
        this.deleting.set(false);
        // Toast shown by errorInterceptor
      }
    });
  }

  private load(id: string): void {
    firstValueFrom(this.api.getById(id))
      .then((a) => {
        this.appointment.set(a);
        this.loadLabels(a.leadId, a.userId);
      })
      .catch(() => {
        // Toast shown by errorInterceptor
      });
  }

  private loadLabels(leadId: string, userId: string): void {
    firstValueFrom(this.leadApi.getById(leadId))
      .then((l) => this.leadLabel.set(`${l.firstName} ${l.lastName}${l.email ? ' (' + l.email + ')' : ''}`))
      .catch(() => this.leadLabel.set(null));

    firstValueFrom(this.userApi.list({ page: 0, size: 200 }))
      .then((res) => {
        const u = res.content.find((x) => x.id === userId);
        if (u) {
          this.userLabel.set(`${u.fullName} (${u.email})`);
        } else {
          this.userLabel.set(null);
        }
      })
      .catch(() => this.userLabel.set(null));
  }
}