import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ErrorService } from './error.service';

describe('ErrorService', () => {
  let service: ErrorService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ErrorService);
  });

  it('shows a toast via show()', () => {
    service.show('Algo salió mal', 'error');
    const toasts = service.toasts();
    expect(toasts.length).toBe(1);
    expect(toasts[0].text).toBe('Algo salió mal');
    expect(toasts[0].type).toBe('error');
  });

  it('success() creates a success toast', () => {
    service.success('Listo');
    const toast = service.toasts()[0];
    expect(toast.text).toBe('Listo');
    expect(toast.type).toBe('success');
  });

  it('error() creates an error toast', () => {
    service.error('Falló');
    const toast = service.toasts()[0];
    expect(toast.text).toBe('Falló');
    expect(toast.type).toBe('error');
  });

  it('assigns increasing ids across toasts', () => {
    service.error('uno');
    service.success('dos');
    const toasts = service.toasts();
    expect(toasts.length).toBe(2);
    expect(toasts[0].id).toBeLessThan(toasts[1].id);
  });

  it('dismiss() removes a toast by id', () => {
    service.error('uno');
    const id = service.toasts()[0].id;
    service.dismiss(id);
    expect(service.toasts()).toEqual([]);
  });

  it('clear() removes all toasts', () => {
    service.error('uno');
    service.warning('dos');
    service.info('tres');
    service.clear();
    expect(service.toasts()).toEqual([]);
  });

  it('auto-dismisses a toast after its ttl', fakeAsync(() => {
    service.show('efímero', 'info', 1000);
    expect(service.toasts().length).toBe(1);
    tick(999);
    expect(service.toasts().length).toBe(1);
    tick(1);
    expect(service.toasts().length).toBe(0);
  }));

  it('does not auto-dismiss when ttl is 0', fakeAsync(() => {
    service.show('permanente', 'error', 0);
    tick(10000);
    expect(service.toasts().length).toBe(1);
  }));
});
