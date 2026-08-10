import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CallApi } from '../../../core/api/call.api';
import { CallResponse } from '../../../shared/models/call.model';

@Component({
  selector: 'app-call-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>Llamadas</h2>
          <p class="muted">Listado paginado contra <code>GET /api/calls</code>.</p>
        </div>
        <div class="page__actions">
          <button type="button" class="secondary" (click)="fetch()">Recargar</button>
          <a [routerLink]="['/calls', 'new']">+ Nueva llamada</a>
        </div>
      </header>

      @if (errorMessage()) {
        <div class="card error-text">Error: {{ errorMessage() }}</div>
      }

      <div class="card">
        <table>
          <thead>
            <tr>
              <th>Inicio</th>
              <th>Estado</th>
              <th>Resultado</th>
              <th>Duración</th>
              <th>Lead</th>
              <th>Agente</th>
              <th class="actions-col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (c of calls(); track c.id) {
              <tr>
                <td>{{ c.startedAt ? (c.startedAt | date: 'short') : '—' }}</td>
                <td><span class="badge">{{ c.status }}</span></td>
                <td>
                  @if (c.outcome) {
                    <span class="badge badge--outcome">{{ c.outcome }}</span>
                  } @else {
                    —
                  }
                </td>
                <td>{{ formatDuration(c.durationSeconds) }}</td>
                <td class="uuid" [title]="c.leadId">{{ shortId(c.leadId) }}</td>
                <td class="uuid" [title]="c.userId">{{ shortId(c.userId) }}</td>
                <td class="actions-col">
                  <a [routerLink]="['/calls', c.id]">Ver</a>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="7" class="muted" style="text-align: center; padding: 2rem;">
                  @if (loading()) {
                    Cargando...
                  } @else {
                    Sin llamadas.
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
      .badge--outcome {
        background: var(--color-info-bg);
        color: var(--color-info);
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
export class CallListComponent implements OnInit {
  private readonly api = inject(CallApi);

  protected readonly calls = signal<CallResponse[]>([]);
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
        this.calls.set(res.content);
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

  protected formatDuration(seconds: number | null): string {
    if (seconds === null || seconds === undefined) {
      return '—';
    }
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}m ${s.toString().padStart(2, '0')}s`;
  }
}