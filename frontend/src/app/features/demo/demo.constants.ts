export const CONTACT_URL = 'https://www.script-9.com/contacto';

export type PainKey = 'late-leads' | 'missed-calls' | 'qualifying-time' | 'booking-quality';

export interface PainData {
  key: PainKey;
  icon: DemoIconName;
  title: string;
  copy: string;
  greeting: string;
  need: string;
  loss: number;
  probability: number;
}

export const PAINS: PainData[] = [
  {
    key: 'late-leads',
    icon: 'user-plus',
    title: 'Respondo tarde a mis leads',
    copy: '42 horas de media. Tu competencia responde en 4 minutos.',
    greeting:
      '¡Hola! Soy el asistente virtual de Callsagents. He visto que te preocupa responder tarde a tus leads: aquí responden en segundos, 24/7. ¿Cómo te llamas?',
    need: 'Respuesta inmediata a leads',
    loss: 2300,
    probability: 2.3
  },
  {
    key: 'missed-calls',
    icon: 'phone',
    title: 'Se me escapan llamadas fuera de horario',
    copy: 'El 62% de tus llamadas nunca se responden.',
    greeting:
      '¡Hola! Soy el asistente virtual de Callsagents. Conmigo ninguna llamada se escapa: cubro también fuera de tu horario. ¿Cómo te llamas?',
    need: 'Cobertura 24/7',
    loss: 1850,
    probability: 4.1
  },
  {
    key: 'qualifying-time',
    icon: 'users',
    title: 'Mi equipo pierde tiempo cualificando',
    copy: 'Horas perdidas en leads que no compran.',
    greeting:
      '¡Hola! Soy el asistente virtual de Callsagents. Yo cualifico a cada lead antes de que llegue a tu equipo: solo habláis con quien de verdad quiere comprar. ¿Cómo te llamas?',
    need: 'Cualificación de leads',
    loss: 2400,
    probability: 3.2
  },
  {
    key: 'booking-quality',
    icon: 'calendar-check',
    title: 'No agendo citas de calidad',
    copy: 'El 64% de negocios nunca pide la cita.',
    greeting:
      '¡Hola! Soy el asistente virtual de Callsagents. Yo pido la cita por ti, en el momento justo y con la persona correcta. ¿Cómo te llamas?',
    need: 'Agendamiento de citas',
    loss: 2100,
    probability: 2.8
  }
];

export const GREETING = '¡Hola! Soy el asistente virtual de Callsagents. ¿Cómo te llamas?';
export const FALLBACK_ANSWER =
  'Buena pregunta. Te recomiendo una llamada informativa de 30 minutos: allí te aclaro todo con detalle y sin compromiso.';
export const OFFER_TEXT =
  'Perfecto. Te agendo una llamada informativa gratuita de 30 minutos, sin compromiso. Elige el día y la hora que mejor te vengan:';
export const SUCCESS_TEXT =
  '¡Listo! Te he reservado una llamada informativa (30 minutos).';
export const SUCCESS_NOTE =
  'Demo simulada: la reserva no es real y no se guarda ningún dato.';

export const QUESTION_CHIPS = [
  '¿Cuánto cuesta?',
  '¿Cómo funciona?',
  '¿Qué servicios tiene?',
  '¿En qué horario responden?'
];

export const FOLLOW_UP_CHIPS = ['Tengo otra pregunta', 'Agendar llamada informativa', 'No, gracias'];

export const OFFER_CHIPS = ['Sí, agenda', 'Tengo otra pregunta', 'No, gracias'];
export const DECLINED_CHIPS = ['Sí, al final sí', 'Volver a empezar'];

export interface AnswerRule {
  keywords: string[];
  answer: string;
  need: string;
}

export const ANSWER_RULES: AnswerRule[] = [
  {
    keywords: ['llamada informativa', 'que es una llamada', 'en que consiste la llamada', 'que incluye la llamada'],
    answer:
      'La llamada informativa es gratuita y sin compromiso: 30 minutos donde te explico cómo funciona Callsagents para tu negocio y te confirmo el precio exacto para tu caso.',
    need: 'Información de la llamada'
  },
  {
    keywords: ['quien eres', 'quien sois', 'que es callsagents', 'que haceis', 'que hace callsagents', 'tu empresa', 'vuestra empresa'],
    answer:
      'Soy el asistente virtual de Callsagents: automatizo la atención de tus clientes por WhatsApp, cualifico su interés y agendo reuniones en tu calendario, 24/7.',
    need: 'Conociendo el servicio'
  },
  {
    keywords: ['integrar', 'integracion', 'conecta', 'conectar', 'herramientas', 'crm', 'calendario', 'whatsapp', 'sistema', 'api'],
    answer:
      'El agente se conecta con las herramientas que ya usa tu negocio: WhatsApp, tu CRM y tu calendario. La integración se configura en minutos y no necesitas cambiar tu forma de trabajar.',
    need: 'Compatibilidad con su stack'
  },
  {
    keywords: ['probar', 'prueba', 'periodo de prueba', 'empezar', 'contratar', 'alta', 'registro', 'registrarme', 'comenzar'],
    answer:
      'Puedes empezar hoy mismo: la llamada informativa es gratuita y sin compromiso. Allí te cuento los planes y el tiempo de puesta en marcha para tu negocio.',
    need: 'Listo para empezar'
  },
  {
    keywords: ['seguro', 'seguridad', 'datos', 'privacidad', 'gdpr', 'proteccion', 'guardais', 'confidencial'],
    answer:
      'Tratamos los datos de tus clientes con total confidencialidad y cumplimos el RGPD. En esta demo nada se guarda: es una simulación completa.',
    need: 'Confianza y privacidad'
  },
  {
    keywords: ['precio', 'cuesta', 'coste', 'caro', '€', 'euro', 'tarifa', 'cuota', 'mensualidad', 'dinero', 'plan'],
    answer:
      'Los planes de Callsagents empiezan desde 49€/mes según el plan que necesite tu negocio. La llamada informativa es gratuita y sin compromiso: en 30 minutos te confirmo el precio exacto para tu caso.',
    need: 'Presupuesto claro'
  },
  {
    keywords: ['como funciona', 'funcionamiento', 'como se usa', 'proceso', 'mecanica', 'onboarding'],
    answer:
      'El agente atiende a tus clientes por WhatsApp al instante: responde sus preguntas, cualifica su interés y agenda reuniones directamente en tu calendario. Sin colas ni esperas, y se conecta con las herramientas que ya usa tu negocio.',
    need: 'Evaluando la solución'
  },
  {
    keywords: ['servicio', 'servicios', 'ofrecen', 'que hace', 'solucion', 'soluciones', 'funcionalidad', 'que incluye'],
    answer:
      'El agente se encarga de la captación de leads, la cualificación de cada contacto y el agendamiento automático de llamadas. Trabaja 24/7 para que tu negocio no pierda ningún cliente.',
    need: 'Captación y cualificación'
  },
  {
    keywords: ['horario', 'horarios', 'cuando', 'dias', 'horas', 'disponible', 'turno', 'manana', 'tarde', 'noche', 'fin de semana'],
    answer:
      'El agente responde 24/7, todos los días del año. Tu equipo atiende en su horario habitual y el agente cubre el resto: ningún cliente se queda sin respuesta.',
    need: 'Cobertura fuera de horario'
  }
];

export interface ChatMessage {
  role: 'assistant' | 'user';
  text: string;
  time: string;
  kind?: 'success';
  chips?: string[];
}

export type ChatStage = 'awaiting-name' | 'questions' | 'offering' | 'calendar' | 'booking' | 'done' | 'declined';

export interface CalendarDay {
  key: string;
  label: string;
  long: string;
  slots: string[];
}

export interface LeadState {
  name: string;
  company: string;
  phone: string;
  need: string;
}

export interface BantState {
  budget: number;
  authority: number;
  need: number;
  timing: number;
}

export const INITIAL_BANT: BantState = { budget: 10, authority: 15, need: 55, timing: 20 };
export const FINAL_BANT: BantState = { budget: 80, authority: 75, need: 92, timing: 100 };

export type FeedKind = 'lead' | 'name' | 'company' | 'intent' | 'score' | 'booking' | 'crm' | 'info';

export interface FeedEvent {
  id: number;
  time: string;
  text: string;
  icon: DemoIconName;
  kind: FeedKind;
}

export type DemoIconName =
  | 'clock'
  | 'phone-off'
  | 'filter'
  | 'calendar'
  | 'calendar-check'
  | 'user-plus'
  | 'users'
  | 'zap'
  | 'send'
  | 'arrow-up'
  | 'arrow-left'
  | 'arrow-right'
  | 'check'
  | 'activity'
  | 'user'
  | 'building'
  | 'phone'
  | 'target'
  | 'sparkles'
  | 'database'
  | 'calendar-plus'
  | 'trend-up';