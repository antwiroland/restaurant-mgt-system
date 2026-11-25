import { describe, expect, it } from "vitest";
import type { DiningCourseRecord } from "@/lib/apiClient";
import {
  awaitingRelease,
  courseTopic,
  effectiveCourseId,
  groupByCourse,
  hasCourseLines,
  minutesAgo,
  orderedCourses,
  stationLabel,
  tokenBranchId,
} from "./dining";

const course = (id: string, name: string, displayOrder: number, active = true): DiningCourseRecord => ({
  id, name, displayOrder, active,
});
const courses = [course("dessert", "Dessert", 50), course("starter", "Starter", 10), course("main", "Main Course", 30), course("old", "Soup", 20, false)];

function jwt(claims: Record<string, unknown>): string {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `${encode({ alg: "HS256" })}.${encode(claims)}.signature`;
}

describe("course dining helpers", () => {
  it("orders active courses by their serving order", () => {
    expect(orderedCourses(courses).map((c) => c.name)).toEqual(["Starter", "Main Course", "Dessert"]);
  });

  it("never holds a beverage for a course", () => {
    expect(effectiveCourseId("main", true)).toBeUndefined();
    expect(effectiveCourseId("main", false)).toBe("main");
    expect(effectiveCourseId("", false)).toBeUndefined();
    expect(hasCourseLines([{ courseId: "main", beverage: true }])).toBe(false);
    expect(hasCourseLines([{ courseId: "main" }, { beverage: true }])).toBe(true);
  });

  it("groups a cart: sent-now first, then courses in serving order", () => {
    const lines = [
      { name: "Cake", courseId: "dessert" },
      { name: "Juice", courseId: "main", beverage: true },
      { name: "Soup", courseId: "starter" },
      { name: "Bread" },
      { name: "Gone", courseId: "old" }, // course no longer offered: sent now rather than lost
    ];
    const groups = groupByCourse(lines, courses);
    expect(groups.map((g) => g.title)).toEqual(["Now", "Starter", "Dessert"]);
    expect(groups[0].lines.map((l) => l.name)).toEqual(["Juice", "Bread", "Gone"]);
  });

  it("knows which courses can still be released", () => {
    expect(awaitingRelease("PENDING")).toBe(true);
    expect(awaitingRelease("ON_HOLD")).toBe(true);
    expect(awaitingRelease("RELEASED")).toBe(false);
  });

  it("subscribes branch staff to their branch and everyone else to all branches", () => {
    const branchToken = jwt({ sub: "u1", role: "CASHIER", branchId: "b-1" });
    expect(tokenBranchId(branchToken)).toBe("b-1");
    expect(courseTopic("CASHIER", branchToken)).toBe("/topic/dining.courses.b-1");
    expect(courseTopic("MANAGER", branchToken)).toBe("/topic/dining.courses.b-1");
    expect(courseTopic("ADMIN", branchToken)).toBe("/topic/dining.courses");
    expect(courseTopic("CASHIER", jwt({ role: "CASHIER" }))).toBe("/topic/dining.courses");
    expect(tokenBranchId("not-a-token")).toBeNull();
  });

  it("formats times and stations", () => {
    const now = Date.parse("2026-11-25T12:00:00Z");
    expect(minutesAgo("2026-11-25T11:59:40Z", now)).toBe("just now");
    expect(minutesAgo("2026-11-25T11:52:00Z", now)).toBe("8 min ago");
    expect(stationLabel("HOT_KITCHEN")).toBe("Hot kitchen");
    expect(stationLabel(null)).toBe("Kitchen");
  });
});
