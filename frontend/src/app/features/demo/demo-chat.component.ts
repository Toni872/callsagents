import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  computed,
  signal,
  viewChild
} from '@angular/core';
import { gsap } from 'gsap';
import { DemoPainActComponent } from './pain-act.component';
import { DemoSolutionChatComponent } from './solution-chat.component';
import { DemoSystemPanelComponent } from './system-panel.component';
import {
  ANSWER_RULES,
  DECLINED_CHIPS,
  FALLBACK_ANSWER,
  FINAL_BANT,
  FOLLOW_UP_CHIPS,
  GREETING,
  INITIAL_BANT,
  OFFER_CHIPS,
  OFFER_TEXT,
  PAINS,
  QUESTION_CHIPS,
  SUCCESS_TEXT,
  type AnswerRule,
  type BantState,
  type CalendarDay,
  type ChatMessage,
  type ChatStage,
  type DemoIconName,
  type FeedEvent,
  type FeedKind,
  type LeadState,
  type PainKey
} from './demo.constants';

const FONT_LINK =
  'https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@500;600;700;800&family=Inter:wght@400;500;600;700;800&display=swap';

const CONFETTI_COLORS = ['#00a86b', '#4ade80', '#f8fafc', '#00a86b', '#4ade80'];

@Component({
  selector: 'app-demo-chat',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DemoPainActComponent, DemoSolutionChatComponent, DemoSystemPanelComponent],
  host: { 'data-theme': 'dark' },
  template: `
    <div class="demo">
      @if (act() === 'pain') {
        <app-demo-pain
          [clock]="clock()"
          (selectPain)="onPainSelected($event)"
          (continueToSolution)="startSolution()"
        />
      } @else {
        <main class="demo__stage">
          <app-demo-solution-chat
            [messages]="messages()"
            [typing]="typing()"
            [busy]="busy()"
            [inputValue]="input()"
            [calendarDays]="calendarDays()"
            [calendarSelectedDay]="calendarSelectedDay()"
            [calendarSelectedTime]="calendarSelectedTime()"
            (calendarDayPick)="onCalendarDayPick($event)"
            (calendarTimePick)="onCalendarTimePick($event)"
            (inputChange)="onInputChange($event)"
            (send)="onSend($event)"
            (chipClick)="onChip($event)"
            (back)="backToPain()"
          />
          <app-demo-system-panel
            [lead]="lead()"
            [bant]="bant()"
            [score]="score()"
            [feed]="feed()"
            [appointment]="appointment()"
            [appointmentSlot]="appointmentSlot()"
          />
          <div class="demo__confetti" #confettiHost aria-hidden="true"></div>
        </main>
        <footer class="demo__footer">
          <p class="demo__footer-text">
            Demo simulada. Sin registro, sin tarjeta. Datos de demostración, nada se guarda.
          </p>
          <p class="demo__footer-brand">Script9 · Automatización B2B para tu negocio</p>
        </footer>
      }
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        color-scheme: dark;
      }
      .demo {
        position: relative;
        min-height: 100dvh;
        overflow: hidden;
        background: var(--color-bg);
        color: #e2e8f0;
        font-family: 'Inter', var(--font-sans);
      }

      /* ---------- Stage ---------- */
      .demo__stage {
        position: relative;
        z-index: 1;
        width: min(1240px, 100% - 32px);
        margin: 0 auto;
        padding: 20px 0 8px;
        display: grid;
        grid-template-columns: minmax(0, 55fr) minmax(0, 45fr);
        gap: 20px;
        align-items: stretch;
      }
      .demo__stage > :not(.demo__confetti) {
        height: calc(100dvh - 88px);
        min-height: 560px;
      }
      .demo__confetti {
        position: absolute;
        inset: 0;
        z-index: 6;
        pointer-events: none;
        overflow: hidden;
      }
      .demo__confetti-piece {
        position: absolute;
        top: 0;
        width: 8px;
        height: 14px;
        border-radius: 2px;
      }
      .demo__footer {
        position: relative;
        z-index: 1;
        text-align: center;
        padding: 4px 16px 18px;
      }
      .demo__footer-text {
        margin: 0;
        font-size: 0.72rem;
        color: #64748b;
      }
      .demo__footer-brand {
        margin: 2px 0 0;
        font-size: 0.72rem;
        font-weight: 600;
        color: #94a3b8;
      }

      /* ---------- Responsive ---------- */
      @media (max-width: 960px) {
        .demo__stage {
          grid-template-columns: 1fr;
        }
        .demo__stage > :not(.demo__confetti) {
          height: auto;
          min-height: 0;
        }
      }
    `
  ]
})
export class DemoChatComponent implements OnDestroy {
  protected readonly act = signal<'pain' | 'solution'>('pain');
  protected readonly clock = signal('--:--:--');
  protected readonly painKey = signal<PainKey | null>(null);

  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly typing = signal(false);
  protected readonly busy = signal(false);
  protected readonly input = signal('');
  protected readonly stage = signal<ChatStage>('awaiting-name');
  protected readonly userName = signal<string | null>(null);

  protected readonly calendarDays = signal<CalendarDay[]>([]);
  protected readonly calendarSelectedDay = signal<string | null>(null);
  protected readonly calendarSelectedTime = signal<string | null>(null);

  protected readonly appointmentSlot = signal<{ dayKey: string; dayLong: string; time: string } | null>(null);

  protected readonly lead = signal<LeadState>({ name: '', company: '', phone: '', need: '' });
  protected readonly bant = signal<BantState>(INITIAL_BANT);
  protected readonly feed = signal<FeedEvent[]>([]);
  protected readonly appointment = signal(false);

  protected readonly score = computed(() => {
    const b = this.bant();
    return Math.round((b.budget + b.authority + b.need + b.timing) / 4);
  });

  protected readonly selectedPain = computed(
    () => PAINS.find((p) => p.key === this.painKey()) ?? null
  );

  private readonly confettiHost = viewChild<ElementRef<HTMLElement>>('confettiHost');

  private readonly pendingTimeouts: ReturnType<typeof setTimeout>[] = [];
  private clockInterval: ReturnType<typeof setInterval> | null = null;
  private feedId = 0;
  private declinedNotified = false;
  private confettiTweens: gsap.core.Tween[] = [];

  constructor() {
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = FONT_LINK;
    document.head.appendChild(link);

    const tick = (): void => {
      const d = new Date();
      const hh = String(d.getHours()).padStart(2, '0');
      const mm = String(d.getMinutes()).padStart(2, '0');
      const ss = String(d.getSeconds()).padStart(2, '0');
      this.clock.set(`${hh}:${mm}:${ss}`);
    };
    tick();
    this.clockInterval = setInterval(tick, 1000);
  }

  ngOnDestroy(): void {
    this.pendingTimeouts.forEach((t) => clearTimeout(t));
    this.confettiTweens.forEach((t) => t.kill());
    if (this.clockInterval) {
      clearInterval(this.clockInterval);
    }
  }

  /* ---------- Act navigation ---------- */

  protected onPainSelected(key: PainKey): void {
    this.painKey.set(key);
  }

  protected startSolution(): void {
    this.act.set('solution');
    this.stage.set('awaiting-name');
    this.userName.set(null);
    this.messages.set([]);
    this.lead.set({ name: '', company: '', phone: '', need: '' });
    this.bant.set(INITIAL_BANT);
    this.appointment.set(false);
    this.appointmentSlot.set(null);
    this.feed.set([]);
    this.declinedNotified = false;

    const pain = this.selectedPain();
    this.pushFeed('Lead detectado', 'activity', 'lead');
    if (pain) {
      this.pushFeed(`Dolor detectado: ${pain.title}`, 'target', 'intent');
    }
    this.pushFeed('Agente conectado · respuesta 0.4s', 'zap', 'info');
    this.reply(pain ? pain.greeting : GREETING);
  }

  protected backToPain(): void {
    this.busy.set(false);
    this.act.set('pain');
    this.painKey.set(null);
  }

  /* ---------- Input handling ---------- */

  protected onInputChange(value: string): void {
    this.input.set(value);
  }

  protected onSend(text: string): void {
    this.input.set('');
    this.sendText(text);
  }

  protected onChip(chip: string): void {
    if (this.typing() || this.busy()) {
      return;
    }
    this.messages.update((msgs) => {
      let done = false;
      return msgs.map((m) => {
        if (!done && m.chips && m.chips.length) {
          done = true;
          return { ...m, chips: [] };
        }
        return m;
      });
    });
    this.sendText(chip);
  }

  /* ---------- Conversation flow (conserved) ---------- */

  private sendText(text: string): void {
    if (this.typing() || this.busy()) {
      return;
    }
    this.messages.update((msgs) => [...msgs, { role: 'user', text, time: this.nowTime() }]);
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
      case 'calendar':
        this.handleCalendarInput(text);
        break;
      case 'declined':
        this.handleDeclined(text);
        break;
      default:
        if (this.isRestart(text)) {
          this.restart();
          return;
        }
        this.reply(
          'Tu llamada informativa ya está reservada. Si quieres ver la demo desde el principio, dime "Volver a empezar".'
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
    this.lead.update((l) => ({ ...l, name }));
    this.pushFeed(`Nombre capturado: ${name}`, 'user', 'name');
    this.bant.update((b) => ({ ...b, authority: 30, timing: 35 }));
    this.stage.set('questions');
    this.reply(
      `Encantado de conocerte, ${name}. ¿Qué te gustaría saber sobre el servicio?`,
      QUESTION_CHIPS
    );
  }

  private handleQuestion(text: string): void {
    if (this.isBookingIntent(text)) {
      this.offerAppointment();
      return;
    }
    if (this.isAccept(text) || this.isNoMoreQuestions(text)) {
      this.offerAppointment();
      return;
    }
    if (this.isAnotherQuestion(text)) {
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
    const rule = this.matchRule(text);
    if (rule) {
      this.onRuleMatched(rule);
      this.reply(rule.answer, FOLLOW_UP_CHIPS);
    } else {
      this.reply(FALLBACK_ANSWER, FOLLOW_UP_CHIPS);
    }
  }

  private handleOffer(text: string): void {
    if (this.isBookingIntent(text) || this.isAccept(text)) {
      this.openCalendar();
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
    const rule = this.matchRule(text);
    if (rule) {
      this.onRuleMatched(rule);
      this.reply(rule.answer);
      this.offerAppointment();
      return;
    }
    this.reply(
      'Disculpa, no te he entendido del todo. ¿Te agendo la llamada para mañana a las 10:00?',
      OFFER_CHIPS
    );
  }

  private handleDeclined(text: string): void {
    if (this.isRestart(text)) {
      this.restart();
      return;
    }
    if (this.isBookingIntent(text) || this.isAccept(text)) {
      this.offerAppointment();
      return;
    }
    if (!this.declinedNotified) {
      this.declinedNotified = true;
      this.pushFeed('Lead en seguimiento', 'clock', 'info');
    }
    this.reply(
      `Sin problema, ${this.userName()}. Cuando quieras, dime y te agendo la llamada.`
    );
  }

  private offerAppointment(): void {
    this.stage.set('offering');
    this.bant.update((b) => ({
      budget: Math.max(b.budget, 65),
      authority: Math.max(b.authority, 55),
      need: Math.max(b.need, 80),
      timing: Math.max(b.timing, 70)
    }));
    this.reply(OFFER_TEXT, OFFER_CHIPS);
  }

  /* ---------- Calendar (simulated, nothing is persisted) ---------- */

  private openCalendar(): void {
    this.stage.set('calendar');
    this.calendarDays.set(this.buildCalendarDays());
    this.calendarSelectedDay.set(null);
    this.calendarSelectedTime.set(null);
    this.pushFeed('Calendario sincronizado', 'calendar-plus', 'booking');
    this.reply(OFFER_TEXT);
  }

  protected onCalendarDayPick(dayKey: string): void {
    this.calendarSelectedDay.set(dayKey);
    this.calendarSelectedTime.set(null);
  }

  protected onCalendarTimePick(time: string): void {
    const day = this.calendarSelectedDay();
    if (!day) {
      return;
    }
    this.calendarSelectedTime.set(time);
    const picked = this.calendarDays().find((d) => d.key === day);
    const long = picked?.long ?? '';
    this.appointmentSlot.set({ dayKey: day, dayLong: long, time });
    this.pushFeed(`Franja elegida · ${long} · ${time}`, 'calendar-plus', 'booking');
    this.startBooking(long, time);
  }

  private handleCalendarInput(text: string): void {
    if (this.isRestart(text)) {
      this.restart();
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
    if (this.isAnotherQuestion(text)) {
      this.stage.set('questions');
      this.reply('Claro, dime.', QUESTION_CHIPS);
      return;
    }
    const rule = this.matchRule(text);
    if (rule) {
      this.onRuleMatched(rule);
      this.reply(rule.answer);
      return;
    }
    this.reply(
      'Elige el día y la hora en el calendario de abajo y te confirmo la reserva al momento.'
    );
  }

  private buildCalendarDays(): CalendarDay[] {
    const days: CalendarDay[] = [];
    const now = new Date();
    const cursor = new Date(now);
    cursor.setDate(cursor.getDate() + 1);
    const formatterShort = new Intl.DateTimeFormat('es-ES', {
      weekday: 'short',
      day: 'numeric'
    });
    const formatterLong = new Intl.DateTimeFormat('es-ES', {
      weekday: 'long',
      day: 'numeric',
      month: 'long'
    });
    while (days.length < 5) {
      const dow = cursor.getDay();
      if (dow !== 0 && dow !== 6) {
        days.push({
          key: cursor.toISOString().slice(0, 10),
          label: formatterShort.format(cursor),
          long: formatterLong.format(cursor),
          slots: ['09:30', '11:00', '12:30', '16:00', '17:30', '19:00']
        });
      }
      cursor.setDate(cursor.getDate() + 1);
    }
    return days;
  }

  private restart(): void {
    this.busy.set(false);
    this.stage.set('awaiting-name');
    this.userName.set(null);
    this.messages.set([]);
    this.lead.set({ name: '', company: '', phone: '', need: '' });
    this.bant.set(INITIAL_BANT);
    this.appointment.set(false);
    this.appointmentSlot.set(null);
    this.feed.set([]);
    this.declinedNotified = false;
    this.pushFeed('Sesión reiniciada', 'activity', 'info');
    this.reply(GREETING);
  }

  /* ---------- Booking (simulated, nothing is persisted) ---------- */

  private startBooking(dayLong: string, time: string): void {
    this.busy.set(true);
    this.stage.set('booking');
    this.lead.update((l) => (l.phone ? l : { ...l, phone: '+34 ·· ··· ···' }));
    this.pushFeed('Teléfono verificado', 'phone', 'company');
    this.reply('¡Perfecto! Dame un momento, estoy agendando tu llamada…');
    this.schedule(() => this.completeBooking(dayLong, time), 1400);
  }

  private completeBooking(dayLong: string, time: string): void {
    const name = this.userName()?.trim() ?? '';
    this.stage.set('done');
    this.busy.set(false);
    this.appointment.set(true);
    this.bant.set(FINAL_BANT);
    this.calendarDays.set([]);
    this.calendarSelectedDay.set(null);
    this.calendarSelectedTime.set(null);
    this.pushFeed('Cualificación 87% · LISTO PARA CITA', 'trend-up', 'score');
    this.pushFeed(`Cita agendada · ${dayLong} · ${time}`, 'calendar-plus', 'booking');
    this.pushFeed('CRM actualizado', 'database', 'crm');
    this.messages.update((msgs) => [
      ...msgs,
      {
        role: 'assistant',
        text: `${SUCCESS_TEXT} ${dayLong ? `Para el ${dayLong} a las ${time}.` : ''} ${name ? `Nos vemos, ${name}.` : ''}`,
        kind: 'success',
        time: this.nowTime()
      }
    ]);
    this.burstConfetti();
  }

  /* ---------- Script matching (conserved) ---------- */

  private matchRule(text: string): AnswerRule | null {
    const normalized = this.normalize(text);
    for (const rule of ANSWER_RULES) {
      if (rule.keywords.some((k) => normalized.includes(this.normalize(k)))) {
        return rule;
      }
    }
    return null;
  }

  private onRuleMatched(rule: AnswerRule): void {
    this.lead.update((l) => (l.company ? l : { ...l, company: 'Detectada' }));
    this.lead.update((l) => (l.need ? l : { ...l, need: rule.need }));
    this.pushFeed('Perfil de empresa detectado', 'building', 'company');
    this.pushFeed(`Intención detectada: ${rule.need}`, 'target', 'intent');
    this.bant.update((b) => ({
      budget: Math.min(90, b.budget + 25),
      authority: Math.min(90, b.authority + 15),
      need: Math.max(75, b.need + 20),
      timing: Math.min(90, b.timing + 10)
    }));
    if (rule.keywords.includes('precio')) {
      this.bant.update((b) => ({ ...b, budget: 85 }));
      this.pushFeed('Presupuesto identificado', 'trend-up', 'score');
    }
  }

  private isAccept(text: string): boolean {
    const n = this.normalize(text);
    if (n === 'si' || n.startsWith('si ') || n.startsWith('si,')) {
      return true;
    }
    return /\b(ok|okay|vale|claro|adelante|dale|perfecto|genial|agenda|agendame|apuntame|confirmo)\b/.test(
      n
    );
  }

  private isBookingIntent(text: string): boolean {
    const n = this.normalize(text);
    return (
      n.includes('agendar') ||
      n.includes('agendame') ||
      n.includes('reservar') ||
      n.includes('reservame') ||
      n.includes('apuntame') ||
      n.includes('quiero la llamada') ||
      n.includes('quiero una llamada') ||
      n.includes('me gustaria la llamada') ||
      n.includes('quiero hablar') ||
      n.includes('me gustaria hablar') ||
      n.includes('hablar con un') ||
      n.includes('hablar con alguien')
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

  private isNoMoreQuestions(text: string): boolean {
    const n = this.normalize(text);
    return (
      n.includes('no tengo mas') ||
      n.includes('no me queda') ||
      n.includes('era todo') ||
      n.includes('eso es todo') ||
      n.includes('nada mas') ||
      n.includes('sin mas dudas') ||
      n.includes('ya esta') ||
      n.includes('ya estaria')
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

  /* ---------- System panel sync ---------- */

  private pushFeed(text: string, icon: DemoIconName, kind: FeedKind): void {
    this.feedId += 1;
    this.feed.update((events) => [
      ...events,
      { id: this.feedId, time: this.nowTimeSeconds(), text, icon, kind }
    ].slice(-9));
  }

  private burstConfetti(): void {
    const host = this.confettiHost()?.nativeElement;
    if (!host || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    const originX = host.clientWidth / 2;
    for (let i = 0; i < 64; i++) {
      const piece = document.createElement('span');
      piece.className = 'demo__confetti-piece';
      piece.style.position = 'absolute';
      piece.style.top = '0';
      piece.style.width = '8px';
      piece.style.height = '14px';
      piece.style.borderRadius = '2px';
      piece.style.background = CONFETTI_COLORS[i % CONFETTI_COLORS.length];
      piece.style.left = `${originX}px`;
      host.appendChild(piece);
      const tween = gsap.fromTo(
        piece,
        { y: -24, x: (Math.random() - 0.5) * 140, scale: 0.4, opacity: 1, rotation: 0 },
        {
          y: host.clientHeight + 140 + Math.random() * 160,
          x: (Math.random() - 0.5) * 900,
          scale: 1,
          opacity: 0,
          rotation: (Math.random() * 2 - 1) * 1080,
          duration: 1.4 + Math.random() * 1.4,
          ease: 'power1.in',
          delay: Math.random() * 0.25,
          onComplete: () => piece.remove()
        }
      );
      this.confettiTweens.push(tween);
    }
  }

  /* ---------- Helpers (conserved) ---------- */

  private nowTime(): string {
    const d = new Date();
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }

  private nowTimeSeconds(): string {
    const d = new Date();
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;
  }

  private reply(text: string, chips?: string[]): void {
    this.typing.set(true);
    this.schedule(() => {
      this.typing.set(false);
      this.messages.update((msgs) => [
        ...msgs,
        { role: 'assistant', text, chips, time: this.nowTime() }
      ]);
    }, 400 + Math.random() * 400);
  }

  private schedule(fn: () => void, delay: number): void {
    const id = setTimeout(fn, delay);
    this.pendingTimeouts.push(id);
  }
}