"use client";

import { useCallback, useEffect, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { EmployeeOverview } from "@/features/hr/EmployeeOverview";
import { EmploymentBadge, errorMessage } from "@/features/hr/ui";
import { getMyProfile, type EmployeeDetail } from "@/lib/hrApi";

/** Employee self-service: read-only view of the signed-in person's own employment record. */
export default function MyHrProfilePage() {
  const { authenticatedFetch } = useStaffSession();
  const [profile, setProfile] = useState<EmployeeDetail | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "missing" | "error">("loading");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setState("loading");
    try {
      setProfile(await authenticatedFetch((s) => getMyProfile(s)));
      setState("ready");
    } catch (err) {
      const message = errorMessage(err, "Could not load your profile");
      if (/no employee record/i.test(message)) {
        setState("missing");
      } else {
        setError(message);
        setState("error");
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  return (
    <div className="grid gap-6">
      <PageHeader
        eyebrow="Human Resources"
        title="My HR profile"
        icon="user"
        description="Your employment record as HR holds it. If something is wrong, ask your manager or HR to correct it."
        actions={profile ? <EmploymentBadge status={profile.summary.employmentStatus} /> : null}
      />
      {state === "loading" ? <Skeleton className="h-64 w-full" /> : null}
      {state === "error" ? <ErrorState message={error} onRetry={load} /> : null}
      {state === "missing" ? (
        <EmptyState icon="user" title="No employee record linked" description="Your login is not linked to an employee record yet. HR can link it from your employee profile." />
      ) : null}
      {state === "ready" && profile ? <section className="panel"><EmployeeOverview employee={profile} /></section> : null}
    </div>
  );
}
