import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LeadApi } from '../../../core/api/lead.api';
import { LeadResponse } from '../../../shared/models/lead.model';

@Component({
  selector: 'app-lead-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>Leads</h2>
          <p class="muted">Listado paginado contra <code>GET /api/leads</code>.</p>
        </div>
        <div class="page__actions">
          <input
            type="search"
            placeholder="Buscar..."
            [formControl]="searchControl"
            (keyup.enter)="onSearch()"
          />
          <button class="secondary" type="button" (click)="onSearch()">Buscar</button>
          <button type="button" (click)="reload()">Recargar</button>
          <a [routerLink]="['/leads', 'new']">+ Nuevo lead</a>
        </div>
      </header>

      <div class="card">
        <table>
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Email</th>
              <th>Teléfono</th>
              <th>Empresa</th>
              <th>Estado</th>
              <th>Origen</th>
              <th>Asignado a</th>
              <th class="actions-col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (lead of leads(); track lead.id) {
              <tr>
                <td>
                  <a [routerLink]="['/leads', lead.id]">
                    {{ lead.firstName }} {{ lead.lastName }}
                  </a>
                </td>
                <td>{{ lead.email || '—' }}</td>
                <td>{{ lead.phone || '—' }}</td>
                <td>{{ lead.company || '—' }}</td>
                <td><span class="badge">{{ lead.status }}</span></td>
                <td>{{ lead.source }}</td>
                <td>{{ lead.assignedTo?.fullName || '—' }}</td>
                <td class="actions-col">
                  <a [routerLink]="['/leads', lead.id]">Ver</a>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="8" class="muted" style="text-align: center; padding: 2rem;">
                  Sin resultados.
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
          <span>Página {{ page() + 1 }} de {{ totalPages() || 1 }} ({{ totalElements() }} items)</span>
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
      .page__actions input[type='search'] {
        width: 220px;
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
export class LeadListComponent implements OnInit {
  private readonly api = inject(LeadApi);

  protected readonly searchControl = new FormControl<string>('', { nonNullable: true });

  protected readonly leads = signal<LeadResponse[]>([]);
  protected readonly loading = signal(false);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly pageSize = 20;

  private currentSearch = '';

  ngOnInit(): void {
    this.fetch();
  }

  protected reload(): void {
    this.page.set(0);
    this.fetch();
  }

  protected onSearch(): void {
    this.currentSearch = this.searchControl.value;
    this.page.set(0);
    this.fetch();
  }

  protected goTo(p: number): void {
    if (p < 0) {
      return;
    }
    this.page.set(p);
    this.fetch();
  }

  private fetch(): void {
    this.loading.set(true);

    this.api
      .list({
        page: this.page(),
        size: this.pageSize,
        search: this.currentSearch || undefined,
        sort: 'createdAt,desc'
      })
      .subscribe({
        next: (res) => {
          this.leads.set(res.content);
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
}
