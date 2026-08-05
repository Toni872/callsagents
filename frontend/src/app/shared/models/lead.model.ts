export type LeadStatus =
  | 'NEW'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'QUALIFIED'
  | 'NOT_QUALIFIED'
  | 'CONVERTED'
  | 'DISQUALIFIED';

export type LeadSource = 'MANUAL' | 'IMPORT' | 'API';

export interface LeadAssignedUser {
  id: string;
  email: string;
  fullName: string;
}

export interface LeadResponse {
  id: string;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  company: string | null;
  status: LeadStatus;
  source: LeadSource;
  assignedTo: LeadAssignedUser | null;
  notes: string | null;
  customFields: Record<string, unknown> | null;
  consentAt: string | null;
  doNotCall: boolean;
  dataRetentionUntil: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateLeadRequest {
  firstName: string;
  lastName: string;
  email?: string | null;
  phone?: string | null;
  company?: string | null;
  source: string;
  notes?: string | null;
  customFields?: Record<string, unknown> | null;
}

export interface UpdateLeadRequest {
  firstName?: string;
  lastName?: string;
  email?: string | null;
  phone?: string | null;
  company?: string | null;
  status?: LeadStatus;
  source?: LeadSource;
  assignedToId?: string | null;
  notes?: string | null;
  customFields?: Record<string, unknown> | null;
}

export interface LeadFilter {
  page?: number;
  size?: number;
  status?: LeadStatus;
  source?: LeadSource;
  assignedToId?: string;
  search?: string;
  sort?: string;
}
