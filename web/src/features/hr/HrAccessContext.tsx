"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { getHrAccess, type HrAccess } from "@/lib/hrApi";

type HrAccessState = {
  access: HrAccess | null;
  loading: boolean;
  /** True when the user holds at least one of the listed permission codes ("self" = has a linked employee record). */
  can: (...anyOf: string[]) => boolean;
  refresh: () => Promise<void>;
};

const HrAccessContext = createContext<HrAccessState>({
  access: null,
  loading: false,
  can: () => false,
  refresh: async () => undefined,
});

/** Checks a requirement list against an access summary. Exported for navigation and tests. */
export function hasHrRequirement(access: HrAccess | null, anyOf: string[]): boolean {
  if (!access) return false;
  return anyOf.some((code) => (code === "self" ? !!access.employeeId : access.permissions.includes(code)));
}

/**
 * Loads what the signed-in user may do in HR. This only drives what the UI offers;
 * every HR endpoint re-checks permissions and branch scope on the server.
 */
export function HrAccessProvider({ children }: { children: React.ReactNode }) {
  const { session, authenticatedFetch } = useStaffSession();
  const userId = session?.user.id;
  const role = session?.user.role;
  const [access, setAccess] = useState<HrAccess | null>(null);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!userId || role === "CUSTOMER") {
      setAccess(null);
      return;
    }
    setLoading(true);
    try {
      setAccess(await authenticatedFetch((s) => getHrAccess(s)));
    } catch {
      setAccess(null);
    } finally {
      setLoading(false);
    }
    // authenticatedFetch identity changes with token refreshes; reload only when the user changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, role]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const value = useMemo<HrAccessState>(
    () => ({ access, loading, can: (...anyOf: string[]) => hasHrRequirement(access, anyOf), refresh }),
    [access, loading, refresh],
  );
  return <HrAccessContext.Provider value={value}>{children}</HrAccessContext.Provider>;
}

export function useHrAccess(): HrAccessState {
  return useContext(HrAccessContext);
}
