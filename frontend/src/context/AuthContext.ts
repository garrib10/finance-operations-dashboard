import { createContext, useContext } from "react";
import type { LoginRequest, UserResponse } from "../types/auth";

import type { UpdateProfileRequest, UpdatePreferencesRequest } from "../types/account";

export interface AuthContextValue {
  updateProfile: (request: UpdateProfileRequest) => Promise<UserResponse>;
  updatePreferences: (request: UpdatePreferencesRequest) => Promise<UserResponse>;
  uploadProfilePhoto: (photo: File) => Promise<UserResponse>;
  removeProfilePhoto: () => Promise<UserResponse>;
  user: UserResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  restorationError: string | null;
  /** Why the user was signed out (expired session, password change, another tab). */
  sessionNotice: string | null;
  login: (request: LoginRequest) => Promise<void>;
  /** Resolves after the server confirms logout; rejects without signing out otherwise. */
  logout: () => Promise<void>;
  /** Ends this session after the server changed the password and revoked every family. */
  completePasswordChange: () => void;
  retrySessionRestore: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | undefined>(
  undefined,
);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }

  return context;
}
