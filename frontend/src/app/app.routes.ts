import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./core/layout/main-layout/main-layout.component').then(
        (m) => m.MainLayoutComponent
      ),
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then(
            (m) => m.DashboardComponent
          )
      },
      {
        path: 'assistant-demo',
        loadComponent: () =>
          import('./features/assistant/assistant-demo.component').then(
            (m) => m.AssistantDemoComponent
          )
      },
      {
        path: 'leads',
        loadChildren: () =>
          import('./features/leads/leads.routes').then((m) => m.LEADS_ROUTES)
      },
      {
        path: 'campaigns',
        loadChildren: () =>
          import('./features/campaigns/campaigns.routes').then(
            (m) => m.CAMPAIGNS_ROUTES
          )
      },
      {
        path: 'calls',
        loadChildren: () =>
          import('./features/calls/calls.routes').then((m) => m.CALLS_ROUTES)
      },
      {
        path: 'voice-calls',
        loadComponent: () =>
          import('./features/voice/voice-calls.component').then(
            (m) => m.VoiceCallsComponent
          )
      },
      {
        path: 'appointments',
        loadChildren: () =>
          import('./features/appointments/appointments.routes').then(
            (m) => m.APPOINTMENTS_ROUTES
          )
      },
      {
        path: 'users',
        loadChildren: () =>
          import('./features/users/users.routes').then((m) => m.USERS_ROUTES)
      },
      {
        path: 'settings/calendar',
        loadComponent: () =>
          import(
            './features/settings/calendar/calendar-settings.component'
          ).then((m) => m.CalendarSettingsComponent)
      }
    ]
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(
        (m) => m.LoginComponent
      )
  },
  {
    path: 'terms',
    loadComponent: () =>
      import('./features/legal/terms/terms.component').then(
        (m) => m.TermsComponent
      )
  },
  {
    path: 'privacy',
    loadComponent: () =>
      import('./features/legal/privacy/privacy.component').then(
        (m) => m.PrivacyComponent
      )
  },
  {
    path: 'landing',
    loadComponent: () =>
      import('./features/landing/landing.component').then(
        (m) => m.LandingComponent
      )
  },
  {
    path: 'demo',
    loadComponent: () =>
      import('./features/demo/demo-chat.component').then(
        (m) => m.DemoChatComponent
      )
  },
  { path: '**', redirectTo: '' }
];
