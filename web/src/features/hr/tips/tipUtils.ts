import type { TipMethod, TipPoolStatus, TipSource } from "@/lib/hrApi";

export const sourceLabel: Record<TipSource, string> = { TIPS: "Tips", SERVICE_CHARGE: "Service charge" };

export const sourcePermission: Record<TipSource, string> = { TIPS: "hr.tips.manage", SERVICE_CHARGE: "hr.service_charge.manage" };

export const methodLabel: Record<TipMethod, string> = {
  HOURS: "shared by hours worked",
  POINTS: "shared by hours × position points",
  EQUAL: "shared equally",
};

export const poolTone: Record<TipPoolStatus, string> = {
  DRAFT: "badge-neutral",
  CALCULATED: "badge-info",
  APPROVED: "badge-pending",
  PAID: "badge-success",
  CANCELLED: "badge-danger",
};
