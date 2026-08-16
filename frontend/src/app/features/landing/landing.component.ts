import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  NgZone,
  OnDestroy,
  ViewChild
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { FooterComponent } from '../../core/layout/footer/footer.component';

/* ------------------------------------------------------------------
   Prototipo visual — landing "wow" tipo codex.io.
   Tema oscuro forzado en el host para que todo el interior (incluido
   el app-footer reutilizado) use la paleta dark del design system.
   ------------------------------------------------------------------ */

interface OrbParticle {
  angle: number;
  radius: number;
  speed: number;
  drift: number;
  size: number;
  baseAlpha: number;
  phase: number;
}

interface LandingStep {
  number: string;
  title: string;
  text: string;
}

const ORB_GREEN = '0,168,107';
const SPEAK_MS = 6000;
const LISTEN_MS = 3000;
const RING_COUNT = 3;
const RING_BASES = [0.5, 0.64, 0.78];
const RING_AMPS = [0.035, 0.05, 0.065];
const RING_ALPHAS = [0.25, 0.4, 0.6];
const RING_SPEEDS = [0.9, 1.15, 1.4];
const RING_PHASES = [0, 1.2, 2.4];
const PARTICLE_COUNT = 42;

@Component({
  selector: 'app-landing',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FooterComponent],
  host: { 'data-theme': 'dark' },
  template: `
    <nav class="lnd-nav">
      <div class="lnd-container lnd-nav-inner">
        <a class="lnd-logo" routerLink="/landing" aria-label="Callsagents — inicio">
          <span class="lnd-logo-wordmark">
            <span class="lnd-logo-calls">CALLS</span><span class="lnd-logo-agents">AGENTS</span>
          </span>
        </a>

        <div class="lnd-nav-links">
          <a href="#producto" (click)="onAnchor($event, '#producto')">Producto</a>
          <a href="#como-funciona" (click)="onAnchor($event, '#como-funciona')"
            >Cómo funciona</a
          >
          <a href="#demo" (click)="onAnchor($event, '#demo')">Demo</a>
        </div>

        <a class="lnd-btn lnd-btn--ghost" routerLink="/login">Entrar</a>
      </div>
    </nav>

    <header class="lnd-hero" id="producto">
      <div class="lnd-container lnd-hero-grid">
        <div class="lnd-hero-copy">
          <span class="lnd-badge">
            <span class="lnd-badge-dot" aria-hidden="true"></span>
            Agentes de voz y chat con IA
          </span>

          <h1 class="lnd-hero-title">
            <span class="lnd-line"
              ><span class="lnd-line-inner">Tu negocio no duerme.</span></span
            >
            <span class="lnd-line"
              ><span class="lnd-line-inner">Tus <span class="lnd-accent">agentes, tampoco.</span></span
              ></span
            >
          </h1>

          <p class="lnd-hero-sub">
            Agentes de voz y chat que llaman, cualifican y agendan por ti — 24/7, en español
            nativo.
          </p>

          <div class="lnd-hero-ctas">
            <a class="lnd-btn lnd-btn--primary" routerLink="/login">Probar demo gratis</a>
            <a class="lnd-btn lnd-btn--outline" routerLink="/login">Ver panel de control</a>
          </div>

          <div class="lnd-stats">
            <div class="lnd-stat">
              <div class="lnd-stat-num">24/7</div>
              <div class="lnd-stat-label">llamadas automáticas</div>
            </div>
            <div class="lnd-stat">
              <div class="lnd-stat-num">+40%</div>
              <div class="lnd-stat-label">tasa de conexión</div>
            </div>
            <div class="lnd-stat">
              <div class="lnd-stat-num">10x</div>
              <div class="lnd-stat-label">menos coste por lead</div>
            </div>
          </div>
        </div>

        <div class="lnd-orb" aria-hidden="true">
          <canvas #voiceOrb class="lnd-orb-canvas"></canvas>
        </div>
      </div>
    </header>

    <section class="lnd-section" id="como-funciona">
      <div class="lnd-container">
        <h2 class="lnd-section-title">Cómo funciona</h2>
        <p class="lnd-section-sub">De tus guiones a citas agendadas, sin que suene un teléfono humano.</p>

        <div class="lnd-grid">
          @for (step of steps; track step.number) {
            <article class="lnd-card">
              <span class="lnd-card-num">{{ step.number }}</span>
              <h3 class="lnd-card-title">{{ step.title }}</h3>
              <p class="lnd-card-text">{{ step.text }}</p>
            </article>
          }
        </div>
      </div>
    </section>

    <section class="lnd-cta" id="demo">
      <div class="lnd-container lnd-cta-inner">
        <h2 class="lnd-cta-title">¿Listo para vender 24/7?</h2>
        <p class="lnd-cta-sub">Tu equipo descansa. Tus agentes, no.</p>
        <a class="lnd-btn lnd-btn--primary lnd-btn--lg" routerLink="/login">Probar demo gratis</a>
      </div>
    </section>

    <app-footer></app-footer>
  `,
  styles: [
    `
      /* Google Fonts se cargan en runtime (ver loadFonts): el builder inlinea
         las fuentes referenciadas en estilos y revienta el budget
         anyComponentStyle de angular.json (8 kB), que no podemos tocar. */
      :host {
        display: block;
        overflow-x: hidden;
        background: var(--color-bg);
        color: var(--color-text);
        font-family: var(--font-sans);
        scroll-behavior: smooth;
        --font-display: 'Space Grotesk', var(--font-sans);
      }

      .lnd-container {
        max-width: 1200px;
        margin: 0 auto;
        padding: 0 clamp(1rem, 4vw, 3rem);
      }

      /* ─── Nav ─────────────────────────────────────────── */
      .lnd-nav {
        position: sticky;
        top: 0;
        z-index: 50;
        background: var(--color-bg);
        background: color-mix(in srgb, var(--color-bg) 78%, transparent);
        backdrop-filter: blur(12px);
        border-bottom: 1px solid var(--color-border);
      }

      .lnd-nav-inner {
        display: flex;
        align-items: center;
        gap: var(--spacing-6);
        min-height: 72px;
      }

      .lnd-logo {
        display: inline-flex;
        align-items: center;
        gap: var(--spacing-2);
        text-decoration: none;
        color: var(--color-text-strong);
        margin-right: auto;
      }

      .lnd-logo:hover {
        text-decoration: none;
      }

      .lnd-logo-wordmark {
        font-family: var(--font-display);
        font-size: 1.25rem;
        font-weight: 800;
        letter-spacing: -0.02em;
      }

      .lnd-logo-calls {
        color: var(--color-text-strong);
      }

      .lnd-logo-agents {
        color: var(--color-primary);
      }

      .lnd-nav-links {
        display: flex;
        align-items: center;
        gap: var(--spacing-6);
      }

      .lnd-nav-links a {
        font-size: 0.9rem;
        font-weight: 500;
        color: var(--color-text-muted);
        text-decoration: none;
        transition: color 0.2s ease;
      }

      .lnd-nav-links a:hover {
        color: var(--color-text);
      }

      /* ─── Buttons ─────────────────────────────────────── */
      .lnd-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: var(--spacing-2);
        padding: 0.8rem 1.5rem;
        border: 1px solid transparent;
        border-radius: var(--radius-lg);
        font-family: var(--font-sans);
        font-size: 0.95rem;
        font-weight: 600;
        text-decoration: none;
        cursor: pointer;
        transition: transform 0.25s ease, background-color 0.25s ease, border-color 0.25s ease,
          box-shadow 0.25s ease, color 0.25s ease;
      }

      .lnd-btn:hover {
        transform: translateY(-2px);
        text-decoration: none;
      }

      .lnd-btn--primary {
        background: var(--color-primary);
        color: var(--color-on-primary);
      }

      .lnd-btn--primary:hover {
        background: var(--color-primary-hover);
        box-shadow: 0 8px 28px color-mix(in srgb, var(--color-primary) 30%, transparent);
      }

      .lnd-btn--outline {
        background: transparent;
        border-color: var(--color-border);
        color: var(--color-text);
      }

      .lnd-btn--outline:hover {
        border-color: var(--color-primary);
        color: var(--color-text-strong);
      }

      .lnd-btn--ghost {
        background: transparent;
        color: var(--color-text-muted);
        padding: 0.5rem 1rem;
      }

      .lnd-btn--ghost:hover {
        color: var(--color-text);
        border-color: var(--color-border);
        background: var(--color-bg-alt);
      }

      .lnd-btn--lg {
        padding: 1rem 2rem;
        font-size: 1.05rem;
      }

      /* ─── Hero ────────────────────────────────────────── */
      .lnd-hero {
        min-height: 92vh;
        display: flex;
        align-items: center;
        padding: clamp(4rem, 10vh, 7rem) 0;
        scroll-margin-top: 72px;
      }

      .lnd-hero-grid {
        display: grid;
        grid-template-columns: 1.05fr 0.95fr;
        gap: clamp(2rem, 5vw, 4rem);
        align-items: center;
        width: 100%;
      }

      .lnd-badge {
        display: inline-flex;
        align-items: center;
        gap: var(--spacing-2);
        padding: 0.4rem 0.9rem;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-full);
        background: var(--color-bg-alt);
        color: var(--color-text-muted);
        font-size: 0.8rem;
        font-weight: 500;
        letter-spacing: 0.02em;
        margin-bottom: var(--spacing-5);
      }

      .lnd-badge-dot {
        width: 8px;
        height: 8px;
        border-radius: var(--radius-full);
        background: var(--color-primary);
        animation: lnd-pulse 2.2s ease-out infinite;
      }

      @keyframes lnd-pulse {
        0% {
          box-shadow: 0 0 0 0 rgba(0, 168, 107, 0.5);
        }
        70% {
          box-shadow: 0 0 0 9px rgba(0, 168, 107, 0);
        }
        100% {
          box-shadow: 0 0 0 0 rgba(0, 168, 107, 0);
        }
      }

      .lnd-hero-title {
        font-family: var(--font-display);
        font-size: clamp(2.75rem, 7vw, 5.5rem);
        line-height: 1.05;
        letter-spacing: -0.02em;
        font-weight: 700;
        margin: 0 0 var(--spacing-5);
        color: var(--color-text-strong);
      }

      .lnd-accent {
        color: var(--color-primary);
      }

      .lnd-line {
        display: block;
        overflow: hidden;
      }

      .lnd-line-inner {
        display: inline-block;
        transform: translateY(110%);
        transition: transform 0.85s cubic-bezier(0.22, 1, 0.36, 1);
      }

      .lnd-hero-title.revealed .lnd-line-inner {
        transform: translateY(0);
      }

      .lnd-hero-sub {
        max-width: 640px;
        color: var(--color-text-muted);
        font-size: clamp(1rem, 1.6vw, 1.2rem);
        line-height: 1.6;
        margin: 0 0 var(--spacing-6);
      }

      .lnd-hero-ctas {
        display: flex;
        flex-wrap: wrap;
        gap: var(--spacing-4);
        margin-bottom: var(--spacing-8);
      }

      .lnd-stats {
        display: flex;
        flex-wrap: wrap;
        gap: var(--spacing-8) var(--spacing-6);
        border-top: 1px solid var(--color-border);
        padding-top: var(--spacing-6);
        max-width: 560px;
      }

      .lnd-stat {
        min-width: 120px;
      }

      .lnd-stat-num {
        font-family: var(--font-display);
        font-size: clamp(1.5rem, 3vw, 2rem);
        font-weight: 700;
        letter-spacing: -0.02em;
        color: var(--color-text-strong);
        line-height: 1.1;
        margin-bottom: var(--spacing-1);
      }

      .lnd-stat-label {
        color: var(--color-text-muted);
        font-size: 0.85rem;
      }

      /* ─── Voice orb ───────────────────────────────────── */
      .lnd-orb {
        width: 100%;
        max-width: 420px;
        aspect-ratio: 1 / 1;
        margin-inline: auto;
        pointer-events: none;
      }

      .lnd-orb-canvas {
        display: block;
        width: 100%;
        height: 100%;
      }

      /* ─── Sections ────────────────────────────────────── */
      .lnd-section {
        padding: clamp(4rem, 10vw, 6.5rem) 0;
        scroll-margin-top: 72px;
      }

      .lnd-section-title {
        font-family: var(--font-display);
        font-size: clamp(2rem, 4vw, 2.75rem);
        font-weight: 700;
        letter-spacing: -0.02em;
        color: var(--color-text-strong);
        text-align: center;
        margin: 0 0 var(--spacing-3);
      }

      .lnd-section-sub {
        text-align: center;
        color: var(--color-text-muted);
        font-size: 1.05rem;
        max-width: 520px;
        margin: 0 auto var(--spacing-8);
      }

      .lnd-grid {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: var(--spacing-6);
      }

      .lnd-card {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: var(--spacing-6);
        transition: transform 0.25s ease, border-color 0.25s ease, box-shadow 0.25s ease;
      }

      .lnd-card:hover {
        transform: translateY(-2px);
        border-color: var(--color-primary);
        box-shadow: var(--shadow-md);
      }

      .lnd-card-num {
        display: block;
        font-family: var(--font-display);
        font-size: 2.25rem;
        font-weight: 700;
        line-height: 1;
        color: var(--color-text-subtle);
        -webkit-text-stroke: 1px var(--color-border-strong);
        margin-bottom: var(--spacing-6);
      }

      .lnd-card-title {
        font-family: var(--font-display);
        font-size: 1.2rem;
        font-weight: 700;
        letter-spacing: -0.01em;
        color: var(--color-text-strong);
        margin-bottom: var(--spacing-2);
      }

      .lnd-card-text {
        color: var(--color-text-muted);
        font-size: 0.95rem;
        line-height: 1.6;
        margin: 0;
      }

      /* ─── Final CTA ───────────────────────────────────── */
      .lnd-cta {
        position: relative;
        overflow: hidden;
        text-align: center;
        padding: clamp(4rem, 12vw, 7rem) 0;
        scroll-margin-top: 72px;
      }

      .lnd-cta::before {
        content: '';
        position: absolute;
        inset: 0;
        background: radial-gradient(
          ellipse 60% 70% at 50% 30%,
          rgba(0, 168, 107, 0.12),
          transparent 70%
        );
        pointer-events: none;
      }

      .lnd-cta-inner {
        position: relative;
      }

      .lnd-cta-title {
        font-family: var(--font-display);
        font-size: clamp(2.25rem, 5vw, 3.75rem);
        font-weight: 700;
        letter-spacing: -0.02em;
        color: var(--color-text-strong);
        margin: 0 0 var(--spacing-3);
      }

      .lnd-cta-sub {
        color: var(--color-text-muted);
        font-size: 1.05rem;
        margin: 0 0 var(--spacing-6);
      }

      /* ─── Responsive ──────────────────────────────────── */
      @media (max-width: 900px) {
        .lnd-hero {
          min-height: auto;
        }

        .lnd-hero-grid {
          grid-template-columns: 1fr;
          text-align: center;
        }

        .lnd-hero-copy {
          display: flex;
          flex-direction: column;
          align-items: center;
        }

        .lnd-hero-sub {
          max-width: 600px;
        }

        .lnd-hero-ctas {
          justify-content: center;
        }

        .lnd-stats {
          justify-content: center;
          max-width: none;
        }

        .lnd-grid {
          grid-template-columns: repeat(2, 1fr);
        }
      }

      @media (max-width: 640px) {
        .lnd-nav-links {
          display: none;
        }

        .lnd-grid {
          grid-template-columns: 1fr;
        }

        .lnd-hero-ctas .lnd-btn {
          width: 100%;
        }

        .lnd-stats {
          flex-direction: column;
          align-items: center;
        }

        .lnd-stat {
          text-align: center;
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .lnd-line-inner {
          transform: none;
          transition: none;
        }

        .lnd-badge-dot {
          animation: none;
        }
      }
    `
  ]
})
export class LandingComponent implements AfterViewInit, OnDestroy {
  private readonly el = inject(ElementRef<HTMLElement>);
  private readonly ngZone = inject(NgZone);

  @ViewChild('voiceOrb') private readonly orbCanvas?: ElementRef<HTMLCanvasElement>;

  protected readonly steps: LandingStep[] = [
    {
      number: '01',
      title: 'Sube tus guiones',
      text: 'El agente lee tu tono, tu oferta y tus objeciones. Solo copia y pega lo que ya les dices a tus clientes.'
    },
    {
      number: '02',
      title: 'Lanza la campaña',
      text: 'Callsagents llama a tu base de leads por ti, en paralelo y 24/7, hablando en español nativo.'
    },
    {
      number: '03',
      title: 'Recibe citas y leads',
      text: 'Cada conversación queda transcrita y los resultados llegan a tu panel en tiempo real.'
    }
  ];

  private particles: OrbParticle[] = [];
  private orbSize = 420;
  private animationFrame = 0;
  private resizeObserver?: ResizeObserver;

  ngAfterViewInit(): void {
    this.loadFonts();
    this.resizeCanvas();
    this.particles = this.createParticles();
    this.observeResize();
    this.initTextReveal();
    this.startOrb();
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.animationFrame);
    this.resizeObserver?.disconnect();
  }

  /* ─── Google Fonts (Space Grotesk + Inter, pesos completos) ── */
  private loadFonts(): void {
    if (document.getElementById('lnd-google-fonts')) {
      return;
    }
    const link = document.createElement('link');
    link.id = 'lnd-google-fonts';
    link.rel = 'stylesheet';
    link.href =
      'https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@500;700&family=Inter:wght@400;500;600&display=swap';
    document.head.appendChild(link);
  }

  /* ─── Anchor scroll (smooth, scoped) ──────────────────── */
  protected onAnchor(event: Event, hash: string): void {
    const host = this.el.nativeElement as HTMLElement;
    const target = host.querySelector(hash) as HTMLElement | null;
    if (!target) {
      return;
    }
    event.preventDefault();
    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /* ─── H1 mask reveal ──────────────────────────────────── */
  private initTextReveal(): void {
    const host = this.el.nativeElement as HTMLElement;
    const title = host.querySelector('.lnd-hero-title') as HTMLElement | null;
    if (!title) {
      return;
    }
    const lines = Array.from(title.querySelectorAll<HTMLElement>('.lnd-line-inner'));
    lines.forEach((line, index) => {
      line.style.transitionDelay = `${index * 130}ms`;
    });

    const reveal = (): void => title.classList.add('revealed');
    const fallback = window.setTimeout(reveal, 1500);

    if (typeof IntersectionObserver === 'undefined') {
      reveal();
      return;
    }

    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        window.clearTimeout(fallback);
        reveal();
        observer.disconnect();
      }
    }, { threshold: 0.3 });
    observer.observe(title);
  }

  /* ─── Voice orb (canvas 2D, puro) ─────────────────────── */
  private createParticles(): OrbParticle[] {
    return Array.from({ length: PARTICLE_COUNT }, () => ({
      angle: Math.random() * Math.PI * 2,
      radius: 0.36 + Math.random() * 0.62,
      speed: (Math.random() * 0.0009 - 0.00045) * (Math.random() > 0.5 ? 1 : -1),
      drift: Math.random() * 0.00012 - 0.00006,
      size: 1 + Math.random() * 1.6,
      baseAlpha: 0.2 + Math.random() * 0.5,
      phase: Math.random() * Math.PI * 2
    }));
  }

  private resizeCanvas(): void {
    const canvas = this.orbCanvas?.nativeElement;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) {
      return;
    }
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const size = canvas.clientWidth || 420;
    this.orbSize = size;
    canvas.width = Math.round(size * dpr);
    canvas.height = Math.round(size * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  private observeResize(): void {
    const canvas = this.orbCanvas?.nativeElement;
    if (!canvas || typeof ResizeObserver === 'undefined') {
      return;
    }
    this.resizeObserver = new ResizeObserver(() => this.resizeCanvas());
    this.resizeObserver.observe(canvas);
  }

  private startOrb(): void {
    this.ngZone.runOutsideAngular(() => {
      const canvas = this.orbCanvas?.nativeElement;
      const ctx = canvas?.getContext('2d');
      if (!canvas || !ctx) {
        return;
      }
      const start = performance.now();
      let last = start;

      const frame = (now: number): void => {
        const dt = Math.min(now - last, 50);
        last = now;
        this.drawOrb(ctx, now - start, dt);
        this.animationFrame = requestAnimationFrame(frame);
      };

      this.animationFrame = requestAnimationFrame(frame);
    });
  }

  private drawOrb(ctx: CanvasRenderingContext2D, t: number, dt: number): void {
    const size = this.orbSize;
    const cx = size / 2;
    const cy = size / 2;
    const R = size * 0.42;

    const speakPhase = (t % SPEAK_MS) / SPEAK_MS;
    const speakBurst = Math.exp(-speakPhase * 7);
    const listenPhase = (t % LISTEN_MS) / LISTEN_MS;
    const listenBurst = Math.exp(-listenPhase * 9) * 0.55;

    ctx.clearRect(0, 0, size, size);

    // Núcleo con glow
    const coreR = R * (0.24 + 0.02 * Math.sin(t * 0.001 * 0.8) + speakBurst * 0.05);
    const glowR = coreR * 2.4;
    const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, glowR);
    gradient.addColorStop(0, `rgba(${ORB_GREEN},0.6)`);
    gradient.addColorStop(0.45, `rgba(${ORB_GREEN},0.18)`);
    gradient.addColorStop(1, `rgba(${ORB_GREEN},0)`);

    ctx.save();
    ctx.shadowColor = `rgba(${ORB_GREEN},0.5)`;
    ctx.shadowBlur = R * 0.4 + speakBurst * 40;
    ctx.fillStyle = gradient;
    ctx.beginPath();
    ctx.arc(cx, cy, glowR, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();

    // 3 anillos concéntricos que respiran
    for (let i = 0; i < RING_COUNT; i++) {
      const breathe = Math.sin(t * 0.001 * RING_SPEEDS[i] + RING_PHASES[i]);
      let radius = R * (RING_BASES[i] + RING_AMPS[i] * breathe);
      radius += R * 0.2 * speakBurst + R * 0.05 * listenBurst;
      const alpha = Math.min(RING_ALPHAS[i] + speakBurst * 0.3 + listenBurst * 0.18, 0.85);

      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.strokeStyle = `rgba(${ORB_GREEN},${alpha.toFixed(3)})`;
      ctx.lineWidth = 1.1;
      ctx.stroke();
    }

    // Partículas orbitando
    for (const particle of this.particles) {
      particle.angle += particle.speed * dt;
      particle.radius += particle.drift * dt;
      if (particle.radius < 0.34) {
        particle.radius = 0.34;
      }
      if (particle.radius > 1.02) {
        particle.radius = 1.02;
      }

      const pRadius = (particle.radius + speakBurst * 0.07) * R;
      const x = cx + Math.cos(particle.angle) * pRadius;
      const y = cy + Math.sin(particle.angle) * pRadius * 0.94;
      const alpha =
        particle.baseAlpha * (0.7 + 0.3 * Math.sin(t * 0.001 + particle.phase)) +
        speakBurst * 0.25;

      ctx.beginPath();
      ctx.arc(x, y, particle.size, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(${ORB_GREEN},${Math.min(alpha, 0.95).toFixed(3)})`;
      ctx.fill();
    }
  }
}
