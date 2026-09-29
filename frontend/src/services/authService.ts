import { apiRequest, authEndpointRequest } from "./api";
import { withAuthLock } from "./authLock";
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserResponse,
} from "../types/auth";

/** Sets the refresh cookie and returns this tab's access token. Never retried. */
export function login(request: LoginRequest): Promise<LoginResponse> {
  return withAuthLock(() => authEndpointRequest<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(request),
  }));
}

/**
 * Rotates the refresh cookie. The cookie is sent by the browser; this code never
 * sees it. Callers hold the auth lock (see sessionRefresh).
 */
export function refreshSession(): Promise<LoginResponse> {
  return authEndpointRequest<LoginResponse>("/api/auth/refresh", { method: "POST" });
}

/** Revokes the current refresh family; the backend answers 204 even when already signed out. */
export function logoutSession(): Promise<void> {
  return withAuthLock(() => authEndpointRequest<void>("/api/auth/logout", { method: "POST" }));
}

export function register(request: RegisterRequest): Promise<UserResponse> {
  return apiRequest<UserResponse>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(request),
    authenticated: false,
  });
}

export function getCurrentUser(): Promise<UserResponse> {
  return apiRequest<UserResponse>("/api/auth/me");
}
