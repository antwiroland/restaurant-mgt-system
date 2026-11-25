import type { CourseStatus, DiningCourseRecord, StaffRole } from "@/lib/apiClient";

/** "Send now": dishes without a course go straight to the kitchen, as before course dining. */
export const SEND_NOW = "";

/** Staff wording for a course's status. */
export const COURSE_STATUS_LABEL: Record<CourseStatus, string> = {
  PENDING: "Waiting to be released",
  ON_HOLD: "On hold",
  RELEASED: "Sent to kitchen",
  PREPARING: "Cooking",
  READY: "Ready to serve",
  PARTIALLY_SERVED: "Partly served",
  SERVED: "Served",
  CANCELLED: "Cancelled",
};

/** Guest wording for a course's status (the QR page). */
export const GUEST_COURSE_STATUS_LABEL: Record<CourseStatus, string> = {
  PENDING: "Ordered · tap “Next course” when you're ready",
  ON_HOLD: "Waiting · our team will bring it soon",
  RELEASED: "Sent to the kitchen",
  PREPARING: "Being prepared",
  READY: "On its way to your table",
  PARTIALLY_SERVED: "Partly served",
  SERVED: "Served",
  CANCELLED: "Cancelled",
};

export const COURSE_STATUS_TONE: Record<CourseStatus, string> = {
  PENDING: "badge-warning",
  ON_HOLD: "badge-neutral",
  RELEASED: "badge-info",
  PREPARING: "badge-info",
  READY: "badge-success",
  PARTIALLY_SERVED: "badge-success",
  SERVED: "badge-neutral",
  CANCELLED: "badge-danger",
};

/** Courses that can still be released to the kitchen. */
export function awaitingRelease(status: CourseStatus): boolean {
  return status === "PENDING" || status === "ON_HOLD";
}

/** Active courses in serving order. */
export function orderedCourses(courses: DiningCourseRecord[]): DiningCourseRecord[] {
  return courses
    .filter((course) => course.active)
    .slice()
    .sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name));
}

/** The course a dish will go with: beverages never wait for a course. */
export function effectiveCourseId(courseId: string | undefined, beverage: boolean | undefined): string | undefined {
  if (beverage) return undefined;
  return courseId && courseId !== SEND_NOW ? courseId : undefined;
}

/** True when at least one line of the cart is held for a course (so the order goes through course dining). */
export function hasCourseLines(lines: { courseId?: string; beverage?: boolean }[]): boolean {
  return lines.some((line) => effectiveCourseId(line.courseId, line.beverage) !== undefined);
}

/**
 * Groups cart lines for display: "Now" first (dishes and drinks sent straight away), then each course in serving
 * order. Courses no longer offered fall back to "Now" so nothing is silently dropped.
 */
export function groupByCourse<T extends { courseId?: string; beverage?: boolean }>(
  lines: T[],
  courses: DiningCourseRecord[],
): { courseId?: string; title: string; lines: T[] }[] {
  const ordered = orderedCourses(courses);
  const known = new Set(ordered.map((course) => course.id));
  const now = lines.filter((line) => {
    const id = effectiveCourseId(line.courseId, line.beverage);
    return id === undefined || !known.has(id);
  });
  const groups: { courseId?: string; title: string; lines: T[] }[] = [];
  if (now.length > 0) groups.push({ title: "Now", lines: now });
  for (const course of ordered) {
    const courseLines = lines.filter((line) => effectiveCourseId(line.courseId, line.beverage) === course.id);
    if (courseLines.length > 0) groups.push({ courseId: course.id, title: course.name, lines: courseLines });
  }
  return groups;
}

/** The branch in a staff access token (JWT "branchId" claim), or null. Only used to pick a live-update topic. */
export function tokenBranchId(token: string | null | undefined): string | null {
  if (!token) return null;
  const part = token.split(".")[1];
  if (!part) return null;
  try {
    const base64 = part.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(part.length / 4) * 4, "=");
    const claims = JSON.parse(atob(base64)) as { branchId?: unknown };
    return typeof claims.branchId === "string" && claims.branchId ? claims.branchId : null;
  } catch {
    return null;
  }
}

/**
 * The live course-dining topic for a staff member: their branch's, if they are a manager or cashier tied to one;
 * otherwise the every-branch topic (admins, staff without a branch). Mirrors the server's topic rules.
 */
export function courseTopic(role: StaffRole | undefined, token: string | null | undefined): string {
  const branchId = role === "MANAGER" || role === "CASHIER" ? tokenBranchId(token) : null;
  return branchId ? `/topic/dining.courses.${branchId}` : "/topic/dining.courses";
}

export function minutesAgo(iso: string, now = Date.now()): string {
  const minutes = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60000));
  return minutes < 1 ? "just now" : `${minutes} min ago`;
}

/** KITCHEN, HOT_KITCHEN -> "Kitchen", "Hot kitchen". */
export function stationLabel(station: string | null | undefined): string {
  const value = (station ?? "KITCHEN").replace(/_/g, " ").toLowerCase();
  return value.charAt(0).toUpperCase() + value.slice(1);
}
