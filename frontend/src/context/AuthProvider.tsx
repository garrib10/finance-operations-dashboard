import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";

import * as accountService from "../services/accountService";
import type { UpdateProfileRequest, UpdatePreferencesRequest } from "../types/account";
import {
  getCurrentUser,
  login as loginRequest,
  logoutSession,
} from "../services/authService";
import {
  beginNewSession,
  invalidateAuthSession,
  subscribeToSessionInvalidation,
} from "../services/authSession";
import { openSessionChannel, publishSessionEvent } from "../services/sessionBroadcast";
import { isTerminalRefreshFailure, refreshAccessToken } from "../services/sessionRefresh";

import {
  clearAccessToken,
  removeLegacyAccessToken,
  setAccessToken,
} from "../utils/authToken";

import type { LoginRequest, UserResponse } from "../types/auth";
import { AuthContext, type AuthContextValue } from "./AuthContext";
import { noticeFor } from "./sessionNotices";

interface AuthProviderProps {
  children: ReactNode;
}

const SESSION_RESTORATION_ERROR =
  "We couldn’t restore your session. Check your connection and try again.";

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [restorationError, setRestorationError] = useState<string | null>(null);
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);

  // Session changes invalidate pending work; successful saves invalidate older reads.
  const sessionVersion = useRef(0);
  const userRevision = useRef(0);
  const restoreSequence = useRef(0);
  const pendingLogout = useRef<Promise<void> | null>(null);

  /** Page-load and explicit-retry restoration: rotate the refresh cookie, then load /me. */
  const restoreSession = useCallback(async (): Promise<void> => {
    const session = sessionVersion.current;
    const revision = userRevision.current;
    const sequence = ++restoreSequence.current;
    const isCurrentRead = () => session === sessionVersion.current
      && sequence === restoreSequence.current && revision === userRevision.current;

    setIsLoading(true);

    try {
      await refreshAccessToken();
      const currentUser = await getCurrentUser();

      if (!isCurrentRead()) return;
      setUser(currentUser);
      setRestorationError(null);
    } catch (error) {
      if (!isCurrentRead()) return;
      clearAccessToken();
      setUser(null);
      // A refresh 401 means there is no session to restore; anything else may be temporary.
      setRestorationError(isTerminalRefreshFailure(error) ? null : SESSION_RESTORATION_ERROR);
    } finally {
      if (session === sessionVersion.current && sequence === restoreSequence.current) {
        setIsLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    let isCancelled = false;

    removeLegacyAccessToken();

    const unsubscribe = subscribeToSessionInvalidation((event) => {
      sessionVersion.current += 1;
      setUser(null);
      setIsLoading(false);
      if (event.reason === "TEMPORARY_FAILURE") {
        setRestorationError(SESSION_RESTORATION_ERROR);
        return;
      }
      setRestorationError(null);
      setSessionNotice(noticeFor(event));
    });

    const closeChannel = openSessionChannel((type) => {
      invalidateAuthSession(type, "remote");
      if (type === "ACCOUNT_CHANGED") {
        // Another tab signed in; restore through the shared cookie with this tab's own token.
        void restoreSession();
      }
    });

    // Deferred so React Strict Mode's discarded first mount never starts a refresh.
    queueMicrotask(() => {
      if (!isCancelled) {
        void restoreSession();
      }
    });

    return () => {
      isCancelled = true;
      sessionVersion.current += 1;
      unsubscribe();
      closeChannel();
    };
  }, [restoreSession]);

  async function login(request: LoginRequest): Promise<void> {
    const session = ++sessionVersion.current;
    beginNewSession();
    setUser(null);
    setIsLoading(false);
    setRestorationError(null);
    const response = await loginRequest(request);
    if (session !== sessionVersion.current) return;

    setAccessToken(response.accessToken);
    try {
      const currentUser = await getCurrentUser();
      if (session !== sessionVersion.current) return;
      setUser(currentUser);
      setRestorationError(null);
      setSessionNotice(null);
      publishSessionEvent("ACCOUNT_CHANGED");
    } catch (error) {
      if (session === sessionVersion.current) {
        clearAccessToken();
        setUser(null);
      }
      throw error;
    }
  }

  function logout(): Promise<void> {
    if (!pendingLogout.current) {
      // Local state is cleared only after the server confirms the family is revoked.
      pendingLogout.current = logoutSession()
        .then(() => {
          invalidateAuthSession("LOGOUT");
        })
        .finally(() => {
          pendingLogout.current = null;
        });
    }
    return pendingLogout.current;
  }

  function completePasswordChange(): void {
    invalidateAuthSession("PASSWORD_CHANGED");
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

  function uploadProfilePhoto(photo: File): Promise<UserResponse> {
    return saveAccount(() => accountService.uploadProfilePhoto(photo));
  }

  function removeProfilePhoto(): Promise<UserResponse> {
    return saveAccount(() => accountService.removeProfilePhoto());
  }

  const value: AuthContextValue = {
    user,
    updateProfile,
    updatePreferences,
    uploadProfilePhoto,
    removeProfilePhoto,
    isAuthenticated: user !== null,
    isLoading,
    restorationError,
    sessionNotice,
    login,
    logout,
    completePasswordChange,
    retrySessionRestore: restoreSession,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
