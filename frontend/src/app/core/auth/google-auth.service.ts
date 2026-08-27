import { Injectable, NgZone } from '@angular/core';
import { environment } from '../../../environments/environment';

declare const google: any;

const MAX_RETRIES = 20;
const RETRY_INTERVAL_MS = 150;

@Injectable({ providedIn: 'root' })
export class GoogleAuthService {
  private initialized = false;
  private callback: ((credential: string) => void) | null = null;

  constructor(private readonly zone: NgZone) {}

  /** Initialize Google Identity Services (idempotent). */
  init(callback: (credential: string) => void): void {
    this.callback = callback;
    if (this.initialized) return;
    this.loadScript(() => this.tryInitialize(0));
  }

  /** Render the Google Sign-In button into the given element. */
  renderButton(element: HTMLElement, retries = 0): void {
    if (retries >= MAX_RETRIES) return;
    if (!this.initialized || typeof google === 'undefined') {
      setTimeout(() => this.renderButton(element, retries + 1), RETRY_INTERVAL_MS);
      return;
    }
    google.accounts.id.renderButton(element, {
      theme: 'outline',
      size: 'large',
      width: element.clientWidth || 380,
      text: 'continue_with',
      locale: 'es'
    });
  }

  private tryInitialize(retries: number): void {
    if (retries >= MAX_RETRIES) return;
    if (typeof google === 'undefined' || !google.accounts?.id) {
      setTimeout(() => this.tryInitialize(retries + 1), RETRY_INTERVAL_MS);
      return;
    }
    google.accounts.id.initialize({
      client_id: environment.googleClientId,
      callback: (response: any) => {
        this.zone.run(() => this.callback?.(response.credential));
      },
      auto_select: false,
      cancel_on_tap_outside: true
    });
    this.initialized = true;
  }

  private loadScript(onLoad: () => void): void {
    const existing = document.querySelector('script[src="https://accounts.google.com/gsi/client"]');
    if (existing) {
      if (typeof google !== 'undefined' && google.accounts?.id) {
        onLoad();
      } else {
        existing.addEventListener('load', onLoad, { once: true });
      }
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.onload = () => onLoad();
    script.onerror = () => console.warn('[GoogleAuthService] Failed to load GSI script');
    document.head.appendChild(script);
  }
}
