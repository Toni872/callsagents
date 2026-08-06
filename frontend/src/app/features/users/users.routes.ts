import { Routes } from '@angular/router';
import { roleGuard } from '../../core/auth/role.guard';

export const USERS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [roleGuard(['ADMIN'])],
    loadComponent: () =>
      import('./list/user-list.component').then((m) => m.UserListComponent)
  }
];
