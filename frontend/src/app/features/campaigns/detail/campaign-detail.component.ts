import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  FormControl,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CampaignApi } from '../../../core/api/campaign.api';
import { CampaignLeadApi, CampaignLeadResponse } from '../../../core/api/campaign-lead.api';
import { LeadApi } from '../../../core/api/lead.api';
import { ErrorService } from '../../../core/errors/error.service';
import { AuthService } from '../../../core/auth/auth.service';
import { CampaignResponse } from '../../../shared/models/campaign.model';
import { LeadResponse } from '../../../shared/models/lead.model';

type CampaignLeadStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';

@Component({
  selector: 'app-campaign-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
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
              <button type="button" (click)="onLaunch()" [disabled]="busy()">
                @if (busy()) { Lanzando... } @else { Lanzar }
              </button>
            }
            @if (canPause(c.status)) {
              <button type="button" class="warning" (click)="onPause()" [disabled]="busy()">
                @if (busy()) { Pausando... } @else { Pausar }
              </button>
            }
          }
        </div>
      </header>

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

          <div class="card leads">
            <header class="leads__header">
              <div>
                <h3>Leads asignados</h3>
                <p class="muted">
                  {{ leadsTotal() }} lead(s) en esta campaña. Solo los leads asignados
                  aquí pueden recibir llamadas desde esta campaña.
                </p>
              </div>
              @if (canManage()) {
                <button type="button" (click)="openAddLeadDialog()" [disabled]="loadingLeads()">
                  + Añadir lead
                </button>
              }
            </header>

            @if (leadsError()) {
              <div class="card error-text">{{ leadsError() }}</div>
            }

            @if (leads().length === 0 && !loadingLeads()) {
              <p class="muted">Sin leads asignados todavía.</p>
            }

            <table>
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Email</th>
                  <th>Teléfono</th>
                  <th>Estado</th>
                  <th>Intentos</th>
                  <th>Último intento</th>
                  @if (canManage()) {
                    <th class="actions-col">Acciones</th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (cl of leads(); track cl.leadId) {
                  <tr>
                    <td>
                      {{ cl.leadFirstName }} {{ cl.leadLastName }}
                      @if (cl.leadCompany) {
                        <small class="muted"> · {{ cl.leadCompany }}</small>
                      }
                    </td>
                    <td>{{ cl.leadEmail || '—' }}</td>
                    <td>{{ cl.leadPhone || '—' }}</td>
                    <td><span class="badge">{{ cl.status }}</span></td>
                    <td>{{ cl.attempts }}</td>
                    <td>{{ formatDate(cl.lastAttemptAt) }}</td>
                    @if (canManage()) {
                      <td class="actions-col">
                        <button
                          type="button"
                          class="danger"
                          (click)="onRemoveLead(cl)"
                          [disabled]="removingLeadId() === cl.leadId"
                        >
                          @if (removingLeadId() === cl.leadId) {
                            Quitando...
                          } @else {
                            Quitar
                          }
                        </button>
                      </td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
    </section>

    <dialog #addLeadDialog class="dialog" (close)="onAddLeadDialogClose()">
      <form
        class="dialog__form"
        [formGroup]="addLeadForm"
        (ngSubmit)="onAddLeadSubmit()"
        novalidate
      >
        <header class="dialog__header">
          <h3>Añadir lead a la campaña</h3>
          <button
            type="button"
            class="dialog__close"
            (click)="closeAddLeadDialog()"
            aria-label="Cerrar"
          >×</button>
        </header>

        <div class="dialog__body">
          @if (addLeadError()) {
            <div class="card error-text">{{ addLeadError() }}</div>
          }

          <label class="field">
            <span class="field__label">Buscar lead</span>
            <input
              type="search"
              placeholder="Nombre, email, empresa..."
              [formControl]="addLeadForm.controls.search"
              (keyup.enter)="onSearchLeads()"
            />
          </label>

          @if (searchingLeads()) {
            <p class="muted">Buscando...</p>
          } @else if (searchResults().length === 0 && hasSearched()) {
            <p class="muted">Sin resultados.</p>
          } @else {
            <label class="field">
              <span class="field__label">Lead *</span>
              <select
                formControlName="leadId"
                [class.field__input--invalid]="isAddLeadInvalid('leadId')"
              >
                <option value="" disabled>Seleccionar...</option>
                @for (l of searchResults(); track l.id) {
                  <option [value]="l.id">
                    {{ l.firstName }} {{ l.lastName }}
                    @if (l.email) {
                      · {{ l.email }}
                    }
                  </option>
                }
              </select>
              @if (isAddLeadInvalid('leadId')) {
                <small class="field__error">Selecciona un lead.</small>
              }
            </label>
          }

          <label class="field">
            <span class="field__label">Estado inicial</span>
            <select formControlName="status">
              <option [ngValue]="null">— Por defecto (PENDING) —</option>
              @for (s of leadStatuses; track s) {
                <option [ngValue]="s">{{ s }}</option>
              }
            </select>
          </label>
        </div>

        <footer class="dialog__footer">
          <button
            type="button"
            class="secondary"
            (click)="closeAddLeadDialog()"
            [disabled]="submittingLead()"
          >
            Cancelar
          </button>
          <button
            type="button"
            (click)="onSearchLeads()"
            [disabled]="searchingLeads()"
          >
            Buscar
          </button>
          <button
            type="submit"
            [disabled]="submittingLead() || addLeadForm.invalid"
          >
            @if (submittingLead()) { Añadiendo... } @else { Añadir }
          </button>
        </footer>
      </form>
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

      .leads {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-3);
      }
      .leads__header {
        display: flex;
        align-items: flex-end;
        justify-content: space-between;
        gap: var(--spacing-3);
        flex-wrap: wrap;
      }
      .leads__header h3 {
        margin: 0 0 var(--spacing-1);
        font-size: 1rem;
      }
      .leads__header p {
        margin: 0;
      }
      .actions-col {
        text-align: right;
        width: 1%;
        white-space: nowrap;
      }
      .danger {
        background: var(--color-error);
        color: white;
      }
      .danger:hover:not(:disabled) {
        background: var(--color-error-bg);
        color: var(--color-error);
      }

      .dialog {
        border: none;
        border-radius: var(--radius-lg);
        padding: 0;
        background: var(--color-surface);
        color: var(--color-text);
        box-shadow: var(--shadow-md);
        width: min(560px, 92vw);
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
      }
      .field__error {
        color: var(--color-error);
        font-size: 0.75rem;
      }
      .field__input--invalid {
        border-color: var(--color-error);
      }
    `
  ]
})
export class CampaignDetailComponent implements OnInit {
  private readonly campaignApi = inject(CampaignApi);
  private readonly campaignLeadApi = inject(CampaignLeadApi);
  private readonly leadApi = inject(LeadApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly errors = inject(ErrorService);
  private readonly fb = inject(FormBuilder);

  protected readonly leadStatuses: CampaignLeadStatus[] = [
    'PENDING',
    'IN_PROGRESS',
    'COMPLETED',
    'SKIPPED'
  ];

  protected readonly campaign = signal<CampaignResponse | null>(null);
  protected readonly busy = signal(false);

  protected readonly leads = signal<CampaignLeadResponse[]>([]);
  protected readonly leadsTotal = signal(0);
  protected readonly leadsError = signal<string | null>(null);
  protected readonly loadingLeads = signal(false);
  protected readonly removingLeadId = signal<string | null>(null);

  protected readonly searchResults = signal<LeadResponse[]>([]);
  protected readonly searchingLeads = signal(false);
  protected readonly hasSearched = signal(false);
  protected readonly submittingLead = signal(false);
  protected readonly addLeadError = signal<string | null>(null);

  @ViewChild('addLeadDialog', { static: true })
  private readonly dialogRef!: ElementRef<HTMLDialogElement>;

  protected readonly addLeadForm = this.fb.nonNullable.group({
    search: this.fb.nonNullable.control(''),
    leadId: this.fb.nonNullable.control('', [Validators.required]),
    status: this.fb.control<CampaignLeadStatus | null>(null)
  });

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
      this.errors.error('ID de campaña no proporcionado');
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
    this.campaignApi.launch(c.id).subscribe({
      next: (updated) => {
        this.campaign.set(updated);
        this.busy.set(false);
        this.errors.success('Campaña lanzada');
      },
      error: () => {
        this.busy.set(false);
        // Toast shown by errorInterceptor
      }
    });
  }

  protected onPause(): void {
    const c = this.campaign();
    if (!c || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.campaignApi.pause(c.id).subscribe({
      next: (updated) => {
        this.campaign.set(updated);
        this.busy.set(false);
        this.errors.success('Campaña pausada');
      },
      error: () => {
        this.busy.set(false);
        // Toast shown by errorInterceptor
      }
    });
  }

  protected openAddLeadDialog(): void {
    this.addLeadForm.reset({ search: '', leadId: '', status: null });
    this.addLeadError.set(null);
    this.searchResults.set([]);
    this.hasSearched.set(false);
    this.dialogRef.nativeElement.showModal();
    // auto-load first page so user sees options without typing
    this.runLeadSearch('');
  }

  protected closeAddLeadDialog(): void {
    if (this.dialogRef.nativeElement.open) {
      this.dialogRef.nativeElement.close();
    }
  }

  protected onAddLeadDialogClose(): void {
    // no-op for now
  }

  protected isAddLeadInvalid(name: 'leadId'): boolean {
    const c = this.addLeadForm.controls[name];
    return c.invalid && (c.dirty || c.touched);
  }

  protected onSearchLeads(): void {
    this.runLeadSearch(this.addLeadForm.controls.search.value);
  }

  protected onAddLeadSubmit(): void {
    if (this.addLeadForm.invalid || this.submittingLead()) {
      this.addLeadForm.markAllAsTouched();
      return;
    }
    const c = this.campaign();
    if (!c) {
      return;
    }
    const raw = this.addLeadForm.getRawValue();
    const body = {
      leadId: raw.leadId,
      status: raw.status ?? undefined,
      assignedToId: null
    };
    this.submittingLead.set(true);
    this.addLeadError.set(null);
    this.campaignLeadApi.add(c.id, body).subscribe({
      next: () => {
        this.submittingLead.set(false);
        this.errors.success('Lead añadido a la campaña');
        this.closeAddLeadDialog();
        this.loadLeads(c.id);
      },
      error: (err: { error?: { message?: string }; message?: string }) => {
        this.submittingLead.set(false);
        const msg = err?.error?.message || err?.message || 'No se pudo añadir el lead';
        this.addLeadError.set(msg);
      }
    });
  }

  protected onRemoveLead(cl: CampaignLeadResponse): void {
    const c = this.campaign();
    if (!c || this.removingLeadId()) {
      return;
    }
    const confirmed = confirm(
      `¿Quitar a "${cl.leadFirstName} ${cl.leadLastName}" de esta campaña? Las llamadas existentes se conservarán.`
    );
    if (!confirmed) {
      return;
    }
    this.removingLeadId.set(cl.leadId);
    this.campaignLeadApi.remove(c.id, cl.leadId).subscribe({
      next: () => {
        this.removingLeadId.set(null);
        this.errors.success('Lead quitado de la campaña');
        this.loadLeads(c.id);
      },
      error: () => {
        this.removingLeadId.set(null);
        // Toast shown by errorInterceptor
      }
    });
  }

  private runLeadSearch(query: string): void {
    this.searchingLeads.set(true);
    this.hasSearched.set(true);
    this.leadApi.list({ page: 0, size: 50, search: query || undefined }).subscribe({
      next: (res) => {
        this.searchResults.set(res.content);
        this.searchingLeads.set(false);
      },
      error: (err: { error?: { message?: string }; message?: string }) => {
        this.searchingLeads.set(false);
        this.addLeadError.set(err?.error?.message || err?.message || 'Error al buscar leads');
      }
    });
  }

  private load(id: string): void {
    firstValueFrom(this.campaignApi.getById(id))
      .then((c) => {
        this.campaign.set(c);
        this.loadLeads(id);
      })
      .catch(() => {
        // Toast shown by errorInterceptor
      });
  }

  private loadLeads(campaignId: string): void {
    this.loadingLeads.set(true);
    this.leadsError.set(null);
    this.campaignLeadApi.list(campaignId, 0, 50).subscribe({
      next: (res) => {
        this.leads.set(res.content);
        this.leadsTotal.set(res.totalElements);
        this.loadingLeads.set(false);
      },
      error: (err: { error?: { message?: string }; message?: string }) => {
        this.loadingLeads.set(false);
        this.leadsError.set(err?.error?.message || err?.message || 'Error al cargar leads');
      }
    });
  }
}