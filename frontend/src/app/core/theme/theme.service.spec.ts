import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

interface MediaQueryListMock {
  mql: MediaQueryList;
  set: (matches: boolean) => void;
  listeners: Array<(event: MediaQueryListEvent) => void>;
}

function mockMatchMedia(matches: boolean): MediaQueryListMock {
  const listeners: Array<(event: MediaQueryListEvent) => void> = [];
  let current = matches;
  const mql = {
    get matches() {
      return current;
    },
    media: '(prefers-color-scheme: dark)',
    onchange: null,
    addEventListener: (
      _type: string,
      listener: (event: MediaQueryListEvent) => void
    ) => {
      listeners.push(listener);
    },
    removeEventListener: (
      _type: string,
      listener: (event: MediaQueryListEvent) => void
    ) => {
      const index = listeners.indexOf(listener);
      if (index >= 0) {
        listeners.splice(index, 1);
      }
    },
    addListener: () => undefined,
    removeListener: () => undefined,
    dispatchEvent: () => true
  } as unknown as MediaQueryList;

  return {
    mql,
    listeners,
    set: (next: boolean) => {
      current = next;
      for (const listener of [...listeners]) {
        listener({ matches: next } as MediaQueryListEvent);
      }
    }
  };
}

describe('ThemeService', () => {
  let service: ThemeService;
  let system: MediaQueryListMock;

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    system = mockMatchMedia(false);
    spyOn(window, 'matchMedia').and.returnValue(system.mql);
    TestBed.configureTestingModule({});
    service = TestBed.inject(ThemeService);
  });

  it('starts with light theme by default', () => {
    expect(service.theme()).toBe('light');
  });

  it('applies the stored theme when present', () => {
    localStorage.setItem('callsagents-theme', 'dark');
    service.initialize();
    expect(service.theme()).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('falls back to the system preference when nothing is stored', () => {
    system.set(true);
    service.initialize();
    expect(service.theme()).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('defaults to light when no stored theme and no dark preference', () => {
    service.initialize();
    expect(service.theme()).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('follows system preference changes while no explicit choice is saved', () => {
    service.initialize();
    expect(service.theme()).toBe('light');

    system.set(true);
    expect(service.theme()).toBe('dark');

    system.set(false);
    expect(service.theme()).toBe('light');
  });

  it('does not follow the system when an explicit theme is stored', () => {
    localStorage.setItem('callsagents-theme', 'light');
    system.set(true);
    service.initialize();
    expect(service.theme()).toBe('light');
    expect(system.listeners.length).toBe(0);
  });

  it('toggle switches, persists, and stops following the system', () => {
    service.initialize();
    service.toggle();
    expect(service.theme()).toBe('dark');
    expect(localStorage.getItem('callsagents-theme')).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(system.listeners.length).toBe(0);

    system.set(true);
    expect(service.theme()).toBe('dark');

    service.toggle();
    expect(service.theme()).toBe('light');
    expect(localStorage.getItem('callsagents-theme')).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('initialize() is idempotent and registers a single listener', () => {
    service.initialize();
    service.initialize();
    expect(system.listeners.length).toBe(1);
  });
});
