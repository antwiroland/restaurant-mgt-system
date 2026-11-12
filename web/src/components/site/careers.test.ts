import { describe, expect, it } from "vitest";
import { formatJobDate, paragraphs, requirementItems } from "./careers";

describe("careers text helpers", () => {
  it("lists requirements typed one per line, dropping bullet markers", () => {
    expect(requirementItems("- Food handler certificate\n• Weekend availability\n\n2) Two years on a busy line")).toEqual([
      "Food handler certificate",
      "Weekend availability",
      "Two years on a busy line",
    ]);
  });

  it("splits a single line of sentences into separate requirements", () => {
    expect(requirementItems("Six months' restaurant experience. Weekend availability.")).toEqual([
      "Six months' restaurant experience.",
      "Weekend availability.",
    ]);
    expect(requirementItems("Latte art a plus.")).toEqual(["Latte art a plus."]);
    expect(requirementItems(null)).toEqual([]);
  });

  it("keeps blank-line separated paragraphs of a description", () => {
    expect(paragraphs("Join the floor team.\n\nShifts are 8 hours.\nWeekends rotate.")).toEqual(["Join the floor team.", "Shifts are 8 hours.\nWeekends rotate."]);
    expect(paragraphs("   ")).toEqual([]);
  });

  it("formats a closing date as the calendar date, whatever the time zone", () => {
    expect(formatJobDate("2026-12-03")).toBe("3 Dec 2026");
    expect(formatJobDate(null)).toBeNull();
    expect(formatJobDate("not a date")).toBeNull();
  });
});
