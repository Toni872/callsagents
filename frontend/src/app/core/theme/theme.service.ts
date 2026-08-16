import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'callsagents-theme';
const SYSTEM_MEDIA_QUERY = '(prefers-color-scheme: dark)';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>('light');

  private initialized = false;
  private media: MediaQueryList | null = null;
  private mediaHandler: ((event: MediaQueryListEvent) => void) | null = null;

  initialize(): void {
    if (typeof document === 'undefined' || this.initialized) {
      return;
    }
    this.initialized = true;

    const stored = this.readStoredTheme();
    if (stored) {
      this.apply(stored);
      return;
    }

    this.apply(this.systemTheme());
    this.watchSystem();
  }

  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.apply(next);
    this.persist(next);
    this.stopWatchingSystem();
  }

  private readStoredTheme(): Theme | null {
    try {
      const value = localStorage.getItem(STORAGE_KEY);
      return value === 'light' || value === 'dark' ? value : null;
    } catch {
      return null;
    }
  }

  private systemTheme(): Theme {
    if (typeof window === 'undefined' || typeof matchMedia === 'undefined') {
      return 'light';
    }
    this.media = matchMedia(SYSTEM_MEDIA_QUERY);
    return this.media.matches ? 'dark' : 'light';
  }

  private watchSystem(): void {
    if (!this.media) {
      return;
    }
    this.mediaHandler = (event) => {
      this.apply(event.matches ? 'dark' : 'light');
    };
    this.media.addEventListener('change', this.mediaHandler);
  }

  private stopWatchingSystem(): void {
    if (this.media && this.mediaHandler) {
      this.media.removeEventListener('change', this.mediaHandler);
      this.mediaHandler = null;
    }
  }

  private apply(theme: Theme): void {
    this.theme.set(theme);
    document.documentElement.setAttribute('data-theme', theme);
  }

  private persist(theme: Theme): void {
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Ignore storage failures (private mode, quota, etc.).
    }
  }
}
