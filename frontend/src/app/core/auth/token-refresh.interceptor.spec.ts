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
import { TokenStorageService } from './token-storage.service';
import { AuthService } from './auth.service';
import { tokenRefreshInterceptor } from './token-refresh.interceptor';
import { apiUrl } from '../api/api-base';
import { Router } from '@angular/router';
import { ErrorService } from '../errors/error.service';

describe('tokenRefreshInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let storage: TokenStorageService;
  let authService: AuthService;
  let logoutSpy: jasmine.Spy;
  let navigateSpy: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([tokenRefreshInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigateByUrl: jasmine.createSpy('navigateByUrl') } }
      ]
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(TokenStorageService);
    authService = TestBed.inject(AuthService);
    logoutSpy = spyOn(authService, 'logout');
    navigateSpy = TestBed.inject(Router).navigateByUrl as jasmine.Spy;
    localStorage.clear();
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('401 en un endpoint protegido dispara refresh y reintenta con el nuevo Bearer', () => {
    let result: unknown;

    http.get(apiUrl('/users')).subscribe({
      next: (res) => (result = res)
    });

    const first = httpTesting.expectOne(apiUrl('/users'));
    first.flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpTesting.expectOne(
      (r) => r.method === 'POST' && r.url === apiUrl('/auth/refresh')
    );
    expect(refreshReq.request.withCredentials).toBeTrue();
    expect(refreshReq.request.body).toBeNull();
    refreshReq.flush({ accessToken: 'access-2', accessTokenExpiresInSeconds: 900 });

    const retried = httpTesting.expectOne(
      (r) => r.url === apiUrl('/users') && r.headers.get('Authorization') === 'Bearer access-2'
    );
    retried.flush({ ok: true });

    expect(result).toEqual({ ok: true });
    expect(storage.getAccess()).toBe('access-2');
    expect(logoutSpy).not.toHaveBeenCalled();
  });

  it('error != 401 se propaga sin tocar refresh', () => {
    let caught: HttpErrorResponse | undefined;

    http.get(apiUrl('/users')).subscribe({ error: (e: HttpErrorResponse) => (caught = e) });

    const req = httpTesting.expectOne(apiUrl('/users'));
    req.flush({ message: 'boom' }, { status: 500, statusText: 'Internal Server Error' });

    expect(caught?.status).toBe(500);
    httpTesting.expectNone(apiUrl('/auth/refresh'));
    expect(logoutSpy).not.toHaveBeenCalled();
  });

  it('401 en un endpoint /auth/ se propaga sin refrescar (credenciales inválidas, no expiración)', () => {
    let caught: HttpErrorResponse | undefined;

    http.get(apiUrl('/auth/me')).subscribe({ error: (e: HttpErrorResponse) => (caught = e) });

    const req = httpTesting.expectOne(apiUrl('/auth/me'));
    req.flush({ message: 'bad token' }, { status: 401, statusText: 'Unauthorized' });

    expect(caught?.status).toBe(401);
    httpTesting.expectNone(apiUrl('/auth/refresh'));
    expect(logoutSpy).not.toHaveBeenCalled();
  });

  it('cuando refresh falla llama logout(false) y propaga el error', () => {
    let caught: HttpErrorResponse | undefined;

    http.get(apiUrl('/users')).subscribe({ error: (e: HttpErrorResponse) => (caught = e) });

    const first = httpTesting.expectOne(apiUrl('/users'));
    first.flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpTesting.expectOne(apiUrl('/auth/refresh'));
    refreshReq.flush({ message: 'refresh revocado' }, { status: 401, statusText: 'Unauthorized' });

    expect(logoutSpy).toHaveBeenCalledWith(false);
    expect(caught?.status).toBe(401);
  });

  it('401s concurrentes disparan UN solo refresh (flag module-scoped)', () => {
    http.get(apiUrl('/a')).subscribe({ error: () => undefined });
    http.get(apiUrl('/b')).subscribe({ error: () => undefined });

    const a = httpTesting.expectOne(apiUrl('/a'));
    a.flush({}, { status: 401, statusText: 'Unauthorized' });

    // El segundo 401 ve isRefreshing=true y propaga el error original sin refrescar.
    const b = httpTesting.expectOne(apiUrl('/b'));
    b.flush({}, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpTesting.expectOne(apiUrl('/auth/refresh'));
    refreshReq.flush({ accessToken: 'access-2', accessTokenExpiresInSeconds: 900 });

    // Solo la request /a se reintenta con el nuevo token.
    httpTesting.expectOne(
      (r) => r.url === apiUrl('/a') && r.headers.get('Authorization') === 'Bearer access-2'
    );
    httpTesting.expectNone(apiUrl('/b'));
  });
});