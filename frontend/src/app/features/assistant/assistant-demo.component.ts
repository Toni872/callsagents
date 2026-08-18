import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  effect,
  inject,
  signal,
  viewChild
} from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { LeadApi } from '../../core/api/lead.api';
import { AppointmentApi } from '../../core/api/appointment.api';

const DEMO_EMAIL = 'demo@callsagents.com';

const GREETING =
  '¡Hola! Soy el asistente virtual de Academia Meridiano. He visto que te interesan nuestros cursos. Para empezar, ¿cómo te llamas?';
const FALLBACK_ANSWER =
  'Buena pregunta. Te recomiendo hacer una prueba de nivel gratuita: allí te resuelvo todas las dudas en detalle y sin compromiso.';
const OFFER_TEXT =
  'Perfecto. ¿Te agendo una prueba de nivel gratuita para mañana a las 10:00? Dura 30 minutos y la hacemos online o en el centro, como prefieras.';

const QUESTION_CHIPS = [
  '¿Cuánto cuesta el curso de inglés?',
  '¿Hay clases online?',
  '¿Preparación DELE?',
  '¿Qué horarios tenéis?'
];

const OFFER_CHIPS = ['Sí, agenda', 'Tengo otra pregunta', 'No, gracias'];
const DECLINED_CHIPS = ['Sí, al final sí', 'Volver a empezar'];

interface AnswerRule {
  keywords: string[];
  answer: string;
}

const ANSWER_RULES: AnswerRule[] = [
  {
    keywords: ['precio', 'cuesta', 'coste', 'caro', '€', 'euro', 'dinero', 'tarifa', 'cuota', 'mensualidad'],
    answer:
      'Nuestros cursos de idiomas parten de 49€/mes en grupo y 79€/mes en clases individuales. La prueba de nivel es gratuita y sin compromiso. El precio final depende del idioma y del nivel: te lo confirmo todo en la prueba.'
  },
  {
    keywords: ['online', 'remoto', 'virtual', 'presencial', 'modalidad', 'a distancia', 'videollamada'],
    answer:
      'Sí, ofrecemos clases 100% online en directo y también presenciales en el centro. Las online son en grupos reducidos por videollamada, con el mismo material y los mismos profesores que las presenciales.'
  },
  {
    keywords: ['dele', 'examen', 'certificado', 'oficial', 'diploma'],
    answer:
      'Sí, preparamos el DELE en todos los niveles, de A1 a C2, con profesores certificados. Además, hacemos un simulacro de examen cada mes para que llegues con confianza.'
  },
  {
    keywords: ['horario', 'horarios', 'hora', 'cuando', 'dias', 'turno', 'manana', 'tarde', 'lunes', 'sabado', 'fin de semana', 'apertura'],
    answer:
      'Nuestro horario es de lunes a viernes de 9:00 a 21:00 y los sábados de 9:00 a 14:00, con clases por la mañana y por la tarde. En las clases online también podemos adaptarnos a tu disponibilidad.'
  }
];

interface ChatMessage {
  role: 'assistant' | 'user';
  text: string;
  kind?: 'success';
  chips?: string[];
}

type ChatStage = 'awaiting-name' | 'questions' | 'offering' | 'booking' | 'done' | 'declined';

@Component({
  selector: 'app-assistant-demo',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  template: `
    <div class="assistant-page">
      <header class="assistant-page__header">
        <div class="assistant-page__heading">
          <div class="assistant-page__avatar" aria-hidden="true">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"
              />
            </svg>
          </div>
          <div>
            <h1 class="assistant-page__title">Asistente conversacional</h1>
            <p class="assistant-page__subtitle">Simulador demo · Academia Meridiano</p>
          </div>
        </div>
        <div class="assistant-page__actions">
          <span class="assistant-page__badge">DEMO</span>
          <button class="btn btn--secondary" type="button" (click)="goToDashboard()">Cerrar</button>
        </div>
      </header>

      <section class="chat" aria-label="Conversación con el asistente">
        <div class="chat__window" #messagesWindow>
          <div class="chat__log" role="log" aria-live="polite">
            @for (m of messages(); track $index) {
              <div
                class="msg"
                [class.msg--user]="m.role === 'user'"
                [class.msg--success]="m.kind === 'success'"
              >
                <div class="msg__bubble">
                  @if (m.kind === 'success') {
                    <p class="msg__text msg__text--strong">{{ m.text }}</p>
                    <button class="btn btn--primary" type="button" (click)="goToCalendar()">
                      Ver cita en el calendario
                    </button>
                  } @else {
                    <p class="msg__text">{{ m.text }}</p>
                  }
                  @if (m.chips && m.chips.length > 0) {
                    <div class="msg__chips">
                      @for (chip of m.chips; track chip) {
                        <button class="chip" type="button" (click)="onChip(m, chip)">
                          {{ chip }}
                        </button>
                      }
                    </div>
                  }
                </div>
              </div>
            }
            @if (typing()) {
              <div class="msg" role="status" aria-label="El asistente está escribiendo">
                <div class="msg__bubble typing-dots">
                  <span></span><span></span><span></span>
                </div>
              </div>
            }
          </div>
        </div>

        <div class="chat__input">
          <input
            #inputEl
            type="text"
            [value]="input()"
            (input)="onInput($event)"
            (keydown.enter)="send()"
            [disabled]="typing() || busy()"
            placeholder="Escribe tu mensaje…"
            aria-label="Mensaje"
          />
          <button
            class="btn btn--primary"
            type="button"
            (click)="send()"
            [disabled]="typing() || busy() || !input().trim()"
          >
            Enviar
          </button>
        </div>
      </section>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .assistant-page {
        max-width: 860px;
        margin: 0 auto;
        display: flex;
        flex-direction: column;
        gap: var(--spacing-4);
      }
      .assistant-page__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--spacing-4);
        flex-wrap: wrap;
      }
      .assistant-page__heading {
        display: flex;
        align-items: center;
        gap: var(--spacing-3);
      }
      .assistant-page__avatar {
        width: 44px;
        height: 44px;
        border-radius: var(--radius-full);
        display: grid;
        place-items: center;
        background: var(--color-primary-soft);
        color: var(--color-primary);
        flex-shrink: 0;
      }
      .assistant-page__title {
        margin: 0;
        font-size: 1.25rem;
      }
      .assistant-page__subtitle {
        margin: 0;
        font-size: 0.8125rem;
        color: var(--color-text-muted);
      }
      .assistant-page__actions {
        display: flex;
        align-items: center;
        gap: var(--spacing-2);
      }
      .assistant-page__badge {
        display: inline-block;
        padding: 0.125rem 0.5rem;
        border-radius: var(--radius-full);
        font-size: 0.75rem;
        font-weight: 600;
        letter-spacing: 0.05em;
        background: var(--color-primary-soft);
        color: var(--color-primary);
        border: 1px solid color-mix(in srgb, var(--color-primary), transparent 60%);
      }

      /* ---------- Chat ---------- */
      .chat {
        display: flex;
        flex-direction: column;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        background: var(--color-surface);
        overflow: hidden;
        box-shadow: var(--shadow-sm);
      }
      .chat__window {
        height: min(60vh, 560px);
        overflow-y: auto;
        padding: var(--spacing-4);
        background: var(--color-bg-alt);
      }
      .chat__log {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-3);
      }
      .msg {
        display: flex;
      }
      .msg--user {
        justify-content: flex-end;
      }
      .msg__bubble {
        max-width: 78%;
        display: flex;
        flex-direction: column;
        gap: var(--spacing-2);
        padding: var(--spacing-3) var(--spacing-4);
        border-radius: var(--radius-lg);
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        color: var(--color-text);
        box-shadow: var(--shadow-sm);
      }
      .msg--user .msg__bubble {
        background: var(--color-primary);
        border-color: var(--color-primary);
        color: var(--color-on-primary);
      }
      .msg--success .msg__bubble {
        background: var(--color-success-bg);
        border-color: color-mix(in srgb, var(--color-success), transparent 60%);
      }
      .msg__text {
        margin: 0;
        font-size: 0.9rem;
        line-height: 1.5;
        white-space: pre-wrap;
      }
      .msg--success .msg__text {
        color: var(--color-text-strong);
        font-weight: 500;
      }
      .msg__chips {
        display: flex;
        flex-wrap: wrap;
        gap: var(--spacing-2);
        margin-top: var(--spacing-1);
      }
      .chip {
        padding: var(--spacing-1) var(--spacing-3);
        border-radius: var(--radius-full);
        border: 1px solid var(--color-border-strong);
        background: var(--color-surface);
        color: var(--color-text);
        font-size: 0.8125rem;
      }
      .chip:hover:not(:disabled) {
        border-color: var(--color-primary);
        color: var(--color-primary);
        background: var(--color-primary-soft);
      }

      /* ---------- Typing indicator ---------- */
      .typing-dots {
        display: flex;
        gap: 4px;
        align-items: center;
      }
      .typing-dots span {
        width: 7px;
        height: 7px;
        border-radius: var(--radius-full);
        background: var(--color-text-muted);
        animation: typing-bounce 1.2s infinite ease-in-out;
      }
      .typing-dots span:nth-child(2) {
        animation-delay: 0.15s;
      }
      .typing-dots span:nth-child(3) {
        animation-delay: 0.3s;
      }
      @keyframes typing-bounce {
        0%,
        80%,
        100% {
          transform: translateY(0);
          opacity: 0.5;
        }
        40% {
          transform: translateY(-4px);
          opacity: 1;
        }
      }
      @media (prefers-reduced-motion: reduce) {
        .typing-dots span {
          animation: none;
        }
      }

      /* ---------- Input ---------- */
      .chat__input {
        display: flex;
        gap: var(--spacing-2);
        padding: var(--spacing-3);
        border-top: 1px solid var(--color-border);
        background: var(--color-surface);
      }
      .chat__input input {
        flex: 1;
      }

      /* ---------- Buttons ---------- */
      .btn {
        padding: var(--spacing-2) var(--spacing-4);
        border-radius: var(--radius);
        border: 1px solid var(--color-border);
        background: var(--color-surface);
        color: var(--color-text);
        cursor: pointer;
        font-size: 0.875rem;
        transition: background-color 0.15s ease, border-color 0.15s ease;
      }
      .btn:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .btn--primary {
        background: var(--color-primary);
        color: var(--color-on-primary);
        border-color: var(--color-primary);
      }
      .btn--primary:hover:not(:disabled) {
        background: var(--color-primary-hover);
      }
      .btn--secondary {
        background: var(--color-surface);
        border-color: var(--color-border);
      }
      .btn--secondary:hover:not(:disabled) {
        background: var(--color-bg-alt);
        border-color: var(--color-border-strong);
      }

      /* ---------- Responsive ---------- */
      @media (max-width: 639px) {
        .msg__bubble {
          max-width: 92%;
        }
        .chat__window {
          height: 68vh;
        }
      }
    `
  ]
})
export class AssistantDemoComponent implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly leadApi = inject(LeadApi);
  private readonly appointmentApi = inject(AppointmentApi);
  private readonly router = inject(Router);

  private readonly messagesEl = viewChild.required<ElementRef<HTMLElement>>('messagesWindow');
  private readonly inputEl = viewChild<ElementRef<HTMLInputElement>>('inputEl');

  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly typing = signal(false);
  protected readonly busy = signal(false);
  protected readonly input = signal('');
  protected readonly stage = signal<ChatStage>('awaiting-name');
  protected readonly userName = signal<string | null>(null);

  private readonly pendingTimeouts: ReturnType<typeof setTimeout>[] = [];

  private readonly scrollEffect = effect(() => {
    this.messages();
    this.typing();
    requestAnimationFrame(() => {
      const el = this.messagesEl().nativeElement;
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
    });
  });

  ngOnInit(): void {
    const user = this.auth.currentUser();
    if (user?.email !== DEMO_EMAIL) {
      this.router.navigateByUrl('/dashboard');
      return;
    }
    this.reply(GREETING);
  }

  ngOnDestroy(): void {
    this.pendingTimeouts.forEach((t) => clearTimeout(t));
  }

  /* ---------- Input handling ---------- */

  protected onInput(event: Event): void {
    this.input.set((event.target as HTMLInputElement).value);
  }

  protected send(): void {
    const text = this.input().trim();
    if (!text) {
      return;
    }
    this.input.set('');
    this.sendText(text);
    this.inputEl()?.nativeElement.focus();
  }

  protected onChip(msg: ChatMessage, chip: string): void {
    if (this.typing() || this.busy()) {
      return;
    }
    this.messages.update((msgs) => msgs.map((m) => (m === msg ? { ...m, chips: [] } : m)));
    this.sendText(chip);
  }

  protected goToDashboard(): void {
    this.router.navigate(['/dashboard']);
  }

  protected goToCalendar(): void {
    this.router.navigate(['/appointments']);
  }

  /* ---------- Conversation flow ---------- */

  private sendText(text: string): void {
    if (this.typing() || this.busy()) {
      return;
    }
    this.messages.update((msgs) => [...msgs, { role: 'user', text }]);
    this.dispatch(text);
  }

  private dispatch(text: string): void {
    switch (this.stage()) {
      case 'awaiting-name':
        this.handleName(text);
        break;
      case 'questions':
        this.handleQuestion(text);
        break;
      case 'offering':
        this.handleOffer(text);
        break;
      case 'declined':
        this.handleDeclined(text);
        break;
      default:
        this.reply(
          'La cita ya está en tu agenda. Si necesitas algo más, puedes verla en el calendario o empezar otra conversación.'
        );
    }
  }

  private handleName(text: string): void {
    const name = text.replace(/\s+/g, ' ').trim();
    if (name.length < 2 || name.length > 60) {
      this.reply('¿Puedes repetirme tu nombre, por favor?');
      return;
    }
    this.userName.set(name);
    this.stage.set('questions');
    this.reply(
      `Encantado de conocerte, ${name}. Soy el asistente virtual de Academia Meridiano. ¿Qué te gustaría saber?`,
      QUESTION_CHIPS
    );
  }

  private handleQuestion(text: string): void {
    if (this.isAccept(text)) {
      this.offerAppointment();
      return;
    }
    if (this.isDecline(text)) {
      this.reply('Entendido, sin problema. Dime si necesitas cualquier otra cosa.');
      return;
    }
    const answer = this.matchAnswer(text);
    if (answer) {
      this.reply(answer);
    } else {
      this.reply(FALLBACK_ANSWER);
    }
    this.offerAppointment();
  }

  private handleOffer(text: string): void {
    if (this.isAccept(text)) {
      this.startBooking();
      return;
    }
    if (this.isAnotherQuestion(text)) {
      this.stage.set('questions');
      this.reply('Claro, dime.', QUESTION_CHIPS);
      return;
    }
    if (this.isDecline(text)) {
      this.stage.set('declined');
      this.reply(
        `Sin problema, ${this.userName()}. Cuando cambies de opinión, aquí estaré.`,
        DECLINED_CHIPS
      );
      return;
    }
    const answer = this.matchAnswer(text);
    if (answer) {
      this.reply(answer);
      this.offerAppointment();
      return;
    }
    this.reply(
      'Disculpa, no te he entendido del todo. ¿Te agendo la prueba de nivel para mañana a las 10:00?',
      OFFER_CHIPS
    );
  }

  private handleDeclined(text: string): void {
    if (this.isRestart(text)) {
      this.restart();
      return;
    }
    if (this.isAccept(text)) {
      this.offerAppointment();
      return;
    }
    this.reply(
      `Sin problema, ${this.userName()}. Cuando quieras, dime y te agendo la prueba de nivel.`
    );
  }

  private offerAppointment(): void {
    this.stage.set('offering');
    this.reply(OFFER_TEXT, OFFER_CHIPS);
  }

  private restart(): void {
    this.stage.set('awaiting-name');
    this.userName.set(null);
    this.messages.set([]);
    this.reply(GREETING);
  }

  /* ---------- Booking ---------- */

  private startBooking(): void {
    this.stage.set('booking');
    this.reply('¡Perfecto! Dame un momento, estoy agendando tu prueba de nivel…');
    this.schedule(() => this.book(), 900);
  }

  private book(): void {
    const user = this.auth.currentUser();
    const name = this.userName()?.trim();
    if (!user?.id || !name) {
      this.bookingFailed();
      return;
    }
    this.busy.set(true);
    this.typing.set(true);

    const parts = name.split(/\s+/);
    const firstName = this.capitalize(parts[0] ?? 'Alumno');
    const lastName = parts.length > 1 ? this.capitalizeAll(parts.slice(1).join(' ')) : 'Alumno';

    this.leadApi
      .create({
        firstName,
        lastName,
        email: `${this.slugify(name)}.alumno@example.com`,
        phone: '+34 600 000 000',
        source: 'API',
        notes: 'Creado por el simulador de asistente conversacional (cuenta demo). Solicita prueba de nivel.'
      })
      .subscribe({
        next: (lead) => this.createAppointment(lead.id, user.id, name),
        error: () => this.bookingFailed()
      });
  }

  private createAppointment(leadId: string, userId: string, name: string): void {
    const interests = this.messages()
      .filter((m) => m.role === 'user' && m.text !== name && !this.isAccept(m.text))
      .map((m) => `"${m.text}"`);
    const summary =
      interests.length > 0
        ? `Intereses del alumno: ${interests.join(' — ')}.`
        : 'Sin preguntas previas.';

    this.appointmentApi
      .create({
        leadId,
        userId,
        scheduledAt: this.tomorrowAtTen(),
        durationMinutes: 30,
        status: 'PENDING',
        notes: `Prueba de nivel agendada por el asistente conversacional (simulador demo). Alumno: ${name}. ${summary}`
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.typing.set(false);
          this.stage.set('done');
          this.messages.update((msgs) => [
            ...msgs,
            {
              role: 'assistant',
              text: `¡Listo, ${name}! Te he agendado una prueba de nivel para mañana a las 10:00 (30 minutos). Ya puedes verla en tu agenda.`,
              kind: 'success'
            }
          ]);
        },
        error: () => this.bookingFailed()
      });
  }

  private bookingFailed(): void {
    this.busy.set(false);
    this.typing.set(false);
    this.stage.set('offering');
    this.reply('Ups, ha habido un problema al agendar la cita. ¿Te lo intento de nuevo?', [
      'Sí, agenda',
      'No, gracias'
    ]);
  }

  /* ---------- Script matching ---------- */

  private matchAnswer(text: string): string | null {
    const normalized = this.normalize(text);
    for (const rule of ANSWER_RULES) {
      if (rule.keywords.some((k) => normalized.includes(this.normalize(k)))) {
        return rule.answer;
      }
    }
    return null;
  }

  private isAccept(text: string): boolean {
    const n = this.normalize(text);
    if (n === 'si' || n.startsWith('si ') || n.startsWith('sí')) {
      return true;
    }
    return /\b(ok|okay|vale|claro|adelante|dale|perfecto|genial|agenda|agendame|agendame|apuntame|apuntame|confirmo)\b/.test(
      n
    );
  }

  private isDecline(text: string): boolean {
    return /\b(no|nop|nope)\b/.test(this.normalize(text));
  }

  private isAnotherQuestion(text: string): boolean {
    const n = this.normalize(text);
    return (
      n.includes('otra pregunta') ||
      n.includes('otra duda') ||
      n.includes('mas preguntas') ||
      n.includes('mas dudas')
    );
  }

  private isRestart(text: string): boolean {
    const n = this.normalize(text);
    return (
      n.includes('volver a empezar') ||
      n.includes('empezar de nuevo') ||
      n.includes('empezar otra') ||
      n.includes('reiniciar')
    );
  }

  private normalize(text: string): string {
    return text
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
  }

  private slugify(text: string): string {
    return this.normalize(text).replace(/[^a-z0-9]+/g, '');
  }

  private capitalize(text: string): string {
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  private capitalizeAll(text: string): string {
    return text
      .split(' ')
      .map((word) => this.capitalize(word))
      .join(' ');
  }

  private tomorrowAtTen(): string {
    const date = new Date();
    date.setDate(date.getDate() + 1);
    date.setHours(10, 0, 0, 0);
    return date.toISOString();
  }

  /* ---------- Helpers ---------- */

  private reply(text: string, chips?: string[]): void {
    this.typing.set(true);
    this.schedule(() => {
      this.typing.set(false);
      this.messages.update((msgs) => [...msgs, { role: 'assistant', text, chips }]);
    }, 650 + Math.random() * 450);
  }

  private schedule(fn: () => void, delay: number): void {
    const id = setTimeout(fn, delay);
    this.pendingTimeouts.push(id);
  }
}
