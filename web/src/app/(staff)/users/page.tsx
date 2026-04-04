"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { hasStaffRole, useStaffSession } from "@/components/SessionProvider";
import { assignUserRole, getBranches, getUsers, registerUser, setUserPin, type BranchRecord, type StaffRole, type UserRecord } from "@/lib/apiClient";

const STAFF_ROLES: StaffRole[] = ["ADMIN", "MANAGER", "CASHIER", "CUSTOMER"];

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
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Users</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link></section></main>;
  if (!canManage) return <main className="shell"><section className="panel"><p className="kicker">Users</p><h1 className="text-2xl font-semibold">Admin only</h1></section></main>;

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <p className="kicker">Users</p>
        <h1 className="text-2xl font-semibold">User Management</h1>
        <div className="mt-3 grid gap-2 md:grid-cols-3">
          <input className="input text-sm" placeholder="Search name, phone, or email" value={query} onChange={(e) => setQuery(e.target.value)} />
          <select className="input text-sm" value={roleFilter} onChange={(e) => setRoleFilter(e.target.value as StaffRole | "ALL")}>
            <option value="ALL">All roles</option>
            {STAFF_ROLES.map((role) => <option key={role} value={role}>{role}</option>)}
          </select>
          <select className="input text-sm" value={branchFilter} onChange={(e) => setBranchFilter(e.target.value)}>
            <option value="">All branches</option>
            {branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.label}</option>)}
          </select>
        </div>
        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
        {message ? <p className="mt-3 alert alert-success">{message}</p> : null}
      </section>

      <section className="panel">
        <h2 className="text-lg font-semibold">Create Staff User</h2>
        <div className="mt-3 grid gap-2 md:grid-cols-3">
          <input className="input text-sm" placeholder="Name" value={newUser.name} onChange={(e) => setNewUser((s) => ({ ...s, name: e.target.value }))} />
          <input className="input text-sm" inputMode="tel" placeholder="Phone" value={newUser.phone} onChange={(e) => setNewUser((s) => ({ ...s, phone: e.target.value }))} />
          <input className="input text-sm" placeholder="Email (optional)" value={newUser.email} onChange={(e) => setNewUser((s) => ({ ...s, email: e.target.value }))} />
          <input className="input text-sm" placeholder="Password (min 6 chars)" type="password" value={newUser.password} onChange={(e) => setNewUser((s) => ({ ...s, password: e.target.value }))} />
          <select className="input text-sm" value={newUser.role} onChange={(e) => setNewUser((s) => ({ ...s, role: e.target.value as StaffRole }))}>
            {STAFF_ROLES.map((role) => <option key={role} value={role}>{role}</option>)}
          </select>
          <select className="input text-sm" value={newUser.branchId} onChange={(e) => setNewUser((s) => ({ ...s, branchId: e.target.value }))}>
            <option value="">No branch</option>
            {branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.label}</option>)}
          </select>
          <input className="input text-sm" inputMode="numeric" placeholder="Optional 4-digit PIN" maxLength={4} value={newUser.pin} onChange={(e) => setNewUser((s) => ({ ...s, pin: e.target.value }))} />
          <button className="btn btn-secondary btn-sm md:col-span-2" onClick={() => void createStaffUser()}>Create User</button>
        </div>
      </section>

      <section className="panel">
        <ul className="grid gap-3">
          {filteredUsers.map((user) => (
            <li key={user.id} className="input">
              <p className="font-semibold">{user.name} ({user.phone})</p>
              <p className="text-xs text-ink-soft">Current: {user.role}{user.branchName ? ` - ${user.branchName}` : ""}</p>

              <div className="mt-2 flex flex-wrap items-center gap-2">
                <select className="input text-sm" defaultValue={user.role} onChange={(e) => void updateRole(user, e.target.value as StaffRole, user.branchId)}>
                  {STAFF_ROLES.map((role) => <option key={role} value={role}>{role}</option>)}
                </select>
                <select className="input text-sm" defaultValue={user.branchId ?? ""} onChange={(e) => void updateRole(user, user.role, e.target.value || undefined)}>
                  <option value="">No branch</option>
                  {branchOptions.map((branch) => <option key={branch.id} value={branch.id}>{branch.label}</option>)}
                </select>
              </div>

              <div className="mt-2 flex items-center gap-2">
                <input
                  className="input text-sm"
                  inputMode="numeric"
                  placeholder="Set 4-digit PIN"
                  value={pinByUserId[user.id] ?? ""}
                  onChange={(e) => setPinByUserId((current) => ({ ...current, [user.id]: e.target.value }))}
                  maxLength={4}
                />
                <button className="btn btn-secondary btn-sm" onClick={() => void applyPin(user)}>Set PIN</button>
              </div>
            </li>
          ))}
          {filteredUsers.length === 0 ? <li className="rounded-xl border border-dashed border-line p-3 text-sm text-ink-soft">No users match the current filters.</li> : null}
        </ul>
      </section>
    </main>
  );
}

