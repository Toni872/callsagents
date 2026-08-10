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
import { CallApi } from '../../../core/api/call.api';
import { ErrorService } from '../../../core/errors/error.service';
import { CallResponse } from '../../../shared/models/call.model';

@Component({
  selector: 'app-call-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>{{ call() ? 'Detalle de llamada' : 'Cargando...' }}</h2>
          @if (call(); as c) {
            <p class="muted">
              <span class="badge">{{ c.status }}</span>
              @if (c.outcome) {
                · <span class="badge badge--outcome">{{ c.outcome }}</span>
              }
              · creada el {{ formatDate(c.createdAt) }}
            </p>
          }
        </div>
        <div class="page__actions">
          <a class="secondary" [routerLink]="['/calls']">← Volver</a>
          @if (call()) {
            <a class="secondary" [routerLink]="['/calls', call()!.id, 'edit']">Editar</a>
          }
        </div>
      </header>

      @if (errorMessage()) {
        <div class="card error-text">Error: {{ errorMessage() }}</div>
      } @else {
        @if (call(); as c) {
          <div class="card detail">
            <dl class="detail__grid">
              <dt>Campaña</dt>
              <dd class="detail__uuid" [title]="c.campaignId">{{ c.campaignId }}</dd>

              <dt>Lead</dt>
              <dd class="detail__uuid" [title]="c.leadId">{{ c.leadId }}</dd>

              <dt>Agente</dt>
              <dd class="detail__uuid" [title]="c.userId">{{ c.userId }}</dd>

              <dt>Inicio</dt>
              <dd>{{ formatDate(c.startedAt) }}</dd>

              <dt>Fin</dt>
              <dd>{{ formatDate(c.endedAt) }}</dd>

              <dt>Duración</dt>
              <dd>{{ formatDuration(c.durationSeconds) }}</dd>

              <dt>Estado</dt>
              <dd><span class="badge">{{ c.status }}</span></dd>

              <dt>Resultado</dt>
              <dd>
                @if (c.outcome) {
                  <span class="badge badge--outcome">{{ c.outcome }}</span>
                } @else {
                  —
                }
              </dd>

              <dt>Grabación</dt>
              <dd>
                @if (c.recordingUrl) {
                  <a [href]="c.recordingUrl" target="_blank" rel="noopener">Abrir</a>
                } @else {
                  —
                }
              </dd>

              <dt>Provider call id</dt>
              <dd class="detail__uuid">{{ c.providerCallId || '—' }}</dd>

              <dt>Notas</dt>
              <dd class="detail__multiline">{{ c.notes || '—' }}</dd>

              <dt>Actualizada</dt>
              <dd>{{ formatDate(c.updatedAt) }}</dd>
            </dl>
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
      .detail__uuid {
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        font-size: 0.8125rem;
      }
      .detail__multiline {
        white-space: pre-wrap;
      }
      .badge--outcome {
        background: var(--color-info-bg);
        color: var(--color-info);
      }
      @media (max-width: 720px) {
        .detail__grid {
          grid-template-columns: 1fr;
        }
        .detail__grid dt {
          margin-top: var(--spacing-2);
        }
      }
    `
  ]
})
export class CallDetailComponent implements OnInit {
  private readonly api = inject(CallApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly errors = inject(ErrorService);

  protected readonly call = signal<CallResponse | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('ID de llamada no proporcionado');
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

  protected formatDuration(seconds: number | null): string {
    if (seconds === null || seconds === undefined) {
      return '—';
    }
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}m ${s.toString().padStart(2, '0')}s`;
  }

  private load(id: string): void {
    firstValueFrom(this.api.getById(id))
      .then((c) => {
        this.call.set(c);
        this.errorMessage.set(null);
      })
      .catch((err: { error?: { message?: string }; message?: string }) => {
        this.errorMessage.set(err?.error?.message || err?.message || 'No se pudo cargar la llamada');
      });
  }
}