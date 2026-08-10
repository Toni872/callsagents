import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppointmentApi } from '../../../core/api/appointment.api';
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
          <a [routerLink]="['/appointments', 'new']">+ Nueva cita</a>
        </div>
      </header>

      @if (errorMessage()) {
        <div class="card error-text">Error: {{ errorMessage() }}</div>
      }

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
    `
  ]
})
export class AppointmentListComponent implements OnInit {
  private readonly api = inject(AppointmentApi);

  protected readonly appointments = signal<AppointmentResponse[]>([]);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly loading = signal(false);

  ngOnInit(): void {
    this.fetch();
  }

  protected fetch(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.list({ page: 0, size: 20 }).subscribe({
      next: (res) => {
        this.appointments.set(res.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err?.error?.message || err?.message || 'Error al cargar');
      }
    });
  }

  protected shortId(uuid: string): string {
    return uuid.length > 8 ? uuid.substring(0, 8) + '…' : uuid;
  }
}