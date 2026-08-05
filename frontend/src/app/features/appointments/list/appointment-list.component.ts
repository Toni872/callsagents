import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AppointmentApi } from '../../../core/api/appointment.api';
import { AppointmentResponse } from '../../../shared/models/appointment.model';

@Component({
  selector: 'app-appointment-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>Citas</h2>
          <p class="muted">Listado paginado contra <code>GET /api/appointments</code>.</p>
        </div>
        <button type="button" (click)="fetch()">Recargar</button>
      </header>

      @if (errorMessage()) {
        <div class="card error-text">Error: {{ errorMessage() }}</div>
      }

      <div class="card">
        <table>
          <thead>
            <tr>
              <th>Lead</th>
              <th>Agendada</th>
              <th>Duración (min)</th>
              <th>Estado</th>
              <th>Notas</th>
            </tr>
          </thead>
          <tbody>
            @for (a of appointments(); track a.id) {
              <tr>
                <td><code>{{ a.leadId | slice: 0 : 8 }}</code></td>
                <td>{{ a.scheduledAt | date: 'short' }}</td>
                <td>{{ a.durationMinutes }}</td>
                <td><span class="badge">{{ a.status }}</span></td>
                <td>{{ a.notes || '—' }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="muted" style="text-align: center; padding: 2rem;">
                  Sin citas agendadas.
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
      }
    `
  ]
})
export class AppointmentListComponent implements OnInit {
  private readonly api = inject(AppointmentApi);

  protected readonly appointments = signal<AppointmentResponse[]>([]);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.fetch();
  }

  protected fetch(): void {
    this.errorMessage.set(null);
    this.api.list({ page: 0, size: 20 }).subscribe({
      next: (res) => this.appointments.set(res.content),
      error: (err) => this.errorMessage.set(err?.error?.message || err?.message || 'Error al cargar')
    });
  }
}
