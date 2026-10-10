import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApiError, setPasswordChangeHandler, setSessionEstablished, setUnauthorizedHandler } from '../http';
import { getMe, getSetupStatus, logout as logoutRequest } from './api';
import type { CurrentUser } from './types';

export type AuthPhase = 'loading' | 'setup' | 'anonymous' | 'ready' | 'error';

type AuthContextValue = {
  phase: AuthPhase;
  user: CurrentUser | null;
  notice: string | null;
  refresh: () => Promise<void>;
  setUser: (user: CurrentUser) => void;
  finishSetup: () => void;
  logout: () => Promise<void>;
  clearNotice: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [phase, setPhase] = useState<AuthPhase>('loading');
  const [user, setUserState] = useState<CurrentUser | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const applyUser = useCallback((next: CurrentUser | null) => {
    setUserState(next);
    setSessionEstablished(next !== null);
    setPhase(next ? 'ready' : 'anonymous');
  }, []);

  const refresh = useCallback(async () => {
    const status = await getSetupStatus();
    if (status.setupRequired) {
      setSessionEstablished(false);
      setUserState(null);
      setPhase('setup');
      return;
    }
    try {
      applyUser(await getMe());
    } catch (error) {
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        setSessionEstablished(false);
        setUserState(null);
        setPhase('anonymous');
        return;
      }
      throw error;
    }
  }, [applyUser]);

  useEffect(() => {
    setUnauthorizedHandler((message) => {
      setSessionEstablished(false);
      setUserState(null);
      setPhase('anonymous');
      setNotice(message);
    });
    setPasswordChangeHandler(() => {
      setUserState((current) => (current ? { ...current, passwordChangeRequired: true } : current));
    });
    return () => {
      setUnauthorizedHandler(null);
      setPasswordChangeHandler(null);
    };
  }, []);

  useEffect(() => {
    let active = true;
    refresh().catch(() => {
      if (active) {
        setPhase('error');
      }
    });
    return () => {
      active = false;
    };
  }, [refresh]);

  const logout = useCallback(async () => {
    setSessionEstablished(false);
    try {
      await logoutRequest();
    } catch {
      // Die lokale Sitzung wird trotzdem beendet.
    }
    setUserState(null);
    setPhase('anonymous');
    setNotice('Du bist abgemeldet.');
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      phase,
      user,
      notice,
      refresh,
      setUser: (next) => {
        setNotice(null);
        applyUser(next);
      },
      finishSetup: () => {
        setSessionEstablished(false);
        setUserState(null);
        setPhase('anonymous');
        setNotice('Die Einrichtung ist abgeschlossen. Bitte melde dich an.');
      },
      logout,
      clearNotice: () => setNotice(null),
    }),
    [applyUser, logout, notice, phase, refresh, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth braucht einen AuthProvider');
  }
  return value;
}
