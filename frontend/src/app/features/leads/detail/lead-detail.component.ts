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
import { LeadApi } from '../../../core/api/lead.api';
import { ErrorService } from '../../../core/errors/error.service';
import { AuthService } from '../../../core/auth/auth.service';
import { LeadResponse } from '../../../shared/models/lead.model';

@Component({
  selector: 'app-lead-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>{{ lead() ? 'Detalle de lead' : 'Cargando...' }}</h2>
          @if (lead(); as l) {
            <p class="muted">
              {{ l.firstName }} {{ l.lastName }} · creado el {{ formatDate(l.createdAt) }}
            </p>
          }
        </div>
        <div class="page__actions">
          <a class="secondary" [routerLink]="['/leads']">← Volver</a>
          @if (lead()) {
            <a class="secondary" [routerLink]="['/leads', lead()!.id, 'edit']">Editar</a>
          }
          @if (lead() && canDelete()) {
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

      @if (errorMessage()) {
        <div class="card error-text">Error: {{ errorMessage() }}</div>
      } @else {
        @if (lead(); as l) {
        <div class="card detail">
          <dl class="detail__grid">
            <dt>Estado</dt>
            <dd>
              <span class="badge">{{ l.status }}</span>
            </dd>

            <dt>Origen</dt>
            <dd>{{ l.source }}</dd>

            <dt>Email</dt>
            <dd>{{ l.email || '—' }}</dd>

            <dt>Teléfono</dt>
            <dd>{{ l.phone || '—' }}</dd>

            <dt>Empresa</dt>
            <dd>{{ l.company || '—' }}</dd>

            <dt>Asignado a</dt>
            <dd>
              @if (l.assignedTo) {
                {{ l.assignedTo.fullName }} ({{ l.assignedTo.email }})
              } @else {
                —
              }
            </dd>

            <dt>Notas</dt>
            <dd class="detail__multiline">{{ l.notes || '—' }}</dd>

            <dt>Do-not-call</dt>
            <dd>{{ l.doNotCall ? 'Sí' : 'No' }}</dd>

            <dt>Consentimiento</dt>
            <dd>{{ formatDate(l.consentAt) }}</dd>

            <dt>Retención hasta</dt>
            <dd>{{ l.dataRetentionUntil || '—' }}</dd>

            <dt>Actualizado</dt>
            <dd>{{ formatDate(l.updatedAt) }}</dd>
          </dl>

          <section class="detail__custom">
            <h3>Campos personalizados</h3>
            @if (customFieldEntries().length === 0) {
              <p class="muted">Sin campos personalizados.</p>
            } @else {
              <dl class="detail__custom-grid">
                @for (cf of customFieldEntries(); track cf.key) {
                  <dt>{{ cf.key }}</dt>
                  <dd>{{ cf.value }}</dd>
                }
              </dl>
            }
          </section>
        </div>
        }
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
      @media (max-width: 720px) {
        .detail__grid {
          grid-template-columns: 1fr;
        }
        .detail__grid dt {
          margin-top: var(--spacing-2);
        }
      }

      .detail__custom h3 {
        margin: 0 0 var(--spacing-2);
        font-size: 0.95rem;
      }
      .detail__custom-grid {
        display: grid;
        grid-template-columns: 200px 1fr;
        gap: var(--spacing-2) var(--spacing-4);
        margin: 0;
      }
      .detail__custom-grid dt {
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        font-size: 0.8125rem;
        color: var(--color-text);
      }
      .detail__custom-grid dd {
        margin: 0;
      }
      @media (max-width: 720px) {
        .detail__custom-grid {
          grid-template-columns: 1fr;
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
export class LeadDetailComponent implements OnInit {
  private readonly api = inject(LeadApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly errors = inject(ErrorService);

  protected readonly lead = signal<LeadResponse | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly deleting = signal(false);

  protected readonly canDelete = (): boolean => this.auth.currentRole() === 'ADMIN';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('ID de lead no proporcionado');
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

  protected customFieldEntries(): Array<{ key: string; value: string }> {
    const l = this.lead();
    if (!l?.customFields) {
      return [];
    }
    return Object.entries(l.customFields).map(([k, v]) => ({
      key: k,
      value: typeof v === 'string' ? v : JSON.stringify(v)
    }));
  }

  protected onDelete(): void {
    const l = this.lead();
    if (!l || this.deleting()) {
      return;
    }
    const confirmed = confirm(`¿Eliminar el lead "${l.firstName} ${l.lastName}"? Esta acción no se puede deshacer.`);
    if (!confirmed) {
      return;
    }

    this.deleting.set(true);
    this.api.delete(l.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.errors.success('Lead eliminado');
        void this.router.navigate(['/leads']);
      },
      error: (err: { error?: { message?: string }; message?: string }) => {
        this.deleting.set(false);
        const msg = err?.error?.message || err?.message || 'No se pudo eliminar el lead';
        this.errors.error(msg);
      }
    });
  }

  private load(id: string): void {
    firstValueFrom(this.api.getById(id))
      .then((lead) => {
        this.lead.set(lead);
        this.errorMessage.set(null);
      })
      .catch((err: { error?: { message?: string }; message?: string }) => {
        this.errorMessage.set(err?.error?.message || err?.message || 'No se pudo cargar el lead');
      });
  }
}