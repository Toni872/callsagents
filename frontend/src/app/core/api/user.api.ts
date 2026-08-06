import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiUrl } from './api-base';
import { PageResponse } from './http.types';
import {
  CreateUserRequest,
  UserListFilter,
  UserListItem
} from '../../shared/models/user.model';

@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);

  list(filter: UserListFilter = {}): Observable<PageResponse<UserListItem>> {
    let params = new HttpParams()
      .set('page', String(filter.page ?? 0))
      .set('size', String(filter.size ?? 20));

    if (filter.role) {
      params = params.set('role', filter.role);
    }

    return this.http.get<PageResponse<UserListItem>>(apiUrl('/users'), { params });
  }

  create(req: CreateUserRequest): Observable<UserListItem> {
    return this.http.post<UserListItem>(apiUrl('/users'), req);
  }
}
