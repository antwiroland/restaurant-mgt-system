"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import {
  loginStaff,
  logoutStaff,
  refreshStaffSession,
  type StaffSession,
  type StaffRole,
} from "@/lib/apiClient";

type SessionContextValue = {
  session: StaffSession | null;
  loading: boolean;
  login: (phone: string, password: string) => Promise<StaffSession>;
  logout: () => Promise<void>;
  authenticatedFetch: <T>(run: (session: StaffSession) => Promise<T>) => Promise<T>;
};

const STORAGE_KEY = "staff-session";
const SessionContext = createContext<SessionContextValue | null>(null);

function persistSession(session: StaffSession | null) {
  if (typeof window === "undefined") return;
  if (!session) {
    window.localStorage.removeItem(STORAGE_KEY);
    return;
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

function readStoredSession(): StaffSession | null {
  if (typeof window === "undefined") return null;
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;

  try {
    return JSON.parse(raw) as StaffSession;
  } catch {
    window.localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<StaffSession | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setSession(readStoredSession());
    setLoading(false);
  }, []);

  const applySession = useCallback((nextSession: StaffSession | null) => {
    setSession(nextSession);
    persistSession(nextSession);
  }, []);

  const login = useCallback(async (phone: string, password: string) => {
    const nextSession = await loginStaff(phone, password);
    applySession(nextSession);
    return nextSession;
  }, [applySession]);

  const logout = useCallback(async () => {
    const current = session;
    applySession(null);
    if (current) {
      await logoutStaff(current).catch(() => {});
    }
  }, [applySession, session]);

  const authenticatedFetch = useCallback(async <T,>(run: (activeSession: StaffSession) => Promise<T>) => {
    const current = session ?? readStoredSession();
    if (!current) {
      throw new Error("Sign in required");
    }

    if (current.expiresAtEpochMs > Date.now() + 30_000) {
      return run(current);
    }

    const refreshed = await refreshStaffSession(current.refreshToken);
    const nextSession: StaffSession = { ...current, ...refreshed };
    applySession(nextSession);
    return run(nextSession);
  }, [applySession, session]);

  const value = useMemo<SessionContextValue>(() => ({
    session,
    loading,
    login,
    logout,
    authenticatedFetch,
  }), [authenticatedFetch, loading, login, logout, session]);

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useStaffSession() {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error("useStaffSession must be used within SessionProvider");
  }
  return value;
}

export function hasStaffRole(role: StaffRole | undefined, allowedRoles: StaffRole[]) {
  return !!role && allowedRoles.includes(role);
}
