import { createContext, useContext } from "react";
import type { LoginRequest, UserResponse } from "../types/auth";

import type { UpdateProfileRequest, UpdatePreferencesRequest } from "../types/account";

export interface AuthContextValue {
  updateProfile: (request: UpdateProfileRequest) => Promise<UserResponse>;
  updatePreferences: (request: UpdatePreferencesRequest) => Promise<UserResponse>;
  user: UserResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  restorationError: string | null;
  login: (request: LoginRequest) => Promise<void>;
  logout: () => void;
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
