"use client";

import { useCallback, useEffect, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { EmployeeOverview } from "@/features/hr/EmployeeOverview";
import { MyContactDetails, MyContractsAndDocuments } from "@/features/hr/MySelfService";
import { EmploymentBadge, errorMessage } from "@/features/hr/ui";
import { getMyProfile, type EmployeeDetail } from "@/lib/hrApi";

/**
 * Employee self-service: the signed-in person's own employment record. They may update their contact details and
 * emergency contacts, and see their contracts and documents; everything else is maintained by HR.
 */
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
        description="Your employment record as HR holds it. Keep your contact details current here; for anything else, ask your manager or HR."
        actions={profile ? <EmploymentBadge status={profile.summary.employmentStatus} /> : null}
      />
      {state === "loading" ? <Skeleton className="h-64 w-full" /> : null}
      {state === "error" ? <ErrorState message={error} onRetry={load} /> : null}
      {state === "missing" ? (
        <EmptyState icon="user" title="No employee record linked" description="Your login is not linked to an employee record yet. HR can link it from your employee profile." />
      ) : null}
      {state === "ready" && profile ? (
        <>
          <MyContactDetails key={profile.version} employee={profile} onSaved={setProfile} />
          <section className="panel"><EmployeeOverview employee={profile} /></section>
          <MyContractsAndDocuments />
        </>
      ) : null}
    </div>
  );
}
