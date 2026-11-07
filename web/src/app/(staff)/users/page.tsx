"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { Icon } from "@/components/Icon";
import { Spinner } from "@/components/Spinner";
import { PageHeader } from "@/components/ui";
import { roleLabel } from "@/lib/navigation";
import { hasStaffRole, useStaffSession } from "@/components/SessionProvider";
import { assignUserRole, getBranches, getUsers, registerUser, sendUserPasswordReset, setUserPin, type BranchRecord, type StaffRole, type UserRecord } from "@/lib/apiClient";

const STAFF_ROLES: StaffRole[] = ["ADMIN", "MANAGER", "CASHIER", "RIDER", "CUSTOMER"];

export default function UsersPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [users, setUsers] = useState<UserRecord[]>([]);
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [query, setQuery] = useState("");
  const [roleFilter, setRoleFilter] = useState<StaffRole | "ALL">("ALL");
  const [branchFilter, setBranchFilter] = useState("");
  const [pinByUserId, setPinByUserId] = useState<Record<string, string>>({});
  const [newUser, setNewUser] = useState({
    name: "",
    phone: "",
    email: "",
    password: "",
    role: "CASHIER" as StaffRole,
    branchId: "",
    pin: "",
  });
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [resettingId, setResettingId] = useState<string | null>(null);

  async function sendReset(user: UserRecord) {
    setResettingId(user.id);
    setError("");
    setMessage("");
    try {
      await authenticatedFetch((activeSession) => sendUserPasswordReset(activeSession, user.id));
      setMessage(`Reset link sent to ${user.name}${user.email ? " by email" : " by SMS"}.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send reset link");
    } finally {
      setResettingId(null);
    }
  }

  const canManage = hasStaffRole(session?.user.role, ["ADMIN"]);

  async function loadData() {
    if (!session) return;
    const [u, b] = await authenticatedFetch(async (activeSession) => {
      const [usersRes, branchesRes] = await Promise.all([getUsers(activeSession), getBranches(activeSession)]);
      return [usersRes, branchesRes] as const;
    });
    setUsers(u);
    setBranches(b);
  }

  useEffect(() => {
    if (!session || !canManage) return;
    loadData().catch((err) => setError(err instanceof Error ? err.message : "Could not load users"));
  }, [session, canManage]);

  const branchOptions = useMemo(() => branches.map((branch) => ({ id: branch.id, label: `${branch.code} - ${branch.name}` })), [branches]);
  const filteredUsers = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return users.filter((user) => {
      const matchesQuery = !normalizedQuery
        || user.name.toLowerCase().includes(normalizedQuery)
        || user.phone.toLowerCase().includes(normalizedQuery)
        || (user.email?.toLowerCase().includes(normalizedQuery) ?? false);
      const matchesRole = roleFilter === "ALL" || user.role === roleFilter;
      const matchesBranch = !branchFilter || (user.branchId ?? "") === branchFilter;
      return matchesQuery && matchesRole && matchesBranch;
    });
  }, [users, query, roleFilter, branchFilter]);

  async function updateRole(user: UserRecord, role: StaffRole, branchId?: string) {
    if (!session) return;
    try {
      await authenticatedFetch((activeSession) => assignUserRole(activeSession, user.id, { role, branchId: branchId || undefined }));
      await loadData();
      setMessage(`Updated role for ${user.name}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update role");
    }
  }

  async function applyPin(user: UserRecord) {
    if (!session) return;
    const pin = pinByUserId[user.id] ?? "";
    if (!/^\d{4}$/.test(pin)) {
      setError("PIN must be exactly 4 digits");
      return;
    }
    try {
      await authenticatedFetch((activeSession) => setUserPin(activeSession, user.id, pin));
      setPinByUserId((current) => ({ ...current, [user.id]: "" }));
      setMessage(`PIN updated for ${user.name}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not set PIN");
    }
  }

  async function createStaffUser() {
    if (!session) return;
    if (!newUser.name || !newUser.phone || !newUser.password) {
      setError("Name, phone and password are required");
      return;
    }
    try {
      setError("");
      const created = await registerUser({
        name: newUser.name.trim(),
        phone: newUser.phone.trim(),
        email: newUser.email.trim() || undefined,
        password: newUser.password,
      });

      await authenticatedFetch((activeSession) => assignUserRole(activeSession, created.id, {
        role: newUser.role,
        branchId: newUser.branchId || undefined,
      }));

      if (newUser.pin && /^\d{4}$/.test(newUser.pin)) {
        await authenticatedFetch((activeSession) => setUserPin(activeSession, created.id, newUser.pin));
      }

      setNewUser({ name: "", phone: "", email: "", password: "", role: "CASHIER", branchId: "", pin: "" });
      await loadData();
      setMessage(`Created ${created.name} as ${newUser.role}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create user");
    }
  }

  if (loading) return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-40" />{Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-12 w-full" />)}</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Users</p><h1 className="heading-lg mt-1">Staff access required</h1><Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link></section></main>;
  if (!canManage) return <main className="shell"><section className="panel"><p className="kicker">Users</p><h1 className="heading-lg mt-1">Admin only</h1></section></main>;

  return (
    <main className="shell grid gap-4">
      <PageHeader eyebrow="Admin" title="Staff & users" description="Create staff accounts, assign roles and branches, set manager PINs and send password resets." />
      <section className="panel">
        <div className="grid gap-2 md:grid-cols-3">
          <input className="input text-sm" placeholder="Search name, phone, or email" value={query} onChange={(e) => setQuery(e.target.value)} />
          <select className="select text-sm" value={roleFilter} onChange={(e) => setRoleFilter(e.target.value as StaffRole | "ALL")}>
            <option value="ALL">All roles</option>
            {STAFF_ROLES.map((role) => <option key={role} value={role}>{role}</option>)}
          </select>
          <select className="select text-sm" value={branchFilter} onChange={(e) => setBranchFilter(e.target.value)}>
            <option value="">All branches</option>
            {branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.label}</option>)}
          </select>
        </div>
        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
        {message ? <p className="mt-3 alert alert-success">{message}</p> : null}
      </section>

      <section className="panel">
        <h2 className="heading-md">Add a staff member</h2>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <label className="field"><span className="field-label">Full name</span>
            <input className="input" autoComplete="off" value={newUser.name} onChange={(e) => setNewUser((s) => ({ ...s, name: e.target.value }))} /></label>
          <label className="field"><span className="field-label">Phone</span>
            <input className="input" inputMode="tel" placeholder="+233…" value={newUser.phone} onChange={(e) => setNewUser((s) => ({ ...s, phone: e.target.value }))} /></label>
          <label className="field"><span className="field-label">Email <span className="font-normal text-ink-muted">(optional)</span></span>
            <input className="input" type="email" value={newUser.email} onChange={(e) => setNewUser((s) => ({ ...s, email: e.target.value }))} /></label>
          <label className="field"><span className="field-label">Temporary password</span>
            <input className="input" type="password" autoComplete="new-password" value={newUser.password} onChange={(e) => setNewUser((s) => ({ ...s, password: e.target.value }))} />
            <span className="field-hint">At least 6 characters.</span></label>
          <label className="field"><span className="field-label">Role</span>
            <select className="select" value={newUser.role} onChange={(e) => setNewUser((s) => ({ ...s, role: e.target.value as StaffRole }))}>
              {STAFF_ROLES.map((role) => <option key={role} value={role}>{roleLabel[role]}</option>)}
            </select></label>
          <label className="field"><span className="field-label">Branch</span>
            <select className="select" value={newUser.branchId} onChange={(e) => setNewUser((s) => ({ ...s, branchId: e.target.value }))}>
              <option value="">No branch</option>
              {branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.label}</option>)}
            </select></label>
          <label className="field"><span className="field-label">Manager PIN <span className="font-normal text-ink-muted">(optional)</span></span>
            <input className="input" inputMode="numeric" maxLength={4} placeholder="4 digits" value={newUser.pin} onChange={(e) => setNewUser((s) => ({ ...s, pin: e.target.value }))} /></label>
          <div className="flex items-end md:col-span-2">
            <button className="btn btn-primary btn-md" onClick={() => void createStaffUser()}><Icon name="plus" size={18} /> Create account</button>
          </div>
        </div>
      </section>

      <section className="panel">
        <ul className="grid gap-3">
          {filteredUsers.map((user) => (
            <li key={user.id} className="grid gap-3 rounded-xl border border-line p-4 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1.5fr)_auto] lg:items-center">
              <div className="flex min-w-0 items-center gap-3">
                <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-espresso font-display font-bold text-gold">{user.name.charAt(0).toUpperCase()}</span>
                <div className="min-w-0">
                  <p className="truncate font-semibold text-ink">{user.name}</p>
                  <p className="truncate text-xs text-ink-soft">{user.phone}{user.email ? ` · ${user.email}` : ""}</p>
                  <p className="mt-1 flex flex-wrap gap-1">
                    <span className={`badge ${user.role === "ADMIN" ? "badge-gold" : user.role === "MANAGER" ? "badge-info" : user.role === "CUSTOMER" ? "badge-neutral" : "badge-success"}`}>{roleLabel[user.role]}</span>
                    {user.branchName ? <span className="badge badge-neutral">{user.branchName}</span> : null}
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2">
                <label className="field"><span className="sr-only">Role for {user.name}</span>
                  <select className="select min-h-[40px] py-2 text-sm" defaultValue={user.role} onChange={(e) => void updateRole(user, e.target.value as StaffRole, user.branchId)}>
                    {STAFF_ROLES.map((role) => <option key={role} value={role}>{roleLabel[role]}</option>)}
                  </select></label>
                <label className="field"><span className="sr-only">Branch for {user.name}</span>
                  <select className="select min-h-[40px] py-2 text-sm" defaultValue={user.branchId ?? ""} onChange={(e) => void updateRole(user, user.role, e.target.value || undefined)}>
                    <option value="">No branch</option>
                    {branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.label}</option>)}
                  </select></label>
              </div>

              <div className="flex flex-wrap items-center gap-2 lg:justify-end">
                <label className="sr-only" htmlFor={`pin-${user.id}`}>New PIN for {user.name}</label>
                <input
                  id={`pin-${user.id}`}
                  className="input min-h-[40px] w-24 py-2 text-sm"
                  inputMode="numeric"
                  placeholder="PIN"
                  value={pinByUserId[user.id] ?? ""}
                  onChange={(e) => setPinByUserId((current) => ({ ...current, [user.id]: e.target.value }))}
                  maxLength={4}
                />
                <button className="btn btn-secondary btn-sm" onClick={() => void applyPin(user)}>Set PIN</button>
                <button className="btn btn-ghost btn-sm" disabled={resettingId === user.id} onClick={() => void sendReset(user)} title="Send a password reset link">
                  {resettingId === user.id ? <Spinner size={14} /> : <Icon name="mail" size={16} />} Reset password
                </button>
              </div>
            </li>
          ))}
          {filteredUsers.length === 0 ? <li className="rounded-xl border border-dashed border-line p-3 text-sm text-ink-soft">No users match the current filters.</li> : null}
        </ul>
      </section>
    </main>
  );
}

