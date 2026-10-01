export interface UserDto {
  id: string;
  email: string;
  fullName: string;
  role: string;
  trialEndsAt: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface LoginResponse {
  accessToken: string;
  accessTokenExpiresInSeconds: number;
  user: UserDto;
}

export interface RefreshResponse {
  accessToken: string;
  accessTokenExpiresInSeconds: number;
}