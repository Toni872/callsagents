export type CalendarProviderType = 'GOOGLE' | 'OUTLOOK';

export type CalendarSyncStatus = 'PENDING' | 'SYNCED' | 'FAILED';

export interface CalendarProviderStatus {
  provider: CalendarProviderType;
  configured: boolean;
}

export interface CalendarIntegration {
  id: string;
  userId: string;
  provider: CalendarProviderType;
  externalAccountEmail: string | null;
  externalCalendarId: string | null;
  accessTokenExpiresAt: string | null;
  syncEnabled: boolean;
  lastSyncAt: string | null;
  lastSyncStatus: CalendarSyncStatus | null;
  lastSyncError: string | null;
  createdAt: string;
}
