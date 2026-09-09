import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LeadApi } from '../../../core/api/lead.api';
import { ErrorService } from '../../../core/errors/error.service';
import { LeadResponse } from '../../../shared/models/lead.model';

@Component({
  selector: 'app-lead-trash',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>Papelera</h2>
          <p class="muted">Leads eliminados. Puedes restaurarlos o borrarlos definitivamente.</p>
        </div>
        <div class="page__actions">
          <a class="secondary" [routerLink]="['/leads']">← Volver a Leads</a>
          <input
            type="search"
            placeholder="Buscar..."
            [formControl]="searchControl"
            (keyup.enter)="onSearch()"
          />
          <button class="secondary" type="button" (click)="onSearch()">Buscar</button>
          <button type="button" (click)="reload()">Recargar</button>
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
              <th>Origen</th>
              <th>Borrado</th>
              <th>Estado</th>
              <th class="actions-col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (lead of leads(); track lead.id) {
              <tr>
                <td>{{ lead.firstName }} {{ lead.lastName }}</td>
                <td>{{ lead.email || '—' }}</td>
                <td>{{ lead.phone || '—' }}</td>
                <td>{{ lead.company || '—' }}</td>
                <td>{{ lead.source }}</td>
                <td>{{ formatDate(lead.deletedAt) }}</td>
                <td><span class="badge">{{ lead.status }}</span></td>
                <td class="actions-col">
                  <button
                    class="secondary"
                    type="button"
                    (click)="onRestore(lead)"
                    [disabled]="busyId() === lead.id"
                  >
                    Restaurar
                  </button>
                  <button
                    class="danger"
                    type="button"
                    (click)="onHardDelete(lead)"
                    [disabled]="busyId() === lead.id"
                  >
                    Borrar definitivamente
                  </button>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="8" class="muted" style="text-align: center; padding: 2rem;">
                  No hay leads en la papelera.
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
      .actions-col button + button {
        margin-left: var(--spacing-2);
      }
      a {
        color: var(--color-primary);
        text-decoration: none;
      }
      a:hover {
        text-decoration: underline;
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
export class LeadTrashComponent implements OnInit {
  private readonly api = inject(LeadApi);
  private readonly errors = inject(ErrorService);

  protected readonly searchControl = new FormControl<string>('', { nonNullable: true });

  protected readonly leads = signal<LeadResponse[]>([]);
  protected readonly loading = signal(false);
  protected readonly busyId = signal<string | null>(null);
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

  protected onRestore(lead: LeadResponse): void {
    if (this.busyId()) {
      return;
    }
    this.busyId.set(lead.id);
    this.api.restore(lead.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.errors.success('Lead restaurado');
        this.reload();
      },
      error: () => {
        this.busyId.set(null);
        // Toast shown by errorInterceptor
      }
    });
  }

  protected onHardDelete(lead: LeadResponse): void {
    if (this.busyId()) {
      return;
    }
    const confirmed = confirm(`¿Borrar definitivamente "${lead.firstName} ${lead.lastName}"? Esta acción no se puede deshacer.`);
    if (!confirmed) {
      return;
    }
    this.busyId.set(lead.id);
    this.api.hardDelete(lead.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.errors.success('Lead borrado definitivamente');
        this.reload();
      },
      error: () => {
        this.busyId.set(null);
        // Toast shown by errorInterceptor
      }
    });
  }

  private fetch(): void {
    this.loading.set(true);

    this.api
      .trash({
        page: this.page(),
        size: this.pageSize,
        search: this.currentSearch || undefined,
        sort: 'deletedAt,desc'
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