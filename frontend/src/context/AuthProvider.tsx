import { useEffect, useState, type ReactNode } from "react";
import { getCurrentUser, login as loginRequest } from "../services/authService";
import { subscribeToSessionInvalidation } from "../services/authSession";

import {
  getAuthToken,
  removeAuthToken,
  setAuthToken,
} from "../utils/authToken";

import type { LoginRequest, UserResponse } from "../types/auth";
import { AuthContext, type AuthContextValue } from "./AuthContext";

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<UserResponse | null>(null);

  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const unsubscribe = subscribeToSessionInvalidation(() => {
      setUser(null);
    });

    async function restoreSession() {
      const token = getAuthToken();

      if (!token) {
        setIsLoading(false);
        return;
      }

      try {
        const currentUser = await getCurrentUser();
        setUser(currentUser);
      } catch {
        removeAuthToken();
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    }

    void restoreSession();

    return unsubscribe;
  }, []);

  async function login(request: LoginRequest): Promise<void> {
    const response = await loginRequest(request);

    setAuthToken(response.accessToken);

    try {
      const currentUser = await getCurrentUser();
      setUser(currentUser);
    } catch (error) {
      removeAuthToken();
      setUser(null);
      throw error;
    }
  }

  function logout(): void {
    removeAuthToken();
    setUser(null);
  }

  const value: AuthContextValue = {
    user,
    isAuthenticated: user !== null,
    isLoading,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
