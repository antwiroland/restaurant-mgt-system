import { apiRequest, type StaffSession } from "@/lib/apiClient";
import type { ClockAction } from "@/lib/hrApi";

/** Biometric attendance devices, local gateways, device user links and the scan review queue (/hr/attendance-devices). */

export type DirectionMode = "DEVICE_REPORTED" | "CLOCK_IN" | "CLOCK_OUT" | "BREAK_OUT" | "BREAK_IN";
export type Vendor = "HIKVISION" | "ZKTECO" | "OTHER";
export type DeviceEventStatus = "ACCEPTED" | "REPEAT_SCAN" | "NEEDS_REVIEW" | "UNMATCHED" | "REJECTED" | "RESOLVED" | "DISMISSED";

export type AttendanceGateway = {
  id: string; name: string; gatewayIdentifier: string; keyPrefix: string; branchId?: string | null; branchName?: string | null;
  status: "ONLINE" | "OFFLINE" | "NEVER_CONNECTED" | "REVOKED"; lastHeartbeatAt?: string | null; lastSyncAt?: string | null;
  pendingEvents: number; failedSyncAttempts: number; lastError?: string | null; softwareVersion?: string | null;
  revokedAt?: string | null; deviceCount: number;
};

export type GatewayWithKey = { gateway: AttendanceGateway; key: string };

export type AttendanceDevice = {
  id: string; name: string; serialNumber: string; vendor: Vendor; model?: string | null; ipAddress?: string | null; port?: number | null;
  useHttps: boolean; gatewayId?: string | null; gatewayName?: string | null; branchId?: string | null; branchName?: string | null;
  directionMode: DirectionMode; active: boolean; status: string; lastConnectedAt?: string | null; lastEventAt?: string | null;
  clockOffsetSeconds?: number | null; statusMessage?: string | null;
};

export type DeviceInput = {
  name: string; serialNumber: string; vendor: Vendor; model?: string; ipAddress?: string; port?: number | null; useHttps?: boolean;
  gatewayId?: string | null; branchId: string; directionMode: DirectionMode; active?: boolean;
};

export type DeviceUserLink = {
  id: string; employeeId: string; employeeName: string; employeeNumber: string; deviceId?: string | null; deviceName?: string | null;
  deviceUserId: string;
};

export type DeviceScan = {
  id: string; deviceSerialNumber: string; deviceName?: string | null; branchId?: string | null; deviceEventId: string; deviceUserId: string;
  employeeId?: string | null; employeeName?: string | null; reportedType: "CLOCK_IN" | "BREAK_OUT" | "BREAK_IN" | "CLOCK_OUT" | "UNKNOWN";
  appliedAction?: ClockAction | null; authenticationMethod?: string | null; deviceTimestamp: string; gatewayReceivedAt?: string | null;
  backendReceivedAt: string; status: DeviceEventStatus; message?: string | null; resolvedByNote?: string | null; resolvedAt?: string | null;
};

export type DevicesOverview = {
  gateways: AttendanceGateway[];
  devices: AttendanceDevice[];
  reviewCounts: Record<string, number>;
  linkedEmployees: number;
  alerts: { severity: "danger" | "warning" | "info"; text: string }[];
};

const auth = (s: StaffSession) => ({ token: s.accessToken });
const base = "/hr/attendance-devices";

export const getDevicesOverview = (s: StaffSession) => apiRequest<DevicesOverview>(`${base}/overview`, auth(s));

export const getGateways = (s: StaffSession) => apiRequest<AttendanceGateway[]>(`${base}/gateways`, auth(s));
export const createGateway = (s: StaffSession, body: { name: string; gatewayIdentifier: string; branchId?: string | null }) =>
  apiRequest<GatewayWithKey>(`${base}/gateways`, { ...auth(s), method: "POST", body });
export const rotateGatewayKey = (s: StaffSession, id: string) =>
  apiRequest<GatewayWithKey>(`${base}/gateways/${id}/rotate-key`, { ...auth(s), method: "POST", body: {} });
export const revokeGateway = (s: StaffSession, id: string) =>
  apiRequest<AttendanceGateway>(`${base}/gateways/${id}/revoke`, { ...auth(s), method: "POST", body: {} });

export const getDevices = (s: StaffSession) => apiRequest<AttendanceDevice[]>(`${base}/devices`, auth(s));
export const saveDevice = (s: StaffSession, body: DeviceInput, id?: string) =>
  apiRequest<AttendanceDevice>(id ? `${base}/devices/${id}` : `${base}/devices`, { ...auth(s), method: id ? "PUT" : "POST", body });

export const getDeviceUserLinks = (s: StaffSession) => apiRequest<DeviceUserLink[]>(`${base}/device-ids`, auth(s));
export const linkDeviceUser = (s: StaffSession, body: { employeeId: string; deviceUserId: string; deviceId?: string | null }) =>
  apiRequest<DeviceUserLink>(`${base}/device-ids`, { ...auth(s), method: "POST", body });
export const unlinkDeviceUser = (s: StaffSession, id: string) =>
  apiRequest<void>(`${base}/device-ids/${id}`, { ...auth(s), method: "DELETE" });

export const getDeviceScans = (s: StaffSession, filter: "review" | "all", from?: string, to?: string) => {
  const params = new URLSearchParams({ filter });
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  return apiRequest<DeviceScan[]>(`${base}/events?${params.toString()}`, auth(s));
};
export const applyDeviceScan = (s: StaffSession, id: string, action: ClockAction, note?: string) =>
  apiRequest<DeviceScan>(`${base}/events/${id}/apply`, { ...auth(s), method: "POST", body: { action, note } });
export const retryDeviceScan = (s: StaffSession, id: string) =>
  apiRequest<DeviceScan>(`${base}/events/${id}/retry`, { ...auth(s), method: "POST", body: {} });
export const dismissDeviceScan = (s: StaffSession, id: string, note: string) =>
  apiRequest<DeviceScan>(`${base}/events/${id}/dismiss`, { ...auth(s), method: "POST", body: { note } });

export const DIRECTION_LABEL: Record<DirectionMode, string> = {
  DEVICE_REPORTED: "As the device reports (review if unclear)",
  CLOCK_IN: "Every scan is a clock-in (entrance)",
  CLOCK_OUT: "Every scan is a clock-out (exit)",
  BREAK_OUT: "Every scan starts a break",
  BREAK_IN: "Every scan ends a break",
};

export const ACTION_LABEL: Record<ClockAction, string> = {
  CLOCK_IN: "Clock in",
  BREAK_START: "Start break",
  BREAK_END: "End break",
  CLOCK_OUT: "Clock out",
};

export const SCAN_STATUS: Record<DeviceEventStatus, { label: string; tone: string }> = {
  ACCEPTED: { label: "Recorded", tone: "badge-success" },
  REPEAT_SCAN: { label: "Double scan (ignored)", tone: "badge-neutral" },
  NEEDS_REVIEW: { label: "Needs a decision", tone: "badge-warning" },
  UNMATCHED: { label: "Unknown person", tone: "badge-warning" },
  REJECTED: { label: "Refused by the rules", tone: "badge-danger" },
  RESOLVED: { label: "Decided by a manager", tone: "badge-success" },
  DISMISSED: { label: "Not counted", tone: "badge-neutral" },
};
