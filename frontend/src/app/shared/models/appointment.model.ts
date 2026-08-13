export type AppointmentStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW';

export interface AppointmentResponse {
  id: string;
  leadId: string;
  userId: string;
  scheduledAt: string;
  durationMinutes: number;
  status: AppointmentStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  externalEventId: string | null;
  externalEventUrl: string | null;
}

export interface CreateAppointmentRequest {
  leadId: string;
  userId: string;
  scheduledAt: string;
  durationMinutes: number;
  status?: string;
  notes?: string | null;
}

export interface UpdateAppointmentRequest {
  scheduledAt?: string;
  durationMinutes?: number;
  status?: AppointmentStatus;
  notes?: string | null;
}

export interface AppointmentFilter {
  page?: number;
  size?: number;
  leadId?: string;
  userId?: string;
  status?: AppointmentStatus;
  sort?: string;
}
