import { ChangeDetectionStrategy, Component, computed, effect, input, OnDestroy, signal } from '@angular/core';
import { gsap } from 'gsap';
import { DemoIconComponent } from './demo-icon.component';
import type { BantState, FeedEvent, LeadState } from './demo.constants';

const BANT_ROWS: { key: keyof BantState; label: string }[] = [
  { key: 'budget', label: 'Presupuesto' },
  { key: 'authority', label: 'Autoridad' },
  { key: 'need', label: 'Necesidad' },
  { key: 'timing', label: 'Timing' }
];

const WEEKDAYS = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];

const LEAD_ROWS: { key: keyof LeadState; icon: 'user' | 'building' | 'phone' | 'target'; label: string }[] = [
  { key: 'name', icon: 'user', label: 'Nombre' },
  { key: 'company', icon: 'building', label: 'Empresa' },
  { key: 'phone', icon: 'phone', label: 'Teléfono' },
  { key: 'need', icon: 'target', label: 'Necesidad' }
];

@Component({
  selector: 'app-demo-system-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DemoIconComponent],
  template: `
    <aside class="panel" aria-label="Panel del sistema en vivo">
      <div class="panel__stats">
        <span class="stat"><demo-icon name="zap" [size]="13" />0.4s respuesta</span>
        <span class="stat"><demo-icon name="clock" [size]="13" />24/7 activo</span>
        <span class="stat stat--score"><demo-icon name="trend-up" [size]="13" />{{ scoreDisplay() }}% cualificación</span>
      </div>

      <section class="panel__card">
        <header class="panel__card-head">
          <h3 class="panel__card-title">Ficha del lead</h3>
          <span class="pill pill--live"><span class="pill__dot" aria-hidden="true"></span>en vivo</span>
        </header>
        <dl class="lead">
          @for (row of leadRows(); track row.key) {
            <div class="lead__row" [class.lead__row--ok]="!!row.value">
              <span class="lead__icon"><demo-icon [name]="row.icon" [size]="15" /></span>
              <dt class="lead__label">{{ row.label }}</dt>
              <dd class="lead__value">{{ row.value || '—' }}</dd>
              @if (row.value) {
                <span class="lead__check"><demo-icon name="check" [size]="13" /></span>
              } @else {
                <span class="lead__pending" aria-hidden="true"></span>
              }
            </div>
          }
        </dl>
      </section>

      <section class="panel__card">
        <header class="panel__card-head">
          <h3 class="panel__card-title">Cualificación BANT</h3>
          <span class="pill" [class.pill--mid]="score() >= 30 && score() < 80" [class.pill--hot]="score() >= 80">{{ scoreLabel() }}</span>
        </header>
        <div class="bant">
          @for (row of bantRows(); track row.key) {
            <div class="bant__bar">
              <div class="bant__bar-head">
                <span class="bant__bar-label">{{ row.label }}</span>
                <span class="bant__bar-value">{{ row.value }}%</span>
              </div>
              <div class="bant__track" role="progressbar" [attr.aria-label]="row.label" [attr.aria-valuenow]="row.value" aria-valuemin="0" aria-valuemax="100">
                <span class="bant__fill" [style.width.%]="row.value"></span>
              </div>
            </div>
          }
          <div class="bant__score">
            <span class="bant__score-num">{{ scoreDisplay() }}</span>
            <span class="bant__score-label">/ 100 · {{ scoreLabel() }}</span>
          </div>
        </div>
      </section>

      <section class="panel__card">
        <header class="panel__card-head">
          <h3 class="panel__card-title">Actividad del agente</h3>
        </header>
        <ol class="feed" aria-live="polite">
          @for (ev of feed(); track ev.id) {
            <li class="feed__item" [class]="'feed__item--' + ev.kind">
              <span class="feed__icon"><demo-icon [name]="ev.icon" [size]="13" /></span>
              <span class="feed__text">{{ ev.text }}</span>
              <time class="feed__time">{{ ev.time }}</time>
            </li>
          } @empty {
            <li class="feed__empty">Esperando actividad…</li>
          }
        </ol>
      </section>

      <section class="panel__card">
        <header class="panel__card-head">
          <h3 class="panel__card-title">Calendario</h3>
          @if (appointment()) {
            <span class="pill pill--hot">cita agendada</span>
          }
        </header>
        <div class="cal">
          <div class="cal__month">{{ monthLabel() }}</div>
          <div class="cal__week">
            @for (w of weekdays; track w) {
              <span class="cal__weekday">{{ w }}</span>
            }
          </div>
          <div class="cal__grid">
            @for (cell of calCells(); track cell.key) {
              <span
                class="cal__cell"
                [class.cal__cell--blank]="cell.day === null"
                [class.cal__cell--booked]="cell.booked"
              >{{ cell.day ?? '' }}</span>
            }
          </div>
          <div class="cal__slot" [class.cal__slot--booked]="appointment()">
            <span class="cal__slot-time">{{ appointmentSlot()?.time ?? '—' }}</span>
            <span class="cal__slot-label">{{ appointment() ? 'Cita con el lead · 30 min' : 'Sin cita aún' }}</span>
            @if (appointment()) {
              <span class="cal__slot-check"><demo-icon name="check" [size]="12" /></span>
            }
          </div>
        </div>
      </section>
    </aside>
  `,
  styles: [
    `
      :host {
        display: flex;
        min-height: 0;
      }
      .panel {
        display: flex;
        flex-direction: column;
        gap: 14px;
        width: 100%;
        overflow-y: auto;
        padding: 2px;
        scrollbar-width: thin;
        scrollbar-color: rgba(255, 255, 255, 0.12) transparent;
      }
      .panel::-webkit-scrollbar {
        width: 8px;
      }
      .panel::-webkit-scrollbar-thumb {
        background: rgba(255, 255, 255, 0.12);
        border-radius: 8px;
      }

      /* ---------- Stats ---------- */
      .panel__stats {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
      }
      .stat {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 7px 12px;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.04);
        border: 1px solid rgba(255, 255, 255, 0.08);
        color: #94a3b8;
        font-size: 0.76rem;
        font-weight: 500;
      }
      .stat demo-icon {
        color: var(--color-primary);
      }
      .stat--score {
        color: var(--color-primary-hover);
        border-color: rgba(0, 168, 107, 0.3);
        background: rgba(0, 168, 107, 0.07);
        font-variant-numeric: tabular-nums;
      }

      /* ---------- Cards ---------- */
      .panel__card {
        background: rgba(13, 19, 31, 0.6);
        backdrop-filter: blur(20px);
        -webkit-backdrop-filter: blur(20px);
        border: 1px solid rgba(255, 255, 255, 0.08);
        border-radius: 20px;
        padding: 16px;
        box-shadow: 0 16px 48px rgba(2, 6, 23, 0.4);
        animation: card-in 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
      }
      .panel__card:nth-child(2) { animation-delay: 70ms; }
      .panel__card:nth-child(3) { animation-delay: 140ms; }
      .panel__card:nth-child(4) { animation-delay: 210ms; }
      .panel__card:nth-child(5) { animation-delay: 280ms; }
      @keyframes card-in {
        from { opacity: 0; transform: translateY(16px); }
      }
      .panel__card-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 10px;
        margin-bottom: 12px;
      }
      .panel__card-title {
        margin: 0;
        font-family: 'Space Grotesk', var(--font-display);
        font-size: 0.9rem;
        font-weight: 700;
        letter-spacing: -0.01em;
        color: #f8fafc;
      }
      .pill {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 3px 10px;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.06);
        border: 1px solid rgba(255, 255, 255, 0.1);
        color: #cbd5e1;
        font-size: 0.62rem;
        font-weight: 700;
        letter-spacing: 0.06em;
        text-transform: uppercase;
      }
      .pill--live {
        color: var(--color-primary-hover);
        border-color: rgba(0, 168, 107, 0.35);
        background: rgba(0, 168, 107, 0.08);
      }
      .pill__dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: var(--color-primary);
        animation: pill-pulse 2s ease-out infinite;
      }
      @keyframes pill-pulse {
        0% { box-shadow: 0 0 0 0 rgba(0, 168, 107, 0.5); }
        70% { box-shadow: 0 0 0 6px rgba(0, 168, 107, 0); }
        100% { box-shadow: 0 0 0 0 rgba(0, 168, 107, 0); }
      }
      .pill--mid {
        color: #fbbf24;
        border-color: rgba(251, 191, 36, 0.4);
        background: rgba(251, 191, 36, 0.1);
      }
      .pill--hot {
        color: var(--color-primary-hover);
        border-color: rgba(0, 168, 107, 0.45);
        background: rgba(0, 168, 107, 0.14);
      }

      /* ---------- Lead card ---------- */
      .lead {
        margin: 0;
        display: flex;
        flex-direction: column;
      }
      .lead__row {
        display: grid;
        grid-template-columns: 20px 92px 1fr 18px;
        align-items: center;
        gap: 8px;
        padding: 8px 0;
        border-bottom: 1px dashed rgba(255, 255, 255, 0.07);
      }
      .lead__row:last-child {
        border-bottom: none;
      }
      .lead__icon {
        display: grid;
        place-items: center;
        color: #64748b;
        transition: color 400ms;
      }
      .lead__row--ok .lead__icon {
        color: var(--color-primary);
      }
      .lead__label {
        font-size: 0.74rem;
        font-weight: 500;
        color: #64748b;
      }
      .lead__value {
        margin: 0;
        font-size: 0.82rem;
        font-weight: 600;
        color: #94a3b8;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        transition: color 400ms;
      }
      .lead__row--ok .lead__value {
        color: #e2e8f0;
      }
      .lead__check {
        display: grid;
        place-items: center;
        color: var(--color-primary);
        animation: check-pop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) both;
      }
      @keyframes check-pop {
        from { transform: scale(0); }
      }
      .lead__pending {
        width: 14px;
        height: 14px;
        border-radius: 50%;
        border: 1.5px dashed rgba(148, 163, 184, 0.4);
      }

      /* ---------- BANT ---------- */
      .bant {
        display: flex;
        flex-direction: column;
        gap: 10px;
      }
      .bant__bar-head {
        display: flex;
        justify-content: space-between;
        margin-bottom: 4px;
        font-size: 0.72rem;
      }
      .bant__bar-label {
        color: #94a3b8;
      }
      .bant__bar-value {
        color: #e2e8f0;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
      }
      .bant__track {
        height: 6px;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.07);
        overflow: hidden;
      }
      .bant__fill {
        display: block;
        height: 100%;
        width: 0;
        border-radius: 999px;
        background: linear-gradient(90deg, var(--color-primary), var(--color-primary-hover));
        box-shadow: 0 0 10px rgba(0, 168, 107, 0.4);
        transition: width 700ms cubic-bezier(0.22, 1, 0.36, 1);
      }
      .bant__score {
        display: flex;
        align-items: baseline;
        gap: 8px;
        margin-top: 6px;
        padding-top: 12px;
        border-top: 1px solid rgba(255, 255, 255, 0.07);
      }
      .bant__score-num {
        font-family: 'Space Grotesk', var(--font-display);
        font-size: 2rem;
        font-weight: 700;
        line-height: 1;
        font-variant-numeric: tabular-nums;
        background: linear-gradient(135deg, var(--color-primary), var(--color-primary-hover));
        -webkit-background-clip: text;
        background-clip: text;
        color: transparent;
      }
      .bant__score-label {
        font-size: 0.7rem;
        font-weight: 600;
        color: #64748b;
      }

      /* ---------- Feed ---------- */
      .feed {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 6px;
      }
      .feed__item {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 7px 8px;
        border-radius: 10px;
        background: rgba(255, 255, 255, 0.03);
        border: 1px solid rgba(255, 255, 255, 0.05);
        animation: feed-in 0.4s cubic-bezier(0.22, 1, 0.36, 1) both;
      }
      @keyframes feed-in {
        from { opacity: 0; transform: translateX(-8px); }
      }
      .feed__icon {
        width: 24px;
        height: 24px;
        border-radius: 8px;
        display: grid;
        place-items: center;
        flex-shrink: 0;
        background: rgba(148, 163, 184, 0.12);
        color: #94a3b8;
      }
      .feed__item--lead .feed__icon { background: var(--color-info-bg); color: var(--color-info); }
      .feed__item--name .feed__icon { background: rgba(0, 168, 107, 0.14); color: var(--color-primary); }
      .feed__item--company .feed__icon { background: rgba(74, 222, 128, 0.14); color: var(--color-primary-hover); }
      .feed__item--intent .feed__icon { background: rgba(167, 139, 250, 0.14); color: #a78bfa; }
      .feed__item--score .feed__icon { background: rgba(251, 191, 36, 0.14); color: #fbbf24; }
      .feed__item--booking .feed__icon { background: rgba(0, 168, 107, 0.16); color: var(--color-primary); }
      .feed__item--crm .feed__icon { background: var(--color-info-bg); color: var(--color-info); }
      .feed__text {
        flex: 1;
        min-width: 0;
        font-size: 0.78rem;
        color: #cbd5e1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .feed__time {
        font-size: 0.7rem;
        color: #64748b;
        font-variant-numeric: tabular-nums;
        flex-shrink: 0;
      }
      .feed__empty {
        font-size: 0.78rem;
        color: #64748b;
        padding: 6px 8px;
      }

      /* ---------- Calendar ---------- */
      .cal__month {
        font-family: 'Space Grotesk', var(--font-display);
        font-size: 0.85rem;
        font-weight: 600;
        color: #e2e8f0;
        text-transform: capitalize;
        margin-bottom: 8px;
      }
      .cal__week {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        gap: 4px;
        margin-bottom: 4px;
      }
      .cal__weekday {
        text-align: center;
        font-size: 0.62rem;
        font-weight: 600;
        color: #64748b;
      }
      .cal__grid {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        gap: 4px;
      }
      .cal__cell {
        aspect-ratio: 1;
        display: grid;
        place-items: center;
        border-radius: 9px;
        font-size: 0.72rem;
        color: #cbd5e1;
        font-variant-numeric: tabular-nums;
        background: rgba(255, 255, 255, 0.03);
        border: 1px solid transparent;
        transition: border-color 300ms, background-color 300ms;
      }
      .cal__cell--blank {
        background: none;
        border: none;
      }
      .cal__cell--booked {
        background: linear-gradient(135deg, var(--color-primary), var(--color-primary-hover));
        border-color: transparent;
        color: #02150c;
        font-weight: 700;
        box-shadow: 0 4px 14px rgba(0, 168, 107, 0.4);
      }
      .cal__slot {
        display: flex;
        align-items: center;
        gap: 10px;
        margin-top: 10px;
        padding: 9px 12px;
        border-radius: 12px;
        border: 1px solid rgba(255, 255, 255, 0.08);
        background: rgba(255, 255, 255, 0.03);
        transition: border-color 400ms, background-color 400ms;
      }
      .cal__slot--booked {
        border-color: rgba(0, 168, 107, 0.45);
        background: rgba(0, 168, 107, 0.1);
      }
      .cal__slot-time {
        font-family: 'Space Grotesk', var(--font-display);
        font-size: 0.85rem;
        font-weight: 700;
        color: #e2e8f0;
        font-variant-numeric: tabular-nums;
        padding: 3px 9px;
        border-radius: 8px;
        background: rgba(255, 255, 255, 0.07);
      }
      .cal__slot-label {
        flex: 1;
        font-size: 0.76rem;
        color: #94a3b8;
      }
      .cal__slot--booked .cal__slot-label {
        color: var(--color-primary-hover);
        font-weight: 600;
      }
      .cal__slot-check {
        display: grid;
        place-items: center;
        color: var(--color-primary);
        animation: check-pop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) both;
      }

      /* ---------- Reduced motion ---------- */
      @media (prefers-reduced-motion: reduce) {
        .panel__card,
        .feed__item,
        .lead__check,
        .cal__slot-check,
        .pill__dot {
          animation: none;
        }
        .bant__fill {
          transition: none;
        }
      }
    `
  ]
})
export class DemoSystemPanelComponent implements OnDestroy {
  readonly lead = input.required<LeadState>();
  readonly bant = input.required<BantState>();
  readonly score = input(0);
  readonly feed = input<FeedEvent[]>([]);
  readonly appointment = input(false);
  readonly appointmentSlot = input<{ dayKey: string; dayLong: string; time: string } | null>(null);

  protected readonly weekdays = WEEKDAYS;

  protected readonly leadRows = computed(() =>
    LEAD_ROWS.map((r) => ({
      key: r.key,
      icon: r.icon,
      label: r.label,
      value: this.lead()[r.key]
    }))
  );

  protected readonly bantRows = computed(() => {
    const b = this.bant();
    return BANT_ROWS.map((r) => ({ ...r, value: b[r.key] }));
  });

  protected readonly scoreLabel = computed(() => {
    const s = this.score();
    if (s >= 80) return 'LISTO PARA CITA';
    if (s >= 55) return 'CALIENTE';
    if (s >= 30) return 'TIBIO';
    return 'FRÍO';
  });

  protected readonly scoreDisplay = signal(0);

  protected readonly monthLabel = computed(() => {
    const slot = this.appointmentSlot();
    const base = slot ? new Date(`${slot.dayKey}T00:00:00`) : new Date();
    return base.toLocaleDateString('es-ES', { month: 'long', year: 'numeric' });
  });

  protected readonly calCells = computed(() => {
    const slot = this.appointmentSlot();
    const now = slot ? new Date(`${slot.dayKey}T00:00:00`) : new Date();
    const year = now.getFullYear();
    const month = now.getMonth();
    const first = new Date(year, month, 1);
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const dayOfMonth = slot ? now.getDate() : new Date().getDate() + 1;
    const leadingBlanks = (first.getDay() + 6) % 7;
    const cells: { key: string; day: number | null; booked: boolean }[] = [];
    for (let i = 0; i < leadingBlanks; i++) {
      cells.push({ key: `b${i}`, day: null, booked: false });
    }
    for (let d = 1; d <= daysInMonth; d++) {
      cells.push({ key: `d${d}`, day: d, booked: Boolean(slot) && d === dayOfMonth });
    }
    return cells;
  });

  private scoreTween: gsap.core.Tween | null = null;

  private readonly scoreEffect = effect(() => {
    const target = this.score();
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    this.scoreTween?.kill();
    if (reduce) {
      this.scoreDisplay.set(target);
      return;
    }
    const proxy = { v: this.scoreDisplay() };
    this.scoreTween = gsap.to(proxy, {
      v: target,
      duration: 0.9,
      ease: 'power2.out',
      onUpdate: () => this.scoreDisplay.set(Math.round(proxy.v))
    });
  });

  ngOnDestroy(): void {
    this.scoreTween?.kill();
  }
}