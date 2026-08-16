export type CampaignStatus =
  | 'DRAFT'
  | 'SCHEDULED'
  | 'RUNNING'
  | 'PAUSED'
  | 'FINISHED'
  | 'CANCELLED';

export interface CampaignCreatedBy {
  id: string;
  email: string;
  fullName: string;
}

export interface CampaignResponse {
  id: string;
  name: string;
  description: string | null;
  status: CampaignStatus;
  startAt: string | null;
  endAt: string | null;
  script: string | null;
  createdBy: CampaignCreatedBy | null;
  createdAt: string;
  updatedAt: string;
  company: string | null;
  website: string | null;
  industry: string | null;
  services: string | null;
  tone: string | null;
}

export interface CreateCampaignRequest {
  name: string;
  description?: string | null;
  startAt?: string | null;
  endAt?: string | null;
  script?: string | null;
  company?: string | null;
  website?: string | null;
  industry?: string | null;
  services?: string | null;
  tone?: string | null;
}

export interface UpdateCampaignRequest {
  name?: string;
  description?: string | null;
  startAt?: string | null;
  endAt?: string | null;
  script?: string | null;
  status?: CampaignStatus;
  company?: string | null;
  website?: string | null;
  industry?: string | null;
  services?: string | null;
  tone?: string | null;
}

export interface CampaignFilter {
  page?: number;
  size?: number;
  status?: CampaignStatus;
  createdById?: string;
  sort?: string;
  /** Filtra solo campañas con configuración de voz (backend, no cliente). */
  hasVoiceConfig?: boolean;
}

export interface VoicePromptPreviewRequest {
  company?: string | null;
  website?: string | null;
  industry?: string | null;
  services?: string | null;
  tone?: string | null;
}

export interface VoicePromptPreviewResponse {
  prompt: string;
}
