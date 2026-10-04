import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthApi } from '../api/auth.api';
import { TokenStorageService } from './token-storage.service';
import { ErrorService } from '../errors/error.service';
import { LoginRequest, RegisterRequest, UserDto } from '../../shared/models/auth.model';

describe('AuthService', () => {
  let service: AuthService;
  let api: AuthApi;
  let storage: TokenStorageService;
  let errorService: ErrorService;
  let navigateSpy: jasmine.Spy;
  let successSpy: jasmine.Spy;
  let infoSpy: jasmine.Spy;

  const user: UserDto = {
    id: 'user-1',
    email: 'admin@callsagents.local',
    fullName: 'Admin',
    role: 'ADMIN',
    trialEndsAt: null
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigateByUrl: jasmine.createSpy('navigateByUrl') } }
      ]
    });
    service = TestBed.inject(AuthService);
    api = TestBed.inject(AuthApi);
    storage = TestBed.inject(TokenStorageService);
    errorService = TestBed.inject(ErrorService);
    navigateSpy = TestBed.inject(Router).navigateByUrl as jasmine.Spy;
    successSpy = spyOn(errorService, 'success');
    infoSpy = spyOn(errorService, 'info');
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('login() guarda access, setea currentUser y navega a /dashboard (por defecto)', () => {
    spyOn(api, 'login').and.returnValue(
      of({ accessToken: 'access-1', accessTokenExpiresInSeconds: 900, user })
    );

    const req: LoginRequest = { email: 'admin@callsagents.local', password: 'admin123' };
    service.login(req).subscribe();

    expect(storage.getAccess()).toBe('access-1');
    expect(service.currentUser()).toEqual(user);
    expect(service.isAuthenticated()).toBeTrue();
    expect(service.currentRole()).toBe('ADMIN');
    expect(navigateSpy).toHaveBeenCalledWith('/dashboard');
    expect(successSpy).toHaveBeenCalledWith('Bienvenido, Admin');
  });

  it('login() con redirect navega al redirect (nunca a /login)', () => {
    spyOn(api, 'login').and.returnValue(
      of({ accessToken: 'access-1', accessTokenExpiresInSeconds: 900, user })
    );

    service.login({ email: 'a@b.c', password: 'secret' }, '/campaigns').subscribe();

    expect(navigateSpy).toHaveBeenCalledWith('/campaigns');
    expect(navigateSpy).not.toHaveBeenCalledWith('/login');
  });

  it('register() guarda access, setea currentUser y navega a /dashboard', () => {
    spyOn(api, 'register').and.returnValue(
      of({ accessToken: 'access-1', accessTokenExpiresInSeconds: 900, user })
    );

    const req: RegisterRequest = { email: 'a@b.c', password: 'secret', fullName: 'Ana' };
    service.register(req).subscribe();

    expect(storage.getAccess()).toBe('access-1');
    expect(service.currentUser()).toEqual(user);
    expect(navigateSpy).toHaveBeenCalledWith('/dashboard');
  });

  it('googleLogin() guarda access, setea currentUser y navega al redirect', () => {
    spyOn(api, 'googleLogin').and.returnValue(
      of({ accessToken: 'access-1', accessTokenExpiresInSeconds: 900, user })
    );

    service.googleLogin('google-token', '/campaigns').subscribe();

    expect(storage.getAccess()).toBe('access-1');
    expect(service.currentUser()).toEqual(user);
    expect(navigateSpy).toHaveBeenCalledWith('/campaigns');
  });

  it('logout() con sesión activa revoca en el API, limpia y navega a /landing', () => {
    spyOn(api, 'login').and.returnValue(
      of({ accessToken: 'access-1', accessTokenExpiresInSeconds: 900, user })
    );
    const logoutSpy = spyOn(api, 'logout').and.returnValue(of(undefined));
    service.login({ email: 'a@b.c', password: 'secret' }).subscribe();

    service.logout();

    expect(logoutSpy).toHaveBeenCalledWith();
    expect(storage.getAccess()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBeFalse();
    expect(infoSpy).toHaveBeenCalledWith('Sesión cerrada');
    expect(navigateSpy).toHaveBeenCalledWith('/landing');
  });

  it('logout(false) no muestra toast pero sí limpia y navega', () => {
    storage.setAccess('access-1');
    spyOn(api, 'logout').and.returnValue(of(undefined));

    service.logout(false);

    expect(infoSpy).not.toHaveBeenCalled();
    expect(storage.getAccess()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith('/landing');
  });

  it('logout() sin sesión no llama al API de revocación', () => {
    const logoutSpy = spyOn(api, 'logout').and.returnValue(of(undefined));

    service.logout();

    expect(logoutSpy).not.toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith('/landing');
  });

  it('initFromStorage() sin access token resuelve sin llamar /auth/me', async () => {
    const meSpy = spyOn(api, 'me');

    await service.initFromStorage();

    expect(meSpy).not.toHaveBeenCalled();
    expect(service.currentUser()).toBeNull();
  });

  it('initFromStorage() con access llama /auth/me y restaura la sesión', async () => {
    storage.setAccess('access-1');
    spyOn(api, 'me').and.returnValue(of(user));

    await service.initFromStorage();

    expect(service.currentUser()).toEqual(user);
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('initFromStorage() con access pero /auth/me 401 hace logout silencioso', async () => {
    storage.setAccess('access-1');
    spyOn(api, 'me').and.returnValue(throwError(() => new Error('401')));
    const logoutSpy = spyOn(service, 'logout');

    await service.initFromStorage();

    expect(logoutSpy).toHaveBeenCalledWith(false);
    expect(service.currentUser()).toBeNull();
  });
});