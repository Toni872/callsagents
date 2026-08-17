import { Injectable, inject } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { driver, Config } from 'driver.js';
import 'driver.js/dist/driver.css';

interface GuideStep {
  element: string;
  popover: {
    title: string;
    description: string;
    side: 'top' | 'bottom' | 'left' | 'right';
    align: 'start' | 'center' | 'end';
  };
}

@Injectable({ providedIn: 'root' })
export class TourService {
  private readonly router = inject(Router);
  private driverInstance: ReturnType<typeof driver> | null = null;

  /**
   * Per-page guide steps. Selectors are verified against the actual DOM:
   * - Sidebar links: `.sidebar__nav a[href="/path"]`
   * - Page content header: `section.page > header.page__header`
   * - Dashboard grid: `.dashboard-page__grid`
   * - Dashboard hero card: `.hero-card` (app-card with class hero-card)
   */
  private readonly guides: Record<string, GuideStep[]> = {
    '/dashboard': [
      {
        element: '.sidebar__nav a[href="/dashboard"]',
        popover: {
          title: 'Panel de control',
          description: 'Tu centro de mando. Resumen en tiempo real de leads, llamadas, campañas y citas. Todo se actualiza solo.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '.dashboard-page .hero-card',
        popover: {
          title: 'Llamadas de hoy',
          description: 'El KPI más importante: cuántas llamadas se hicieron y cuántas se conectaron. Si sube, tu equipo está activo.',
          side: 'bottom',
          align: 'center'
        }
      },
      {
        element: '.dashboard-page__grid',
        popover: {
          title: 'Métricas clave',
          description: 'Leads totales, campañas activas, tasa de conexión y próximas citas. Cada tarjeta se actualiza automáticamente.',
          side: 'top',
          align: 'center'
        }
      }
    ],
    '/leads': [
      {
        element: '.sidebar__nav a[href="/leads"]',
        popover: {
          title: 'Gestión de leads',
          description: 'Tu base de datos de prospectos. Cada lead es un posible cliente que ha contactado tu centro.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: 'app-lead-list section.page > header.page__header',
        popover: {
          title: 'Crear lead',
          description: 'Añade leads manualmente o importa desde CSV. También se crean automáticamente desde tu formulario web.',
          side: 'bottom',
          align: 'start'
        }
      }
    ],
    '/campaigns': [
      {
        element: '.sidebar__nav a[href="/campaigns"]',
        popover: {
          title: 'Campañas',
          description: 'Grupo de leads que se procesan juntos. Una campaña por producto, zona o período.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: 'app-campaign-list section.page > header.page__header',
        popover: {
          title: 'Crear campaña',
          description: 'Define nombre, descripción y el script que la IA usará para hablar con los leads.',
          side: 'bottom',
          align: 'start'
        }
      }
    ],
    '/calls': [
      {
        element: '.sidebar__nav a[href="/calls"]',
        popover: {
          title: 'Registro de llamadas',
          description: 'Bitácora completa: a quién, cuándo, duración y resultado de cada llamada.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: 'app-call-list section.page > header.page__header',
        popover: {
          title: 'Filtrar llamadas',
          description: 'Busca por estado, fecha o responsable. Cada fila muestra si se conectó, buzón, ocupado o no respondieron.',
          side: 'bottom',
          align: 'start'
        }
      }
    ],
    '/voice-calls': [
      {
        element: '.sidebar__nav a[href="/voice-calls"]',
        popover: {
          title: 'Llamadas con voz IA',
          description: 'Configura el agente de voz: número, tono e instrucciones. La IA habla con los leads como un humano.',
          side: 'right',
          align: 'start'
        }
      }
    ],
    '/appointments': [
      {
        element: '.sidebar__nav a[href="/appointments"]',
        popover: {
          title: 'Citas',
          description: 'Cuando la IA detecta interés, agenda una cita automáticamente. Aquí ves todas las programadas.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: 'app-appointment-list section.page > header.page__header',
        popover: {
          title: 'Gestionar citas',
          description: 'Cada cita muestra lead, fecha, duración y estado. Se sincroniza con Google Calendar.',
          side: 'bottom',
          align: 'start'
        }
      }
    ],
    '/users': [
      {
        element: '.sidebar__nav a[href="/users"]',
        popover: {
          title: 'Usuarios',
          description: 'Administra tu equipo: administrador, supervisor o agente. Cada rol controla los permisos.',
          side: 'right',
          align: 'start'
        }
      }
    ],
    '/settings/calendar': [
      {
        element: '.sidebar__nav a[href="/settings/calendar"]',
        popover: {
          title: 'Calendario',
          description: 'Conecta Google Calendar para que las citas se sincronicen automáticamente.',
          side: 'right',
          align: 'start'
        }
      }
    ]
  };

  /** Launch guide for a given path. Only fires if there are steps defined. */
  startGuide(path: string): void {
    const steps = this.guides[path];
    if (!steps || steps.length === 0) {
      return;
    }

    this.driverInstance = driver({
      showProgress: true,
      animate: true,
      allowClose: true,
      overlayColor: 'rgba(0, 0, 0, 0.65)',
      stagePadding: 6,
      stageRadius: 8,
      popoverOffset: 10,
      progressText: '{{current}} de {{total}}',
      nextBtnText: 'Siguiente',
      prevBtnText: 'Anterior',
      doneBtnText: 'Entendido',
      steps: steps.map(step => ({
        element: step.element,
        popover: step.popover
      })) as Config['steps'],
      onDestroyed: () => {
        this.markGuideCompleted(path);
      }
    });

    this.driverInstance.drive();
  }

  isGuideCompleted(path: string): boolean {
    return localStorage.getItem(`callsagents-guide-${path}`) === 'done';
  }

  markGuideCompleted(path: string): void {
    localStorage.setItem(`callsagents-guide-${path}`, 'done');
  }

  /** Only auto-show if the user has never seen it AND a guide exists for this path */
  shouldShowGuide(path: string): boolean {
    return !this.isGuideCompleted(path) && !!this.guides[path];
  }
}
