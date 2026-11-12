import type { PublicVacancy } from "@/lib/publicServer";

export const employmentTypeLabel: Record<PublicVacancy["employmentType"], string> = {
  FULL_TIME: "Full-time",
  PART_TIME: "Part-time",
  CASUAL: "Casual",
  CONTRACT: "Contract",
  TEMPORARY: "Temporary",
  INTERN: "Internship",
  SEASONAL: "Seasonal",
};

/** "12 Nov 2026"; date-only values are read as calendar dates, not shifted by time zone. */
export function formatJobDate(value: string | null): string | null {
  if (!value) return null;
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T12:00:00Z`) : new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });
}

/** Paragraphs from free text: blank lines separate paragraphs. */
export function paragraphs(text: string | null): string[] {
  return (text ?? "").split(/\n\s*\n/).map((part) => part.trim()).filter(Boolean);
}

/**
 * Requirements as a list. HR types them one per line (optionally with "-", "*" or "•"),
 * or as sentences on one line; both become one item each.
 */
export function requirementItems(text: string | null): string[] {
  const lines = (text ?? "").split(/\r?\n/).map((line) => line.replace(/^\s*(?:[-*•]|\d+[.)])\s*/, "").trim()).filter(Boolean);
  if (lines.length !== 1) return lines;
  return lines[0].split(/(?<=[.;])\s+(?=[A-Z0-9])/).map((part) => part.trim()).filter(Boolean);
}
