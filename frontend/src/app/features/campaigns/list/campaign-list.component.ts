import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CampaignApi } from '../../../core/api/campaign.api';
import { CampaignResponse } from '../../../shared/models/campaign.model';

@Component({
  selector: 'app-campaign-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>Campañas</h2>
          <p class="muted">Listado paginado contra <code>GET /api/campaigns</code>.</p>
        </div>
        <div class="page__actions">
          <button type="button" class="secondary" (click)="fetch()">Recargar</button>
          <a [routerLink]="['/campaigns', 'new']">+ Nueva campaña</a>
        </div>
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
              <th class="actions-col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (c of campaigns(); track c.id) {
              <tr>
                <td>
                  <a [routerLink]="['/campaigns', c.id]">{{ c.name }}</a>
                </td>
                <td><span class="badge">{{ c.status }}</span></td>
                <td>{{ c.startAt ? (c.startAt | date: 'short') : '—' }}</td>
                <td>{{ c.endAt ? (c.endAt | date: 'short') : '—' }}</td>
                <td>{{ c.createdBy?.fullName || '—' }}</td>
                <td>{{ c.createdAt | date: 'short' }}</td>
                <td class="actions-col">
                  <a [routerLink]="['/campaigns', c.id]">Ver</a>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="7" class="muted" style="text-align: center; padding: 2rem;">
                  @if (loading()) {
                    Cargando...
                  } @else {
                    Sin campañas.
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
export class CampaignListComponent implements OnInit {
  private readonly api = inject(CampaignApi);

  protected readonly campaigns = signal<CampaignResponse[]>([]);
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
        this.campaigns.set(res.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err?.error?.message || err?.message || 'Error al cargar');
      }
    });
  }
}