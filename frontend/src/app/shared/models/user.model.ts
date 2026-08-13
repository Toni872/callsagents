export type UserRole = 'ADMIN' | 'SUPERVISOR' | 'AGENT';

export type UserStatus = 'ACTIVE' | 'DISABLED';

export interface UserListItem {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  status: UserStatus;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface CreateUserRequest {
  email: string;
  password: string;
  fullName: string;
  role: UserRole;
}

export interface UpdateUserStatusRequest {
  status: UserStatus;
}

export interface UserListFilter {
  page?: number;
  size?: number;
  role?: UserRole;
}
