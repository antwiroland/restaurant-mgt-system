"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { errorMessage } from "@/features/hr/ui";
import { getPermissionCatalogue, getUserGrants, saveUserGrants, type PermissionInfo, type UserGrants } from "@/lib/hrApi";

/** Admin-only: grant extra HR permissions to staff accounts (e.g. payroll rights for an accountant). */
export default function HrAccessPage() {
  const { session, authenticatedFetch } = useStaffSession();
  const { refresh } = useHrAccess();
  const [catalogue, setCatalogue] = useState<PermissionInfo[]>([]);
  const [users, setUsers] = useState<UserGrants[] | null>(null);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<UserGrants | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    setError("");
    try {
      await authenticatedFetch(async (s) => {
        const [c, u] = await Promise.all([getPermissionCatalogue(s), getUserGrants(s)]);
        setCatalogue(c);
        setUsers(u);
      });
    } catch (err) {
      setError(errorMessage(err, "Could not load HR access"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (session?.user.role === "ADMIN") void load();
  }, [load, session?.user.role]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (users ?? []).filter((u) => !q || u.name.toLowerCase().includes(q) || (u.branchName ?? "").toLowerCase().includes(q));
  }, [users, query]);

  const groups = useMemo(() => {
    const map = new Map<string, PermissionInfo[]>();
    catalogue.forEach((p) => {
      const area = p.code.split(".")[1] ?? "other";
      map.set(area, [...(map.get(area) ?? []), p]);
    });
    return [...map.entries()];
  }, [catalogue]);

  function open(user: UserGrants) {
    setEditing(user);
    setSelected(new Set(user.grantedPermissions));
    setSaveError("");
  }

  async function save() {
    if (!editing) return;
    setSaving(true);
    setSaveError("");
    try {
      const updated = await authenticatedFetch((s) => saveUserGrants(s, editing.userId, [...selected]));
      setUsers((list) => (list ?? []).map((u) => (u.userId === updated.userId ? updated : u)));
      setEditing(null);
      setNotice(`HR access updated for ${updated.name}. They will see the change the next time they load the app.`);
      void refresh();
    } catch (err) {
      setSaveError(errorMessage(err, "Could not save"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="grid gap-6">
      <PageHeader
        eyebrow="Human Resources"
        title="HR Access"
        icon="shield"
        description="Owners and admins hold every HR permission. Managers get operational HR access for their own branch. Grant anything extra per person here — for example payroll rights for an accountant, or every-branch access for central HR."
      />
      {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}
      {error ? <ErrorState message={error} onRetry={load} /> : null}

      <section className="panel grid gap-4">
        <input className="input max-w-sm" placeholder="Search staff or branch" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search staff" />
        {!users ? <Skeleton className="h-48 w-full" /> : filtered.length === 0 ? <EmptyState compact icon="users" title="No staff accounts" /> : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead><tr><th>Staff</th><th>Branch</th><th>From role</th><th>Extra grants</th><th className="num" /></tr></thead>
              <tbody>
                {filtered.map((u) => (
                  <tr key={u.userId}>
                    <td><span className="font-semibold">{u.name}</span><p className="text-xs text-ink-muted">{humanize(u.role)}</p></td>
                    <td>{u.branchName ?? <span className="text-ink-muted">No branch</span>}</td>
                    <td className="tabular">{u.rolePermissions.length}</td>
                    <td>
                      {u.grantedPermissions.length ? (
                        <div className="flex max-w-md flex-wrap gap-1">{u.grantedPermissions.map((p) => <span key={p} className="badge badge-info">{p.replace(/^hr\./, "")}</span>)}</div>
                      ) : <span className="text-ink-muted">None</span>}
                    </td>
                    <td className="num"><button type="button" className="btn btn-secondary btn-sm" onClick={() => open(u)}>Edit</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <Drawer
        open={!!editing}
        title={editing ? `HR access — ${editing.name}` : ""}
        onClose={() => setEditing(null)}
        footer={(
          <>
            <button type="button" className="btn btn-ghost btn-md" onClick={() => setEditing(null)}>Cancel</button>
            <button type="button" className="btn btn-primary btn-md" onClick={save} disabled={saving}>{saving ? "Saving…" : "Save"}</button>
          </>
        )}
      >
        {editing ? (
          <div className="grid gap-5">
            {saveError ? <p className="alert alert-danger" role="alert">{saveError}</p> : null}
            {!editing.branchId ? (
              <p className="alert alert-warning">This account has no branch. Without “Access HR data for every branch” it cannot see any branch&apos;s HR records.</p>
            ) : null}
            {groups.map(([area, perms]) => (
              <fieldset key={area} className="grid gap-2">
                <legend className="kicker mb-1">{humanize(area.toUpperCase())}</legend>
                {perms.map((p) => {
                  const fromRole = editing.rolePermissions.includes(p.code);
                  return (
                    <label key={p.code} className="flex items-start gap-3 rounded-lg p-2 hover:bg-muted">
                      <input
                        type="checkbox"
                        className="mt-1"
                        checked={fromRole || selected.has(p.code)}
                        disabled={fromRole}
                        onChange={(e) => setSelected((set) => {
                          const next = new Set(set);
                          if (e.target.checked) next.add(p.code); else next.delete(p.code);
                          return next;
                        })}
                      />
                      <span className="text-sm">
                        <span className="font-semibold">{p.description}</span>
                        <span className="block text-xs text-ink-muted">{p.code}{fromRole ? " · included with role" : ""}</span>
                      </span>
                    </label>
                  );
                })}
              </fieldset>
            ))}
          </div>
        ) : null}
      </Drawer>
    </div>
  );
}
