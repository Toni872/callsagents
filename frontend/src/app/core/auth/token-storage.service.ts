import { Injectable } from '@angular/core';

/**
 * Storage del access token (corto, 15min).
 *
 * El refresh token NO se guarda aquí: vive solo en la cookie httpOnly
 * `callsagents_refresh` (Path=/api/auth), fuera del alcance de JavaScript.
 * Esto cierra la superficie XSS que exponía la sesión de 7 días en
 * localStorage.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  private readonly ACCESS_KEY = 'callsagents.access';
  /** Legacy key que guardaba el refresh en localStorage; se limpia por migración. */
  private readonly LEGACY_REFRESH_KEY = 'callsagents.refresh';

  setAccess(access: string): void {
    if (typeof localStorage === 'undefined') {
      return;
    }
    localStorage.setItem(this.ACCESS_KEY, access);
  }

  getAccess(): string | null {
    if (typeof localStorage === 'undefined') {
      return null;
    }
    return localStorage.getItem(this.ACCESS_KEY);
  }

  clear(): void {
    if (typeof localStorage === 'undefined') {
      return;
    }
    localStorage.removeItem(this.ACCESS_KEY);
    // Elimina cualquier refresh persistido por versiones anteriores del cliente.
    localStorage.removeItem(this.LEGACY_REFRESH_KEY);
  }
}