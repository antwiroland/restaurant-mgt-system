"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { useStaffSession } from "@/components/SessionProvider";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { EmptyState, PageHeader } from "@/components/ui";
import {
  COURSE_STATUS_LABEL,
  COURSE_STATUS_TONE,
  awaitingRelease,
  courseTopic,
  minutesAgo,
  orderedCourses,
  stationLabel,
} from "@/features/dining/dining";
import { playAlertSound } from "@/lib/alertSound";
import {
  changeDiningCourse,
  createDiningCourse,
  dismissCourseRequest,
  getCourseRequests,
  getDiningCourses,
  getOpenMeals,
  releaseCourseRequest,
  releaseDiningCourse,
  reorderDiningCourses,
  setDiningCourseActive,
  updateDiningCourse,
  type CourseAction,
  type CourseReleaseRequestRecord,
  type DiningCourseRecord,
  type DiningOrderCourseRecord,
  type DiningOrderRecord,
} from "@/lib/apiClient";
import { createRealtimeClient, useAccessTokenGetter } from "@/lib/realtime";

const POLL_INTERVAL_MS = 30000;

type PendingCancel = { course: DiningOrderCourseRecord; tableNumber: string };

/**
 * Course dining for the floor: tables asking for their next course, every open meal with its courses, and (for
 * managers) the list of courses guests can choose from. Waiters, cashiers and managers see the same live view, so
 * whoever releases a course, everyone else sees it go.
 */
export default function DiningPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const getToken = useAccessTokenGetter();
  const role = session?.user.role;
  const canManage = role === "ADMIN" || role === "MANAGER";
  const [meals, setMeals] = useState<DiningOrderRecord[] | null>(null);
  const [requests, setRequests] = useState<CourseReleaseRequestRecord[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [live, setLive] = useState(false);
  const [pendingCancel, setPendingCancel] = useState<PendingCancel | null>(null);
  const [cancelReason, setCancelReason] = useState("");
  const [, setTick] = useState(0);
  const knownRequests = useRef<Set<string> | null>(null);

  const load = useCallback(async () => {
    await authenticatedFetch(async (active) => {
      const [nextMeals, nextRequests] = await Promise.all([getOpenMeals(active), getCourseRequests(active, true)]);
      const ids = nextRequests.map((request) => request.id);
      if (knownRequests.current && ids.some((id) => !knownRequests.current!.has(id))) playAlertSound("request");
      knownRequests.current = new Set(ids);
      setMeals(nextMeals);
      setRequests(nextRequests);
    });
  }, [authenticatedFetch]);

  useEffect(() => {
    if (!session) return;
    load().catch((err) => setError(err instanceof Error ? err.message : "Could not load meals"));
    const timer = window.setInterval(() => load().catch(() => {}), POLL_INTERVAL_MS);
    const clock = window.setInterval(() => setTick((value) => value + 1), 30000);
    return () => {
      window.clearInterval(timer);
      window.clearInterval(clock);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reload when the signed-in user changes, not every token refresh
  }, [session?.user.id, load]);

  const topic = courseTopic(role, session?.accessToken);
  useEffect(() => {
    if (!session) return;
    const client = createRealtimeClient({
      onConnect: () => {
        setLive(true);
        void load().catch(() => {});
        client.subscribe(topic, () => void load().catch(() => {}));
        client.subscribe("/topic/orders.status", () => void load().catch(() => {}));
      },
      onWebSocketClose: () => setLive(false),
      onStompError: () => setLive(false),
    }, getToken);
    client.activate();
    return () => {
      setLive(false);
      void client.deactivate();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- one connection per user and topic
  }, [session?.user.id, topic, load, getToken]);

  async function run(id: string, action: () => Promise<string | void>) {
    setBusyId(id);
    setError("");
    setNotice("");
    try {
      const message = await action();
      if (message) setNotice(message);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
      await load().catch(() => {});
    } finally {
      setBusyId(null);
    }
  }

  const release = (course: DiningOrderCourseRecord) =>
    run(course.id, async () => (await authenticatedFetch((active) => releaseDiningCourse(active, course.id))).message);

  const change = (course: DiningOrderCourseRecord, action: CourseAction, reason?: string) =>
    run(course.id, async () => {
      await authenticatedFetch((active) => changeDiningCourse(active, course.id, action, reason));
    });

  const releaseRequest = (request: CourseReleaseRequestRecord) =>
    run(request.id, async () => (await authenticatedFetch((active) => releaseCourseRequest(active, request.id))).message);

  const dismissRequest = (request: CourseReleaseRequestRecord) =>
    run(request.id, async () => {
      await authenticatedFetch((active) => dismissCourseRequest(active, request.id));
    });

  if (loading || !session) {
    return (
      <div className="grid gap-4">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  return (
    <main className="grid gap-6">
      <PageHeader
        icon="chef"
        eyebrow="Service"
        title="Courses"
        description="Release each course to the kitchen when the table is ready for it. Drinks and dishes ordered for now go straight through."
        actions={
          <span className="flex items-center gap-2 text-sm font-semibold text-ink-soft" role="status">
            <span className={`h-2.5 w-2.5 rounded-full ${live ? "bg-success" : "animate-pulse bg-warning"}`} aria-hidden="true" />
            {live ? "Live" : "Reconnecting…"}
          </span>
        }
      />

      {error ? <p className="alert alert-danger" role="alert"><Icon name="alert" size={18} className="mt-0.5 shrink-0" />{error}</p> : null}
      {notice ? <p className="alert alert-success" role="status"><Icon name="checkCircle" size={18} className="mt-0.5 shrink-0" />{notice}</p> : null}

      <section aria-labelledby="course-requests" className="grid gap-3">
        <h2 id="course-requests" className="heading-sm flex items-center gap-2">
          <Icon name="bell" size={18} /> Tables ready for their next course
          {requests.length > 0 ? <span className="badge badge-warning">{requests.length}</span> : null}
        </h2>
        {requests.length === 0 ? (
          <p className="rounded-xl border border-dashed border-line px-4 py-6 text-center text-sm text-ink-soft">
            No table is waiting. Requests from guests appear here and on the cashier&apos;s screen.
          </p>
        ) : (
          <ul className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {requests.map((request) => (
              <li key={request.id} className="card grid gap-3 border-warning-border p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-display text-xl font-extrabold">Table {request.tableNumber ?? "?"}</p>
                    <p className="text-sm text-ink-soft">
                      {request.courseName ?? "Next course"} · asked {minutesAgo(request.requestedAt)}
                    </p>
                  </div>
                  <span className="font-mono text-xs text-ink-muted">#{request.diningOrderNumber}</span>
                </div>
                {request.items.length > 0 ? (
                  <ul className="text-sm text-ink">
                    {request.items.map((item) => (
                      <li key={item.itemId}>
                        {item.quantity} × {item.name}
                        {item.modifiers.length > 0 ? <span className="text-ink-muted"> · {item.modifiers.join(", ")}</span> : null}
                      </li>
                    ))}
                  </ul>
                ) : null}
                <div className="flex gap-2">
                  <button type="button" className="btn btn-primary btn-md flex-1" disabled={busyId === request.id}
                    onClick={() => void releaseRequest(request)}>
                    {busyId === request.id ? <Spinner className="text-current" /> : <Icon name="chef" size={18} />} Send to kitchen
                  </button>
                  <button type="button" className="btn btn-secondary btn-md" disabled={busyId === request.id}
                    onClick={() => void dismissRequest(request)}>
                    Not yet
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="open-meals" className="grid gap-3">
        <h2 id="open-meals" className="heading-sm flex items-center gap-2"><Icon name="table" size={18} /> Meals in progress</h2>
        {meals === null ? (
          <Skeleton className="h-40 w-full" />
        ) : meals.length === 0 ? (
          <EmptyState icon="table" title="No meals in courses right now"
            description="When a guest or a waiter orders dishes for later courses, the table shows up here." compact />
        ) : (
          <ul className="grid gap-4 lg:grid-cols-2">
            {meals.map((meal) => (
              <li key={meal.id} className="card grid gap-3 p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-display text-xl font-extrabold">Table {meal.tableNumber}</p>
                    <p className="text-xs text-ink-muted">#{meal.number} · seated {minutesAgo(meal.openedAt)}</p>
                  </div>
                  {meal.pendingRequest ? <span className="badge badge-warning">Asked for {meal.pendingRequest.courseName ?? "next course"}</span> : null}
                </div>
                {meal.sentNow.length > 0 ? (
                  <p className="text-xs text-ink-soft">
                    Sent straight away: {meal.sentNow.flatMap((batch) => batch.items).map((item) => `${item.quantity} × ${item.name}`).join(", ")}
                  </p>
                ) : null}
                <ol className="grid gap-2">
                  {meal.courses.map((course) => (
                    <CourseRow
                      key={course.id}
                      course={course}
                      next={meal.nextCourseId === course.id}
                      busy={busyId === course.id}
                      onRelease={() => void release(course)}
                      onAction={(action) => void change(course, action)}
                      onCancel={() => { setCancelReason(""); setPendingCancel({ course, tableNumber: meal.tableNumber }); }}
                    />
                  ))}
                </ol>
              </li>
            ))}
          </ul>
        )}
      </section>

      {canManage ? <CourseListEditor /> : null}

      <Modal
        open={pendingCancel !== null}
        title={pendingCancel ? `Cancel ${pendingCancel.course.courseName} for table ${pendingCancel.tableNumber}?` : "Cancel course"}
        onClose={() => setPendingCancel(null)}
        footer={
          <>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setPendingCancel(null)}>Keep it</button>
            <button type="button" className="btn btn-danger btn-sm" disabled={pendingCancel !== null && busyId === pendingCancel.course.id}
              onClick={() => {
                if (!pendingCancel) return;
                void change(pendingCancel.course, "cancel", cancelReason.trim() || undefined).then(() => setPendingCancel(null));
              }}>
              Cancel course
            </button>
          </>
        }
      >
        <div className="grid gap-3">
          <p className="text-sm text-ink-soft">Its dishes come off the bill and will not be cooked. This cannot be undone.</p>
          <label className="field">
            <span className="field-label">Reason <span className="font-normal text-ink-muted">(optional)</span></span>
            <input className="input" maxLength={200} value={cancelReason} onChange={(e) => setCancelReason(e.target.value)}
              placeholder="e.g. Guest changed their mind" />
          </label>
        </div>
      </Modal>
    </main>
  );
}

function CourseRow({
  course,
  next,
  busy,
  onRelease,
  onAction,
  onCancel,
}: {
  course: DiningOrderCourseRecord;
  next: boolean;
  busy: boolean;
  onRelease: () => void;
  onAction: (action: CourseAction) => void;
  onCancel: () => void;
}) {
  const items = course.batches.flatMap((batch) => batch.items);
  const stations = Array.from(new Set(course.batches.map((batch) => stationLabel(batch.kitchenStation))));
  const servable = course.status === "READY" || course.status === "PARTIALLY_SERVED" || course.status === "PREPARING" || course.status === "RELEASED";
  return (
    <li className={`rounded-xl border p-3 ${next ? "border-brand-soft bg-brand-subtle/40" : "border-line-subtle"}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-semibold text-ink">
          {course.courseName}
          {next ? <span className="ml-2 text-xs font-semibold text-brand">Next up</span> : null}
        </p>
        <span className={`badge ${COURSE_STATUS_TONE[course.status]}`}>{COURSE_STATUS_LABEL[course.status]}</span>
      </div>
      {items.length > 0 ? (
        <p className="mt-1 text-sm text-ink-soft">
          {items.map((item) => `${item.quantity} × ${item.name}`).join(", ")}
          {stations.length > 1 || (stations[0] && stations[0] !== "Kitchen") ? <span className="text-ink-muted"> · {stations.join(", ")}</span> : null}
        </p>
      ) : null}
      {course.releasedAt ? (
        <p className="mt-1 text-xs text-ink-muted">
          Sent {minutesAgo(course.releasedAt)}{course.releasedByName ? ` by ${course.releasedByName}` : ""}
        </p>
      ) : null}
      {course.status !== "SERVED" && course.status !== "CANCELLED" ? (
        <div className="mt-2 flex flex-wrap gap-2">
          {awaitingRelease(course.status) ? (
            <button type="button" className="btn btn-primary btn-sm" disabled={busy} onClick={onRelease}>
              {busy ? <Spinner className="text-current" /> : <Icon name="chef" size={16} />} Send to kitchen
            </button>
          ) : null}
          {course.status === "PENDING" ? (
            <button type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={() => onAction("hold")}>Hold</button>
          ) : null}
          {course.status === "ON_HOLD" ? (
            <button type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={() => onAction("resume")}>Resume</button>
          ) : null}
          {servable ? (
            <button type="button" className="btn btn-success btn-sm" disabled={busy} onClick={() => onAction("serve")}>
              <Icon name="checkCircle" size={16} /> Served
            </button>
          ) : null}
          {awaitingRelease(course.status) ? (
            <button type="button" className="btn btn-ghost btn-sm text-danger-on" disabled={busy} onClick={onCancel}>Cancel course</button>
          ) : null}
        </div>
      ) : null}
    </li>
  );
}

/** Managers: the courses guests and waiters can pick, in serving order. */
function CourseListEditor() {
  const { authenticatedFetch } = useStaffSession();
  const [courses, setCourses] = useState<DiningCourseRecord[] | null>(null);
  const [editing, setEditing] = useState<DiningCourseRecord | "new" | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    const next = await authenticatedFetch((active) => getDiningCourses(active, { includeInactive: true }));
    setCourses(next.slice().sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name)));
  }, [authenticatedFetch]);

  useEffect(() => {
    load().catch((err) => setError(err instanceof Error ? err.message : "Could not load courses"));
  }, [load]);

  async function act(action: () => Promise<unknown>) {
    setBusy(true);
    setError("");
    try {
      await action();
      await load();
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save");
      return false;
    } finally {
      setBusy(false);
    }
  }

  function openEditor(course: DiningCourseRecord | "new") {
    setEditing(course);
    setName(course === "new" ? "" : course.name);
    setDescription(course === "new" ? "" : course.description ?? "");
    setError("");
  }

  async function save() {
    if (!name.trim() || editing === null) return;
    const payload = { name: name.trim(), description: description.trim() || undefined };
    const ok = await act(() =>
      authenticatedFetch((active) =>
        editing === "new"
          ? createDiningCourse(active, { ...payload, displayOrder: ((courses?.at(-1)?.displayOrder ?? 0) + 10), active: true })
          : updateDiningCourse(active, editing.id, { ...payload, displayOrder: editing.displayOrder, active: editing.active, defaultStation: editing.defaultStation ?? undefined }),
      ),
    );
    if (ok) setEditing(null);
  }

  function move(index: number, delta: number) {
    if (!courses) return;
    const next = courses.slice();
    const [moved] = next.splice(index, 1);
    next.splice(index + delta, 0, moved);
    setCourses(next);
    void act(() => authenticatedFetch((active) => reorderDiningCourses(active, next.map((course) => course.id))));
  }

  const activeCount = courses ? orderedCourses(courses).length : 0;

  return (
    <section aria-labelledby="course-list" className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="course-list" className="heading-sm flex items-center gap-2"><Icon name="book" size={18} /> Course list</h2>
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => openEditor("new")}><Icon name="plus" size={16} /> Add course</button>
      </div>
      <p className="text-sm text-ink-soft">
        Guests and waiters choose from these, in this order. {activeCount === 0 ? "With none active, everything goes straight to the kitchen." : null}
      </p>
      {error && editing === null ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {courses === null ? (
        <Skeleton className="h-32 w-full" />
      ) : (
        <ol className="card divide-y divide-line-subtle">
          {courses.map((course, index) => (
            <li key={course.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className={`font-semibold ${course.active ? "text-ink" : "text-ink-muted line-through"}`}>{course.name}</p>
                {course.description ? <p className="text-xs text-ink-soft">{course.description}</p> : null}
              </div>
              {!course.active ? <span className="badge badge-neutral">Hidden</span> : null}
              <div className="flex items-center gap-1">
                <button type="button" className="btn btn-ghost btn-sm" disabled={busy || index === 0} onClick={() => move(index, -1)} aria-label={`Move ${course.name} earlier`}>
                  <Icon name="chevronLeft" size={16} className="rotate-90" />
                </button>
                <button type="button" className="btn btn-ghost btn-sm" disabled={busy || index === courses.length - 1} onClick={() => move(index, 1)} aria-label={`Move ${course.name} later`}>
                  <Icon name="chevronDown" size={16} />
                </button>
                <button type="button" className="btn btn-ghost btn-sm" disabled={busy} onClick={() => openEditor(course)} aria-label={`Edit ${course.name}`}>
                  <Icon name="edit" size={16} />
                </button>
                <button type="button" className="btn btn-secondary btn-sm" disabled={busy}
                  onClick={() => void act(() => authenticatedFetch((active) => setDiningCourseActive(active, course.id, !course.active)))}>
                  {course.active ? "Hide" : "Show"}
                </button>
              </div>
            </li>
          ))}
        </ol>
      )}

      <Modal
        open={editing !== null}
        title={editing === "new" ? "Add a course" : "Edit course"}
        onClose={() => setEditing(null)}
        footer={
          <>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditing(null)} disabled={busy}>Cancel</button>
            <button type="button" className="btn btn-primary btn-sm" onClick={() => void save()} disabled={busy || !name.trim()}>
              {busy ? <Spinner className="text-current" /> : null} Save
            </button>
          </>
        }
      >
        <div className="grid gap-4">
          <label className="field">
            <span className="field-label">Name</span>
            <input className="input" maxLength={80} value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Starter" />
          </label>
          <label className="field">
            <span className="field-label">Description <span className="font-normal text-ink-muted">(optional)</span></span>
            <input className="input" maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
          </label>
          {error ? <p className="field-error" role="alert">{error}</p> : null}
        </div>
      </Modal>
    </section>
  );
}
