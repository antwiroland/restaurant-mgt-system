"use client";

import { Client } from "@stomp/stompjs";
import { useCallback, useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { Spinner } from "@/components/Spinner";
import { GUEST_COURSE_STATUS_LABEL, awaitingRelease } from "@/features/dining/dining";
import { REALTIME_URL } from "@/lib/realtime";
import { getPublicDining, requestNextCourse, type PublicDiningRecord } from "@/lib/apiClient";

type Notice = { tone: "success" | "info" | "warning"; text: string };

/**
 * Guest side of course dining on the table QR page: the party's courses and where each one is, plus "Request next
 * course", which tells the waiter and the cashier the table is ready. Guests never send a course to the kitchen
 * themselves; staff release it. The panel stays hidden until the party has ordered a course.
 */
export function DiningCoursesPanel({
  token,
  refreshKey = 0,
  sessionRef,
  onLoaded,
}: {
  token: string;
  /** Bump to reload (e.g. after the guest ordered). */
  refreshKey?: number;
  /** The guest's ordering session on this phone. */
  sessionRef?: string;
  onLoaded?: (dining: PublicDiningRecord) => void;
}) {
  const [dining, setDining] = useState<PublicDiningRecord | null>(null);
  const [sending, setSending] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const next = await getPublicDining(token);
      setDining(next);
      onLoaded?.(next);
    } catch {
      /* the rest of the page still works */
    }
  }, [token, onLoaded]);

  useEffect(() => {
    const first = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(first);
  }, [load, refreshKey]);

  useEffect(() => {
    // Live updates; on every (re)connect the panel reloads, so anything missed while offline is caught up.
    const client = new Client({
      brokerURL: REALTIME_URL,
      reconnectDelay: 5000,
      onConnect: () => {
        void load();
        client.subscribe(`/topic/public.tables.${token}.courses`, (message) => {
          void load();
          try {
            const event = JSON.parse(message.body) as { event?: string; courseName?: string };
            if (event.event === "COURSE_RELEASED") {
              setNotice({ tone: "success", text: `Your ${event.courseName ?? "next course"} has been sent to the kitchen.` });
            } else if (event.event === "COURSE_READY") {
              setNotice({ tone: "success", text: `Your ${event.courseName ?? "course"} is ready and on its way.` });
            }
          } catch {
            /* reload is enough */
          }
        });
        client.subscribe(`/topic/public.tables.${token}.orders`, () => void load());
      },
    });
    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [load, token]);

  async function askForNextCourse() {
    setSending(true);
    setError("");
    try {
      const result = await requestNextCourse(token, sessionRef);
      setNotice({
        tone: result.outcome === "REQUESTED" ? "success" : result.outcome === "ALREADY_PENDING" ? "info" : "warning",
        text: result.message,
      });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send the request");
    } finally {
      setSending(false);
    }
  }

  if (!dining || dining.mealCourses.length === 0) return null;

  const pending = dining.pendingRequest;
  const waitingCourses = dining.mealCourses.filter((course) => awaitingRelease(course.status));

  return (
    <section className="rounded-2xl border border-line bg-surface p-4 shadow-sm" aria-labelledby="your-courses">
      <div className="flex items-center justify-between gap-3">
        <h2 id="your-courses" className="font-display font-bold">Your courses</h2>
        {pending ? <span className="badge badge-warning">Next course requested</span> : null}
      </div>

      <ol className="mt-3 grid gap-3">
        {dining.mealCourses.map((course) => (
          <li key={course.id} className="rounded-xl border border-line-subtle p-3">
            <div className="flex items-center justify-between gap-2">
              <p className="font-semibold text-ink">{course.courseName}</p>
              <span className={`text-xs font-semibold ${course.status === "READY" ? "text-success-on" : "text-ink-soft"}`}>
                {GUEST_COURSE_STATUS_LABEL[course.status]}
              </span>
            </div>
            {course.items.length > 0 ? (
              <ul className="mt-1 text-sm text-ink-soft">
                {course.items.map((item, index) => (
                  <li key={`${course.id}-${index}`}>
                    {item.quantity} × {item.name}
                    {item.modifiers.length > 0 ? <span className="text-ink-muted"> · {item.modifiers.join(", ")}</span> : null}
                  </li>
                ))}
              </ul>
            ) : null}
          </li>
        ))}
      </ol>

      {notice ? (
        <p className={`alert alert-${notice.tone} mt-3`} role="status">
          <Icon name={notice.tone === "warning" ? "info" : "checkCircle"} size={18} className="mt-0.5 shrink-0" />
          {notice.text}
        </p>
      ) : null}
      {error ? <p className="field-error mt-2" role="alert">{error}</p> : null}

      {pending ? (
        <p className="mt-3 text-sm text-ink-soft" role="status">
          Your {pending.courseName ?? "next course"} is awaiting approval. Your request has been sent to the waiter and cashier.
        </p>
      ) : dining.canRequestNextCourse ? (
        <button
          type="button"
          className="btn btn-primary btn-md mt-3 w-full"
          disabled={sending}
          onClick={() => void askForNextCourse()}
        >
          {sending ? <Spinner className="text-current" /> : <Icon name="chef" size={18} />}
          {sending ? "Sending…" : `Request next course${dining.nextCourseName ? ` · ${dining.nextCourseName}` : ""}`}
        </button>
      ) : waitingCourses.length === 0 ? (
        <p className="mt-3 text-xs text-ink-muted">
          To have another course later, add dishes and choose the course in your order. You can order drinks any time.
        </p>
      ) : null}
    </section>
  );
}
