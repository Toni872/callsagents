import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { UserApi } from './user.api';
import { apiUrl } from './api-base';
import { PageResponse } from './http.types';
import { UserListItem, UserRole } from '../../shared/models/user.model';

describe('UserApi', () => {
  let api: UserApi;
  let http: HttpTestingController;

  const user = (id: string, status: 'ACTIVE' | 'DISABLED' = 'ACTIVE'): UserListItem => ({
    id,
    email: `${id}@example.com`,
    fullName: 'Ana García',
    role: 'AGENT',
    status,
    lastLoginAt: null,
    createdAt: '2026-01-01T00:00:00Z'
  });

  const page = (content: UserListItem[]): PageResponse<UserListItem> => ({
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    api = TestBed.inject(UserApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('list() sends GET /users with page/size/role params', () => {
    const expected = page([user('1')]);
    let received: PageResponse<UserListItem> | undefined;

    api.list({ page: 2, size: 10, role: 'SUPERVISOR' }).subscribe((res) => (received = res));

    const req = http.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === apiUrl('/users') &&
        r.params.get('page') === '2' &&
        r.params.get('size') === '10' &&
        r.params.get('role') === 'SUPERVISOR'
    );
    req.flush(expected);

    expect(received).toEqual(expected);
  });

  it('list() omits the role param when not provided', () => {
    api.list({ page: 0, size: 20 }).subscribe();

    const req = http.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === apiUrl('/users') &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '20' &&
        !r.params.has('role')
    );
    expect(req.request.params.has('role')).toBeFalse();
    req.flush(page([]));
  });

  it('create() sends POST /users with the request body', () => {
    const body = {
      email: 'new@example.com',
      fullName: 'Nuevo Usuario',
      password: 'secret123',
      role: 'ADMIN' as UserRole
    };
    let received: UserListItem | undefined;

    api.create(body).subscribe((res) => (received = res));

    const req = http.expectOne((r) => r.method === 'POST' && r.url === apiUrl('/users'));
    expect(req.request.body).toEqual(body);
    req.flush(user('9', 'ACTIVE'));

    expect(received?.email).toBe('9@example.com');
  });

  it('updateStatus() sends PATCH /users/{id}/status with { status }', () => {
    let received: UserListItem | undefined;

    api.updateStatus('42', 'DISABLED').subscribe((res) => (received = res));

    const req = http.expectOne(
      (r) => r.method === 'PATCH' && r.url === apiUrl('/users/42/status')
    );
    expect(req.request.body).toEqual({ status: 'DISABLED' });
    req.flush(user('42', 'DISABLED'));

    expect(received?.status).toBe('DISABLED');
  });
});
