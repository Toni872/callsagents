import { Injectable, inject } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { driver, Config } from 'driver.js';
import 'driver.js/dist/driver.css';

interface TourStep {
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

  private readonly tours: Record<string, TourStep[]> = {
    '/dashboard': [
      {
        element: '.sidebar__brand',
        popover: {
          title: 'Bienvenido a CallsAgents',
          description: 'Esta es tu plataforma de gestión de leads y llamadas con IA. Vamos a recorrer cada sección para que saques el máximo partido.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '.sidebar__nav a[href="/dashboard"]',
        popover: {
          title: 'Panel de Control',
          description: 'Aquí ves un resumen en tiempo real de toda tu operación: llamadas, leads, campañas y citas. Es tu centro de mando.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '.hero-card',
        popover: {
          title: 'Llamadas de Hoy',
          description: 'Este es el KPI más importante: cuántas llamadas se han hecho hoy y cuántas se conectaron. Si el número sube, tu equipo está activo.',
          side: 'bottom',
          align: 'center'
        }
      },
      {
        element: '.dashboard-page__grid',
        popover: {
          title: 'Métricas Clave',
          description: 'Cada tarjeta muestra un indicador clave: leads totales, campañas activas, conexiones y próximas citas. Todo se actualiza automáticamente.',
          side: 'top',
          align: 'center'
        }
      }
    ],
    '/leads': [
      {
        element: '.sidebar__nav a[href="/leads"]',
        popover: {
          title: 'Gestión de Leads',
          description: 'Aquí está tu base de datos de prospectos. Cada lead representa un posible alumno o cliente que ha contactado tu centro.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: 'app-page-header',
        popover: {
          title: 'Crear Lead',
          description: 'Puedes añadir leads manualmente o importarlos desde un archivo. También se crean automáticamente cuando alguien rellena tu formulario.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: 'table',
        popover: {
          title: 'Tabla de Leads',
          description: 'Aquí ves todos tus leads con su estado, quién está asignado y cuándo se creó. Puedes filtrar, buscar y ordenar por cualquier columna.',
          side: 'top',
          align: 'center'
        }
      }
    ],
    '/campaigns': [
      {
        element: '.sidebar__nav a[href="/campaigns"]',
        popover: {
          title: 'Campañas',
          description: 'Una campaña es un grupo de leads que se van a procesar juntos. Puedes crear campañas para diferentes productos, zonas o períodos.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: 'app-page-header',
        popover: {
          title: 'Nueva Campaña',
          description: 'Al crear una campaña, defines el nombre, la descripción y el script que la IA usará para hablar con los leads.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: 'table',
        popover: {
          title: 'Estado de Campañas',
          description: 'Cada campaña muestra su estado: programada, en ejecución o completada. Puedes pausar, reanudar o editar en cualquier momento.',
          side: 'top',
          align: 'center'
        }
      }
    ],
    '/calls': [
      {
        element: '.sidebar__nav a[href="/calls"]',
        popover: {
          title: 'Registro de Llamadas',
          description: 'Aquí se registra cada llamada que la IA realiza: a quién, cuándo, duración y resultado. Es tu bitácora completa de actividad.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: 'table',
        popover: {
          title: 'Historial de Llamadas',
          description: 'Cada fila es una llamada. Puedes ver si se conectó, si fue a buzón, si estaba ocupado o no respondieron. Filtra por estado o fecha.',
          side: 'top',
          align: 'center'
        }
      }
    ],
    '/voice-calls': [
      {
        element: '.sidebar__nav a[href="/voice-calls"]',
        popover: {
          title: 'Voz — Llamadas en Vivo',
          description: 'Aquí controlas las llamadas de voz de la IA. Puedes configurar el agente de voz, el número desde el que llama y el script que sigue.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '.voice-page',
        popover: {
          title: 'Configuración del Agente',
          description: 'Define la voz, el tono y las instrucciones que la IA seguirá durante cada llamada. Esto determina la experiencia del lead al contestar.',
          side: 'bottom',
          align: 'center'
        }
      }
    ],
    '/appointments': [
      {
        element: '.sidebar__nav a[href="/appointments"]',
        popover: {
          title: 'Citas',
          description: 'Cuando la IA qualifica un lead con interés, agenda una cita automáticamente. Aquí ves todas las citas programadas y su estado.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: 'table',
        popover: {
          title: 'Calendario de Citas',
          description: 'Cada cita muestra el lead, la fecha, la duración y si está confirmada o pendiente. Se sincroniza con tu calendario de Google.',
          side: 'top',
          align: 'center'
        }
      }
    ],
    '/users': [
      {
        element: '.sidebar__nav a[href="/users"]',
        popover: {
          title: 'Gestión de Usuarios',
          description: 'Aquí administrares los miembros de tu equipo. Cada usuario tiene un rol: administrador, supervisor o agente.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: 'table',
        popover: {
          title: 'Equipo',
          description: 'Puedes crear, editar o desactivar usuarios. Los roles controlan qué puede hacer cada persona en la plataforma.',
          side: 'top',
          align: 'center'
        }
      }
    ],
    '/settings/calendar': [
      {
        element: '.sidebar__nav a[href="/settings/calendar"]',
        popover: {
          title: 'Configuración del Calendario',
          description: 'Conecta tu Google Calendar para que las citas se sincronicen automáticamente. Así tu equipo siempre sabe qué le espera.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '.calendar-page',
        popover: {
          title: 'Integración con Google',
          description: 'Haz clic en "Conectar Google" para autorizar el acceso. Una vez conectado, cada cita creada por la IA aparecerá en tu calendario.',
          side: 'bottom',
          align: 'center'
        }
      }
    ]
  };

  startTour(path: string): void {
    const steps = this.tours[path];
    if (!steps || steps.length === 0) {
      return;
    }

    this.driverInstance = driver({
      showProgress: true,
      animate: true,
      allowClose: true,
      overlayColor: 'rgba(0, 0, 0, 0.7)',
      stagePadding: 8,
      stageRadius: 8,
      popoverOffset: 12,
      progressText: '{{current}} de {{total}}',
      nextBtnText: 'Siguiente',
      prevBtnText: 'Anterior',
      doneBtnText: 'Entendido',
      steps: steps.map(step => ({
        element: step.element,
        popover: step.popover
      })) as Config['steps']
    });

    this.driverInstance.drive();
  }

  isTourCompleted(path: string): boolean {
    return localStorage.getItem(`callsagents-tour-${path}`) === 'done';
  }

  markTourCompleted(path: string): void {
    localStorage.setItem(`callsagents-tour-${path}`, 'done');
  }

  shouldShowTour(path: string): boolean {
    return !this.isTourCompleted(path) && !!this.tours[path];
  }
}
