import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";

import * as accountService from "../services/accountService";
import type { UpdateProfileRequest, UpdatePreferencesRequest } from "../types/account";
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

  // Session changes invalidate pending work; successful saves invalidate older reads.
  const sessionVersion = useRef(0);
  const userRevision = useRef(0);
  const restoreSequence = useRef(0);

  const restoreSession = useCallback(async (): Promise<void> => {
    const token = getAuthToken();
    const session = sessionVersion.current;
    const revision = userRevision.current;
    const sequence = ++restoreSequence.current;
    const isCurrentRead = () => session === sessionVersion.current
      && sequence === restoreSequence.current && revision === userRevision.current;

    if (!token) {
      setUser(null);
      setRestorationError(null);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);

    try {
      const currentUser = await getCurrentUser();

      if (!isCurrentRead()) return;
      setUser(currentUser);
      setRestorationError(null);
    } catch (error) {
      if (!isCurrentRead()) return;
      setUser(null);

      if (error instanceof ApiError && error.status === 401) {
        setRestorationError(null);
      } else {
        setRestorationError(SESSION_RESTORATION_ERROR);
      }
    } finally {
      if (session === sessionVersion.current && sequence === restoreSequence.current) {
        setIsLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    let isCancelled = false;

    const unsubscribe = subscribeToSessionInvalidation(() => {
      sessionVersion.current += 1;
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
      sessionVersion.current += 1;
      unsubscribe();
    };
  }, [restoreSession]);

  async function login(request: LoginRequest): Promise<void> {
    const session = ++sessionVersion.current;
    setUser(null);
    setIsLoading(false);
    setRestorationError(null);
    const response = await loginRequest(request);
    if (session !== sessionVersion.current) return;

    setAuthToken(response.accessToken);
    try {
      const currentUser = await getCurrentUser();
      if (session !== sessionVersion.current) return;
      setUser(currentUser);
      setRestorationError(null);
    } catch (error) {
      if (session === sessionVersion.current) {
        removeAuthToken();
        setUser(null);
      }
      throw error;
    }
  }

  function logout(): void {
    sessionVersion.current += 1;
    removeAuthToken();
    setUser(null);
    setIsLoading(false);
    setRestorationError(null);
  }

  async function saveAccount(save: () => Promise<UserResponse>): Promise<UserResponse> {
    if (!user) throw new Error("Sign in before updating your account.");
    const session = sessionVersion.current;
    const updatedUser = await save();
    if (session === sessionVersion.current) {
      userRevision.current += 1;
      setUser(updatedUser);
    }
    return updatedUser;
  }

  function updateProfile(request: UpdateProfileRequest): Promise<UserResponse> {
    return saveAccount(() => accountService.updateProfile(request));
  }

  function updatePreferences(request: UpdatePreferencesRequest): Promise<UserResponse> {
    return saveAccount(() => accountService.updatePreferences(request));
  }

  const value: AuthContextValue = {
    user,
    updateProfile,
    updatePreferences,
    isAuthenticated: user !== null,
    isLoading,
    restorationError,
    login,
    logout,
    retrySessionRestore: restoreSession,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
