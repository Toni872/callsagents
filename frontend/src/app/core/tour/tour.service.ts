import { Injectable, signal, inject, NgZone } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { driver, DriveStep } from 'driver.js';

/** One segment of the multi-page tour. */
interface TourSegment {
  path: string;
  steps: DriveStep[];
}

const SESSION_SHOWN_KEY = 'callsagents-tour-welcome-shown';
const TOUR_COMPLETED_KEY = 'callsagents-tour-completed';

@Injectable({ providedIn: 'root' })
export class TourService {
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);

  readonly showWelcome = signal(false);

  private driverInstance: ReturnType<typeof driver> | null = null;
  private segments: TourSegment[] = [];
  private currentSegmentIndex = 0;
  private isFullTourActive = false;
  private navigationSubscription: Subscription | null = null;

  /** Ordered tour across the entire app — no emojis, clean text. */
  private readonly fullTour: TourSegment[] = [
    {
      path: '/dashboard',
      steps: [
        {
          element: '.sidebar__nav a[href="/dashboard"]',
          popover: {
            title: 'Panel de control',
            description: 'Tu centro de mando. Aqui ves todo de un vistazo: leads, llamadas, campanas y citas. Se actualiza solo.',
            side: 'right' as const,
            align: 'start' as const,
          },
        },
        {
          element: '.dashboard-page .hero-card',
          popover: {
            title: 'Llamadas de hoy',
            description: 'El KPI mas importante: cuantas llamadas se hicieron y cuantas se conectaron. Si sube, tu equipo esta activo.',
            side: 'bottom' as const,
            align: 'center' as const,
          },
        },
        {
          element: '.dashboard-page__grid',
          popover: {
            title: 'Metricas clave',
            description: 'Leads totales, campanas activas, tasa de conexion y proximas citas. Cada tarjeta se actualiza automaticamente.',
            side: 'top' as const,
            align: 'center' as const,
          },
        },
      ],
    },
    {
      path: '/leads',
      steps: [
        {
          element: '.sidebar__nav a[href="/leads"]',
          popover: {
            title: 'Gestion de leads',
            description: 'Tu base de datos de prospectos. Cada lead es un posible cliente que ha contactado tu centro o que vas a contactar.',
            side: 'right' as const,
            align: 'start' as const,
          },
        },
        {
          element: 'app-lead-list section.page > header.page__header',
          popover: {
            title: 'Crear lead',
            description: 'Aniade leads manualmente o importa desde CSV. Tambien se crean automaticamente desde tu formulario web.',
            side: 'bottom' as const,
            align: 'start' as const,
          },
        },
        {
          element: 'app-lead-list .card',
          popover: {
            title: 'Lista de leads',
            description: 'Cada fila muestra nombre, telefono, email y estado. Haz clic en uno para ver su historial completo.',
            side: 'top' as const,
            align: 'center' as const,
          },
        },
      ],
    },
    {
      path: '/campaigns',
      steps: [
        {
          element: '.sidebar__nav a[href="/campaigns"]',
          popover: {
            title: 'Campanas',
            description: 'Un grupo de leads que se procesan juntos. Crea una campana por producto, zona o periodo.',
            side: 'right' as const,
            align: 'start' as const,
          },
        },
        {
          element: 'app-campaign-list section.page > header.page__header',
          popover: {
            title: 'Crear campana',
            description: 'Define nombre, descripcion y el script que la IA usara para hablar con los leads. La IA personaliza cada llamada.',
            side: 'bottom' as const,
            align: 'start' as const,
          },
        },
        {
          element: 'app-campaign-list .card',
          popover: {
            title: 'Tus campanas',
            description: 'Cada campana muestra leads asignados, llamadas realizadas y tasa de conversion. Activa o pausa cuando quieras.',
            side: 'top' as const,
            align: 'center' as const,
          },
        },
      ],
    },
    {
      path: '/calls',
      steps: [
        {
          element: '.sidebar__nav a[href="/calls"]',
          popover: {
            title: 'Registro de llamadas',
            description: 'Bitacora completa: a quien, cuando, duracion y resultado de cada llamada. Aqui auditas todo.',
            side: 'right' as const,
            align: 'start' as const,
          },
        },
        {
          element: 'app-call-list section.page > header.page__header',
          popover: {
            title: 'Filtrar llamadas',
            description: 'Busca por estado, fecha o responsable. Cada fila muestra si se conecto, buzon, ocupado o no respondieron.',
            side: 'bottom' as const,
            align: 'start' as const,
          },
        },
      ],
    },
    {
      path: '/voice-calls',
      steps: [
        {
          element: '.sidebar__nav a[href="/voice-calls"]',
          popover: {
            title: 'Voz IA',
            description: 'Configura tu agente de voz: numero, tono, velocidad e instrucciones. La IA habla con los leads como un humano.',
            side: 'right' as const,
            align: 'start' as const,
          },
        },
      ],
    },
    {
      path: '/appointments',
      steps: [
        {
          element: '.sidebar__nav a[href="/appointments"]',
          popover: {
            title: 'Citas',
            description: 'Cuando la IA detecta interes, agenda una cita automaticamente. Aqui ves todas las programadas.',
            side: 'right' as const,
            align: 'start' as const,
          },
        },
        {
          element: 'app-appointment-list section.page > header.page__header',
          popover: {
            title: 'Gestionar citas',
            description: 'Cada cita muestra lead, fecha, duracion y estado. Se sincroniza con Google Calendar.',
            side: 'bottom' as const,
            align: 'start' as const,
          },
        },
      ],
    },
    {
      path: '/users',
      steps: [
        {
          element: '.sidebar__nav a[href="/users"]',
          popover: {
            title: 'Usuarios',
            description: 'Administra tu equipo: administrador, supervisor o agente. Cada rol controla los permisos del panel.',
            side: 'right' as const,
            align: 'start' as const,
          },
        },
      ],
    },
    {
      path: '/settings/calendar',
      steps: [
        {
          element: '.sidebar__nav a[href="/settings/calendar"]',
          popover: {
            title: 'Calendario',
            description: 'Conecta Google Calendar para que las citas se sincronicen automaticamente. Asi nunca pierdes una.',
            side: 'right' as const,
            align: 'start' as const,
          },
        },
      ],
    },
  ];

  /** Called on login / page load. Shows welcome banner if this session hasn't seen it yet. */
  checkWelcome(): void {
    const alreadyCompleted = localStorage.getItem(TOUR_COMPLETED_KEY) === 'done';
    const alreadyShownThisSession = sessionStorage.getItem(SESSION_SHOWN_KEY) === 'yes';

    if (!alreadyCompleted && !alreadyShownThisSession) {
      this.showWelcome.set(true);
    }
  }

  /** User accepts the welcome -> start full continuous tour. */
  startFullTour(): void {
    this.showWelcome.set(false);
    sessionStorage.setItem(SESSION_SHOWN_KEY, 'yes');

    this.segments = [...this.fullTour];
    this.currentSegmentIndex = 0;
    this.isFullTourActive = true;

    const firstPath = this.segments[0].path;
    const currentPath = this.router.url;

    if (currentPath === firstPath) {
      this.startSegment(0);
    } else {
      this.listenForNavigationThenStart();
      this.router.navigate([firstPath]);
    }
  }

  /** Start a single segment (one page's steps). */
  private startSegment(index: number): void {
    if (index >= this.segments.length) {
      this.finishFullTour();
      return;
    }

    this.currentSegmentIndex = index;
    const segment = this.segments[index];

    // Small delay so Angular renders the new page
    setTimeout(() => {
      this.zone.runOutsideAngular(() => {
        this.driverInstance = driver({
          showProgress: true,
          animate: true,
          allowClose: true,
          overlayColor: 'rgba(0, 0, 0, 0.65)',
          stagePadding: 6,
          stageRadius: 8,
          popoverOffset: 10,
          progressText: `${index + 1}/${this.segments.length} \u2014 Paso {{current}} de {{total}}`,
          nextBtnText: 'Siguiente',
          prevBtnText: 'Anterior',
          doneBtnText: 'Continuar',
          steps: segment.steps,
          onDestroyed: () => {
            this.zone.run(() => {
              this.onSegmentDestroyed();
            });
          },
        });
        this.driverInstance.drive();

        // Direct DOM listener on X button — most reliable way to detect user close
        const closeBtn = document.querySelector('.driver-popover-close-btn');
        closeBtn?.addEventListener('click', () => {
          this.isFullTourActive = false;
        }, { once: true });
      });
    }, 400);
  }

  /** When a segment ends, move to next page or finish. */
  private onSegmentDestroyed(): void {
    if (!this.isFullTourActive) return;

    const nextIndex = this.currentSegmentIndex + 1;

    if (nextIndex >= this.segments.length) {
      this.finishFullTour();
      return;
    }

    const nextPath = this.segments[nextIndex].path;
    const currentPath = this.router.url;

    if (currentPath === nextPath) {
      this.startSegment(nextIndex);
    } else {
      this.listenForNavigationThenStart();
      this.router.navigate([nextPath]);
    }
  }

  /** Listen for NavigationEnd, then start next segment. */
  private listenForNavigationThenStart(): void {
    this.navigationSubscription?.unsubscribe();
    this.navigationSubscription = this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(() => {
        this.navigationSubscription?.unsubscribe();
        this.navigationSubscription = null;
        const nextIndex = this.currentSegmentIndex + 1;
        if (nextIndex < this.segments.length) {
          this.startSegment(nextIndex);
        }
      });
  }

  /** Tour finished -- mark completed and reset state. */
  private finishFullTour(): void {
    this.isFullTourActive = false;
    this.currentSegmentIndex = 0;
    this.navigationSubscription?.unsubscribe();
    this.navigationSubscription = null;
    localStorage.setItem(TOUR_COMPLETED_KEY, 'done');
  }

  /** User declines the welcome banner. */
  dismissWelcome(): void {
    this.showWelcome.set(false);
    sessionStorage.setItem(SESSION_SHOWN_KEY, 'yes');
  }

  /** Reset tour (for testing or "show tour again"). */
  resetTour(): void {
    localStorage.removeItem(TOUR_COMPLETED_KEY);
    sessionStorage.removeItem(SESSION_SHOWN_KEY);
  }
}
