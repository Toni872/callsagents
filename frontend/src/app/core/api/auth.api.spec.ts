import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { AuthApi } from './auth.api';
import { apiUrl } from './api-base';
import { LoginResponse, UserDto } from '../../shared/models/auth.model';

describe('AuthApi', () => {
  let api: AuthApi;
  let http: HttpTestingController;

  const user: UserDto = {
    id: 'user-1',
    email: 'admin@callsagents.local',
    fullName: 'Admin',
    role: 'ADMIN',
    trialEndsAt: null
  };

  const loginResponse: LoginResponse = {
    accessToken: 'access-1',
    accessTokenExpiresInSeconds: 900,
    user
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    api = TestBed.inject(AuthApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('login() hace POST con credentials', () => {
    api.login({ email: 'a@b.c', password: 'secret' }).subscribe((res) => {
      expect(res.accessToken).toBe('access-1');
    });

    const req = http.expectOne((r) => r.method === 'POST' && r.url === apiUrl('/auth/login'));
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.body).toEqual({ email: 'a@b.c', password: 'secret' });
    req.flush(loginResponse);
  });

  it('register() hace POST con credentials', () => {
    api
      .register({ email: 'a@b.c', password: 'secret', fullName: 'Ana' })
      .subscribe((res) => expect(res.user.fullName).toBe('Admin'));

    const req = http.expectOne((r) => r.method === 'POST' && r.url === apiUrl('/auth/register'));
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.body).toEqual({ email: 'a@b.c', password: 'secret', fullName: 'Ana' });
    req.flush(loginResponse);
  });

  it('refresh() hace POST sin body y con credentials (la cookie httpOnly viaja sola)', () => {
    api
      .refresh()
      .subscribe((res) => expect(res.accessToken).toBe('access-2'));

    const req = http.expectOne((r) => r.method === 'POST' && r.url === apiUrl('/auth/refresh'));
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.body).toBeNull();
    req.flush({ accessToken: 'access-2', accessTokenExpiresInSeconds: 900 });
  });

  it('logout() hace POST sin body y con credentials', () => {
    api.logout().subscribe();

    const req = http.expectOne((r) => r.method === 'POST' && r.url === apiUrl('/auth/logout'));
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.body).toBeNull();
    req.flush(null);
  });

  it('googleLogin() hace POST con credential y credentials', () => {
    api.googleLogin('google-token').subscribe((res) => {
      expect(res.user.email).toBe('admin@callsagents.local');
    });

    const req = http.expectOne((r) => r.method === 'POST' && r.url === apiUrl('/auth/google'));
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.body).toEqual({ credential: 'google-token' });
    req.flush(loginResponse);
  });

  it('me() hace GET sin credentials (no necesita cookie)', () => {
    api.me().subscribe((res) => expect(res.id).toBe('user-1'));

    const req = http.expectOne((r) => r.method === 'GET' && r.url === apiUrl('/auth/me'));
    expect(req.request.withCredentials).toBeFalse();
    req.flush(user);
  });
});