"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { ConfirmModal } from "@/components/ConfirmModal";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { hasStaffRole, useStaffSession } from "@/components/SessionProvider";
import {
  deleteTable,
  getTableQr,
  getTables,
  updateTable,
  type TableQrRecord,
  type TableRecord,
} from "@/lib/apiClient";

export default function TableQrDetailPage() {
  const routeParams = useParams<{ id: string }>();
  const router = useRouter();
  const { session, loading, authenticatedFetch } = useStaffSession();
  const tableId = typeof routeParams?.id === "string" ? routeParams.id : "";
  const [tables, setTables] = useState<TableRecord[]>([]);
  const [qr, setQr] = useState<TableQrRecord | null>(null);
  const [qrImageUrl, setQrImageUrl] = useState("");
  const [origin, setOrigin] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [copied, setCopied] = useState(false);
  const [form, setForm] = useState({ number: "", capacity: "4", zone: "" });

  const canManage = hasStaffRole(session?.user.role, ["ADMIN"]);

  useEffect(() => {
    if (typeof window !== "undefined") {
      setOrigin(window.location.origin);
    }
  }, []);

  useEffect(() => {
    if (!session || !tableId) return;
    authenticatedFetch(async (activeSession) => {
      const [nextTables, nextQr] = await Promise.all([
        getTables(activeSession),
        getTableQr(activeSession, tableId),
      ]);
      const response = await fetch(`/api/rm/tables/${tableId}/qr-image?payload=${encodeURIComponent(`${window.location.origin}/scan/${nextQr.qrToken}`)}&sizePx=480`, {
        headers: {
          Authorization: `Bearer ${activeSession.accessToken}`,
        },
        cache: "no-store",
      });
      if (!response.ok) {
        throw new Error("Could not load QR image");
      }
      const blob = await response.blob();
      return { nextTables, nextQr, blobUrl: URL.createObjectURL(blob) };
    })
      .then(({ nextTables, nextQr, blobUrl }) => {
        setTables(nextTables);
        setQr(nextQr);
        setQrImageUrl((current) => {
          if (current) {
            URL.revokeObjectURL(current);
          }
          return blobUrl;
        });
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load table QR"));
  }, [authenticatedFetch, session, tableId]);

  useEffect(() => {
    return () => {
      if (qrImageUrl) {
        URL.revokeObjectURL(qrImageUrl);
      }
    };
  }, [qrImageUrl]);

  const table = useMemo(
    () => tables.find((entry) => entry.id === tableId) ?? null,
    [tableId, tables],
  );

  useEffect(() => {
    if (!table) return;
    setForm({
      number: table.number,
      capacity: String(table.capacity),
      zone: table.zone ?? "",
    });
  }, [table]);

  const scanUrl = qr ? `${origin}/scan/${qr.qrToken}` : "";

  async function reload() {
    if (!tableId) return;
    const activeSession = await authenticatedFetch(async (sessionValue) => sessionValue);
    const [nextTables, nextQr] = await Promise.all([
      getTables(activeSession),
      getTableQr(activeSession, tableId),
    ]);
    setTables(nextTables);
    setQr(nextQr);
  }

  async function handleSave() {
    if (!table || !canManage) return;
    setSaving(true);
    setError("");
    try {
      await authenticatedFetch((activeSession) => updateTable(activeSession, table.id, {
        number: form.number,
        capacity: Number.parseInt(form.capacity || "1", 10),
        zone: form.zone || undefined,
        branchId: table.branchId,
      }));
      await reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update table");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!table || !canManage) return;
    setDeleting(true);
    setError("");
    try {
      await authenticatedFetch((activeSession) => deleteTable(activeSession, table.id));
      router.push("/tables/qr");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete table");
      setDeleting(false);
      setConfirmDelete(false);
    }
  }

  async function copyValue(value: string) {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1200);
  }

  if (loading || !tableId) {
    return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-56" />{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24 w-full" />)}</section></main>;
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Table QR</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  if (!table || !qr) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Table QR</p>
          <h1 className="text-2xl font-semibold">Table not found</h1>
          <p className="mt-2 text-sm text-ink-soft">{error || "The requested table could not be loaded."}</p>
          <Link className="mt-4 inline-flex btn btn-primary" href="/tables/qr">Back to QR Studio</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4 xl:grid-cols-[1.1fr,420px]">
      <section className="panel">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="kicker">Printable QR</p>
            <h1 className="text-2xl font-semibold">Table {table.number}</h1>
            <p className="mt-2 text-sm text-ink-soft">Use this dedicated page to print the QR card for one table or maintain its metadata.</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button className="btn btn-primary btn-sm" onClick={() => window.print()}>
              Print QR
            </button>
            <Link className="btn btn-secondary btn-sm" href="/tables/qr">Back to QR Studio</Link>
          </div>
        </div>

        <div className="mt-6 grid gap-4 rounded-2xl border border-line bg-surface p-6 print:border-0 print:p-0">
          <div className="grid gap-2 text-center">
            <p className="text-sm font-semibold uppercase tracking-[0.3em] text-ink-soft">Restaurant Manager</p>
            <h2 className="text-4xl font-semibold text-ink">Table {table.number}</h2>
            <p className="text-sm text-ink-soft">{table.zone || "Main Floor"} · {table.capacity} seats</p>
          </div>

          <div className="mx-auto rounded-[2rem] border border-line bg-white p-6 shadow-card">
            {qrImageUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={qrImageUrl} alt={`QR code for table ${table.number}`} className="h-[320px] w-[320px] object-contain" />
            ) : (
              <div className="grid h-[320px] w-[320px] place-items-center">
                <Spinner className="text-current" />
              </div>
            )}
          </div>

          <div className="grid gap-2 text-center">
            <p className="text-base font-semibold text-ink">Scan to open the dine-in menu for this table</p>
            <p className="mx-auto max-w-xl text-sm text-ink-soft break-all">{scanUrl}</p>
          </div>
        </div>
      </section>

      <section className="panel grid gap-4">
        <div>
          <p className="kicker">Table Details</p>
          <h2 className="text-xl font-semibold">Manage Table</h2>
        </div>

        <div className="grid gap-2 rounded-xl border border-line p-3 text-sm">
          <p><span className="font-semibold">Status:</span> {table.status}</p>
          <p><span className="font-semibold">Zone:</span> {table.zone || "Main Floor"}</p>
          <p><span className="font-semibold">Branch:</span> {table.branchName || "Unassigned"}</p>
        </div>

        <div className="grid gap-2 rounded-xl border border-line p-3">
          <p className="field-label">QR Metadata</p>
          <code className="input overflow-x-auto">{qr.qrToken}</code>
          <div className="grid gap-2">
            <button className="btn btn-secondary btn-sm" onClick={() => void copyValue(qr.qrToken)}>
              {copied ? "Copied" : "Copy Token"}
            </button>
            <a className="btn btn-secondary btn-sm" href={scanUrl} target="_blank" rel="noreferrer">
              Open Scan URL
            </a>
          </div>
        </div>

        <div className="grid gap-2 rounded-xl border border-line p-3">
          <p className="field-label">Edit Table</p>
          {canManage ? (
            <>
              <input className="input" placeholder="Table number" value={form.number} onChange={(event) => setForm((current) => ({ ...current, number: event.target.value }))} />
              <input className="input" type="number" min={1} placeholder="Capacity" value={form.capacity} onChange={(event) => setForm((current) => ({ ...current, capacity: event.target.value }))} />
              <input className="input" placeholder="Zone" value={form.zone} onChange={(event) => setForm((current) => ({ ...current, zone: event.target.value }))} />
              <button className="btn btn-primary btn-md" onClick={() => void handleSave()} disabled={saving}>
                {saving ? <Spinner className="text-current" /> : null}
                Save Changes
              </button>
            </>
          ) : (
            <p className="text-sm text-ink-soft">Only admins can edit table definitions.</p>
          )}
        </div>

        <div className="grid gap-2 rounded-xl border border-danger-border bg-danger-subtle p-3">
          <p className="field-label text-danger-on">Delete Table</p>
          {canManage ? (
            <button className="btn btn-danger btn-md" onClick={() => setConfirmDelete(true)}>
              Delete This Table
            </button>
          ) : (
            <p className="text-sm text-danger-on">Only admins can delete tables.</p>
          )}
        </div>

        {error ? <p className="alert alert-danger">{error}</p> : null}
      </section>

      <ConfirmModal
        open={confirmDelete}
        title="Delete Table"
        message={`Delete table ${table.number}? This removes the current table definition and its QR token.`}
        confirmLabel="Delete Table"
        danger
        busy={deleting}
        onClose={() => setConfirmDelete(false)}
        onConfirm={() => void handleDelete()}
      />
    </main>
  );
}
