import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  OnDestroy,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { FooterComponent } from '../../core/layout/footer/footer.component';
import { PhoneMockupComponent } from './phone-mockup/phone-mockup.component';

/* ------------------------------------------------------------------
   Prototipo visual — landing "wow" tipo codex.io.
   Tema oscuro forzado en el host para que todo el interior (incluido
   el app-footer reutilizado) use la paleta dark del design system.
   ------------------------------------------------------------------ */

interface LandingStep {
  number: string;
  title: string;
  text: string;
}

interface FaqItem {
  q: string;
  a: string;
}

const ORB_GREEN = '0,168,107';

@Component({
  selector: 'app-landing',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FooterComponent, PhoneMockupComponent],
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
          <a href="#faq" (click)="onAnchor($event, '#faq')">FAQ</a>
        </div>

        <a class="lnd-btn lnd-btn--ghost" routerLink="/login">Entrar</a>
      </div>
    </nav>

    <header class="lnd-hero" id="producto">
      <div class="lnd-container lnd-hero-grid">
        <div class="lnd-hero-copy">
          <span class="lnd-badge">
            <span class="lnd-badge-dot" aria-hidden="true"></span>
            Atención automática para tu negocio
          </span>

          <h1 class="lnd-hero-title">
            <span class="lnd-line"
              ><span class="lnd-line-inner">Tu negocio responde</span></span
            >
            <span class="lnd-line"
              ><span class="lnd-line-inner">cuando <span class="lnd-accent">ya es tarde.</span></span
              ></span
            >
          </h1>

          <p class="lnd-hero-sub">
            CallsAgents atiende cada solicitud en menos de un minuto, cualifica al lead
            y agenda la cita — sin que tu equipo deje de hacer lo que mejor sabe hacer.
          </p>

          <div class="lnd-hero-ctas">
            <a class="lnd-btn lnd-btn--primary lnd-btn--hero" routerLink="/login">Probar demo</a>
          </div>

          <p class="lnd-demo-creds">
            Cuenta demo: demo&#64;callsagents.com · contraseña: demo12345
          </p>

          <div class="lnd-stats">
            <div class="lnd-stat">
              <div class="lnd-stat-num">&lt;1 min</div>
              <div class="lnd-stat-label">respuesta media</div>
            </div>
            <div class="lnd-stat">
              <div class="lnd-stat-num">24/7</div>
              <div class="lnd-stat-label">disponibilidad</div>
            </div>
            <div class="lnd-stat">
              <div class="lnd-stat-num">0</div>
              <div class="lnd-stat-label">leads perdidos por demora</div>
            </div>
          </div>
        </div>

        <div class="lnd-hero-visual">
          <app-phone-mockup></app-phone-mockup>
        </div>
      </div>
    </header>

    <section class="lnd-section" id="como-funciona">
      <div class="lnd-container">
        <h2 class="lnd-section-title">Cómo funciona</h2>
        <p class="lnd-section-sub">De solicitud a cita en tu calendario, sin intervención manual.</p>

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

    <section class="lnd-section" id="faq">
      <div class="lnd-container">
        <h2 class="lnd-section-title">Preguntas frecuentes</h2>
        <p class="lnd-section-sub">Resolvemos tus dudas antes de que las tengas.</p>

        <div class="lnd-grid">
          @for (item of faqs; track item.q) {
            <article class="lnd-card">
              <h3 class="lnd-card-title">{{ item.q }}</h3>
              <p class="lnd-card-text">{{ item.a }}</p>
            </article>
          }
        </div>
      </div>
    </section>

    <section class="lnd-cta" id="demo">
      <div class="lnd-container lnd-cta-inner">
        <h2 class="lnd-cta-title">¿Listo para que tu negocio responda en menos de un minuto?</h2>
        <p class="lnd-cta-sub">Empieza gratis. Sin tarjeta. Sin compromiso.</p>
        <a class="lnd-btn lnd-btn--primary lnd-btn--lg lnd-btn--hero" routerLink="/login">Probar demo</a>
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

      .lnd-btn--hero {
        padding: 1rem 2.5rem;
        font-size: 1.1rem;
        font-weight: 600;
        border-radius: var(--radius-full);
      }

      .lnd-btn--hero:hover {
        transform: translateY(-2px);
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

      .lnd-demo-creds {
        margin: calc(-1 * var(--spacing-4)) 0 var(--spacing-8);
        color: var(--color-text-muted);
        font-size: 0.8rem;
        font-family: var(--font-mono, monospace);
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

      /* ─── Hero visual (phone mockup) ─────────────────── */
      .lnd-hero-visual {
        width: 100%;
        max-width: 320px;
        margin-inline: auto;
        pointer-events: none;
      }

      .lnd-hero-visual app-phone-mockup {
        display: block;
        width: 100%;
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
  private textRevealObserver: IntersectionObserver | null = null;

  protected readonly steps: LandingStep[] = [
    {
      number: '01',
      title: 'El lead te contacta',
      text: 'Rellena tu formulario o te contacta. CallsAgents le responde en menos de un minuto, con su nombre y los datos de su solicitud.'
    },
    {
      number: '02',
      title: 'Se hace la pregunta correcta',
      text: 'Pregunta qué necesita, cuándo quiere empezar y si necesita información sobre precios, disponibilidad o requisitos.'
    },
    {
      number: '03',
      title: 'Cita en tu calendario',
      text: 'Si hay interés, reserva la cita con el responsable. Si no responde, le llama automáticamente al día siguiente.'
    }
  ];

  protected readonly faqs = [
    {
      q: '¿Necesito saber programar?',
      a: 'No. Lo configuramos en 15 minutos con tu formulario y tu calendario.'
    },
    {
      q: '¿Funciona con mi web actual?',
      a: 'Sí. Se conecta con tu formulario existente, sin tocar nada en tu web.'
    },
    {
      q: '¿Y si el lead no responde al chat?',
      a: 'CallsAgents le llama al día siguiente para recuperar el contacto. No se pierde ningún lead.'
    },
    {
      q: '¿Puedo probarlo gratis?',
      a: 'Sí. 14 días sin tarjeta. Solo pagas si decides quedarte.'
    },
    {
      q: '¿La voz suena natural?',
      a: 'Sí. Habla en español con acento natural. Sin pausas raras ni robóticos.'
    },
    {
      q: '¿Cuánto cuesta?',
      a: 'Desde 49€/mes para negocios pequeños. Sin permanencia.'
    }
  ];

  ngAfterViewInit(): void {
    this.loadFonts();
    this.initTextReveal();
  }

  ngOnDestroy(): void {
    this.textRevealObserver?.disconnect();
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

    this.textRevealObserver = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        window.clearTimeout(fallback);
        reveal();
        this.textRevealObserver?.disconnect();
      }
    }, { threshold: 0.3 });
    this.textRevealObserver.observe(title);
  }
}
