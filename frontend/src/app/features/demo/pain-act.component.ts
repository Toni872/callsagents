import { ChangeDetectionStrategy, Component, OnDestroy, input, output, signal } from '@angular/core';
import { gsap } from 'gsap';
import { DemoIconComponent } from './demo-icon.component';
import { PAINS, type PainData, type PainKey } from './demo.constants';

const SIM_STEPS: { at: number; label: string; lost?: boolean }[] = [
  { at: 0, label: 'Lead interesado' },
  { at: 34, label: 'Esperando respuesta' },
  { at: 62, label: 'Tu competencia responde' },
  { at: 82, label: 'VENTA PERDIDA', lost: true }
];

const SIM_DURATION = 6.2;

@Component({
  selector: 'app-demo-pain',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DemoIconComponent],
  template: `
    <div class="pain">
      <header class="pain__top">
        <span class="pain__brand">
          <span class="pain__brand-name">
            <span class="pain__brand-calls">CALLS</span><span class="pain__brand-agents">AGENTS</span>
          </span>
        </span>
        <span class="pain__badge">
          <span class="pain__badge-dot" aria-hidden="true"></span>
          Demo interactiva
        </span>
        <time class="pain__clock" [attr.aria-label]="'Hora actual: ' + clock()">{{ clock() }}</time>
      </header>

      <main class="pain__body">
        @if (selected(); as pain) {
          <div class="pain__sim">
            <button class="pain__sim-reset" type="button" (click)="reset()" aria-label="Elegir otro dolor">
              <demo-icon name="arrow-left" [size]="15" /> Otro dolor
            </button>
            <h2 class="pain__sim-title">{{ pain.title }}</h2>
            <p class="pain__sim-copy">{{ pain.copy }}</p>

            <ol class="pain__steps">
              @for (step of simSteps; track step.at) {
                <li
                  class="pain__step"
                  [class.pain__step--on]="stepIndex() >= $index"
                  [class.pain__step--lost]="!!step.lost && stepIndex() >= $index"
                >
                  <span class="pain__step-dot" aria-hidden="true"></span>
                  <span class="pain__step-label">{{ step.label }}</span>
                </li>
              }
            </ol>

            <div class="pain__metrics">
              <div class="pain__metric">
                <span class="pain__metric-label">Pérdida estimada</span>
                <span
                  class="pain__metric-value pain__metric-value--loss"
                  [class.pain__metric-value--final]="simDone()"
                >{{ moneyText() }}</span>
              </div>
              <div class="pain__metric">
                <span class="pain__metric-label">Probabilidad de venta</span>
                <span
                  class="pain__metric-value"
                  [class.pain__metric-value--final]="simDone()"
                >{{ probabilityText() }}</span>
              </div>
            </div>

            <div
              class="pain__bar"
              role="progressbar"
              aria-label="Avance de la pérdida"
              [attr.aria-valuenow]="roundedProgress()"
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <span class="pain__bar-fill" [style.width.%]="progress()"></span>
            </div>

            @if (simDone()) {
              <button class="pain__cta" type="button" (click)="continueToSolution.emit()">
                <span class="pain__cta-shine" aria-hidden="true"></span>
                <span class="pain__cta-label">Ver cómo Callsagents lo resuelve</span>
                <demo-icon name="arrow-right" [size]="18" />
              </button>
            }
          </div>
        } @else {
          <div class="pain__intro">
            <h1 class="pain__title">
              ¿Cuánto te está costando
              <span class="pain__title-accent">NO responder a tiempo</span>?
            </h1>
            <p class="pain__sub">
              Elige tu dolor y mira en vivo cuánto pierde tu negocio por cada minuto de retraso.
            </p>
          </div>
          <div class="pain__cards">
            @for (pain of pains; track pain.key) {
              <button
                class="pain__card"
                type="button"
                (click)="select(pain)"
                [attr.aria-label]="pain.title + '. ' + pain.copy"
              >
                <span class="pain__card-icon"><demo-icon [name]="pain.icon" [size]="22" /></span>
                <span class="pain__card-title">{{ pain.title }}</span>
                <span class="pain__card-copy">{{ pain.copy }}</span>
              </button>
            }
          </div>
        }
      </main>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        position: relative;
        z-index: 1;
      }
      .pain {
        min-height: 100dvh;
        display: flex;
        flex-direction: column;
        width: min(1080px, 100% - 40px);
        margin: 0 auto;
        padding: 22px 0 40px;
      }

      /* ---------- Header ---------- */
      .pain__top {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 14px;
      }
      .pain__brand {
        display: inline-flex;
        align-items: center;
        gap: 10px;
      }
      .pain__brand-name {
        font-family: 'Space Grotesk', var(--font-display);
        font-weight: 800;
        font-size: 1.15rem;
        letter-spacing: -0.02em;
      }
      .pain__brand-calls {
        color: var(--color-text-strong);
      }
      .pain__brand-agents {
        color: var(--color-primary);
      }
      .pain__badge {
        margin-left: auto;
        display: inline-flex;
        align-items: center;
        gap: 7px;
        padding: 6px 14px;
        border-radius: 999px;
        background: rgba(0, 168, 107, 0.1);
        border: 1px solid rgba(0, 168, 107, 0.35);
        color: var(--color-primary-hover);
        font-size: 0.75rem;
        font-weight: 600;
      }
      .pain__badge-dot {
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background: var(--color-primary);
        animation: badge-pulse 2s ease-out infinite;
      }
      @keyframes badge-pulse {
        0% { box-shadow: 0 0 0 0 rgba(0, 168, 107, 0.5); }
        70% { box-shadow: 0 0 0 7px rgba(0, 168, 107, 0); }
        100% { box-shadow: 0 0 0 0 rgba(0, 168, 107, 0); }
      }
      .pain__clock {
        font-variant-numeric: tabular-nums;
        font-size: 0.82rem;
        color: #94a3b8;
        border: 1px solid rgba(255, 255, 255, 0.1);
        background: rgba(255, 255, 255, 0.04);
        padding: 6px 12px;
        border-radius: 999px;
        flex-shrink: 0;
      }

      /* ---------- Body ---------- */
      .pain__body {
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: center;
        gap: 36px;
        padding: 40px 0 20px;
      }
      .pain__title {
        font-family: 'Space Grotesk', var(--font-display);
        font-size: clamp(1.7rem, 4.4vw, 3.1rem);
        font-weight: 700;
        letter-spacing: -0.02em;
        line-height: 1.12;
        color: #f8fafc;
        margin: 0 0 14px;
        max-width: 820px;
      }
      .pain__title-accent {
        background: linear-gradient(90deg, var(--color-primary), var(--color-primary-hover));
        background-size: 200% auto;
        -webkit-background-clip: text;
        background-clip: text;
        color: transparent;
        animation: gradient-shift 5s ease infinite;
      }
      @keyframes gradient-shift {
        0%, 100% { background-position: 0% center; }
        50% { background-position: 100% center; }
      }
      .pain__sub {
        color: #94a3b8;
        font-size: clamp(0.95rem, 1.4vw, 1.1rem);
        max-width: 640px;
        margin: 0;
      }

      /* ---------- Pain cards ---------- */
      .pain__cards {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
        gap: 16px;
      }
      .pain__card {
        position: relative;
        overflow: hidden;
        text-align: left;
        display: flex;
        flex-direction: column;
        gap: 10px;
        padding: 20px;
        border-radius: 20px;
        background: rgba(13, 19, 31, 0.92);
        border: 1px solid rgba(255, 255, 255, 0.08);
        color: inherit;
        animation: card-in 0.55s cubic-bezier(0.22, 1, 0.36, 1) both;
        transition: transform 300ms cubic-bezier(0.22, 1, 0.36, 1), box-shadow 300ms cubic-bezier(0.22, 1, 0.36, 1), border-color 300ms cubic-bezier(0.22, 1, 0.36, 1);
      }
      .pain__card:nth-child(2) { animation-delay: 80ms; }
      .pain__card:nth-child(3) { animation-delay: 160ms; }
      .pain__card:nth-child(4) { animation-delay: 240ms; }
      .pain__card:hover {
        transform: translateY(-4px);
        border-color: rgba(255, 255, 255, 0.18);
        background: rgba(13, 19, 31, 0.92);
      }
      .pain__card:active {
        transform: translateY(-2px) scale(0.98);
        transition-duration: 120ms;
        background: rgba(13, 19, 31, 0.92);
      }
      .pain__card:focus-visible {
        outline: none;
        box-shadow: 0 0 0 3px rgba(0, 168, 107, 0.4);
      }
      @keyframes card-in {
        from { opacity: 0; transform: translateY(18px); }
      }
      .pain__card-icon {
        width: 44px;
        height: 44px;
        border-radius: 12px;
        display: grid;
        place-items: center;
        background: rgba(255, 255, 255, 0.06);
        border: 1px solid rgba(255, 255, 255, 0.12);
        color: #cbd5e1;
      }
      .pain__card-title {
        font-family: 'Space Grotesk', var(--font-display);
        font-size: 1.02rem;
        font-weight: 600;
        color: #f8fafc;
      }
      .pain__card-copy {
        font-size: 0.83rem;
        line-height: 1.5;
        color: #94a3b8;
      }

      /* ---------- Simulation ---------- */
      .pain__sim {
        width: 100%;
        max-width: 720px;
        margin: 0 auto;
        display: flex;
        flex-direction: column;
        gap: 22px;
        animation: card-in 0.45s cubic-bezier(0.22, 1, 0.36, 1) both;
      }
      .pain__sim-reset {
        align-self: flex-start;
        display: inline-flex;
        align-items: center;
        gap: 6px;
        background: none;
        border: 1px solid rgba(255, 255, 255, 0.1);
        color: #94a3b8;
        border-radius: 999px;
        padding: 6px 14px;
        font-size: 0.78rem;
        transition: color 300ms, border-color 300ms;
      }
      .pain__sim-reset:hover {
        color: #e2e8f0;
        border-color: rgba(255, 255, 255, 0.22);
        background: none;
      }
      .pain__sim-reset:focus-visible {
        outline: none;
        box-shadow: 0 0 0 3px rgba(0, 168, 107, 0.35);
      }
      .pain__sim-title {
        font-family: 'Space Grotesk', var(--font-display);
        font-size: clamp(1.4rem, 3vw, 2rem);
        font-weight: 700;
        letter-spacing: -0.015em;
        color: #f8fafc;
        margin: 0;
      }
      .pain__sim-copy {
        color: #94a3b8;
        margin: -12px 0 0;
        font-size: 0.95rem;
      }
      .pain__steps {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 10px;
      }
      .pain__step {
        display: flex;
        align-items: center;
        gap: 12px;
        font-size: 0.92rem;
        color: #64748b;
        transition: color 400ms;
      }
      .pain__step-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        border: 2px solid #475569;
        flex-shrink: 0;
        transition: background-color 400ms, border-color 400ms, box-shadow 400ms;
      }
      .pain__step--on {
        color: #e2e8f0;
      }
      .pain__step--on .pain__step-dot {
        background: var(--color-primary);
        border-color: var(--color-primary);
        box-shadow: 0 0 12px rgba(0, 168, 107, 0.6);
      }
      .pain__step--lost {
        color: #f87171;
        font-weight: 700;
      }
      .pain__step--lost .pain__step-dot {
        background: #ef4444;
        border-color: #ef4444;
        box-shadow: 0 0 14px rgba(239, 68, 68, 0.6);
        animation: lost-pulse 1s ease-in-out infinite;
      }
      @keyframes lost-pulse {
        50% { box-shadow: 0 0 24px rgba(239, 68, 68, 0.95); }
      }
      .pain__metrics {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 14px;
      }
      .pain__metric {
        background: rgba(13, 19, 31, 0.6);
        backdrop-filter: blur(20px);
        -webkit-backdrop-filter: blur(20px);
        border: 1px solid rgba(255, 255, 255, 0.08);
        border-radius: 16px;
        padding: 16px 18px;
        display: flex;
        flex-direction: column;
        gap: 6px;
      }
      .pain__metric-label {
        font-size: 0.72rem;
        text-transform: uppercase;
        letter-spacing: 0.06em;
        color: #64748b;
      }
      .pain__metric-value {
        font-family: 'Space Grotesk', var(--font-display);
        font-size: clamp(1.5rem, 3.4vw, 2.2rem);
        font-weight: 700;
        font-variant-numeric: tabular-nums;
        color: #f8fafc;
        transition: color 400ms, text-shadow 400ms;
      }
      .pain__metric-value--loss {
        color: #f87171;
      }
      .pain__metric-value--final {
        color: #ef4444;
        text-shadow: 0 0 24px rgba(239, 68, 68, 0.4);
      }
      .pain__bar {
        height: 12px;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.06);
        border: 1px solid rgba(255, 255, 255, 0.07);
        overflow: hidden;
      }
      .pain__bar-fill {
        display: block;
        height: 100%;
        width: 0;
        border-radius: 999px;
        background: linear-gradient(90deg, #f59e0b, #ef4444);
        box-shadow: 0 0 18px rgba(239, 68, 68, 0.5);
        transition: width 120ms linear;
      }

      /* ---------- CTA ---------- */
      .pain__cta {
        position: relative;
        overflow: hidden;
        align-self: center;
        display: inline-flex;
        align-items: center;
        gap: 10px;
        margin-top: 8px;
        padding: 16px 34px;
        border: none;
        border-radius: 999px;
        background: linear-gradient(135deg, var(--color-primary), var(--color-primary-hover));
        color: #02150c;
        font-family: 'Space Grotesk', var(--font-display);
        font-weight: 700;
        font-size: 1.02rem;
        box-shadow: 0 12px 40px rgba(0, 168, 107, 0.45);
        animation: cta-in 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) both;
        transition: transform 300ms cubic-bezier(0.22, 1, 0.36, 1), box-shadow 300ms cubic-bezier(0.22, 1, 0.36, 1);
      }
      .pain__cta:hover {
        transform: scale(1.04);
        box-shadow: 0 16px 52px rgba(0, 168, 107, 0.6);
      }
      .pain__cta:focus-visible {
        outline: none;
        box-shadow: 0 0 0 3px rgba(0, 168, 107, 0.45);
      }
      @keyframes cta-in {
        from { opacity: 0; transform: translateY(16px) scale(0.95); }
      }
      .pain__cta-shine {
        position: absolute;
        inset: 0;
        background: linear-gradient(100deg, transparent 30%, rgba(255, 255, 255, 0.4) 50%, transparent 70%);
        transform: translateX(-130%);
        animation: cta-shimmer 2.6s ease-in-out infinite;
      }
      @keyframes cta-shimmer {
        0%, 55% { transform: translateX(-130%); }
        85%, 100% { transform: translateX(130%); }
      }

      /* ---------- Responsive ---------- */
      @media (max-width: 640px) {
        .pain__metrics { grid-template-columns: 1fr; }
        .pain__body { justify-content: flex-start; padding-top: 30px; }
        .pain__badge { margin-left: 0; }
      }

      /* ---------- Reduced motion ---------- */
      @media (prefers-reduced-motion: reduce) {
        .pain__card,
        .pain__sim,
        .pain__cta {
          animation: none;
        }
        .pain__badge-dot,
        .pain__step--lost .pain__step-dot,
        .pain__title-accent,
        .pain__cta-shine {
          animation: none;
        }
      }
    `
  ]
})
export class DemoPainActComponent implements OnDestroy {
  readonly clock = input.required<string>();
  readonly selectPain = output<PainKey>();
  readonly continueToSolution = output<void>();

  protected readonly pains = PAINS;
  protected readonly simSteps = SIM_STEPS;

  protected readonly selected = signal<PainData | null>(null);
  protected readonly simRunning = signal(false);
  protected readonly simDone = signal(false);
  protected readonly stepIndex = signal(0);
  protected readonly progress = signal(0);
  protected readonly money = signal(0);
  protected readonly probability = signal(100);

  private tween: gsap.core.Tween | null = null;

  protected select(pain: PainData): void {
    if (this.simRunning()) {
      return;
    }
    this.selected.set(pain);
    this.selectPain.emit(pain.key);
    this.runSimulation(pain);
  }

  protected reset(): void {
    this.tween?.kill();
    this.selected.set(null);
    this.simRunning.set(false);
    this.simDone.set(false);
    this.progress.set(0);
  }

  protected roundedProgress(): number {
    return Math.round(this.progress());
  }

  protected moneyText(): string {
    const v = Math.abs(Math.round(this.money()));
    return `-€${v.toLocaleString('es-ES')}`;
  }

  protected probabilityText(): string {
    return `${this.probability().toFixed(1).replace('.', ',')}%`;
  }

  private runSimulation(pain: PainData): void {
    this.simRunning.set(true);
    this.simDone.set(false);
    this.stepIndex.set(0);
    this.progress.set(0);
    this.money.set(0);
    this.probability.set(100);

    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduce) {
      this.progress.set(100);
      this.money.set(-pain.loss);
      this.probability.set(pain.probability);
      this.stepIndex.set(SIM_STEPS.length - 1);
      this.simDone.set(true);
      return;
    }

    const proxy = { progress: 0, money: 0, probability: 100 };
    this.tween = gsap.to(proxy, {
      progress: 100,
      money: -pain.loss,
      probability: pain.probability,
      duration: SIM_DURATION,
      ease: 'power1.inOut',
      onUpdate: () => {
        this.progress.set(proxy.progress);
        this.money.set(proxy.money);
        this.probability.set(proxy.probability);
        let idx = 0;
        for (let i = 0; i < SIM_STEPS.length; i++) {
          if (proxy.progress >= SIM_STEPS[i].at) {
            idx = i;
          }
        }
        this.stepIndex.set(idx);
      },
      onComplete: () => {
        this.simDone.set(true);
      }
    });
  }

  ngOnDestroy(): void {
    this.tween?.kill();
  }
}