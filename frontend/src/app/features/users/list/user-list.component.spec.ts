import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { UserListComponent } from './user-list.component';
import { apiUrl } from '../../../core/api/api-base';
import { PageResponse } from '../../../core/api/http.types';
import { UserListItem } from '../../../shared/models/user.model';

describe('UserListComponent', () => {
  let fixture: ComponentFixture<UserListComponent>;
  let http: HttpTestingController;

  const user = (id: string, status: 'ACTIVE' | 'DISABLED'): UserListItem => ({
    id,
    email: `${id}@example.com`,
    fullName: `Usuario ${id}`,
    role: 'AGENT',
    status,
    lastLoginAt: null,
    createdAt: '2026-01-01T00:00:00Z'
  });

  const page = (
    content: UserListItem[],
    totalPages = 1
  ): PageResponse<UserListItem> => ({
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages,
    first: true,
    last: totalPages <= 1
  });

  function flushUsers(users: UserListItem[], totalPages = 1): void {
    const req = http.expectOne(
      (r) => r.method === 'GET' && r.url === apiUrl('/users')
    );
    req.flush(page(users, totalPages));
    fixture.detectChanges();
  }

  function buttonByText(text: string): HTMLButtonElement {
    const buttons = fixture.debugElement.queryAll(By.css('button'));
    const match = buttons.find((b) =>
      b.nativeElement.textContent?.trim().includes(text)
    );
    expect(match).withContext(`button "${text}" not found`).toBeTruthy();
    return match!.nativeElement as HTMLButtonElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    fixture = TestBed.createComponent(UserListComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  it('loads users on init and renders their rows', () => {
    flushUsers([user('1', 'ACTIVE'), user('2', 'DISABLED')]);

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('1@example.com');
    expect(text).toContain('Usuario 1');
    expect(text).toContain('2@example.com');
    expect(text).toContain('Usuario 2');
  });

  it('pagination buttons call list() with the next page', () => {
    flushUsers([user('1', 'ACTIVE')], 3);

    buttonByText('Siguiente').click();

    const req = http.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === apiUrl('/users') &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '20'
    );
    req.flush(page([user('2', 'ACTIVE')], 3));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('2@example.com');
  });

  it('Deshabilitar calls updateStatus with DISABLED', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    flushUsers([user('1', 'ACTIVE')]);

    buttonByText('Deshabilitar').click();

    const req = http.expectOne(
      (r) => r.method === 'PATCH' && r.url === apiUrl('/users/1/status')
    );
    expect(req.request.body).toEqual({ status: 'DISABLED' });
    req.flush(user('1', 'DISABLED'));

    const refetch = http.expectOne(
      (r) => r.method === 'GET' && r.url === apiUrl('/users')
    );
    refetch.flush(page([user('1', 'DISABLED')]));
    fixture.detectChanges();
  });

  it('Habilitar calls updateStatus with ACTIVE', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    flushUsers([user('2', 'DISABLED')]);

    buttonByText('Habilitar').click();

    const req = http.expectOne(
      (r) => r.method === 'PATCH' && r.url === apiUrl('/users/2/status')
    );
    expect(req.request.body).toEqual({ status: 'ACTIVE' });
    req.flush(user('2', 'ACTIVE'));

    const refetch = http.expectOne(
      (r) => r.method === 'GET' && r.url === apiUrl('/users')
    );
    refetch.flush(page([user('2', 'ACTIVE')]));
    fixture.detectChanges();
  });
});
