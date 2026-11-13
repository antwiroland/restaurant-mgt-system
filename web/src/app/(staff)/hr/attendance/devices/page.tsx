"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, StatCard } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Field, HrGuard, errorMessage, formatDateTime } from "@/features/hr/ui";
import { getBranches, type BranchRecord } from "@/lib/apiClient";
import { listEmployees, type ClockAction, type EmployeeSummary } from "@/lib/hrApi";
import {
  ACTION_LABEL, DIRECTION_LABEL, SCAN_STATUS,
  applyDeviceScan, createGateway, dismissDeviceScan, getDeviceScans, getDeviceUserLinks, getDevicesOverview, linkDeviceUser,
  retryDeviceScan, revokeGateway, rotateGatewayKey, saveDevice, unlinkDeviceUser,
  type AttendanceDevice, type AttendanceGateway, type DeviceInput, type DeviceScan, type DeviceUserLink, type DevicesOverview,
  type DirectionMode, type GatewayWithKey,
} from "@/lib/attendanceDevicesApi";

type Tab = "overview" | "review" | "devices" | "people" | "gateways";

export default function AttendanceDevicesPage() {
  return (
    <HrGuard anyOf={["hr.attendance.view", "hr.settings.manage"]}>
      <AttendanceDevices />
    </HrGuard>
  );
}

const STATUS_TONE: Record<string, string> = {
  ONLINE: "badge-success", OFFLINE: "badge-danger", ERROR: "badge-danger", REVOKED: "badge-neutral",
  NEVER_CONNECTED: "badge-warning", UNKNOWN: "badge-neutral", NO_GATEWAY: "badge-warning", DISABLED: "badge-neutral",
};
const STATUS_TEXT: Record<string, string> = {
  ONLINE: "Online", OFFLINE: "Offline", ERROR: "Error", REVOKED: "Revoked", NEVER_CONNECTED: "Not connected yet",
  UNKNOWN: "Unknown", NO_GATEWAY: "No gateway", DISABLED: "Switched off",
};

function StatusPill({ status }: { status: string }) {
  return <span className={`badge ${STATUS_TONE[status] ?? "badge-neutral"}`}>{STATUS_TEXT[status] ?? status}</span>;
}

function AttendanceDevices() {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const canConfigure = can("hr.settings.manage");
  const canDecide = can("hr.attendance.correct");
  const [tab, setTab] = useState<Tab>("overview");
  const [overview, setOverview] = useState<DevicesOverview | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setOverview(await authenticatedFetch((s) => getDevicesOverview(s)));
      setError("");
    } catch (err) {
      setError(errorMessage(err, "Could not load devices"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 30000);
    return () => window.clearInterval(timer);
  }, [load]);

  const reviewCount = overview ? Object.values(overview.reviewCounts).reduce((a, b) => a + b, 0) : 0;
  const tabs: { id: Tab; label: string; show: boolean }[] = [
    { id: "overview", label: "Overview", show: true },
    { id: "review", label: `Review${reviewCount ? ` (${reviewCount})` : ""}`, show: true },
    { id: "devices", label: "Devices", show: true },
    { id: "people", label: "People on devices", show: true },
    { id: "gateways", label: "Gateways", show: canConfigure },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        eyebrow="Human Resources"
        title="Attendance devices"
        icon="shield"
        description="Biometric clocking terminals, the gateways that read them, and scans that need a decision."
        actions={<Link href="/hr/attendance" className="btn btn-secondary btn-sm"><Icon name="clock" size={16} /> Attendance board</Link>}
      />

      <div className="segmented max-w-full overflow-x-auto" role="tablist">
        {tabs.filter((t) => t.show).map((t) => (
          <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} onClick={() => setTab(t.id)}>{t.label}</button>
        ))}
      </div>

      {error ? <ErrorState message={error} onRetry={load} /> : null}

      {tab === "overview" ? <OverviewPanel overview={overview} onOpen={setTab} /> : null}
      {tab === "review" ? <ReviewPanel canDecide={canDecide} onChanged={load} /> : null}
      {tab === "devices" ? <DevicesPanel overview={overview} canConfigure={canConfigure} onChanged={load} /> : null}
      {tab === "people" ? <PeoplePanel devices={overview?.devices ?? []} canConfigure={canConfigure} onChanged={load} /> : null}
      {tab === "gateways" && canConfigure ? <GatewaysPanel gateways={overview?.gateways ?? []} onChanged={load} /> : null}
    </div>
  );
}

// ======================================================================== overview

function OverviewPanel({ overview, onOpen }: { overview: DevicesOverview | null; onOpen: (tab: Tab) => void }) {
  if (!overview) return <div className="grid gap-3"><Skeleton className="h-24 w-full" /><Skeleton className="h-40 w-full" /></div>;
  const online = overview.devices.filter((d) => d.status === "ONLINE").length;
  const active = overview.devices.filter((d) => d.active).length;
  const review = (overview.reviewCounts.NEEDS_REVIEW ?? 0) + (overview.reviewCounts.REJECTED ?? 0);
  return (
    <div className="grid gap-5">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label="Devices online" value={`${online} / ${active}`} icon="shield" tone={online < active ? "gold" : "sage"} />
        <StatCard label="Gateways online" value={`${overview.gateways.filter((g) => g.status === "ONLINE").length} / ${overview.gateways.filter((g) => g.status !== "REVOKED").length}`} icon="refresh" />
        <StatCard label="Scans to review" value={String(review)} icon="alert" tone={review ? "gold" : "neutral"} />
        <StatCard label="Unknown people" value={String(overview.reviewCounts.UNMATCHED ?? 0)} icon="user" meta={`${overview.linkedEmployees} linked`} tone={overview.reviewCounts.UNMATCHED ? "gold" : "neutral"} />
      </div>

      {overview.alerts.length > 0 ? (
        <ul className="grid gap-2" aria-label="Problems">
          {overview.alerts.map((a, i) => (
            <li key={i} className={`alert ${a.severity === "danger" ? "alert-danger" : a.severity === "warning" ? "alert-warning" : "alert-info"}`}>
              <Icon name={a.severity === "info" ? "info" : "alert"} size={18} className="mt-0.5 shrink-0" />{a.text}
            </li>
          ))}
        </ul>
      ) : (
        <p className="alert alert-success"><Icon name="checkCircle" size={18} className="mt-0.5 shrink-0" />All devices and gateways are reporting normally.</p>
      )}

      <section className="panel">
        <div className="flex items-center justify-between gap-2">
          <h2 className="heading-sm">Devices</h2>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => onOpen("devices")}>Manage</button>
        </div>
        {overview.devices.length === 0 ? (
          <EmptyState compact icon="shield" title="No devices registered" description="Register a terminal under Devices, then link staff under People on devices." />
        ) : (
          <ul className="mt-3 grid gap-2 sm:grid-cols-2">
            {overview.devices.map((d) => (
              <li key={d.id} className="rounded-xl border border-line p-3">
                <div className="flex items-center justify-between gap-2">
                  <p className="font-semibold">{d.name}</p>
                  <StatusPill status={d.status} />
                </div>
                <p className="text-xs text-ink-soft">{d.branchName ?? "No branch"} · {d.gatewayName ?? "no gateway"} · {d.serialNumber}</p>
                <p className="mt-1 text-xs text-ink-muted">Last scan {formatDateTime(d.lastEventAt)}{d.statusMessage ? ` · ${d.statusMessage}` : ""}</p>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

// ======================================================================== review queue

function ReviewPanel({ canDecide, onChanged }: { canDecide: boolean; onChanged: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [filter, setFilter] = useState<"review" | "all">("review");
  const [scans, setScans] = useState<DeviceScan[] | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [deciding, setDeciding] = useState<{ scan: DeviceScan; mode: "apply" | "dismiss" } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setScans(await authenticatedFetch((s) => getDeviceScans(s, filter)));
      setError("");
    } catch (err) {
      setError(errorMessage(err, "Could not load scans"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter]);

  useEffect(() => { void load(); }, [load]);

  async function retry(scan: DeviceScan) {
    setBusy(scan.id);
    setNotice("");
    setError("");
    try {
      const result = await authenticatedFetch((s) => retryDeviceScan(s, scan.id));
      setNotice(`${SCAN_STATUS[result.status].label}${result.message ? `: ${result.message}` : ""}`);
      await load();
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not retry"));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="panel grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="heading-sm">Scans</h2>
          <p className="text-sm text-ink-soft">The device&apos;s own time is used. Nothing is guessed: a scan the system cannot place waits here.</p>
        </div>
        <div className="segmented" role="tablist">
          <button type="button" role="tab" aria-selected={filter === "review"} onClick={() => setFilter("review")}>Waiting</button>
          <button type="button" role="tab" aria-selected={filter === "all"} onClick={() => setFilter("all")}>Last 7 days</button>
        </div>
      </div>
      {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {!scans ? <Skeleton className="h-32 w-full" /> : scans.length === 0 ? (
        <EmptyState compact icon="checkCircle" title={filter === "review" ? "Nothing waiting" : "No scans"} description={filter === "review" ? "Every scan has been recorded or decided." : "No scans in the last 7 days."} />
      ) : (
        <ul className="grid gap-2">
          {scans.map((scan) => {
            const status = SCAN_STATUS[scan.status];
            const waiting = ["NEEDS_REVIEW", "UNMATCHED", "REJECTED"].includes(scan.status);
            return (
              <li key={scan.id} className="rounded-xl border border-line p-3">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold">
                      {scan.employeeName ?? `Device user ${scan.deviceUserId}`}
                      <span className="ml-2 text-sm font-normal text-ink-soft">{formatDateTime(scan.deviceTimestamp)}</span>
                    </p>
                    <p className="text-xs text-ink-soft">
                      {scan.deviceName ?? scan.deviceSerialNumber} · device said {scan.reportedType === "UNKNOWN" ? "nothing" : scan.reportedType.toLowerCase().replace("_", " ")}
                      {scan.authenticationMethod ? ` · ${scan.authenticationMethod.toLowerCase()}` : ""}
                      {scan.appliedAction ? ` · recorded as ${ACTION_LABEL[scan.appliedAction].toLowerCase()}` : ""}
                    </p>
                    {scan.message ? <p className="mt-1 text-sm">{scan.message}</p> : null}
                    {scan.resolvedByNote ? <p className="mt-1 text-sm italic text-ink-soft">“{scan.resolvedByNote}”</p> : null}
                  </div>
                  <span className={`badge ${status.tone}`}>{status.label}</span>
                </div>
                {waiting && canDecide ? (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {scan.employeeId && scan.deviceName ? (
                      <button type="button" className="btn btn-primary btn-sm" onClick={() => setDeciding({ scan, mode: "apply" })}>Record as…</button>
                    ) : null}
                    <button type="button" className="btn btn-secondary btn-sm" disabled={busy === scan.id} onClick={() => void retry(scan)}>
                      <Icon name="refresh" size={14} /> Try again
                    </button>
                    <button type="button" className="btn btn-ghost btn-sm" onClick={() => setDeciding({ scan, mode: "dismiss" })}>Don&apos;t count</button>
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}
      {deciding ? (
        <DecisionDialog
          scan={deciding.scan}
          mode={deciding.mode}
          onClose={() => setDeciding(null)}
          onDone={(message) => { setDeciding(null); setNotice(message); void load(); onChanged(); }}
        />
      ) : null}
    </section>
  );
}

function DecisionDialog({ scan, mode, onClose, onDone }: { scan: DeviceScan; mode: "apply" | "dismiss"; onClose: () => void; onDone: (message: string) => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [action, setAction] = useState<ClockAction>(scan.reportedType === "CLOCK_OUT" ? "CLOCK_OUT" : "CLOCK_IN");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setError("");
    try {
      if (mode === "apply") {
        const result = await authenticatedFetch((s) => applyDeviceScan(s, scan.id, action, note.trim() || undefined));
        onDone(result.message ?? "Recorded");
      } else {
        await authenticatedFetch((s) => dismissDeviceScan(s, scan.id, note.trim()));
        onDone("The scan will not count");
      }
    } catch (err) {
      setError(errorMessage(err, "Could not save the decision"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open title={mode === "apply" ? "Record this scan" : "Don't count this scan"} onClose={onClose}
      footer={(
        <div className="flex justify-end gap-2">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="button" className="btn btn-primary" disabled={busy || (mode === "dismiss" && !note.trim())} onClick={() => void submit()}>
            {mode === "apply" ? "Record" : "Don't count"}
          </button>
        </div>
      )}>
      <div className="grid gap-4">
        <p className="text-sm text-ink-soft">
          {scan.employeeName ?? `Device user ${scan.deviceUserId}`} at {formatDateTime(scan.deviceTimestamp)} on {scan.deviceName ?? scan.deviceSerialNumber}.
          {mode === "apply" ? " It is recorded at the device's time, with you as the person who decided." : ""}
        </p>
        {mode === "apply" ? (
          <Field label="Action">
            <select className="select" value={action} onChange={(e) => setAction(e.target.value as ClockAction)}>
              {(Object.keys(ACTION_LABEL) as ClockAction[]).map((a) => <option key={a} value={a}>{ACTION_LABEL[a]}</option>)}
            </select>
          </Field>
        ) : null}
        <Field label={mode === "apply" ? "Note (optional)" : "Why should it not count?"}>
          <textarea className="textarea" rows={2} maxLength={300} value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      </div>
    </Modal>
  );
}

// ======================================================================== devices

const EMPTY_DEVICE: DeviceInput = { name: "", serialNumber: "", vendor: "HIKVISION", model: "", ipAddress: "", port: 80, useHttps: false, gatewayId: null, branchId: "", directionMode: "DEVICE_REPORTED", active: true };

function DevicesPanel({ overview, canConfigure, onChanged }: { overview: DevicesOverview | null; canConfigure: boolean; onChanged: () => void }) {
  const [editing, setEditing] = useState<{ device?: AttendanceDevice } | null>(null);
  const devices = overview?.devices ?? [];
  return (
    <section className="panel grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="heading-sm">Devices</h2>
          <p className="text-sm text-ink-soft">Device logins are kept only on the gateway, never here.</p>
        </div>
        {canConfigure ? <button type="button" className="btn btn-primary btn-sm" onClick={() => setEditing({})}><Icon name="plus" size={16} /> Register device</button> : null}
      </div>
      {devices.length === 0 ? (
        <EmptyState compact icon="shield" title="No devices yet" description="Register each Hikvision terminal with its serial number and the branch it is in." />
      ) : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Device</th><th>Branch</th><th>Direction</th><th>Status</th><th>Clock</th>{canConfigure ? <th /> : null}</tr></thead>
            <tbody>
              {devices.map((d) => (
                <tr key={d.id}>
                  <td><p className="font-semibold">{d.name}</p><p className="text-xs text-ink-soft">{d.vendor} {d.model ?? ""} · {d.serialNumber}{d.ipAddress ? ` · ${d.ipAddress}${d.port ? `:${d.port}` : ""}` : ""}</p></td>
                  <td>{d.branchName ?? "—"}</td>
                  <td className="text-xs">{DIRECTION_LABEL[d.directionMode]}</td>
                  <td><StatusPill status={d.status} /><p className="text-xs text-ink-muted">Last scan {formatDateTime(d.lastEventAt)}</p></td>
                  <td className="text-xs">{d.clockOffsetSeconds == null ? "—" : Math.abs(d.clockOffsetSeconds) <= 120 ? "In sync" : `${Math.round(Math.abs(d.clockOffsetSeconds) / 60)} min ${d.clockOffsetSeconds > 0 ? "fast" : "slow"}`}</td>
                  {canConfigure ? <td><button type="button" className="btn btn-ghost btn-sm" onClick={() => setEditing({ device: d })}>Edit</button></td> : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing ? <DeviceDialog device={editing.device} gateways={overview?.gateways ?? []} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); onChanged(); }} /> : null}
    </section>
  );
}

function DeviceDialog({ device, gateways, onClose, onSaved }: { device?: AttendanceDevice; gateways: AttendanceGateway[]; onClose: () => void; onSaved: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [form, setForm] = useState<DeviceInput>(device ? {
    name: device.name, serialNumber: device.serialNumber, vendor: device.vendor, model: device.model ?? "", ipAddress: device.ipAddress ?? "",
    port: device.port ?? null, useHttps: device.useHttps, gatewayId: device.gatewayId ?? null, branchId: device.branchId ?? "",
    directionMode: device.directionMode, active: device.active,
  } : { ...EMPTY_DEVICE, gatewayId: gateways.find((g) => g.status !== "REVOKED")?.id ?? null });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof DeviceInput>(key: K, value: DeviceInput[K]) => setForm((f) => ({ ...f, [key]: value }));

  useEffect(() => {
    authenticatedFetch((s) => getBranches(s)).then((list) => {
      const active = list.filter((b) => b.active);
      setBranches(active);
      setForm((f) => (f.branchId ? f : { ...f, branchId: active[0]?.id ?? "" }));
    }).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function submit() {
    setBusy(true);
    setError("");
    try {
      await authenticatedFetch((s) => saveDevice(s, { ...form, port: form.port ? Number(form.port) : null }, device?.id));
      onSaved();
    } catch (err) {
      setError(errorMessage(err, "Could not save the device"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open title={device ? `Edit ${device.name}` : "Register device"} onClose={onClose}
      footer={(
        <div className="flex justify-end gap-2">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="button" className="btn btn-primary" disabled={busy || !form.name.trim() || !form.serialNumber.trim() || !form.branchId} onClick={() => void submit()}>Save</button>
        </div>
      )}>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Name"><input className="input" value={form.name} onChange={(e) => set("name", e.target.value)} placeholder="Staff entrance" /></Field>
        <Field label="Serial number" hint="As shown in the device's system information"><input className="input font-mono" value={form.serialNumber} onChange={(e) => set("serialNumber", e.target.value)} /></Field>
        <Field label="Make">
          <select className="select" value={form.vendor} onChange={(e) => set("vendor", e.target.value as DeviceInput["vendor"])}>
            <option value="HIKVISION">Hikvision</option>
            <option value="ZKTECO">ZKTeco (planned)</option>
            <option value="OTHER">Other</option>
          </select>
        </Field>
        <Field label="Model"><input className="input" value={form.model ?? ""} onChange={(e) => set("model", e.target.value)} placeholder="DS-K1T341AMF" /></Field>
        <Field label="IP address on the restaurant network"><input className="input font-mono" value={form.ipAddress ?? ""} onChange={(e) => set("ipAddress", e.target.value)} placeholder="192.168.1.60" /></Field>
        <Field label="Port"><input className="input" inputMode="numeric" value={form.port ?? ""} onChange={(e) => set("port", e.target.value ? Number(e.target.value) : null)} /></Field>
        <Field label="Branch">
          <select className="select" value={form.branchId} onChange={(e) => set("branchId", e.target.value)}>
            {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        </Field>
        <Field label="Gateway">
          <select className="select" value={form.gatewayId ?? ""} onChange={(e) => set("gatewayId", e.target.value || null)}>
            <option value="">None yet</option>
            {gateways.filter((g) => g.status !== "REVOKED").map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
          </select>
        </Field>
        <Field label="Which action is a scan?" className="sm:col-span-2" hint="Most terminals report check-in, break or check-out themselves. Pick a fixed action only for a reader that does one thing, e.g. an exit door.">
          <select className="select" value={form.directionMode} onChange={(e) => set("directionMode", e.target.value as DirectionMode)}>
            {(Object.keys(DIRECTION_LABEL) as DirectionMode[]).map((m) => <option key={m} value={m}>{DIRECTION_LABEL[m]}</option>)}
          </select>
        </Field>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={!!form.useHttps} onChange={(e) => set("useHttps", e.target.checked)} /> Device uses HTTPS</label>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.active !== false} onChange={(e) => set("active", e.target.checked)} /> In use</label>
        {error ? <p className="alert alert-danger sm:col-span-2" role="alert">{error}</p> : null}
      </div>
    </Modal>
  );
}

// ======================================================================== people on devices

function PeoplePanel({ devices, canConfigure, onChanged }: { devices: AttendanceDevice[]; canConfigure: boolean; onChanged: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [links, setLinks] = useState<DeviceUserLink[] | null>(null);
  const [employees, setEmployees] = useState<EmployeeSummary[]>([]);
  const [form, setForm] = useState({ employeeId: "", deviceUserId: "", deviceId: "" });
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    try {
      setLinks(await authenticatedFetch((s) => getDeviceUserLinks(s)));
    } catch (err) {
      setError(errorMessage(err, "Could not load links"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void load();
    if (canConfigure) {
      authenticatedFetch((s) => listEmployees(s, { size: 500, sort: "name" })).then((page) => setEmployees(page.items)).catch(() => {});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load, canConfigure]);

  const linked = useMemo(() => new Set((links ?? []).map((l) => l.employeeId)), [links]);

  async function add() {
    setError("");
    setNotice("");
    try {
      const link = await authenticatedFetch((s) => linkDeviceUser(s, { employeeId: form.employeeId, deviceUserId: form.deviceUserId.trim(), deviceId: form.deviceId || null }));
      setNotice(`${link.employeeName} is device user ${link.deviceUserId}. Their waiting scans can now be retried under Review.`);
      setForm({ employeeId: "", deviceUserId: "", deviceId: "" });
      await load();
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not link"));
    }
  }

  async function remove(link: DeviceUserLink) {
    setError("");
    try {
      await authenticatedFetch((s) => unlinkDeviceUser(s, link.id));
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not remove the link"));
    }
  }

  return (
    <section className="panel grid gap-4">
      <div>
        <h2 className="heading-sm">People on devices</h2>
        <p className="text-sm text-ink-soft">Each person enrolled on a terminal has a user number there (the &quot;employee no.&quot; you typed when enrolling their face, finger or card). Link it to their employee record.</p>
      </div>
      {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {canConfigure ? (
        <div className="grid gap-3 rounded-xl bg-sunken p-3 sm:grid-cols-[2fr_1fr_1.5fr_auto] sm:items-end">
          <Field label="Employee">
            <select className="select" value={form.employeeId} onChange={(e) => setForm((f) => ({ ...f, employeeId: e.target.value }))}>
              <option value="">Choose…</option>
              {employees.map((e) => <option key={e.id} value={e.id}>{e.fullName} ({e.employeeNumber}){linked.has(e.id) ? " · linked" : ""}</option>)}
            </select>
          </Field>
          <Field label="Device user no."><input className="input font-mono" value={form.deviceUserId} onChange={(e) => setForm((f) => ({ ...f, deviceUserId: e.target.value }))} placeholder="1001" /></Field>
          <Field label="On">
            <select className="select" value={form.deviceId} onChange={(e) => setForm((f) => ({ ...f, deviceId: e.target.value }))}>
              <option value="">Every device</option>
              {devices.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          </Field>
          <button type="button" className="btn btn-primary" disabled={!form.employeeId || !form.deviceUserId.trim()} onClick={() => void add()}>Link</button>
        </div>
      ) : null}
      {!links ? <Skeleton className="h-24 w-full" /> : links.length === 0 ? (
        <EmptyState compact icon="user" title="Nobody linked yet" description="Scans from people who are not linked wait under Review as “Unknown person”." />
      ) : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Device user no.</th><th>Employee</th><th>On</th>{canConfigure ? <th /> : null}</tr></thead>
            <tbody>
              {links.map((l) => (
                <tr key={l.id}>
                  <td className="font-mono">{l.deviceUserId}</td>
                  <td>{l.employeeName} <span className="text-xs text-ink-soft">({l.employeeNumber})</span></td>
                  <td>{l.deviceName ?? "Every device"}</td>
                  {canConfigure ? <td><button type="button" className="btn btn-ghost btn-sm" onClick={() => void remove(l)}>Remove</button></td> : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

// ======================================================================== gateways

function GatewaysPanel({ gateways, onChanged }: { gateways: AttendanceGateway[]; onChanged: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [form, setForm] = useState({ name: "", gatewayIdentifier: "" });
  const [issued, setIssued] = useState<GatewayWithKey | null>(null);
  const [error, setError] = useState("");

  async function create() {
    setError("");
    try {
      setIssued(await authenticatedFetch((s) => createGateway(s, { name: form.name.trim(), gatewayIdentifier: form.gatewayIdentifier.trim() })));
      setForm({ name: "", gatewayIdentifier: "" });
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not register the gateway"));
    }
  }

  async function rotate(g: AttendanceGateway) {
    if (!window.confirm(`Issue a new key for ${g.name}? The current key stops working at once.`)) return;
    setError("");
    try {
      setIssued(await authenticatedFetch((s) => rotateGatewayKey(s, g.id)));
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not issue a new key"));
    }
  }

  async function revoke(g: AttendanceGateway) {
    if (!window.confirm(`Revoke ${g.name}? It can no longer send scans until you issue a new key.`)) return;
    setError("");
    try {
      await authenticatedFetch((s) => revokeGateway(s, g.id));
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not revoke"));
    }
  }

  return (
    <section className="panel grid gap-4">
      <div>
        <h2 className="heading-sm">Gateways</h2>
        <p className="text-sm text-ink-soft">A gateway is the small program on a computer at the restaurant that reads the terminals and sends scans here, keeping them safe while the internet is down.</p>
      </div>
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      <div className="grid gap-3 rounded-xl bg-sunken p-3 sm:grid-cols-[2fr_1.5fr_auto] sm:items-end">
        <Field label="Name"><input className="input" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} placeholder="Osu back-office PC" /></Field>
        <Field label="Gateway id"><input className="input font-mono" value={form.gatewayIdentifier} onChange={(e) => setForm((f) => ({ ...f, gatewayIdentifier: e.target.value }))} placeholder="GATEWAY-001" /></Field>
        <button type="button" className="btn btn-primary" disabled={!form.name.trim() || !form.gatewayIdentifier.trim()} onClick={() => void create()}>Register</button>
      </div>
      {gateways.length === 0 ? null : (
        <ul className="grid gap-2">
          {gateways.map((g) => (
            <li key={g.id} className="rounded-xl border border-line p-3">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">{g.name} <span className="font-mono text-xs text-ink-soft">{g.gatewayIdentifier}</span></p>
                  <p className="text-xs text-ink-soft">Key {g.keyPrefix}… · {g.deviceCount} device(s){g.softwareVersion ? ` · v${g.softwareVersion}` : ""}</p>
                  <p className="text-xs text-ink-muted">Last report {formatDateTime(g.lastHeartbeatAt)} · last sent {formatDateTime(g.lastSyncAt)} · {g.pendingEvents} waiting</p>
                  {g.lastError ? <p className="mt-1 text-xs text-danger-on">{g.lastError}</p> : null}
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <StatusPill status={g.status} />
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => void rotate(g)}>New key</button>
                  {g.status !== "REVOKED" ? <button type="button" className="btn btn-ghost btn-sm" onClick={() => void revoke(g)}>Revoke</button> : null}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      {issued ? (
        <Modal open title="Gateway key" onClose={() => setIssued(null)}
          footer={<div className="flex justify-end"><button type="button" className="btn btn-primary" onClick={() => setIssued(null)}>I have saved it</button></div>}>
          <div className="grid gap-3 text-sm">
            <p className="alert alert-warning"><Icon name="alert" size={18} className="mt-0.5 shrink-0" />This key is shown once. Put it in the gateway&apos;s configuration now; if it is lost, issue a new one.</p>
            <Field label="Key"><input className="input font-mono text-xs" readOnly value={issued.key} onFocus={(e) => e.currentTarget.select()} /></Field>
            <p>On the gateway computer:</p>
            <pre className="overflow-x-auto rounded-lg bg-sunken p-3 text-xs">{`GATEWAY_BACKEND_URL=${typeof window === "undefined" ? "" : window.location.origin.replace(":3000", ":8080")}
GATEWAY_ID=${issued.gateway.gatewayIdentifier}
GATEWAY_KEY=${issued.key}
java -jar attendance-gateway.jar`}</pre>
            <p className="text-ink-soft">See attendance-gateway/README.md for device logins and running it as a service.</p>
          </div>
        </Modal>
      ) : null}
    </section>
  );
}
