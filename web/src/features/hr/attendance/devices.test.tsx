import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";

const session = { accessToken: "t", refreshToken: "r", expiresAtEpochMs: 0, user: { id: "u", name: "Ama", role: "ADMIN" } };
const permissions = vi.hoisted(() => ({ list: [] as string[] }));

vi.mock("@/components/SessionProvider", () => ({
  useStaffSession: () => ({ session, loading: false, authenticatedFetch: (run: (s: typeof session) => Promise<unknown>) => run(session) }),
}));
vi.mock("@/features/hr/HrAccessContext", () => ({
  useHrAccess: () => ({ access: { allBranches: true, branchIds: [] }, loading: false, can: (...codes: string[]) => codes.some((c) => permissions.list.includes(c)) }),
}));

const api = vi.hoisted(() => ({
  getDevicesOverview: vi.fn(),
  getDeviceScans: vi.fn(),
  applyDeviceScan: vi.fn(),
  retryDeviceScan: vi.fn(),
  dismissDeviceScan: vi.fn(),
  createGateway: vi.fn(),
}));
vi.mock("@/lib/attendanceDevicesApi", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/attendanceDevicesApi")>()), ...api }));

import AttendanceDevicesPage from "@/app/(staff)/hr/attendance/devices/page";

const overview = {
  gateways: [{ id: "g1", name: "Osu PC", gatewayIdentifier: "GATEWAY-001", keyPrefix: "rmgw_abc", status: "ONLINE", pendingEvents: 0, failedSyncAttempts: 0, deviceCount: 1 }],
  devices: [{ id: "d1", name: "Staff entrance", serialNumber: "DS-1", vendor: "HIKVISION", useHttps: false, directionMode: "DEVICE_REPORTED", active: true, status: "OFFLINE", branchName: "Osu", gatewayName: "Osu PC", statusMessage: "No route to host" }],
  reviewCounts: { NEEDS_REVIEW: 1, UNMATCHED: 0, REJECTED: 0 },
  linkedEmployees: 3,
  alerts: [{ severity: "danger", text: "Device Staff entrance is not connected: No route to host" }],
};

const scan = {
  id: "s1", deviceSerialNumber: "DS-1", deviceName: "Staff entrance", deviceEventId: "301", deviceUserId: "1001", employeeId: "e1",
  employeeName: "Kwesi Arthur", reportedType: "UNKNOWN", authenticationMethod: "FACE", deviceTimestamp: "2026-11-13T07:55:00Z",
  backendReceivedAt: "2026-11-13T07:56:00Z", status: "NEEDS_REVIEW",
  message: "The device did not say whether this was a clock-in, break or clock-out; choose the action",
};

describe("Attendance devices", () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset());
    api.getDevicesOverview.mockResolvedValue(overview);
    api.getDeviceScans.mockResolvedValue([scan]);
  });

  test("the overview shows problems in plain words", async () => {
    permissions.list = ["hr.attendance.view"];
    render(<AttendanceDevicesPage />);
    expect(await screen.findByText(/Staff entrance is not connected/)).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Review (1)" })).toBeInTheDocument();
    // without hr.settings.manage there is no Gateways tab
    expect(screen.queryByRole("tab", { name: "Gateways" })).not.toBeInTheDocument();
  });

  test("a manager records an unclear scan as a clock-in at the device's time", async () => {
    permissions.list = ["hr.attendance.view", "hr.attendance.correct"];
    api.applyDeviceScan.mockResolvedValue({ ...scan, status: "RESOLVED", appliedAction: "CLOCK_IN", message: "Kwesi clocked in" });
    render(<AttendanceDevicesPage />);
    await userEvent.click(await screen.findByRole("tab", { name: "Review (1)" }));
    expect(await screen.findByText("Kwesi Arthur")).toBeInTheDocument();
    expect(screen.getByText(/device said nothing/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Record as…" }));
    await userEvent.type(screen.getByLabelText("Note (optional)"), "Confirmed with Kwesi");
    await userEvent.click(screen.getByRole("button", { name: "Record" }));
    expect(api.applyDeviceScan).toHaveBeenCalledWith(session, "s1", "CLOCK_IN", "Confirmed with Kwesi");
    expect(await screen.findByText("Kwesi clocked in")).toBeInTheDocument();
  });

  test("a new gateway's key is shown once with setup instructions", async () => {
    permissions.list = ["hr.attendance.view", "hr.settings.manage"];
    api.createGateway.mockResolvedValue({ gateway: { ...overview.gateways[0], id: "g2", gatewayIdentifier: "GATEWAY-002" }, key: "rmgw_secretkey_ABC" });
    render(<AttendanceDevicesPage />);
    await userEvent.click(await screen.findByRole("tab", { name: "Gateways" }));
    await userEvent.type(screen.getByLabelText("Name"), "East Legon PC");
    await userEvent.type(screen.getByLabelText("Gateway id"), "GATEWAY-002");
    await userEvent.click(screen.getByRole("button", { name: "Register" }));
    await waitFor(() => expect(api.createGateway).toHaveBeenCalledWith(session, { name: "East Legon PC", gatewayIdentifier: "GATEWAY-002" }));
    expect(await screen.findByDisplayValue("rmgw_secretkey_ABC")).toBeInTheDocument();
    expect(screen.getByText(/shown once/)).toBeInTheDocument();
  });
});
