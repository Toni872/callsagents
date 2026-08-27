import { Routes } from '@angular/router';

export const ONBOARDING_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./onboarding-wizard.component').then(
        (m) => m.OnboardingWizardComponent
      )
  }
];
