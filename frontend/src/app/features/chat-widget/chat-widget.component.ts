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
import { firstValueFrom } from 'rxjs';
import { apiUrl } from '../../core/api/api-base';
import { VoiceWebApi } from '../../core/api/voice-web.api';
import { WidgetConfigResponse } from '../../shared/models/business-profile.model';
import { RetellWebClient } from 'retell-client-js-sdk';

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
          <span class="chat__brand-name">{{ widgetConfig()?.companyName || 'CALLSAGENTS' }}</span>
          <span class="chat__online-dot"></span>
          <span class="chat__online-label">En línea</span>
        </div>
        <button
          class="chat__voice"
          [class.chat__voice--active]="voiceActive()"
          [disabled]="voiceCalling()"
          (click)="toggleVoiceCall()"
          attr.aria-label="{{ voiceActive() ? 'Colgar llamada' : 'Hablar con un agente' }}"
        >
          @if (voiceCalling()) {
            <span class="chat__voice-spinner" aria-hidden="true"></span>
          } @else if (voiceActive()) {
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
            <span class="chat__voice-text">Colgar</span>
          } @else {
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92z"></path>
            </svg>
            <span class="chat__voice-text">Hablar con un agente</span>
          }
        </button>
      </header>

      @if (voiceError()) {
        <div class="chat__voice-error">
          {{ voiceError() }}
        </div>
      }

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
          [style.background]="widgetConfig()?.chatColor || undefined"
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

    .chat__voice {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-left: auto;
      padding: 6px 12px;
      background: #0f172a;
      border: 1px solid #334155;
      border-radius: 999px;
      color: #a7f3d0;
      font-size: 0.75rem;
      font-weight: 600;
      font-family: inherit;
      cursor: pointer;
      transition: border-color 0.2s, background 0.2s, color 0.2s;
      flex-shrink: 0;
    }

    .chat__voice:hover:not(:disabled) {
      border-color: #00a86b;
      color: #ffffff;
      background: rgba(0, 168, 107, 0.12);
    }

    .chat__voice:disabled {
      opacity: 0.6;
      cursor: wait;
    }

    .chat__voice--active {
      border-color: #ef4444;
      color: #fecaca;
      background: rgba(239, 68, 68, 0.12);
    }

    .chat__voice--active:hover:not(:disabled) {
      border-color: #ef4444;
      color: #ffffff;
      background: rgba(239, 68, 68, 0.2);
    }

    .chat__voice-text {
      white-space: nowrap;
    }

    .chat__voice-spinner {
      width: 14px;
      height: 14px;
      border: 2px solid rgba(0, 168, 107, 0.3);
      border-top-color: #00a86b;
      border-radius: 50%;
      animation: voiceSpin 0.8s linear infinite;
    }

    @keyframes voiceSpin {
      to { transform: rotate(360deg); }
    }

    .chat__voice-error {
      margin: 0 16px;
      padding: 8px 12px;
      background: rgba(239, 68, 68, 0.12);
      border: 1px solid rgba(239, 68, 68, 0.3);
      border-radius: 8px;
      font-size: 0.75rem;
      color: #fca5a5;
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
  private readonly voiceWebApi = inject(VoiceWebApi);
  private readonly messagesContainer = viewChild<ElementRef<HTMLElement>>('messagesContainer');

  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly input = signal('');
  protected readonly typing = signal(false);
  protected readonly leadCaptured = signal(false);
  protected readonly sessionId = signal(this.getOrCreateSessionId());
  protected readonly widgetConfig = signal<WidgetConfigResponse | null>(null);
  protected readonly businessId = signal<string | null>(this.resolveBusinessId());

  protected readonly voiceCalling = signal(false);
  protected readonly voiceActive = signal(false);
  protected readonly voiceError = signal<string | null>(null);

  private retellClient: RetellWebClient | null = null;

  constructor() {
    afterNextRender(() => {
      this.loadWidgetConfig();
      this.scrollBottom();
      if (this.messages().length === 0) {
        this.messages.set([
          { role: 'bot', text: 'Hola! Soy tu asistente de CallsAgents. En que puedo ayudarte hoy?', time: this.now() }
        ]);
      }
    });
  }

  ngOnDestroy(): void {
    this.stopVoiceCall();
  }

  protected toggleVoiceCall(): void {
    if (this.voiceCalling()) return;
    if (this.voiceActive()) {
      this.stopVoiceCall();
    } else {
      void this.startVoiceCall();
    }
  }

  private async startVoiceCall(): Promise<void> {
    this.voiceCalling.set(true);
    this.voiceError.set(null);

    try {
      const res = await firstValueFrom(this.voiceWebApi.createWebCall());
      if (!res?.access_token) {
        throw new Error('No se recibió un token de acceso del servidor.');
      }

      const client = new RetellWebClient();
      this.retellClient = client;

      client.on('call_started', () => {
        this.voiceCalling.set(false);
        this.voiceActive.set(true);
      });

      client.on('call_ended', () => {
        this.voiceCalling.set(false);
        this.voiceActive.set(false);
        this.retellClient = null;
      });

      client.on('error', (error) => {
        this.voiceCalling.set(false);
        this.voiceActive.set(false);
        this.voiceError.set(
          typeof error === 'string' ? error : 'Error al conectar la llamada de voz.'
        );
        this.retellClient = null;
        try {
          client.stopCall();
        } catch {}
      });

      await client.startCall({ accessToken: res.access_token });
      this.voiceCalling.set(false);
    } catch (err) {
      this.voiceCalling.set(false);
      this.voiceActive.set(false);
      const msg = err instanceof Error ? err.message : 'Error al iniciar la llamada de voz.';
      this.voiceError.set(msg);
      if (this.retellClient) {
        try {
          this.retellClient.stopCall();
        } catch {}
        this.retellClient = null;
      }
    }
  }

  private stopVoiceCall(): void {
    if (this.retellClient) {
      try {
        this.retellClient.stopCall();
      } catch {}
      this.retellClient = null;
    }
    this.voiceCalling.set(false);
    this.voiceActive.set(false);
  }

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

    const payload: Record<string, string> = { sessionId: this.sessionId(), message: text };
    if (this.businessId()) {
      payload['businessId'] = this.businessId()!;
    }

    this.http.post<{ sessionId: string; reply: string; leadCaptured: boolean }>(
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
          { role: 'bot', text: res.reply, time: this.now() }
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

  private loadWidgetConfig(): void {
    const bid = this.businessId();
    if (!bid) {
      // No businessId configured — use defaults silently
      return;
    }
    this.http.get<{ success: boolean; data: WidgetConfigResponse }>(
      apiUrl(`/business/profile/widget-config/${bid}`)
    ).subscribe({
      next: (res) => {
        if (res.data) {
          this.widgetConfig.set(res.data);
          // Update the initial greeting if config is available
          if (this.messages().length === 1 && res.data.greeting) {
            this.messages.set([
              { role: 'bot', text: res.data.greeting, time: this.now() }
            ]);
          }
        }
      },
      error: () => {
        // Widget config not available — use defaults silently
      }
    });
  }

  private resolveBusinessId(): string | null {
    // 1. Check window.CallsagentsConfig (set by widget.js embed)
    try {
      const w = window as unknown as Record<string, unknown>;
      const cfg = w['CallsagentsConfig'] as Record<string, unknown> | undefined;
      if (cfg && typeof cfg['businessId'] === 'string') return cfg['businessId'];
    } catch {}
    // 2. Check URL query param ?businessId=...
    try {
      const params = new URLSearchParams(window.location.search);
      const bid = params.get('businessId');
      if (bid) return bid;
    } catch {}
    return null;
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
