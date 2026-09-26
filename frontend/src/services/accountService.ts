import { apiRequest } from "./api";
import type { UserResponse } from "../types/auth";
import type {
  ChangePasswordRequest,
  UpdatePreferencesRequest,
  UpdateProfileRequest,
} from "../types/account";

export function updateProfile({ firstName, lastName, displayName }: UpdateProfileRequest): Promise<UserResponse> {
  return apiRequest<UserResponse>("/api/account/profile", {
    method: "PUT",
    body: JSON.stringify({ firstName, lastName, displayName }),
  });
}

export function updatePreferences({ dateFormat, transactionPageSize }: UpdatePreferencesRequest): Promise<UserResponse> {
  return apiRequest<UserResponse>("/api/account/preferences", {
    method: "PUT",
    body: JSON.stringify({ dateFormat, transactionPageSize }),
  });
}

export function changePassword({ currentPassword, newPassword }: ChangePasswordRequest): Promise<void> {
  return apiRequest<void>("/api/account/password", {
    method: "POST",
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}
