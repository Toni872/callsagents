import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <footer class="ca-footer">
      <div class="ca-footer-inner">
        <div class="ca-footer-grid">
          <!-- ─── Brand ─────────────────────────────────── -->
          <div class="ca-footer-brand">
            <span class="ca-footer-wordmark" aria-label="Callsagents — Automatización de llamadas con IA">
              <span class="ca-footer-calls">CALLS</span><span class="ca-footer-agents">AGENTS</span>
            </span>
            <span class="ca-footer-tagline">AUTOMATIZACIÓN DE LLAMADAS CON IA</span>

            <p class="ca-footer-desc">
              Plataforma de automatización de llamadas con IA para la prospección, análisis y
              gestión de clientes. Convierte tu equipo en una máquina de ventas.
            </p>
          </div>

          <!-- ─── Legal ────────────────────────────────── -->
          <div class="ca-footer-legal">
            <nav class="ca-footer-legal-links" aria-label="Enlaces legales">
              <a [routerLink]="['/terms']">Términos</a>
              <a [routerLink]="['/privacy']">Privacidad</a>
            </nav>
            <p class="ca-footer-copy">
              © {{ year }} Callsagents · Todos los derechos reservados.
            </p>
          </div>

          <!-- ─── Script9 ──────────────────────────────── -->
          <div class="ca-footer-script9">
            <p class="ca-footer-madeby">Desarrollado con excelencia por</p>
            <a
              href="https://www.script-9.com"
              target="_blank"
              rel="noopener noreferrer"
              class="ca-footer-s9link"
            >
              <span class="ca-footer-s9">Script<span class="ca-footer-s9g">9</span></span>
            </a>
            <p class="ca-footer-s9desc">
              La factoría de software detrás de las soluciones más disruptivas. Ingeniería de
              vanguardia aplicada al crecimiento empresarial.
            </p>
          </div>
        </div>
      </div>
    </footer>
  `,
  styles: [
    `
      .ca-footer {
        background: var(--color-bg-alt);
        padding: 80px 48px 60px;
        border-top: 1px solid var(--color-border);
      }

      .ca-footer-inner {
        max-width: 1120px;
        margin: 0 auto;
      }

      .ca-footer-grid {
        display: grid;
        grid-template-columns: 1fr 1fr 1fr;
        gap: 40px;
      }

      .ca-footer-brand {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 12px;
      }

      .ca-footer-desc {
        font-size: 14.5px;
        color: var(--color-text-muted);
        line-height: 1.65;
        font-weight: 400;
        margin: 0;
        max-width: 320px;
      }

      .ca-footer-wordmark {
        font-family: var(--font-display), 'Inter', system-ui, sans-serif;
        font-size: 1.5rem;
        font-weight: 800;
        letter-spacing: -0.02em;
        line-height: 1;
        display: inline-flex;
      }

      .ca-footer-calls {
        color: var(--color-text-strong);
      }

      .ca-footer-agents {
        color: var(--color-primary);
      }

      .ca-footer-tagline {
        font-size: 10px;
        font-weight: 600;
        letter-spacing: 0.12em;
        color: var(--color-text-subtle);
      }

      .ca-footer-legal {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 16px;
      }

      .ca-footer-legal-links {
        display: flex;
        flex-direction: column;
        gap: 10px;
      }

      .ca-footer-legal-links a {
        font-size: 13px;
        font-weight: 500;
        color: var(--color-primary);
        text-decoration: none;
        transition: color 0.2s;
      }

      .ca-footer-legal-links a:hover {
        color: var(--color-primary-hover);
      }

      .ca-footer-copy {
        font-size: 11px;
        color: var(--color-text-subtle);
        font-weight: 500;
        margin: 0;
      }

      .ca-footer-script9 {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 6px;
        text-align: left;
      }

      .ca-footer-madeby {
        font-size: 9px;
        font-weight: 800;
        color: var(--color-text-subtle);
        letter-spacing: 1.5px;
        text-transform: uppercase;
        margin: 0;
      }

      .ca-footer-s9link {
        text-decoration: none;
      }

      .ca-footer-s9 {
        font-family: 'Inter', system-ui, sans-serif;
        font-weight: 800;
        font-size: 18px;
        letter-spacing: -0.8px;
        line-height: 1;
        color: var(--color-text-strong);
      }

      .ca-footer-s9g {
        color: var(--color-primary);
      }

      .ca-footer-s9desc {
        font-size: 11px;
        color: var(--color-text-muted);
        line-height: 1.4;
        margin: 0;
        max-width: 280px;
      }

      @media (max-width: 768px) {
        .ca-footer {
          padding: 60px 24px 40px;
        }

        .ca-footer-grid {
          grid-template-columns: 1fr;
          gap: 40px;
        }

        .ca-footer-brand,
        .ca-footer-legal,
        .ca-footer-script9 {
          align-items: center;
          text-align: center;
        }

        .ca-footer-desc {
          max-width: none;
        }

        .ca-footer-legal-links {
          align-items: center;
        }

        .ca-footer-s9desc {
          max-width: none;
        }
      }
    `
  ]
})
export class FooterComponent {
  protected readonly year = new Date().getFullYear();
}
