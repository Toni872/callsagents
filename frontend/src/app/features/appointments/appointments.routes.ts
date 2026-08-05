import { Routes } from '@angular/router';

export const APPOINTMENTS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./list/appointment-list.component').then((m) => m.AppointmentListComponent)
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./form/appointment-form.component').then((m) => m.AppointmentFormComponent)
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./detail/appointment-detail.component').then((m) => m.AppointmentDetailComponent)
  },
  {
    path: ':id/edit',
    loadComponent: () =>
      import('./form/appointment-form.component').then((m) => m.AppointmentFormComponent)
  }
];
