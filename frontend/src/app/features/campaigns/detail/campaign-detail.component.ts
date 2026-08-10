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
import { CampaignApi } from '../../../core/api/campaign.api';
import { ErrorService } from '../../../core/errors/error.service';
import { AuthService } from '../../../core/auth/auth.service';
import { CampaignResponse } from '../../../shared/models/campaign.model';

@Component({
  selector: 'app-campaign-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>{{ campaign() ? campaign()!.name : 'Cargando...' }}</h2>
          @if (campaign(); as c) {
            <p class="muted">
              <span class="badge">{{ c.status }}</span>
              · creada el {{ formatDate(c.createdAt) }}
            </p>
          }
        </div>
        <div class="page__actions">
          <a class="secondary" [routerLink]="['/campaigns']">← Volver</a>
          @if (campaign()) {
            <a class="secondary" [routerLink]="['/campaigns', campaign()!.id, 'edit']">Editar</a>
          }
          @if (canManage() && campaign(); as c) {
            @if (canLaunch(c.status)) {
              <button
                type="button"
                (click)="onLaunch()"
                [disabled]="busy()"
              >
                @if (busy()) { Lanzando... } @else { Lanzar }
              </button>
            }
            @if (canPause(c.status)) {
              <button
                type="button"
                class="warning"
                (click)="onPause()"
                [disabled]="busy()"
              >
                @if (busy()) { Pausando... } @else { Pausar }
              </button>
            }
          }
        </div>
      </header>

      @if (errorMessage()) {
        <div class="card error-text">Error: {{ errorMessage() }}</div>
      } @else {
        @if (campaign(); as c) {
          <div class="card detail">
            <dl class="detail__grid">
              <dt>Estado</dt>
              <dd><span class="badge">{{ c.status }}</span></dd>

              <dt>Descripción</dt>
              <dd class="detail__multiline">{{ c.description || '—' }}</dd>

              <dt>Inicio</dt>
              <dd>{{ formatDate(c.startAt) }}</dd>

              <dt>Fin</dt>
              <dd>{{ formatDate(c.endAt) }}</dd>

              <dt>Creada por</dt>
              <dd>
                @if (c.createdBy) {
                  {{ c.createdBy.fullName }} ({{ c.createdBy.email }})
                } @else {
                  —
                }
              </dd>

              <dt>Actualizada</dt>
              <dd>{{ formatDate(c.updatedAt) }}</dd>
            </dl>

            @if (c.script) {
              <section class="detail__script">
                <h3>Script</h3>
                <pre class="detail__script-body">{{ c.script }}</pre>
              </section>
            }
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
        flex-wrap: wrap;
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

      .detail__script h3 {
        margin: 0 0 var(--spacing-2);
        font-size: 0.95rem;
      }
      .detail__script-body {
        margin: 0;
        padding: var(--spacing-4);
        background: var(--color-bg-alt);
        border: 1px solid var(--color-border);
        border-radius: var(--radius);
        white-space: pre-wrap;
        word-break: break-word;
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        font-size: 0.875rem;
        max-height: 360px;
        overflow: auto;
      }

      .warning {
        background: var(--color-warning);
        color: white;
      }
      .warning:hover:not(:disabled) {
        background: var(--color-info-bg);
        color: var(--color-warning);
      }
    `
  ]
})
export class CampaignDetailComponent implements OnInit {
  private readonly api = inject(CampaignApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly errors = inject(ErrorService);

  protected readonly campaign = signal<CampaignResponse | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly canManage = (): boolean => this.auth.currentRole() === 'ADMIN';

  protected canLaunch(status: string): boolean {
    return status === 'DRAFT' || status === 'SCHEDULED' || status === 'PAUSED';
  }

  protected canPause(status: string): boolean {
    return status === 'RUNNING';
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('ID de campaña no proporcionado');
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

  protected onLaunch(): void {
    const c = this.campaign();
    if (!c || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.launch(c.id).subscribe({
      next: (updated) => {
        this.campaign.set(updated);
        this.busy.set(false);
        this.errors.success('Campaña lanzada');
      },
      error: (err: { error?: { message?: string }; message?: string }) => {
        this.busy.set(false);
        const msg = err?.error?.message || err?.message || 'No se pudo lanzar la campaña';
        this.errors.error(msg);
      }
    });
  }

  protected onPause(): void {
    const c = this.campaign();
    if (!c || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.pause(c.id).subscribe({
      next: (updated) => {
        this.campaign.set(updated);
        this.busy.set(false);
        this.errors.success('Campaña pausada');
      },
      error: (err: { error?: { message?: string }; message?: string }) => {
        this.busy.set(false);
        const msg = err?.error?.message || err?.message || 'No se pudo pausar la campaña';
        this.errors.error(msg);
      }
    });
  }

  private load(id: string): void {
    firstValueFrom(this.api.getById(id))
      .then((c) => {
        this.campaign.set(c);
        this.errorMessage.set(null);
      })
      .catch((err: { error?: { message?: string }; message?: string }) => {
        this.errorMessage.set(err?.error?.message || err?.message || 'No se pudo cargar la campaña');
      });
  }
}