import { TestBed } from '@angular/core/testing';
import { TokenStorageService } from './token-storage.service';

describe('TokenStorageService', () => {
  let service: TokenStorageService;

  const ACCESS_KEY = 'callsagents.access';
  const LEGACY_REFRESH_KEY = 'callsagents.refresh';

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TokenStorageService);
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('setAccess()/getAccess() guardan y recuperan el access token', () => {
    service.setAccess('access-1');
    expect(localStorage.getItem(ACCESS_KEY)).toBe('access-1');
    expect(service.getAccess()).toBe('access-1');
  });

  it('getAccess() devuelve null cuando no hay token', () => {
    expect(service.getAccess()).toBeNull();
  });

  it('clear() borra el access token', () => {
    service.setAccess('access-1');
    service.clear();
    expect(service.getAccess()).toBeNull();
    expect(localStorage.getItem(ACCESS_KEY)).toBeNull();
  });

  it('clear() elimina también la legacy refresh key migrada desde localStorage', () => {
    // Sesión vieja donde el refresh vivía en localStorage
    localStorage.setItem(LEGACY_REFRESH_KEY, 'legacy-refresh-1');
    localStorage.setItem(ACCESS_KEY, 'access-1');

    service.clear();

    expect(localStorage.getItem(LEGACY_REFRESH_KEY)).toBeNull();
    expect(localStorage.getItem(ACCESS_KEY)).toBeNull();
  });

  it('nunca persiste el refresh token en localStorage', () => {
    service.setAccess('access-1');
    const keys = Object.keys(localStorage);
    expect(keys).not.toContain(LEGACY_REFRESH_KEY);
    expect(keys.some((k) => k.toLowerCase().includes('refresh'))).toBeFalse();
  });
});