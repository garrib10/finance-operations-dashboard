import { useCallback, useEffect, useState, type ReactNode } from "react";

import { ApiError } from "../services/api";
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

const SESSION_RESTORATION_ERROR =
  "We couldn’t restore your session. Check your connection and try again.";

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [restorationError, setRestorationError] = useState<string | null>(null);

  const restoreSession = useCallback(async (): Promise<void> => {
    const token = getAuthToken();

    if (!token) {
      setUser(null);
      setRestorationError(null);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);

    try {
      const currentUser = await getCurrentUser();

      setUser(currentUser);
      setRestorationError(null);
    } catch (error) {
      setUser(null);

      if (error instanceof ApiError && error.status === 401) {
        setRestorationError(null);
      } else {
        setRestorationError(SESSION_RESTORATION_ERROR);
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let isCancelled = false;

    const unsubscribe = subscribeToSessionInvalidation(() => {
      setUser(null);
      setRestorationError(null);
      setIsLoading(false);
    });

    queueMicrotask(() => {
      if (!isCancelled) {
        void restoreSession();
      }
    });

    return () => {
      isCancelled = true;
      unsubscribe();
    };
  }, [restoreSession]);

  async function login(request: LoginRequest): Promise<void> {
    const response = await loginRequest(request);

    setAuthToken(response.accessToken);

    try {
      const currentUser = await getCurrentUser();

      setUser(currentUser);
      setRestorationError(null);
    } catch (error) {
      removeAuthToken();
      setUser(null);
      throw error;
    }
  }

  function logout(): void {
    removeAuthToken();
    setUser(null);
    setRestorationError(null);
  }

  const value: AuthContextValue = {
    user,
    isAuthenticated: user !== null,
    isLoading,
    restorationError,
    login,
    logout,
    retrySessionRestore: restoreSession,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
