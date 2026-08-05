import { Routes } from '@angular/router';

export const CAMPAIGNS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./list/campaign-list.component').then((m) => m.CampaignListComponent)
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./form/campaign-form.component').then((m) => m.CampaignFormComponent)
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./detail/campaign-detail.component').then((m) => m.CampaignDetailComponent)
  },
  {
    path: ':id/edit',
    loadComponent: () =>
      import('./form/campaign-form.component').then((m) => m.CampaignFormComponent)
  }
];
