import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CampaignApi } from '../../../core/api/campaign.api';
import { CampaignResponse } from '../../../shared/models/campaign.model';

@Component({
  selector: 'app-campaign-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>Campañas</h2>
          <p class="muted">Listado paginado contra <code>GET /api/campaigns</code>.</p>
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
              <th>Nombre</th>
              <th>Estado</th>
              <th>Inicio</th>
              <th>Fin</th>
              <th>Creada por</th>
              <th>Creada</th>
            </tr>
          </thead>
          <tbody>
            @for (c of campaigns(); track c.id) {
              <tr>
                <td>{{ c.name }}</td>
                <td><span class="badge">{{ c.status }}</span></td>
                <td>{{ c.startAt ? (c.startAt | date: 'short') : '—' }}</td>
                <td>{{ c.endAt ? (c.endAt | date: 'short') : '—' }}</td>
                <td>{{ c.createdBy?.fullName || '—' }}</td>
                <td>{{ c.createdAt | date: 'short' }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="6" class="muted" style="text-align: center; padding: 2rem;">
                  Sin campañas.
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
export class CampaignListComponent implements OnInit {
  private readonly api = inject(CampaignApi);

  protected readonly campaigns = signal<CampaignResponse[]>([]);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.fetch();
  }

  protected fetch(): void {
    this.errorMessage.set(null);
    this.api.list({ page: 0, size: 20 }).subscribe({
      next: (res) => this.campaigns.set(res.content),
      error: (err) => this.errorMessage.set(err?.error?.message || err?.message || 'Error al cargar')
    });
  }
}
