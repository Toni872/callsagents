import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-phone-mockup',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="phone">
      <div class="phone__frame">
        <!-- Notch -->
        <div class="phone__notch"></div>

        <!-- Screen -->
        <div class="phone__screen">
          <!-- Header -->
          <div class="screen__header">
            <div class="screen__logo">
              <div class="screen__logo-icon">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
                </svg>
              </div>
              <span class="screen__logo-text">CALLS<span class="screen__logo-green">AGENTS</span></span>
            </div>
            <div class="screen__notification">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
              <span class="screen__notification-dot"></span>
            </div>
          </div>

          <!-- Active Call Card -->
          <div class="screen__call">
            <div class="screen__call-wave">
              <span></span><span></span><span></span><span></span><span></span>
            </div>
            <div class="screen__call-btn">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
              </svg>
            </div>
            <div class="screen__call-time">00:24</div>
          </div>

          <!-- Chat Bubbles -->
          <div class="screen__chat">
            <div class="screen__bubble screen__bubble--incoming">
              <div class="screen__bubble-icon">
                <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                  <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                </svg>
              </div>
              <span>¿Qué servicio te interesa?</span>
            </div>
            <div class="screen__bubble screen__bubble--outgoing">
              <span>Marketing Digital</span>
              <svg class="screen__bubble-check" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </div>
            <div class="screen__bubble screen__bubble--incoming">
              <div class="screen__bubble-icon">
                <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                  <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                </svg>
              </div>
              <span>Perfecto. ¿Cuándo quieres empezar?</span>
            </div>
          </div>

          <!-- Action Buttons -->
          <div class="screen__actions">
            <div class="screen__action">
              <div class="screen__action-btn">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M3 18v-6a9 9 0 0 1 18 0v6"/>
                  <path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z"/>
                </svg>
              </div>
              <span>LLAMA</span>
            </div>
            <div class="screen__action">
              <div class="screen__action-btn">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
              </div>
              <span>CUALIFICA</span>
            </div>
            <div class="screen__action">
              <div class="screen__action-btn">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                  <line x1="16" y1="2" x2="16" y2="6"/>
                  <line x1="8" y1="2" x2="8" y2="6"/>
                  <line x1="3" y1="10" x2="21" y2="10"/>
                </svg>
              </div>
              <span>AGENDA</span>
            </div>
            <div class="screen__action">
              <div class="screen__action-btn">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="20" x2="18" y2="10"/>
                  <line x1="12" y1="20" x2="12" y2="4"/>
                  <line x1="6" y1="20" x2="6" y2="14"/>
                </svg>
              </div>
              <span>REPORTA</span>
            </div>
          </div>

          <!-- Dashboard Preview -->
          <div class="screen__dashboard">
            <div class="screen__dashboard-header">
              <span>Panel de control</span>
              <span class="screen__live">Tiempo real</span>
            </div>
            <div class="screen__metrics">
              <div class="screen__metric">
                <span class="screen__metric-value">12,450</span>
                <span class="screen__metric-label">Llamadas</span>
                <span class="screen__metric-change">+23%</span>
              </div>
              <div class="screen__metric">
                <span class="screen__metric-value">5,462</span>
                <span class="screen__metric-label">Conexiones</span>
                <span class="screen__metric-change">+40%</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Home Indicator -->
        <div class="phone__home"></div>
      </div>
    </div>
  `,
  styles: [`
    .phone {
      width: 100%;
      max-width: 280px;
      margin: 0 auto;
      perspective: 1000px;
      animation: float 6s ease-in-out infinite;
    }

    .phone__frame {
      position: relative;
      background: #1a1a2e;
      border-radius: 36px;
      padding: 12px;
      box-shadow:
        0 0 0 2px rgba(0, 168, 107, 0.3),
        0 20px 60px rgba(0, 0, 0, 0.5),
        0 0 40px rgba(0, 168, 107, 0.1);
      transform: rotateY(-5deg) rotateX(2deg);
      transition: transform 0.5s ease;
    }

    .phone__frame:hover {
      transform: rotateY(0deg) rotateX(0deg);
    }

    .phone__notch {
      position: absolute;
      top: 12px;
      left: 50%;
      transform: translateX(-50%);
      width: 100px;
      height: 24px;
      background: #0a0a1a;
      border-radius: 0 0 16px 16px;
      z-index: 10;
    }

    .phone__screen {
      background: linear-gradient(180deg, #0a0a1a 0%, #0f172a 100%);
      border-radius: 28px;
      padding: 40px 12px 12px;
      min-height: 500px;
      overflow: hidden;
    }

    .phone__home {
      width: 40px;
      height: 4px;
      background: rgba(255, 255, 255, 0.3);
      border-radius: 2px;
      margin: 8px auto 0;
    }

    /* Screen Header */
    .screen__header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }

    .screen__logo {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .screen__logo-icon {
      width: 24px;
      height: 24px;
      background: linear-gradient(135deg, #00A86B 0%, #007A4D 100%);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
    }

    .screen__logo-text {
      font-family: 'Space Grotesk', sans-serif;
      font-size: 11px;
      font-weight: 700;
      color: white;
      letter-spacing: -0.5px;
    }

    .screen__logo-green {
      color: #00A86B;
    }

    .screen__notification {
      position: relative;
      color: rgba(255, 255, 255, 0.6);
    }

    .screen__notification-dot {
      position: absolute;
      top: -2px;
      right: -2px;
      width: 6px;
      height: 6px;
      background: #00A86B;
      border-radius: 50%;
    }

    /* Active Call */
    .screen__call {
      background: rgba(0, 168, 107, 0.1);
      border: 1px solid rgba(0, 168, 107, 0.3);
      border-radius: 12px;
      padding: 12px;
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 12px;
    }

    .screen__call-wave {
      display: flex;
      align-items: center;
      gap: 2px;
      height: 20px;
    }

    .screen__call-wave span {
      width: 2px;
      height: 100%;
      background: #00A86B;
      border-radius: 1px;
      animation: wave 1.2s ease-in-out infinite;
    }

    .screen__call-wave span:nth-child(1) { animation-delay: 0s; }
    .screen__call-wave span:nth-child(2) { animation-delay: 0.1s; }
    .screen__call-wave span:nth-child(3) { animation-delay: 0.2s; }
    .screen__call-wave span:nth-child(4) { animation-delay: 0.3s; }
    .screen__call-wave span:nth-child(5) { animation-delay: 0.4s; }

    .screen__call-btn {
      width: 28px;
      height: 28px;
      background: #00A86B;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      flex-shrink: 0;
    }

    .screen__call-time {
      font-family: 'Space Grotesk', monospace;
      font-size: 12px;
      color: rgba(255, 255, 255, 0.8);
      margin-left: auto;
    }

    /* Chat Bubbles */
    .screen__chat {
      display: flex;
      flex-direction: column;
      gap: 6px;
      margin-bottom: 12px;
    }

    .screen__bubble {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 10px;
      border-radius: 12px;
      font-size: 9px;
      max-width: 85%;
    }

    .screen__bubble--incoming {
      background: rgba(255, 255, 255, 0.08);
      color: rgba(255, 255, 255, 0.8);
      align-self: flex-start;
      border-bottom-left-radius: 4px;
    }

    .screen__bubble--outgoing {
      background: #00A86B;
      color: white;
      align-self: flex-end;
      border-bottom-right-radius: 4px;
    }

    .screen__bubble-icon {
      width: 16px;
      height: 16px;
      background: rgba(255, 255, 255, 0.1);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .screen__bubble-check {
      opacity: 0.7;
    }

    /* Action Buttons */
    .screen__actions {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 6px;
      margin-bottom: 12px;
    }

    .screen__action {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
    }

    .screen__action-btn {
      width: 36px;
      height: 36px;
      border: 1px solid rgba(0, 168, 107, 0.5);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #00A86B;
      transition: all 0.3s ease;
      box-shadow: 0 0 10px rgba(0, 168, 107, 0.2);
    }

    .screen__action-btn:hover {
      background: rgba(0, 168, 107, 0.1);
      box-shadow: 0 0 20px rgba(0, 168, 107, 0.4);
    }

    .screen__action span {
      font-size: 6px;
      font-weight: 600;
      color: rgba(255, 255, 255, 0.6);
      letter-spacing: 0.5px;
    }

    /* Dashboard */
    .screen__dashboard {
      background: rgba(255, 255, 255, 0.03);
      border-radius: 10px;
      padding: 10px;
    }

    .screen__dashboard-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      font-size: 8px;
      color: rgba(255, 255, 255, 0.6);
    }

    .screen__live {
      display: flex;
      align-items: center;
      gap: 4px;
      color: #00A86B;
      font-weight: 500;
    }

    .screen__live::before {
      content: '';
      width: 4px;
      height: 4px;
      background: #00A86B;
      border-radius: 50%;
      animation: pulse 2s ease-in-out infinite;
    }

    .screen__metrics {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 8px;
    }

    .screen__metric {
      display: flex;
      flex-direction: column;
    }

    .screen__metric-value {
      font-family: 'Space Grotesk', sans-serif;
      font-size: 12px;
      font-weight: 700;
      color: white;
    }

    .screen__metric-label {
      font-size: 7px;
      color: rgba(255, 255, 255, 0.5);
    }

    .screen__metric-change {
      font-size: 8px;
      color: #00A86B;
      font-weight: 600;
    }

    /* Animations */
    @keyframes float {
      0%, 100% { transform: translateY(0px); }
      50% { transform: translateY(-10px); }
    }

    @keyframes wave {
      0%, 100% { transform: scaleY(0.5); }
      50% { transform: scaleY(1); }
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.5; }
    }
  `]
})
export class PhoneMockupComponent {}
