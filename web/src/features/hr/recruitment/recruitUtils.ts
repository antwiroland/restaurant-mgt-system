import type { ApplicationStage, InterviewKind, InterviewOutcome, VacancyStatus } from "@/lib/hrApi";

export const stageLabel: Record<ApplicationStage, string> = {
  APPLIED: "Applied",
  SCREENING: "Screening",
  INTERVIEW: "Interview",
  TRIAL_SHIFT: "Trial shift",
  OFFER: "Offer",
  HIRED: "Hired",
  REJECTED: "Rejected",
  WITHDRAWN: "Withdrawn",
};

export const stageTone: Record<ApplicationStage, string> = {
  APPLIED: "badge-neutral",
  SCREENING: "badge-info",
  INTERVIEW: "badge-info",
  TRIAL_SHIFT: "badge-gold",
  OFFER: "badge-pending",
  HIRED: "badge-success",
  REJECTED: "badge-danger",
  WITHDRAWN: "badge-neutral",
};

export const vacancyTone: Record<VacancyStatus, string> = {
  DRAFT: "badge-neutral",
  OPEN: "badge-success",
  ON_HOLD: "badge-warning",
  FILLED: "badge-gold",
  CLOSED: "badge-neutral",
  CANCELLED: "badge-danger",
};

export const kindLabel: Record<InterviewKind, string> = { PHONE: "Phone call", IN_PERSON: "In person", VIDEO: "Video call", TRIAL_SHIFT: "Trial shift" };

export const outcomeTone: Record<InterviewOutcome, string> = {
  SCHEDULED: "badge-pending",
  PASSED: "badge-success",
  FAILED: "badge-danger",
  NO_SHOW: "badge-warning",
  CANCELLED: "badge-neutral",
};

/** Stages an application at {@code stage} may move to by hand (mirrors ApplicationStage.refusal on the server). */
export function nextStages(stage: ApplicationStage): ApplicationStage[] {
  const pipeline: ApplicationStage[] = ["APPLIED", "SCREENING", "INTERVIEW", "TRIAL_SHIFT", "OFFER"];
  if (stage === "HIRED") return [];
  if (stage === "REJECTED" || stage === "WITHDRAWN") return ["SCREENING"];
  return pipeline.slice(pipeline.indexOf(stage) + 1);
}
