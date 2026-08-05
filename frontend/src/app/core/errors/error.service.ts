import { Injectable, signal } from '@angular/core';
import { ToastMessage, ToastType } from './error.types';

@Injectable({ providedIn: 'root' })
export class ErrorService {
  private nextId = 1;
  readonly toasts = signal<ToastMessage[]>([]);

  show(text: string, type: ToastType = 'error', ttlMs = 5000): void {
    const msg: ToastMessage = { id: this.nextId++, text, type };
    this.toasts.update((list) => [...list, msg]);
    if (ttlMs > 0) {
      setTimeout(() => this.dismiss(msg.id), ttlMs);
    }
  }

  success(text: string, ttlMs = 3000): void {
    this.show(text, 'success', ttlMs);
  }

  info(text: string, ttlMs = 3000): void {
    this.show(text, 'info', ttlMs);
  }

  warning(text: string, ttlMs = 4000): void {
    this.show(text, 'warning', ttlMs);
  }

  error(text: string, ttlMs = 5000): void {
    this.show(text, 'error', ttlMs);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((m) => m.id !== id));
  }

  clear(): void {
    this.toasts.set([]);
  }
}
