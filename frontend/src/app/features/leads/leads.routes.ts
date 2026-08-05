import { Routes } from '@angular/router';

export const LEADS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./list/lead-list.component').then((m) => m.LeadListComponent)
  },
  {
    path: 'new',
    loadComponent: () => import('./form/lead-form.component').then((m) => m.LeadFormComponent)
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./detail/lead-detail.component').then((m) => m.LeadDetailComponent)
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./form/lead-form.component').then((m) => m.LeadFormComponent)
  }
];
