import { Routes } from '@angular/router';

export const CALLS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./list/call-list.component').then((m) => m.CallListComponent)
  },
  {
    path: 'new',
    loadComponent: () => import('./form/call-form.component').then((m) => m.CallFormComponent)
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./form/call-form.component').then((m) => m.CallFormComponent)
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./detail/call-detail.component').then((m) => m.CallDetailComponent)
  }
];
