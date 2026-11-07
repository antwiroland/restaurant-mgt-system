"use client";

import { useEffect, useState } from "react";
import { ConfirmModal } from "@/components/ConfirmModal";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { EmptyState, ErrorState, PageHeader } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import {
  deletePrinter,
  getBranches,
  getPrinters,
  savePrinter,
  testPrinter,
  type BranchRecord,
  type PrinterRecord,
  type PrinterUpsert,
} from "@/lib/apiClient";

const EMPTY: PrinterUpsert = { name: "", kind: "RECEIPT", host: "", port: 9100, paperWidthMm: 80, branchId: null, active: true };

export default function PrintersPage() {
  const { session, authenticatedFetch } = useStaffSession();
  const [printers, setPrinters] = useState<PrinterRecord[] | null>(null);
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [editing, setEditing] = useState<{ id?: string; form: PrinterUpsert } | null>(null);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<PrinterRecord | null>(null);

  async function load() {
    try {
      const [list, branchList] = await authenticatedFetch((s) => Promise.all([getPrinters(s), getBranches(s).catch(() => [])]));
      setPrinters(list);
      setBranches(branchList);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load printers");
    }
  }

  useEffect(() => {
    if (session) void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session]);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (!editing) return;
    setSaving(true);
    try {
      await authenticatedFetch((s) => savePrinter(s, editing.form, editing.id));
      setEditing(null);
      setNotice({ tone: "success", text: "Printer saved." });
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: err instanceof Error ? err.message : "Could not save printer" });
    } finally {
      setSaving(false);
    }
  }

  async function runTest(printer: PrinterRecord) {
    setTesting(printer.id);
    setNotice(null);
    try {
      const result = await authenticatedFetch((s) => testPrinter(s, printer.id));
      setNotice({ tone: "success", text: `${result.message}. Check the printer for a test slip.` });
    } catch (err) {
      setNotice({ tone: "danger", text: err instanceof Error ? err.message : "Test print failed" });
    } finally {
      setTesting(null);
    }
  }

  const branchName = (id?: string | null) => (id ? branches.find((b) => b.id === id)?.name ?? "Branch" : "All branches");
  const set = <K extends keyof PrinterUpsert>(key: K, value: PrinterUpsert[K]) =>
    setEditing((current) => (current ? { ...current, form: { ...current.form, [key]: value } } : current));

  return (
    <main className="shell max-w-5xl">
      <PageHeader
        eyebrow="Admin"
        title="Printers"
        icon="print"
        description="Network thermal printers (ESC/POS on port 9100) for receipts and kitchen tickets. Any other printer can still print from the browser."
        actions={<button className="btn btn-primary btn-sm" onClick={() => setEditing({ form: { ...EMPTY } })}><Icon name="plus" size={16} /> Add printer</button>}
      />
      {error ? <ErrorState message={error} onRetry={() => void load()} /> : null}
      {notice ? <p className={`alert alert-${notice.tone}`} role={notice.tone === "danger" ? "alert" : "status"}>{notice.text}</p> : null}

      {printers === null ? (
        <div className="grid gap-3"><Skeleton className="h-20" /><Skeleton className="h-20" /></div>
      ) : printers.length === 0 ? (
        <EmptyState icon="print" title="No network printers yet" description="Add the receipt printer at the counter and the kitchen printer at the pass. Use the printer's IP address from its self-test slip." />
      ) : (
        <ul className="grid gap-3">
          {printers.map((printer) => (
            <li key={printer.id} className="flex flex-wrap items-center gap-4 rounded-card border border-line bg-surface p-4">
              <span className={`grid h-11 w-11 shrink-0 place-items-center rounded-xl ${printer.kind === "KITCHEN" ? "bg-preparing-subtle text-preparing" : "bg-info-subtle text-info-on"}`}>
                <Icon name={printer.kind === "KITCHEN" ? "chef" : "receipt"} size={20} />
              </span>
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-ink">{printer.name} {!printer.active ? <span className="badge badge-neutral ml-1">Off</span> : null}</p>
                <p className="text-sm text-ink-soft">
                  {printer.kind === "KITCHEN" ? "Kitchen tickets" : "Receipts"} · {printer.paperWidthMm} mm · <span className="font-mono">{printer.host}:{printer.port}</span> · {branchName(printer.branchId)}
                </p>
              </div>
              <div className="flex gap-2">
                <button className="btn btn-secondary btn-sm" disabled={testing === printer.id} onClick={() => void runTest(printer)}>
                  {testing === printer.id ? <Spinner size={14} /> : <Icon name="print" size={16} />} Test
                </button>
                <button className="btn btn-ghost btn-icon btn-sm" aria-label={`Edit ${printer.name}`} onClick={() => setEditing({ id: printer.id, form: { name: printer.name, kind: printer.kind, host: printer.host, port: printer.port, paperWidthMm: printer.paperWidthMm, branchId: printer.branchId ?? null, active: printer.active } })}>
                  <Icon name="edit" size={18} />
                </button>
                <button className="btn btn-ghost btn-icon btn-sm text-ink-muted hover:text-danger-on" aria-label={`Delete ${printer.name}`} onClick={() => setDeleting(printer)}>
                  <Icon name="trash" size={18} />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <section className="panel-muted text-sm text-ink-soft">
        <p className="font-semibold text-ink">Browser printing</p>
        <p className="mt-1">From an order you can also choose <strong>Print in browser</strong> for a 58/80 mm receipt or kitchen ticket. This works with USB and Bluetooth printers installed on the device.</p>
      </section>

      <Modal
        open={!!editing}
        title={editing?.id ? "Edit printer" : "Add printer"}
        onClose={() => setEditing(null)}
        footer={
          <>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditing(null)}>Cancel</button>
            <button type="submit" form="printer-form" className="btn btn-primary btn-sm" disabled={saving}>{saving ? <Spinner size={14} /> : null} Save</button>
          </>
        }
      >
        {editing ? (
          <form id="printer-form" onSubmit={save} className="grid gap-3">
            <label className="field"><span className="field-label">Name</span><input className="input" required maxLength={80} placeholder="Counter, Pass, Bar…" value={editing.form.name} onChange={(e) => set("name", e.target.value)} /></label>
            <div className="segmented grid grid-cols-2" role="group" aria-label="Printer use">
              <button type="button" aria-pressed={editing.form.kind === "RECEIPT"} onClick={() => set("kind", "RECEIPT")}>Receipts</button>
              <button type="button" aria-pressed={editing.form.kind === "KITCHEN"} onClick={() => set("kind", "KITCHEN")}>Kitchen tickets</button>
            </div>
            <div className="grid grid-cols-[1fr_110px] gap-3">
              <label className="field"><span className="field-label">IP address or host</span><input className="input font-mono" required placeholder="192.168.1.50" value={editing.form.host} onChange={(e) => set("host", e.target.value)} /></label>
              <label className="field"><span className="field-label">Port</span><input className="input" type="number" min={1} max={65535} value={editing.form.port} onChange={(e) => set("port", Number(e.target.value) || 9100)} /></label>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <label className="field"><span className="field-label">Paper</span>
                <select className="select" value={editing.form.paperWidthMm} onChange={(e) => set("paperWidthMm", Number(e.target.value) as 58 | 80)}>
                  <option value={80}>80 mm</option><option value={58}>58 mm</option>
                </select></label>
              <label className="field"><span className="field-label">Branch</span>
                <select className="select" value={editing.form.branchId ?? ""} onChange={(e) => set("branchId", e.target.value || null)}>
                  <option value="">All branches</option>
                  {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
                </select></label>
            </div>
            <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={editing.form.active} onChange={(e) => set("active", e.target.checked)} /> In use</label>
          </form>
        ) : null}
      </Modal>

      <ConfirmModal
        open={!!deleting}
        title="Remove printer?"
        message={deleting ? `${deleting.name} will no longer receive prints.` : ""}
        confirmLabel="Remove"
        danger
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await authenticatedFetch((s) => deletePrinter(s, deleting.id));
            setDeleting(null);
            await load();
          } catch (err) {
            setNotice({ tone: "danger", text: err instanceof Error ? err.message : "Could not remove printer" });
            setDeleting(null);
          }
        }}
        onClose={() => setDeleting(null)}
      />
    </main>
  );
}
