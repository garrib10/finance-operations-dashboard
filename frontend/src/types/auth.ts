import type { AccountPreferences } from "./account";

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export interface UserResponse {
  displayName: string;
  preferences: AccountPreferences;
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  createdAt: string;
}
