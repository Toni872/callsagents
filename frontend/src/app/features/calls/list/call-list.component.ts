import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CallApi } from '../../../core/api/call.api';
import { CallResponse } from '../../../shared/models/call.model';

@Component({
  selector: 'app-call-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>Llamadas</h2>
          <p class="muted">Listado paginado contra <code>GET /api/calls</code>.</p>
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
              <th>Estado</th>
              <th>Outcome</th>
              <th>Inicio</th>
              <th>Duración (s)</th>
              <th>Notas</th>
            </tr>
          </thead>
          <tbody>
            @for (c of calls(); track c.id) {
              <tr>
                <td><code>{{ c.leadId | slice: 0 : 8 }}</code></td>
                <td><span class="badge">{{ c.status }}</span></td>
                <td>{{ c.outcome || '—' }}</td>
                <td>{{ c.startedAt ? (c.startedAt | date: 'short') : '—' }}</td>
                <td>{{ c.durationSeconds ?? '—' }}</td>
                <td>{{ c.notes || '—' }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="6" class="muted" style="text-align: center; padding: 2rem;">
                  Sin llamadas registradas.
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
export class CallListComponent implements OnInit {
  private readonly api = inject(CallApi);

  protected readonly calls = signal<CallResponse[]>([]);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.fetch();
  }

  protected fetch(): void {
    this.errorMessage.set(null);
    this.api.list({ page: 0, size: 20 }).subscribe({
      next: (res) => this.calls.set(res.content),
      error: (err) => this.errorMessage.set(err?.error?.message || err?.message || 'Error al cargar')
    });
  }
}
