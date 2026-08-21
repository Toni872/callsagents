import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppointmentApi } from '../../../core/api/appointment.api';
import { AuthService } from '../../../core/auth/auth.service';
import { AppointmentResponse } from '../../../shared/models/appointment.model';

@Component({
  selector: 'app-appointment-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>Citas</h2>
          <p class="muted">Listado paginado contra <code>GET /api/appointments</code>.</p>
        </div>
        <div class="page__actions">
          <button type="button" class="secondary" (click)="fetch()">Recargar</button>
          <a
            [routerLink]="trialLocked() ? null : ['/appointments', 'new']"
            [title]="trialLocked() ? 'Disponible al contratar' : undefined"
            [attr.aria-disabled]="trialLocked()"
            [class.is-locked]="trialLocked()"
          >+ Nueva cita</a>
        </div>
      </header>

      <div class="card">
        <table>
          <thead>
            <tr>
              <th>Fecha y hora</th>
              <th>Estado</th>
              <th>Duración</th>
              <th>Lead</th>
              <th>Agente</th>
              <th>Notas</th>
              <th class="actions-col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (a of appointments(); track a.id) {
              <tr>
                <td>{{ a.scheduledAt | date: 'short' }}</td>
                <td><span class="badge">{{ a.status }}</span></td>
                <td>{{ a.durationMinutes }} min</td>
                <td class="uuid" [title]="a.leadId">{{ shortId(a.leadId) }}</td>
                <td class="uuid" [title]="a.userId">{{ shortId(a.userId) }}</td>
                <td>{{ a.notes || '—' }}</td>
                <td class="actions-col">
                  <a [routerLink]="['/appointments', a.id]">Ver</a>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="7" class="muted" style="text-align: center; padding: 2rem;">
                  @if (loading()) {
                    Cargando...
                  } @else {
                    Sin citas agendadas.
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
            ({{ totalElements() }} citas)
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
      .uuid {
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        font-size: 0.8125rem;
      }
      a {
        color: var(--color-primary);
        text-decoration: none;
      }
      a:hover {
        text-decoration: underline;
      }
      a.is-locked {
        opacity: 0.5;
        cursor: not-allowed;
        text-decoration: none;
      }
    `
  ]
})
export class AppointmentListComponent implements OnInit {
  private readonly api = inject(AppointmentApi);
  private readonly auth = inject(AuthService);

  protected readonly trialLocked = this.auth.isTrialExpired;

  protected readonly appointments = signal<AppointmentResponse[]>([]);
  protected readonly loading = signal(false);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly pageSize = 20;

  ngOnInit(): void {
    this.fetch();
  }

  protected goTo(p: number): void {
    if (p < 0) {
      return;
    }
    this.page.set(p);
    this.fetch();
  }

  protected fetch(): void {
    this.loading.set(true);
    this.api.list({ page: this.page(), size: this.pageSize }).subscribe({
      next: (res) => {
        this.appointments.set(res.content);
        this.totalPages.set(res.totalPages);
        this.totalElements.set(res.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        // Toast shown by errorInterceptor
      }
    });
  }

  protected shortId(uuid: string): string {
    return uuid.length > 8 ? uuid.substring(0, 8) + '…' : uuid;
  }
}