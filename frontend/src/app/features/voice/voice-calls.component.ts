import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  OnDestroy,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { VoiceApi } from '../../core/api/voice.api';
import { CampaignApi } from '../../core/api/campaign.api';
import { ErrorService } from '../../core/errors/error.service';
import { BadgeComponent } from '../../shared/components/badge.component';
import {
  VoiceCall,
  VoiceCallDirection,
  VoiceCallStatus,
  VoiceProviderType
} from '../../shared/models/voice.model';
import { CampaignResponse } from '../../shared/models/campaign.model';
import {
  isLiveStatus,
  voiceCallStatusPresentation,
  type VoiceCallStatusPresentation
} from './voice-call-status.util';

const POLL_INTERVAL_MS = 10_000;

@Component({
  selector: 'app-voice-calls',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, BadgeComponent],
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h2>Llamadas de voz</h2>
          <p class="muted">
            Historial de llamadas realizadas a través de Vapi / Retell.
          </p>
        </div>
        <div class="page__actions">
          @if (pollingActive()) {
            <app-badge tone="success">
              <span class="live-dot" aria-hidden="true"></span>
              Actualización en vivo
            </app-badge>
          }
          <button
            type="button"
            class="secondary"
            (click)="reload()"
            [disabled]="loading()"
          >
            Actualizar
          </button>
          <button
            type="button"
            class="secondary"
            (click)="openManualDialog()"
            [disabled]="loading()"
          >
            Log manual
          </button>
          <button
            type="button"
            (click)="openStartDialog()"
            [disabled]="loading()"
          >
            + Iniciar llamada
          </button>
        </div>
      </header>

      @if (providerHint()) {
        <div class="config-hint" role="alert">
          <strong>Proveedor no configurado.</strong>
          Configura la variable de entorno correspondiente en el servidor
          (<code>VAPI_API_KEY</code> o <code>RETELL_API_KEY</code>, ver RUNBOOK).
          El log manual sigue funcionando.
        </div>
      }

      <div class="card">
        @if (loading() && calls().length === 0) {
          <p class="muted">Cargando llamadas…</p>
        } @else if (calls().length === 0) {
          <p class="empty-state">
            No hay llamadas registradas todavía. Inicia una con
            <strong>+ Iniciar llamada</strong> o registra una existente con
            <strong>Log manual</strong>.
          </p>
        } @else {
          <table>
            <thead>
              <tr>
                <th>Teléfono</th>
                <th>Proveedor</th>
                <th>Estado</th>
                <th>Dirección</th>
                <th>Duración</th>
                <th>Costo</th>
                <th>Fecha</th>
              </tr>
            </thead>
            <tbody>
              @for (call of calls(); track call.id) {
                <tr
                  class="row-clickable"
                  [class.row-selected]="selected()?.id === call.id"
                  (click)="selectCall(call)"
                >
                  <td><code>{{ call.phoneNumber }}</code></td>
                  <td>
                    @if (call.provider) {
                      <span class="badge">{{ call.provider }}</span>
                    } @else {
                      <span class="muted">—</span>
                    }
                  </td>
                  <td>
                    <app-badge
                      [tone]="voiceCallPresentation(call.status).tone"
                    >
                      @if (voiceCallPresentation(call.status).live) {
                        <span class="live-dot" aria-hidden="true"></span>
                      }
                      {{ voiceCallPresentation(call.status).label }}
                    </app-badge>
                  </td>
                  <td>
                    <span class="direction" [class.direction--in]="call.direction === 'INBOUND'">
                      {{ call.direction === 'INBOUND' ? '↙ Entrante' : '↗ Saliente' }}
                    </span>
                  </td>
                  <td>{{ formatDuration(call.durationSeconds) }}</td>
                  <td>{{ formatCost(call.costUsd) }}</td>
                  <td>{{ formatDate(call.createdAt) }}</td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    </section>

    <!-- Iniciar llamada -->
    <dialog #startDialog class="dialog" (close)="onStartDialogClose()">
      <form
        class="dialog__form"
        [formGroup]="startForm"
        (ngSubmit)="onStartCall()"
        novalidate
      >
        <header class="dialog__header">
          <h3>Iniciar llamada</h3>
          <button
            type="button"
            class="dialog__close"
            (click)="closeStartDialog()"
            aria-label="Cerrar"
          >
            ×
          </button>
        </header>

        <div class="dialog__body">
          <label class="field">
            <span class="field__label">Proveedor</span>
            <select
              formControlName="provider"
              [class.field__input--invalid]="isStartInvalid('provider')"
            >
              <option value="" disabled>Seleccionar…</option>
              <option value="VAPI">VAPI</option>
              <option value="RETELL">RETELL</option>
            </select>
            @if (isStartInvalid('provider')) {
              <small class="field__error">Selecciona un proveedor.</small>
            }
          </label>

          <label class="field">
            <span class="field__label">Número de teléfono</span>
            <input
              type="tel"
              placeholder="+5491123456789"
              autocomplete="off"
              formControlName="phoneNumber"
              [class.field__input--invalid]="isStartInvalid('phoneNumber')"
            />
            @if (isStartInvalid('phoneNumber')) {
              <small class="field__error">Introduce un número en formato E.164.</small>
            }
          </label>

          @if (startForm.controls.provider.value === 'RETELL') {
            <label class="field">
              <span class="field__label">Campaña (config de voz)</span>
              <select formControlName="campaignId">
                <option value="">Sin campaña (config por defecto)</option>
                @for (c of voiceCampaigns(); track c.id) {
                  <option [value]="c.id">{{ c.name }}</option>
                }
              </select>
              <small class="muted hint">
                Usa el prompt configurado en la campaña seleccionada.
              </small>
            </label>
          }

          <p class="muted hint">
            La llamada se inicia contra el proveedor seleccionado. Si no está
            configurado, el servidor responderá 500.
          </p>
        </div>

        <footer class="dialog__footer">
          <button
            type="button"
            class="secondary"
            (click)="closeStartDialog()"
            [disabled]="starting()"
          >
            Cancelar
          </button>
          <button type="submit" [disabled]="starting() || startForm.invalid">
            @if (starting()) {
              Iniciando…
            } @else {
              Iniciar
            }
          </button>
        </footer>
      </form>
    </dialog>

    <!-- Log manual -->
    <dialog #manualDialog class="dialog" (close)="onManualDialogClose()">
      <form
        class="dialog__form"
        [formGroup]="manualForm"
        (ngSubmit)="onManualLog()"
        novalidate
      >
        <header class="dialog__header">
          <h3>Log manual de llamada</h3>
          <button
            type="button"
            class="dialog__close"
            (click)="closeManualDialog()"
            aria-label="Cerrar"
          >
            ×
          </button>
        </header>

        <div class="dialog__body">
          <label class="field">
            <span class="field__label">Número de teléfono</span>
            <input
              type="tel"
              placeholder="+5491123456789"
              autocomplete="off"
              formControlName="phoneNumber"
              [class.field__input--invalid]="isManualInvalid('phoneNumber')"
            />
            @if (isManualInvalid('phoneNumber')) {
              <small class="field__error">Introduce un número en formato E.164.</small>
            }
          </label>

          <div class="field-row">
            <label class="field">
              <span class="field__label">Estado</span>
              <select formControlName="status">
                <option value="ENDED">ENDED</option>
                <option value="NO_ANSWER">NO_ANSWER</option>
                <option value="FAILED">FAILED</option>
              </select>
            </label>

            <label class="field">
              <span class="field__label">Dirección</span>
              <select formControlName="direction">
                <option value="OUTBOUND">OUTBOUND</option>
                <option value="INBOUND">INBOUND</option>
              </select>
            </label>
          </div>

          <label class="field">
            <span class="field__label">Duración (segundos)</span>
            <input
              type="number"
              min="0"
              formControlName="durationSeconds"
            />
          </label>

          <label class="field">
            <span class="field__label">Costo (USD)</span>
            <input
              type="number"
              min="0"
              step="0.01"
              formControlName="costUsd"
            />
          </label>

          <label class="field">
            <span class="field__label">Notas</span>
            <textarea
              rows="3"
              formControlName="notes"
              placeholder="Descripción, contexto, etc."
            ></textarea>
          </label>
        </div>

        <footer class="dialog__footer">
          <button
            type="button"
            class="secondary"
            (click)="closeManualDialog()"
            [disabled]="logging()"
          >
            Cancelar
          </button>
          <button type="submit" [disabled]="logging() || manualForm.invalid">
            @if (logging()) {
              Guardando…
            } @else {
              Guardar
            }
          </button>
        </footer>
      </form>
    </dialog>

    <!-- Detalle -->
    <dialog #detailDialog class="dialog dialog--wide" (close)="onDetailDialogClose()">
      @if (selected(); as call) {
        <div class="dialog__form">
          <header class="dialog__header">
            <h3>
              Detalle
              <code class="dialog__id">{{ call.id | slice: 0 : 8 }}</code>
            </h3>
            <button
              type="button"
              class="dialog__close"
              (click)="closeDetailDialog()"
              aria-label="Cerrar"
            >
              ×
            </button>
          </header>

          <div class="dialog__body">
            <dl class="detail-grid">
              <dt>Teléfono</dt>
              <dd><code>{{ call.phoneNumber }}</code></dd>

              <dt>Proveedor</dt>
              <dd>{{ call.provider || '—' }}</dd>

              <dt>Provider call ID</dt>
              <dd>
                @if (call.providerCallId) {
                  <code>{{ call.providerCallId }}</code>
                } @else {
                  <span class="muted">—</span>
                }
              </dd>

              <dt>Estado</dt>
              <dd>
                <app-badge [tone]="voiceCallPresentation(call.status).tone">
                  @if (voiceCallPresentation(call.status).live) {
                    <span class="live-dot" aria-hidden="true"></span>
                  }
                  {{ voiceCallPresentation(call.status).label }}
                </app-badge>
              </dd>

              <dt>Dirección</dt>
              <dd>{{ call.direction }}</dd>

              <dt>Inicio</dt>
              <dd>{{ formatDate(call.startedAt) }}</dd>

              <dt>Fin</dt>
              <dd>{{ formatDate(call.endedAt) }}</dd>

              <dt>Duración</dt>
              <dd>{{ formatDuration(call.durationSeconds) }}</dd>

              <dt>Costo</dt>
              <dd>{{ formatCost(call.costUsd) }}</dd>

              <dt>Lead</dt>
              <dd>
                @if (call.leadId) {
                  <code>{{ call.leadId | slice: 0 : 8 }}</code>
                } @else {
                  <span class="muted">—</span>
                }
              </dd>

              <dt>Appointment</dt>
              <dd>
                @if (call.appointmentId) {
                  <code>{{ call.appointmentId | slice: 0 : 8 }}</code>
                } @else {
                  <span class="muted">—</span>
                }
              </dd>
            </dl>

            @if (call.recordingUrl) {
              <div class="detail-section">
                <h4>Grabación</h4>
                <div class="player">
                  <div
                    class="player__equalizer"
                    [class.player__equalizer--playing]="playing()"
                    aria-hidden="true"
                  >
                    @for (bar of equalizerBars; track $index) {
                      <span
                        class="player__bar"
                        [style.--bar-delay]="bar"
                      ></span>
                    }
                  </div>
                  <audio
                    controls
                    preload="metadata"
                    [src]="call.recordingUrl"
                    (play)="playing.set(true)"
                    (pause)="playing.set(false)"
                    (ended)="playing.set(false)"
                  ></audio>
                </div>
              </div>
            } @else {
              <div class="detail-section">
                <h4>Grabación</h4>
                <span class="muted">Sin grabación</span>
              </div>
            }

            @if (call.transcript) {
              <div class="detail-section">
                <h4>Transcripción</h4>
                <pre class="transcript">{{ call.transcript }}</pre>
              </div>
            }

            @if (call.errorMessage) {
              <div class="detail-section detail-section--error">
                <h4>Error</h4>
                <p>{{ call.errorMessage }}</p>
              </div>
            }

            @if (call.metadata) {
              <div class="detail-section">
                <h4>Metadata</h4>
                <dl class="detail-grid">
                  @for (entry of metadataEntries(call); track entry.key) {
                    <dt>{{ entry.key }}</dt>
                    <dd>
                      <code>{{ entry.value }}</code>
                    </dd>
                  }
                </dl>
              </div>
            }
          </div>

          <footer class="dialog__footer">
            <button type="button" class="secondary" (click)="closeDetailDialog()">
              Cerrar
            </button>
          </footer>
        </div>
      }
    </dialog>
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
      .empty-state {
        margin: 0;
        padding: var(--spacing-6);
        text-align: center;
        color: var(--color-text-muted);
      }
      .config-hint {
        background: #fffbeb;
        border: 1px solid var(--color-warning);
        border-radius: var(--radius);
        padding: var(--spacing-3) var(--spacing-4);
        color: var(--color-warning);
        font-size: 0.875rem;
      }
      .config-hint code {
        background: rgba(0, 0, 0, 0.05);
        padding: 0 0.25rem;
        border-radius: 4px;
      }

      .row-clickable {
        cursor: pointer;
      }
      .row-selected {
        background: var(--color-info-bg);
      }

      .direction {
        font-size: 0.8125rem;
        color: var(--color-text-muted);
      }
      .direction--in {
        color: var(--color-primary);
      }

      /* Live dot */
      .live-dot {
        display: inline-block;
        width: 0.5rem;
        height: 0.5rem;
        margin-right: 0.375rem;
        border-radius: var(--radius-full);
        background: currentColor;
        animation: live-pulse 1.6s ease-in-out infinite;
      }
      @keyframes live-pulse {
        50% {
          opacity: 0.35;
          transform: scale(0.7);
        }
      }

      /* Audio player + equalizer */
      .player {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-2);
        max-width: 420px;
      }
      .player__equalizer {
        display: flex;
        align-items: flex-end;
        gap: 3px;
        height: 22px;
      }
      .player__bar {
        width: 3px;
        height: 6px;
        background: var(--color-primary);
      }
      .player__equalizer--playing .player__bar {
        animation: equalizer-bounce 0.9s ease-in-out infinite;
        animation-delay: var(--bar-delay, 0s);
      }
      @keyframes equalizer-bounce {
        50% {
          height: 20px;
        }
      }
      @media (prefers-reduced-motion: reduce) {
        .live-dot,
        .player__equalizer--playing .player__bar {
          animation: none;
        }
      }

      /* Dialog */
      .dialog {
        border: none;
        border-radius: var(--radius-lg);
        padding: 0;
        background: var(--color-surface);
        color: var(--color-text);
        box-shadow: var(--shadow-md);
        width: min(480px, 92vw);
      }
      .dialog--wide {
        width: min(640px, 94vw);
      }
      .dialog::backdrop {
        background: rgba(15, 23, 42, 0.55);
      }
      .dialog__form {
        display: flex;
        flex-direction: column;
      }
      .dialog__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: var(--spacing-4) var(--spacing-6);
        border-bottom: 1px solid var(--color-border);
      }
      .dialog__header h3 {
        margin: 0;
        font-size: 1rem;
        display: flex;
        align-items: center;
        gap: var(--spacing-2);
      }
      .dialog__id {
        font-size: 0.75rem;
        color: var(--color-text-muted);
        background: var(--color-bg-alt);
        padding: 0.125rem var(--spacing-2);
        border-radius: var(--radius);
      }
      .dialog__close {
        background: transparent;
        color: var(--color-text-muted);
        border: none;
        padding: 0 var(--spacing-2);
        font-size: 1.25rem;
        line-height: 1;
      }
      .dialog__close:hover:not(:disabled) {
        background: transparent;
        color: var(--color-text);
      }
      .dialog__body {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-3);
        padding: var(--spacing-6);
      }
      .dialog__footer {
        display: flex;
        justify-content: flex-end;
        gap: var(--spacing-2);
        padding: var(--spacing-4) var(--spacing-6);
        border-top: 1px solid var(--color-border);
        background: var(--color-bg-alt);
      }

      .field {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-1);
      }
      .field__label {
        font-size: 0.8125rem;
        font-weight: 500;
        color: var(--color-text);
      }
      .field__error {
        color: var(--color-error);
        font-size: 0.75rem;
      }
      .field__input--invalid {
        border-color: var(--color-error);
      }
      .field-row {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: var(--spacing-3);
      }
      .hint {
        margin: 0;
        font-size: 0.75rem;
      }

      /* Detail */
      .detail-grid {
        display: grid;
        grid-template-columns: max-content 1fr;
        gap: var(--spacing-2) var(--spacing-4);
        margin: 0;
      }
      .detail-grid dt {
        font-size: 0.8125rem;
        color: var(--color-text-muted);
        font-weight: 500;
      }
      .detail-grid dd {
        margin: 0;
        font-size: 0.875rem;
        word-break: break-word;
      }
      .detail-section {
        margin-top: var(--spacing-4);
        padding-top: var(--spacing-4);
        border-top: 1px solid var(--color-border);
      }
      .detail-section h4 {
        margin: 0 0 var(--spacing-2);
        font-size: 0.875rem;
        font-weight: 600;
      }
      .detail-section--error {
        background: var(--color-error-bg);
        border: 1px solid var(--color-error);
        border-radius: var(--radius);
        padding: var(--spacing-3);
        color: var(--color-error);
      }
      .detail-section--error p {
        margin: 0;
      }
      .transcript {
        margin: 0;
        padding: var(--spacing-3);
        background: var(--color-bg-alt);
        border-radius: var(--radius);
        font-size: 0.8125rem;
        white-space: pre-wrap;
        max-height: 240px;
        overflow-y: auto;
      }
    `
  ]
})
export class VoiceCallsComponent implements OnInit {
  private readonly api = inject(VoiceApi);
  private readonly campaignApi = inject(CampaignApi);
  private readonly fb = inject(FormBuilder);
  private readonly errors = inject(ErrorService);

  protected readonly calls = signal<VoiceCall[]>([]);
  protected readonly loading = signal(false);
  protected readonly starting = signal(false);
  protected readonly logging = signal(false);
  protected readonly selected = signal<VoiceCall | null>(null);
  protected readonly providerHint = signal(false);
  protected readonly playing = signal(false);
  protected readonly pollingActive = signal(false);
  protected readonly voiceCampaigns = signal<CampaignResponse[]>([]);
  protected readonly hasLiveCalls = computed(() =>
    this.calls().some((call) => isLiveStatus(call.status))
  );
  protected readonly equalizerBars = [
    '0s',
    '-0.2s',
    '-0.4s',
    '-0.6s',
    '-0.8s',
    '-0.3s',
    '-0.5s'
  ];

  protected readonly startForm = this.fb.nonNullable.group({
    provider: this.fb.nonNullable.control<VoiceProviderType | ''>('', [
      Validators.required
    ]),
    phoneNumber: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.pattern(/^\+?[1-9]\d{6,14}$/)
    ]),
    campaignId: this.fb.nonNullable.control('')
  });

  protected readonly manualForm = this.fb.nonNullable.group({
    phoneNumber: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.pattern(/^\+?[1-9]\d{6,14}$/)
    ]),
    status: this.fb.nonNullable.control<VoiceCallStatus>('ENDED', [
      Validators.required
    ]),
    direction: this.fb.nonNullable.control<VoiceCallDirection>('OUTBOUND', [
      Validators.required
    ]),
    durationSeconds: this.fb.nonNullable.control<number | null>(null),
    costUsd: this.fb.nonNullable.control<number | null>(null),
    notes: this.fb.nonNullable.control('')
  });

  @ViewChild('startDialog', { static: true })
  private readonly startDialogRef!: ElementRef<HTMLDialogElement>;

  @ViewChild('manualDialog', { static: true })
  private readonly manualDialogRef!: ElementRef<HTMLDialogElement>;

  @ViewChild('detailDialog', { static: true })
  private readonly detailDialogRef!: ElementRef<HTMLDialogElement>;

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  private readonly onVisibilityChange = (): void => {
    if (document.visibilityState === 'hidden') {
      this.stopPolling();
    } else {
      this.syncPolling();
    }
  };

  ngOnInit(): void {
    document.addEventListener('visibilitychange', this.onVisibilityChange);
    this.fetch();
  }

  ngOnDestroy(): void {
    this.stopPolling();
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
  }

  protected reload(): void {
    this.fetch();
  }

  protected selectCall(call: VoiceCall): void {
    this.playing.set(false);
    this.selected.set(call);
    this.detailDialogRef.nativeElement.showModal();
  }

  protected voiceCallPresentation(
    status: VoiceCallStatus
  ): VoiceCallStatusPresentation {
    return voiceCallStatusPresentation(status);
  }

  protected formatDate(value: string | null): string {
    if (!value) {
      return '—';
    }
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) {
      return '—';
    }
    return d.toLocaleString();
  }

  protected formatDuration(seconds: number | null): string {
    if (seconds === null || seconds === undefined) {
      return '—';
    }
    if (seconds < 60) {
      return `${seconds}s`;
    }
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}m ${s.toString().padStart(2, '0')}s`;
  }

  protected formatCost(value: number | string | null): string {
    if (value === null || value === undefined) {
      return '—';
    }
    const n = typeof value === 'string' ? parseFloat(value) : value;
    if (Number.isNaN(n)) {
      return '—';
    }
    return `$${n.toFixed(2)}`;
  }

  protected metadataEntries(call: VoiceCall): { key: string; value: string }[] {
    if (!call.metadata) {
      return [];
    }
    return Object.entries(call.metadata).map(([key, value]) => ({
      key,
      value: typeof value === 'string' ? value : JSON.stringify(value)
    }));
  }

  /* --- Start dialog --- */

  protected openStartDialog(): void {
    this.startForm.reset({
      provider: '' as VoiceProviderType | '',
      phoneNumber: '',
      campaignId: ''
    });
    this.startDialogRef.nativeElement.showModal();
    // Solo campañas con voz configurada: el filtrado lo hace el backend
    // (hasVoiceConfig=true); el listado está paginado, no se filtra en cliente.
    this.campaignApi.list({ hasVoiceConfig: true, size: 100 }).subscribe({
      next: (page) => this.voiceCampaigns.set(page.content),
      error: () => {
        // errorInterceptor ya muestra el toast.
        this.voiceCampaigns.set([]);
      }
    });
  }

  protected closeStartDialog(): void {
    if (this.startDialogRef.nativeElement.open) {
      this.startDialogRef.nativeElement.close();
    }
  }

  protected onStartDialogClose(): void {
    // no-op
  }

  protected isStartInvalid(
    controlName: 'provider' | 'phoneNumber'
  ): boolean {
    const c = this.startForm.controls[controlName];
    return c.invalid && (c.dirty || c.touched);
  }

  protected onStartCall(): void {
    if (this.startForm.invalid || this.starting()) {
      this.startForm.markAllAsTouched();
      return;
    }
    const raw = this.startForm.getRawValue();
    const provider = raw.provider as VoiceProviderType;
    const campaignId = raw.campaignId || undefined;
    this.starting.set(true);
    this.api.startCall(provider, raw.phoneNumber, campaignId).subscribe({
      next: (call) => {
        this.starting.set(false);
        this.providerHint.set(false);
        this.closeStartDialog();
        this.errors.success(`Llamada ${call.id.slice(0, 8)} iniciada`);
        this.fetch();
      },
      error: (err: HttpErrorResponse) => {
        this.starting.set(false);
        if (err.status === 500) {
          this.providerHint.set(true);
        }
        // errorInterceptor ya muestra el toast.
      }
    });
  }

  /* --- Manual log dialog --- */

  protected openManualDialog(): void {
    this.manualForm.reset({
      phoneNumber: '',
      status: 'ENDED',
      direction: 'OUTBOUND',
      durationSeconds: null,
      costUsd: null,
      notes: ''
    });
    this.manualDialogRef.nativeElement.showModal();
  }

  protected closeManualDialog(): void {
    if (this.manualDialogRef.nativeElement.open) {
      this.manualDialogRef.nativeElement.close();
    }
  }

  protected onManualDialogClose(): void {
    // no-op
  }

  protected isManualInvalid(
    controlName: 'phoneNumber' | 'status' | 'direction'
  ): boolean {
    const c = this.manualForm.controls[controlName];
    return c.invalid && (c.dirty || c.touched);
  }

  protected onManualLog(): void {
    if (this.manualForm.invalid || this.logging()) {
      this.manualForm.markAllAsTouched();
      return;
    }
    const raw = this.manualForm.getRawValue();
    const payload: Partial<VoiceCall> = {
      phoneNumber: raw.phoneNumber,
      status: raw.status,
      direction: raw.direction,
      durationSeconds: raw.durationSeconds ?? null,
      costUsd: raw.costUsd ?? null,
      metadata: raw.notes
        ? { notes: raw.notes, source: 'manual' }
        : { source: 'manual' }
    };
    this.logging.set(true);
    this.api.logManualCall(payload).subscribe({
      next: () => {
        this.logging.set(false);
        this.closeManualDialog();
        this.errors.success('Llamada registrada manualmente');
        this.fetch();
      },
      error: () => {
        // errorInterceptor ya muestra el toast.
        this.logging.set(false);
      }
    });
  }

  /* --- Detail dialog --- */

  protected closeDetailDialog(): void {
    if (this.detailDialogRef.nativeElement.open) {
      this.detailDialogRef.nativeElement.close();
    }
  }

  protected onDetailDialogClose(): void {
    this.selected.set(null);
  }

  /* --- Fetch & live polling --- */

  private fetch(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (list) => {
        this.calls.set(list);
        this.loading.set(false);
        this.syncPolling();
      },
      error: () => {
        // errorInterceptor ya muestra el toast.
        this.loading.set(false);
        this.syncPolling();
      }
    });
  }

  private syncPolling(): void {
    if (!this.hasLiveCalls() || document.visibilityState === 'hidden') {
      this.stopPolling();
      return;
    }
    if (this.pollTimer === null) {
      this.pollTimer = setInterval(() => this.pollTick(), POLL_INTERVAL_MS);
    }
    this.pollingActive.set(true);
  }

  private pollTick(): void {
    if (this.loading()) {
      return;
    }
    this.fetch();
  }

  private stopPolling(): void {
    if (this.pollTimer !== null) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
    this.pollingActive.set(false);
  }
}
