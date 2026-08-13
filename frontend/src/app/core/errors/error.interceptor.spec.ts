import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { ErrorService } from './error.service';
import { errorInterceptor } from './error.interceptor';
import { apiUrl } from '../api/api-base';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let errors: ErrorService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    errors = TestBed.inject(ErrorService);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('shows a connection toast when status is 0', () => {
    http.get(apiUrl('/users')).subscribe({ error: () => undefined });

    const req = httpTesting.expectOne(apiUrl('/users'));
    req.error(new ProgressEvent('network'), { status: 0, statusText: 'Unknown Error' });

    expect(errors.toasts().length).toBe(1);
    expect(errors.toasts()[0].text).toBe('No se pudo conectar con el servidor');
  });

  it('shows the message from an ApiErrorBody', () => {
    http.get(apiUrl('/users')).subscribe({ error: () => undefined });

    const req = httpTesting.expectOne(apiUrl('/users'));
    req.flush(
      {
        timestamp: '2026-01-01T00:00:00Z',
        status: 404,
        error: 'Not Found',
        message: 'Usuario no encontrado',
        path: '/api/users/1'
      },
      { status: 404, statusText: 'Not Found' }
    );

    expect(errors.toasts()[0].text).toBe('Usuario no encontrado');
  });

  it('falls back to the body error field when message is absent', () => {
    http.get(apiUrl('/users')).subscribe({ error: () => undefined });

    const req = httpTesting.expectOne(apiUrl('/users'));
    req.flush(
      { status: 500, error: 'Internal Server Error' },
      { status: 500, statusText: 'Internal Server Error' }
    );

    expect(errors.toasts()[0].text).toBe('Internal Server Error');
  });

  it('shows the body text when the error body is a string', () => {
    http.get(apiUrl('/users')).subscribe({ error: () => undefined });

    const req = httpTesting.expectOne(apiUrl('/users'));
    req.flush('No autorizado', { status: 401, statusText: 'Unauthorized' });

    expect(errors.toasts()[0].text).toBe('No autorizado');
  });

  it('rethrows the original HttpErrorResponse', () => {
    let caught: unknown;

    http.get(apiUrl('/users')).subscribe({ error: (e: unknown) => (caught = e) });

    const req = httpTesting.expectOne(apiUrl('/users'));
    req.flush(
      { message: 'Error de negocio' },
      { status: 400, statusText: 'Bad Request' }
    );

    expect(caught).toBeInstanceOf(HttpErrorResponse);
    expect((caught as HttpErrorResponse).status).toBe(400);
    expect(errors.toasts()[0].text).toBe('Error de negocio');
  });
});
