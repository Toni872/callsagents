import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, input, output, viewChild } from '@angular/core';
import { DemoIconComponent } from './demo-icon.component';
import { CONTACT_URL, SUCCESS_NOTE, type CalendarDay, type ChatMessage } from './demo.constants';

@Component({
  selector: 'app-demo-solution-chat',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DemoIconComponent],
  template: `
    <section class="chat glass" aria-label="Conversación con el asistente">
      <header class="chat__head">
        <div class="chat__titles">
          <strong class="chat__title"><span class="chat__brand-calls">CALLS</span><span class="chat__brand-agents">AGENTS</span></strong>
          <span class="chat__badge">
            <span class="chat__badge-dot" aria-hidden="true"></span>
            respuesta en 0.4s
          </span>
        </div>
        <button class="chat__back" type="button" (click)="back.emit()" aria-label="Volver al inicio">
          <demo-icon name="arrow-left" [size]="18" />
        </button>
      </header>

      <div class="chat__log" #log role="log" aria-live="polite">
        @for (m of messages(); track m; let i = $index) {
          <div class="msg" [class.msg--user]="m.role === 'user'" [style.animation-delay]="(i * 40) + 'ms'">
            <div class="msg__bubble" [class.msg__bubble--success]="m.kind === 'success'">
              @if (m.kind === 'success') {
                <span class="msg__check"><demo-icon name="check" [size]="26" /></span>
              }
              <p class="msg__text">{{ m.text }}</p>
              @if (m.kind === 'success') {
                <a class="msg__cta" [href]="contactUrl" target="_blank" rel="noopener noreferrer">
                  ¿Quieres esto en tu negocio? Hablemos
                  <demo-icon name="arrow-right" [size]="16" />
                </a>
                <p class="msg__note">{{ note }}</p>
              }
              @if (m.chips && m.chips.length) {
                <div class="msg__chips">
                  @for (chip of m.chips; track chip) {
                    <button class="chip" type="button" (click)="chipClick.emit(chip)">{{ chip }}</button>
                  }
                </div>
              }
              <span class="msg__time">{{ m.time }}</span>
            </div>
          </div>
        }
        @if (typing()) {
          <div class="msg" role="status" aria-label="El asistente está escribiendo">
            <div class="msg__bubble typing">
              <span></span><span></span><span></span>
            </div>
          </div>
        }
        @if (calendarDays().length && !typing()) {
          <div class="msg" role="group" aria-label="Elige día y hora para la llamada">
            <div class="msg__bubble cal">
              <p class="cal__title">Elige el día</p>
              <div class="cal__days">
                @for (d of calendarDays(); track d.key) {
                  <button
                    class="cal__day"
                    [class.cal__day--on]="d.key === calendarSelectedDay()"
                    type="button"
                    (click)="calendarDayPick.emit(d.key)"
                  >{{ d.label }}</button>
                }
              </div>
              @if (selectedDaySlots(); as slots) {
                <p class="cal__title">Elige la hora</p>
                <div class="cal__times">
                  @for (t of slots; track t) {
                    <button
                      class="cal__time"
                      [class.cal__time--on]="t === calendarSelectedTime()"
                      type="button"
                      (click)="calendarTimePick.emit(t)"
                    >{{ t }}</button>
                  }
                </div>
              }
              <p class="cal__hint">La reserva es simulada: en el producto real se conecta a tu calendario.</p>
            </div>
          </div>
        }
      </div>

      <form class="chat__input" (submit)="onSubmit($event)">
        <input
          #inputEl
          type="text"
          [value]="inputValue()"
          (input)="onInput($event)"
          [disabled]="typing() || busy()"
          placeholder="Escribe tu mensaje…"
          aria-label="Mensaje"
          autocomplete="off"
        />
        <button
          class="chat__send"
          type="submit"
          [disabled]="typing() || busy() || !inputValue().trim()"
          aria-label="Enviar"
        >
          <demo-icon name="arrow-up" [size]="18" />
        </button>
      </form>
    </section>
  `,
  styles: [
    `
      :host {
        display: flex;
        min-height: 0;
      }
      .glass {
        background: rgba(13, 19, 31, 0.6);
        backdrop-filter: blur(20px);
        -webkit-backdrop-filter: blur(20px);
        border: 1px solid rgba(255, 255, 255, 0.08);
      }
      .chat {
        display: flex;
        flex-direction: column;
        width: 100%;
        border-radius: 24px;
        box-shadow: 0 24px 64px rgba(2, 6, 23, 0.5);
        overflow: hidden;
        min-height: 0;
      }

      /* ---------- Header ---------- */
      .chat__head {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 14px 18px;
        border-bottom: 1px solid rgba(255, 255, 255, 0.08);
        background: rgba(13, 19, 31, 0.5);
        flex-shrink: 0;
      }
      .chat__titles {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .chat__title {
        font-family: 'Space Grotesk', var(--font-display);
        font-size: 0.98rem;
        font-weight: 800;
        letter-spacing: -0.02em;
      }
      .chat__brand-calls {
        color: var(--color-text-strong);
      }
      .chat__brand-agents {
        color: var(--color-primary);
      }
      .chat__badge {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-size: 0.72rem;
        font-weight: 500;
        color: var(--color-primary-hover);
      }
      .chat__badge-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: var(--color-primary);
        animation: badge-pulse 2s ease-out infinite;
      }
      @keyframes badge-pulse {
        0% { box-shadow: 0 0 0 0 rgba(0, 168, 107, 0.5); }
        70% { box-shadow: 0 0 0 6px rgba(0, 168, 107, 0); }
        100% { box-shadow: 0 0 0 0 rgba(0, 168, 107, 0); }
      }
      .chat__back {
        margin-left: auto;
        width: 44px;
        height: 44px;
        flex-shrink: 0;
        display: grid;
        place-items: center;
        border-radius: 10px;
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid rgba(255, 255, 255, 0.1);
        color: #cbd5e1;
        transition: transform 300ms cubic-bezier(0.22, 1, 0.36, 1), background-color 300ms, color 300ms;
      }
      .chat__back:hover {
        background: rgba(255, 255, 255, 0.1);
        color: #f8fafc;
        transform: translateX(-2px);
      }
      .chat__back:focus-visible {
        outline: none;
        box-shadow: 0 0 0 3px rgba(0, 168, 107, 0.35);
      }

      /* ---------- Log ---------- */
      .chat__log {
        flex: 1;
        overflow-y: auto;
        padding: 18px;
        display: flex;
        flex-direction: column;
        gap: 12px;
        scroll-behavior: smooth;
        scrollbar-width: thin;
        scrollbar-color: rgba(255, 255, 255, 0.12) transparent;
      }
      .chat__log::-webkit-scrollbar {
        width: 8px;
      }
      .chat__log::-webkit-scrollbar-thumb {
        background: rgba(255, 255, 255, 0.12);
        border-radius: 8px;
      }
      .msg {
        display: flex;
        animation: msg-in 0.3s cubic-bezier(0.22, 1, 0.36, 1) both;
      }
      @keyframes msg-in {
        from { opacity: 0; transform: translateY(10px); }
      }
      .msg--user {
        justify-content: flex-end;
      }
      .msg__bubble {
        max-width: 82%;
        display: flex;
        flex-direction: column;
        gap: 4px;
        padding: 10px 14px;
        border-radius: 18px;
        border-bottom-left-radius: 6px;
        background: rgba(30, 41, 59, 0.75);
        border: 1px solid rgba(255, 255, 255, 0.08);
        color: #f1f5f9;
        box-shadow: 0 4px 16px rgba(2, 6, 23, 0.35);
        animation: bubble-in 0.35s cubic-bezier(0.22, 1, 0.36, 1) both;
      }
      @keyframes bubble-in {
        from { opacity: 0; transform: translateY(10px) scale(0.97); }
      }
      .msg--user .msg__bubble {
        background: linear-gradient(135deg, var(--color-primary), var(--color-primary-hover));
        border-color: rgba(255, 255, 255, 0.12);
        border-bottom-left-radius: 18px;
        border-bottom-right-radius: 6px;
        color: #ffffff;
      }
      .msg__bubble--success {
        align-items: center;
        text-align: center;
        background: rgba(0, 168, 107, 0.12);
        border-color: rgba(0, 168, 107, 0.4);
      }
      .msg__check {
        width: 44px;
        height: 44px;
        border-radius: 50%;
        display: grid;
        place-items: center;
        background: rgba(0, 168, 107, 0.15);
        color: var(--color-primary);
        margin-bottom: 4px;
        animation: check-pop 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) both;
      }
      @keyframes check-pop {
        from { transform: scale(0); }
      }
      .msg__text {
        margin: 0;
        font-size: 0.9rem;
        line-height: 1.55;
        white-space: pre-wrap;
        word-break: break-word;
      }
      .msg__cta {
        position: relative;
        overflow: hidden;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        margin-top: 8px;
        padding: 11px 22px;
        border-radius: 999px;
        background: linear-gradient(135deg, var(--color-primary), var(--color-primary-hover));
        color: #02150c;
        font-size: 0.86rem;
        font-weight: 700;
        text-decoration: none;
        box-shadow: 0 8px 24px rgba(0, 168, 107, 0.4);
        transition: transform 300ms cubic-bezier(0.22, 1, 0.36, 1), box-shadow 300ms cubic-bezier(0.22, 1, 0.36, 1);
      }
      .msg__cta::after {
        content: '';
        position: absolute;
        inset: 0;
        background: linear-gradient(100deg, transparent 30%, rgba(255, 255, 255, 0.45) 50%, transparent 70%);
        transform: translateX(-120%);
        animation: cta-shimmer 2.6s ease-in-out infinite;
      }
      @keyframes cta-shimmer {
        0%, 55% { transform: translateX(-120%); }
        85%, 100% { transform: translateX(120%); }
      }
      .msg__cta:hover {
        transform: scale(1.03);
        box-shadow: 0 12px 32px rgba(0, 168, 107, 0.55);
      }
      .msg__cta:focus-visible {
        outline: none;
        box-shadow: 0 0 0 3px rgba(0, 168, 107, 0.45);
      }
      .msg__note {
        margin: 2px 0 0;
        font-size: 0.72rem;
        line-height: 1.4;
        color: #94a3b8;
      }
      .msg__time {
        font-size: 0.7rem;
        opacity: 0.75;
        text-align: right;
        margin-top: 2px;
        font-variant-numeric: tabular-nums;
      }
      .msg__chips {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        margin-top: 8px;
      }
      .chip {
        padding: 7px 14px;
        border-radius: 999px;
        border: 1px solid rgba(0, 168, 107, 0.4);
        background: rgba(0, 168, 107, 0.08);
        color: var(--color-primary-hover);
        font-size: 0.8rem;
        font-weight: 500;
        transition: transform 300ms cubic-bezier(0.22, 1, 0.36, 1), background-color 300ms, border-color 300ms;
      }
      .chip:hover:not(:disabled) {
        background: rgba(0, 168, 107, 0.18);
        border-color: var(--color-primary);
        transform: translateY(-1px);
      }
      .chip:focus-visible {
        outline: none;
        box-shadow: 0 0 0 3px rgba(0, 168, 107, 0.35);
      }

      /* ---------- Typing ---------- */
      .typing {
        display: flex;
        gap: 4px;
        align-items: center;
        padding: 13px 15px;
      }
      .typing span {
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background: #94a3b8;
        animation: typing-bounce 1.1s infinite ease-in-out;
      }
      .typing span:nth-child(2) {
        animation-delay: 0.13s;
      }
      .typing span:nth-child(3) {
        animation-delay: 0.26s;
      }
      @keyframes typing-bounce {
        0%, 80%, 100% {
          transform: translateY(0);
          opacity: 0.4;
        }
        40% {
          transform: translateY(-4px);
          opacity: 1;
        }
      }

      /* ---------- Input ---------- */
      .chat__input {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 14px 16px;
        border-top: 1px solid rgba(255, 255, 255, 0.08);
        background: rgba(13, 19, 31, 0.5);
        flex-shrink: 0;
      }
      .chat__input input {
        flex: 1;
        min-width: 0;
        padding: 12px 18px;
        border-radius: 999px;
        border: 1px solid rgba(255, 255, 255, 0.12);
        background: rgba(30, 41, 59, 0.6);
        color: #f1f5f9;
        font-size: 0.9rem;
        transition: border-color 300ms, box-shadow 300ms;
      }
      .chat__input input::placeholder {
        color: #64748b;
      }
      .chat__input input:focus {
        outline: none;
        border-color: rgba(0, 168, 107, 0.6);
        box-shadow: 0 0 0 3px rgba(0, 168, 107, 0.18);
      }
      .chat__send {
        width: 44px;
        height: 44px;
        flex-shrink: 0;
        border: none;
        background: transparent;
        display: grid;
        place-items: center;
        color: var(--color-primary);
        cursor: pointer;
        transition: color 300ms, transform 300ms cubic-bezier(0.22, 1, 0.36, 1);
      }
      .chat__send:hover:not(:disabled) {
        transform: scale(1.08);
        color: var(--color-primary-hover);
      }
      .chat__send:disabled {
        opacity: 0.4;
        cursor: default;
      }

      /* ---------- Calendar (simulated booking) ---------- */
      .msg__bubble.cal {
        max-width: 100%;
      }
      .cal__title {
        margin: 0 0 8px;
        font-size: 0.78rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: #94a3b8;
      }
      .cal__days {
        display: grid;
        grid-template-columns: repeat(5, 1fr);
        gap: 6px;
        margin-bottom: 12px;
      }
      .cal__day {
        padding: 8px 4px;
        border-radius: 10px;
        border: 1px solid rgba(255, 255, 255, 0.1);
        background: rgba(30, 41, 59, 0.6);
        color: #cbd5e1;
        font-size: 0.72rem;
        font-weight: 600;
        text-align: center;
        cursor: pointer;
        transition: border-color 200ms, background-color 200ms, color 200ms, transform 200ms;
      }
      .cal__day:hover:not(:disabled) {
        border-color: rgba(0, 168, 107, 0.5);
        color: #f8fafc;
        transform: translateY(-1px);
      }
      .cal__day--on {
        border-color: var(--color-primary);
        background: rgba(0, 168, 107, 0.16);
        color: var(--color-text-strong);
      }
      .cal__times {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
        margin-bottom: 12px;
      }
      .cal__time {
        padding: 7px 14px;
        border-radius: 999px;
        border: 1px solid rgba(255, 255, 255, 0.12);
        background: rgba(30, 41, 59, 0.6);
        color: #cbd5e1;
        font-size: 0.78rem;
        font-weight: 600;
        cursor: pointer;
        transition: border-color 200ms, background-color 200ms, color 200ms;
      }
      .cal__time:hover:not(:disabled) {
        border-color: rgba(0, 168, 107, 0.5);
        color: #f8fafc;
      }
      .cal__time--on {
        border-color: var(--color-primary);
        background: var(--color-primary);
        color: #02150c;
      }
      .cal__hint {
        margin: 0;
        font-size: 0.68rem;
        color: #64748b;
      }

      /* ---------- Responsive ---------- */
      @media (max-width: 960px) {
        .chat {
          height: 68dvh;
          border-radius: 20px;
        }
      }

      /* ---------- Reduced motion ---------- */
      @media (prefers-reduced-motion: reduce) {
        .msg,
        .msg__bubble,
        .msg__check,
        .typing span,
        .chat__badge-dot {
          animation: none;
        }
        .msg__cta::after {
          display: none;
        }
      }
    `
  ]
})
export class DemoSolutionChatComponent {
  readonly messages = input<ChatMessage[]>([]);
  readonly typing = input(false);
  readonly busy = input(false);
  readonly inputValue = input('');
  readonly calendarDays = input<CalendarDay[]>([]);
  readonly calendarSelectedDay = input<string | null>(null);
  readonly calendarSelectedTime = input<string | null>(null);
  readonly inputChange = output<string>();
  readonly send = output<string>();
  readonly chipClick = output<string>();
  readonly calendarDayPick = output<string>();
  readonly calendarTimePick = output<string>();
  readonly back = output<void>();

  protected readonly contactUrl = CONTACT_URL;
  protected readonly note = SUCCESS_NOTE;

  protected readonly selectedDaySlots = computed(() => {
    const key = this.calendarSelectedDay();
    if (!key) {
      return null;
    }
    return this.calendarDays().find((d) => d.key === key)?.slots ?? null;
  });

  private readonly logEl = viewChild.required<ElementRef<HTMLElement>>('log');
  private readonly inputEl = viewChild<ElementRef<HTMLInputElement>>('inputEl');

  private readonly scrollEffect = effect(() => {
    this.messages();
    this.typing();
    requestAnimationFrame(() => {
      const el = this.logEl().nativeElement;
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
    });
  });

  protected onInput(event: Event): void {
    this.inputChange.emit((event.target as HTMLInputElement).value);
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    const text = this.inputValue().trim();
    if (!text) {
      return;
    }
    this.send.emit(text);
    this.inputEl()?.nativeElement.focus();
  }
}