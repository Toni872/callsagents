import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  afterNextRender,
  inject,
  signal,
  viewChild
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiUrl } from '../../core/api/api-base';

interface ChatMessage {
  role: 'bot' | 'user' | 'system';
  text: string;
  time: string;
}

@Component({
  selector: 'app-chat-widget',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="chat">
      <header class="chat__header">
        <div class="chat__brand">
          <span class="chat__brand-name">CALLSAGENTS</span>
          <span class="chat__online-dot"></span>
          <span class="chat__online-label">En línea</span>
        </div>
      </header>

      <div class="chat__messages" #messagesContainer>
        @for (msg of messages(); track msg.time + msg.text) {
          @if (msg.role === 'system') {
            <div class="chat__system">{{ msg.text }}</div>
          } @else {
            <div class="chat__bubble" [class.chat__bubble--user]="msg.role === 'user'">
              <span class="chat__text">{{ msg.text }}</span>
              <span class="chat__time">{{ msg.time }}</span>
            </div>
          }
        }
        @if (typing()) {
          <div class="chat__bubble chat__bubble--typing">
            <span class="chat__dots">
              <span></span><span></span><span></span>
            </span>
          </div>
        }
      </div>

      @if (leadCaptured()) {
        <div class="chat__lead-banner">
          ¡Perfecto! Te hemos registrado. Pronto nos pondremos en contacto.
        </div>
      }

      <div class="chat__input-row">
        <input
          class="chat__input"
          type="text"
          placeholder="Escribe un mensaje..."
          [value]="input()"
          (keydown.enter)="send()"
          (input)="onInput($event)"
          [disabled]="typing()"
        />
        <button
          class="chat__send"
          (click)="send()"
          [disabled]="typing() || !input().trim()"
          aria-label="Enviar"
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13"></line>
            <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
          </svg>
        </button>
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;
      height: 100dvh;
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
      color-scheme: dark;
    }

    .chat {
      display: flex;
      flex-direction: column;
      height: 100%;
      background: #0f172a;
      color: #e2e8f0;
    }

    .chat__header {
      display: flex;
      align-items: center;
      padding: 14px 16px;
      background: #1e293b;
      border-bottom: 1px solid #334155;
    }

    .chat__brand {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .chat__brand-name {
      font-size: 0.85rem;
      font-weight: 700;
      letter-spacing: 0.08em;
      color: #f8fafc;
    }

    .chat__online-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #00a86b;
      box-shadow: 0 0 6px rgba(0, 168, 107, 0.6);
    }

    .chat__online-label {
      font-size: 0.72rem;
      color: #64748b;
    }

    .chat__messages {
      flex: 1;
      overflow-y: auto;
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .chat__messages::-webkit-scrollbar {
      width: 4px;
    }
    .chat__messages::-webkit-scrollbar-track {
      background: transparent;
    }
    .chat__messages::-webkit-scrollbar-thumb {
      background: #334155;
      border-radius: 4px;
    }

    .chat__bubble {
      max-width: 78%;
      padding: 10px 14px;
      border-radius: 12px;
      background: #1e293b;
      color: #e2e8f0;
      font-size: 0.85rem;
      line-height: 1.5;
      align-self: flex-start;
    }

    .chat__bubble--user {
      background: #00a86b;
      color: #fff;
      align-self: flex-end;
      border-bottom-right-radius: 4px;
    }

    .chat__bubble--typing {
      background: #1e293b;
      align-self: flex-start;
      padding: 12px 18px;
    }

    .chat__text {
      display: block;
      white-space: pre-wrap;
      word-break: break-word;
    }

    .chat__time {
      display: block;
      margin-top: 4px;
      font-size: 0.65rem;
      color: #64748b;
      text-align: right;
    }

    .chat__bubble--user .chat__time {
      color: rgba(255, 255, 255, 0.6);
    }

    .chat__system {
      text-align: center;
      font-size: 0.72rem;
      color: #64748b;
      padding: 4px 0;
    }

    .chat__dots {
      display: flex;
      gap: 4px;
    }

    .chat__dots span {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #64748b;
      animation: dotBounce 1.2s infinite;
    }

    .chat__dots span:nth-child(2) {
      animation-delay: 0.15s;
    }

    .chat__dots span:nth-child(3) {
      animation-delay: 0.3s;
    }

    @keyframes dotBounce {
      0%, 60%, 100% { opacity: 0.3; transform: translateY(0); }
      30% { opacity: 1; transform: translateY(-3px); }
    }

    .chat__lead-banner {
      margin: 0 16px;
      padding: 10px 14px;
      background: rgba(0, 168, 107, 0.15);
      border: 1px solid rgba(0, 168, 107, 0.3);
      border-radius: 8px;
      font-size: 0.8rem;
      color: #4ade80;
      text-align: center;
    }

    .chat__input-row {
      display: flex;
      gap: 8px;
      padding: 12px 16px;
      background: #1e293b;
      border-top: 1px solid #334155;
    }

    .chat__input {
      flex: 1;
      padding: 10px 14px;
      background: #0f172a;
      border: 1px solid #334155;
      border-radius: 8px;
      color: #e2e8f0;
      font-size: 0.85rem;
      font-family: inherit;
      outline: none;
      transition: border-color 0.2s;
    }

    .chat__input::placeholder {
      color: #64748b;
    }

    .chat__input:focus {
      border-color: #00a86b;
    }

    .chat__input:disabled {
      opacity: 0.5;
    }

    .chat__send {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      background: #00a86b;
      border: none;
      border-radius: 8px;
      color: #fff;
      cursor: pointer;
      transition: background 0.2s;
      flex-shrink: 0;
    }

    .chat__send:hover:not(:disabled) {
      background: #009959;
    }

    .chat__send:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
  `
})
export class ChatWidgetComponent implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly messagesContainer = viewChild<ElementRef<HTMLElement>>('messagesContainer');

  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly input = signal('');
  protected readonly typing = signal(false);
  protected readonly leadCaptured = signal(false);
  protected readonly sessionId = signal(this.getOrCreateSessionId());

  constructor() {
    afterNextRender(() => {
      this.scrollBottom();
      if (this.messages().length === 0) {
        this.messages.set([
          { role: 'bot', text: '¡Hola! Soy tu asistente de CallsAgents. ¿En qué puedo ayudarte hoy?', time: this.now() }
        ]);
      }
    });
  }

  ngOnDestroy(): void {}

  protected onInput(event: Event): void {
    this.input.set((event.target as HTMLInputElement).value);
  }

  protected send(): void {
    const text = this.input().trim();
    if (!text || this.typing()) return;

    const userMsg: ChatMessage = { role: 'user', text, time: this.now() };
    this.messages.update((msgs) => [...msgs, userMsg]);
    this.input.set('');
    this.typing.set(true);
    this.scrollBottom();

    const payload = { sessionId: this.sessionId(), message: text };

    this.http.post<{ sessionId: string; response: string; leadCaptured: boolean }>(
      apiUrl('/chat/message'),
      payload
    ).subscribe({
      next: (res) => {
        if (res.sessionId && res.sessionId !== this.sessionId()) {
          this.sessionId.set(res.sessionId);
          localStorage.setItem('callsagents_chat_session', res.sessionId);
        }
        if (res.leadCaptured) {
          this.leadCaptured.set(true);
        }
        this.messages.update((msgs) => [
          ...msgs,
          { role: 'bot', text: res.response, time: this.now() }
        ]);
        this.typing.set(false);
        this.scrollBottom();
      },
      error: () => {
        this.messages.update((msgs) => [
          ...msgs,
          { role: 'system', text: 'Error de conexión. Intenta de nuevo.', time: this.now() }
        ]);
        this.typing.set(false);
      }
    });
  }

  private getOrCreateSessionId(): string {
    try {
      const stored = localStorage.getItem('callsagents_chat_session');
      if (stored) return stored;
    } catch {}
    const id = crypto.randomUUID();
    try {
      localStorage.setItem('callsagents_chat_session', id);
    } catch {}
    return id;
  }

  private now(): string {
    const d = new Date();
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }

  private scrollBottom(): void {
    setTimeout(() => {
      const el = this.messagesContainer()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}
