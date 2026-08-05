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
}

export interface CreateCampaignRequest {
  name: string;
  description?: string | null;
  startAt?: string | null;
  endAt?: string | null;
  script?: string | null;
}

export interface UpdateCampaignRequest {
  name?: string;
  description?: string | null;
  startAt?: string | null;
  endAt?: string | null;
  script?: string | null;
  status?: CampaignStatus;
}

export interface CampaignFilter {
  page?: number;
  size?: number;
  status?: CampaignStatus;
  createdById?: string;
  sort?: string;
}
