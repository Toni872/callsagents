export type CalendarProviderType = 'GOOGLE' | 'OUTLOOK';

export type CalendarSyncStatus = 'PENDING' | 'SYNCED' | 'FAILED';

export type CalendarConnectionStatus =
  | 'ACTIVE'
  | 'NEEDS_REAUTH'
  | 'INSUFFICIENT_PERMISSIONS';

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
  connectionStatus: CalendarConnectionStatus | null;
  conflictCalendarIds: string[] | null;
  createdAt: string;
}

export interface CalendarInfo {
  id: string;
  summary: string;
  primary: boolean;
  accessRole: string;
}

export interface CalendarSelectionPayload {
  destinationCalendarId: string;
  conflictCalendarIds: string[];
}