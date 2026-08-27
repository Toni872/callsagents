export interface BusinessProfile {
  id: string;
  userId: string;
  companyName: string;
  website: string | null;
  industry: string | null;
  services: string | null;
  tone: string;
  botName: string;
  greeting: string | null;
  chatColor: string;
  onboardingComplete: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface BusinessProfileRequest {
  companyName: string;
  website?: string;
  industry?: string;
  services?: string;
  tone?: string;
  botName?: string;
  greeting?: string;
  chatColor?: string;
}

export interface WidgetConfigResponse {
  botName: string;
  greeting: string;
  chatColor: string;
  companyName: string;
}
