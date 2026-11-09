import { API_PREFIX, apiRequest, downloadFile, rawError, type DownloadedFile, type StaffSession } from "@/lib/apiClient";

// ---------------------------------------------------------------------------
// Types (mirror backend records in com.restaurantmanager.core.hr)
// ---------------------------------------------------------------------------

export type HrAccess = { permissions: string[]; allBranches: boolean; branchIds: string[]; employeeId: string | null };

export type StaffCategory = "MANAGEMENT" | "FRONT_OF_HOUSE" | "KITCHEN" | "BAR" | "OPERATIONS" | "SUPPORT" | "ADMINISTRATION" | "OTHER";
export type EmploymentType = "FULL_TIME" | "PART_TIME" | "CASUAL" | "CONTRACT" | "TEMPORARY" | "INTERN" | "SEASONAL";
export type EmploymentStatus = "ACTIVE" | "ON_LEAVE" | "SUSPENDED" | "RESIGNED" | "TERMINATED" | "RETIRED";
export type Gender = "FEMALE" | "MALE" | "OTHER" | "UNDISCLOSED";
export type ContractType = "PERMANENT" | "FIXED_TERM" | "PART_TIME" | "CASUAL" | "PROBATION" | "INTERNSHIP" | "SEASONAL";
export type ContractStatus = "DRAFT" | "ACTIVE" | "EXPIRED" | "ENDED" | "RENEWED";
export type SalaryType = "MONTHLY" | "WEEKLY" | "DAILY" | "HOURLY" | "CONTRACT_SUM" | "COMMISSION";
export type DocumentType =
  | "EMPLOYMENT_CONTRACT" | "IDENTIFICATION" | "CV" | "ACADEMIC_CERTIFICATE" | "FOOD_SAFETY_CERTIFICATE"
  | "MEDICAL_FITNESS" | "TRAINING_CERTIFICATE" | "PHOTO" | "OTHER";

export const STAFF_CATEGORIES: StaffCategory[] = ["MANAGEMENT", "FRONT_OF_HOUSE", "KITCHEN", "BAR", "OPERATIONS", "SUPPORT", "ADMINISTRATION", "OTHER"];
export const EMPLOYMENT_TYPES: EmploymentType[] = ["FULL_TIME", "PART_TIME", "CASUAL", "CONTRACT", "TEMPORARY", "INTERN", "SEASONAL"];
export const EMPLOYMENT_STATUSES: EmploymentStatus[] = ["ACTIVE", "ON_LEAVE", "SUSPENDED", "RESIGNED", "TERMINATED", "RETIRED"];
export const EXIT_STATUSES: EmploymentStatus[] = ["RESIGNED", "TERMINATED", "RETIRED"];
export const GENDERS: Gender[] = ["FEMALE", "MALE", "OTHER", "UNDISCLOSED"];
export const CONTRACT_TYPES: ContractType[] = ["PERMANENT", "FIXED_TERM", "PART_TIME", "CASUAL", "PROBATION", "INTERNSHIP", "SEASONAL"];
export const OPEN_ENDED_CONTRACTS: ContractType[] = ["PERMANENT", "PROBATION", "PART_TIME"];
export const SALARY_TYPES: SalaryType[] = ["MONTHLY", "WEEKLY", "DAILY", "HOURLY", "CONTRACT_SUM", "COMMISSION"];
export const DOCUMENT_TYPES: DocumentType[] = [
  "EMPLOYMENT_CONTRACT", "IDENTIFICATION", "CV", "ACADEMIC_CERTIFICATE", "FOOD_SAFETY_CERTIFICATE",
  "MEDICAL_FITNESS", "TRAINING_CERTIFICATE", "PHOTO", "OTHER",
];

export type Page<T> = { items: T[]; page: number; size: number; total: number };

export type Department = {
  id: string; code: string; name: string; category: StaffCategory; description?: string | null;
  branchId?: string | null; headEmployeeId?: string | null; headEmployeeName?: string | null; active: boolean;
};
export type DepartmentInput = {
  code: string; name: string; category: StaffCategory; description?: string; branchId?: string | null; headEmployeeId?: string | null;
};

export type Position = {
  id: string; code: string; title: string; departmentId?: string | null; departmentName?: string | null;
  category: StaffCategory; description?: string | null; requirements?: string | null; active: boolean;
};
export type PositionInput = {
  code: string; title: string; departmentId?: string | null; category?: StaffCategory | null; description?: string; requirements?: string;
};

export type EmployeeSummary = {
  id: string; employeeNumber: string; fullName: string; email?: string | null; phone?: string | null;
  departmentId?: string | null; departmentName?: string | null; positionId?: string | null; positionTitle?: string | null;
  employmentType: EmploymentType; employmentStatus: EmploymentStatus; hireDate: string;
  primaryBranchId?: string | null; primaryBranchName?: string | null; hasUserAccount: boolean;
};

export type EmergencyContact = { id?: string; name: string; relationship?: string | null; phone: string; email?: string | null };

export type EmployeeDetail = {
  summary: EmployeeSummary;
  firstName: string; middleName?: string | null; lastName?: string | null; gender?: Gender | null; dateOfBirth?: string | null;
  address?: string | null; exitDate?: string | null; exitReason?: string | null;
  reportingManagerId?: string | null; reportingManagerName?: string | null;
  qualifications?: string | null; experience?: string | null;
  emergencyContacts: EmergencyContact[];
  linkedUser?: { id: string; name: string; role: string; active: boolean } | null;
  /** False when the employee is visible only through a secondary/temporary assignment at one of your branches. */
  manageable: boolean;
  version: number; createdAt: string; updatedAt: string;
};

export type AssignmentType = "PRIMARY" | "SECONDARY" | "TEMPORARY";
export type BranchAssignment = {
  id: string; employeeId: string; branchId: string; branchName?: string | null; assignmentType: AssignmentType;
  positionId?: string | null; positionTitle?: string | null; startDate: string; endDate?: string | null; current: boolean;
  reason?: string | null; endReason?: string | null; createdAt: string;
};
export type BranchWorkforce = {
  branchId: string | null; code?: string | null; name: string; active: boolean; currentEmployees: number;
  activeCount: number; onLeave: number; suspended: number; coveringFromOtherBranches: number;
};

export type EmployeeInput = {
  employeeNumber?: string; userId?: string | null;
  firstName: string; middleName?: string; lastName?: string; gender?: Gender | null; dateOfBirth?: string | null;
  email?: string; phone?: string; address?: string;
  departmentId?: string | null; positionId?: string | null; employmentType: EmploymentType; hireDate: string;
  reportingManagerId?: string | null; primaryBranchId?: string | null;
  qualifications?: string; experience?: string; emergencyContacts: EmergencyContact[]; version?: number;
};

export type EmployeeFilters = {
  q?: string; branchId?: string; departmentId?: string; positionId?: string;
  status?: EmploymentStatus | ""; type?: EmploymentType | ""; includeExited?: boolean;
  page?: number; size?: number; sort?: string;
};

export type LinkableUser = { id: string; name: string; phone: string; email?: string | null; role: string; branchId?: string | null };
export type ImportResult = { created: number; employeeIds: string[]; skipped: { userId: string; name: string; reason: string }[] };
export type AuditEntry = { id: string; action: string; actorName?: string | null; metadata?: string | null; createdAt: string };

export type Contract = {
  id: string; contractNumber: string; employeeId: string; employeeName?: string | null; contractType: ContractType; status: ContractStatus;
  startDate: string; endDate?: string | null; probationEndDate?: string | null;
  salaryType?: SalaryType | null; salaryAmount?: number | null; currency?: string | null; payVisible: boolean;
  weeklyHours?: number | null; terms?: string | null; branchId?: string | null; previousContractId?: string | null;
  documentId?: string | null; endReason?: string | null; version: number; createdAt: string;
};
export type ContractInput = {
  contractType: ContractType; startDate: string; endDate?: string | null; probationEndDate?: string | null;
  salaryType?: SalaryType | null; salaryAmount?: string | null; currency?: string; weeklyHours?: string | null;
  terms?: string; branchId?: string | null; documentId?: string | null;
};

export type EmployeeDocument = {
  id: string; employeeId: string; documentType: DocumentType; title: string; fileName: string; contentType: string;
  sizeBytes: number; expiresOn?: string | null; notes?: string | null; archived: boolean; uploadedAt: string;
};

export type PermissionInfo = { code: string; description: string; managerDefault: boolean };
export type UserGrants = {
  userId: string; name: string; role: string; branchId?: string | null; branchName?: string | null;
  rolePermissions: string[]; grantedPermissions: string[]; extraBranchIds: string[];
};

// ---------------------------------------------------------------------------
// Calls
// ---------------------------------------------------------------------------

const auth = (session: StaffSession) => ({ token: session.accessToken });

function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "" && value !== false) search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : "";
}

export const getHrAccess = (s: StaffSession) => apiRequest<HrAccess>("/hr/me/access", auth(s));
export const getMyProfile = (s: StaffSession) => apiRequest<EmployeeDetail>("/hr/me/profile", auth(s));

export const getDepartments = (s: StaffSession, includeArchived = false) =>
  apiRequest<Department[]>(`/hr/departments${query({ includeArchived })}`, auth(s));
export const saveDepartment = (s: StaffSession, input: DepartmentInput, id?: string) =>
  apiRequest<Department>(id ? `/hr/departments/${id}` : "/hr/departments", { ...auth(s), method: id ? "PUT" : "POST", body: input });
export const setDepartmentActive = (s: StaffSession, id: string, active: boolean) =>
  apiRequest<Department>(`/hr/departments/${id}/${active ? "restore" : "archive"}`, { ...auth(s), method: "POST" });

export const getPositions = (s: StaffSession, includeArchived = false) =>
  apiRequest<Position[]>(`/hr/positions${query({ includeArchived })}`, auth(s));
export const savePosition = (s: StaffSession, input: PositionInput, id?: string) =>
  apiRequest<Position>(id ? `/hr/positions/${id}` : "/hr/positions", { ...auth(s), method: id ? "PUT" : "POST", body: input });
export const setPositionActive = (s: StaffSession, id: string, active: boolean) =>
  apiRequest<Position>(`/hr/positions/${id}/${active ? "restore" : "archive"}`, { ...auth(s), method: "POST" });

export const listEmployees = (s: StaffSession, filters: EmployeeFilters) =>
  apiRequest<Page<EmployeeSummary>>(`/hr/employees${query(filters)}`, auth(s));
export const getEmployee = (s: StaffSession, id: string) => apiRequest<EmployeeDetail>(`/hr/employees/${id}`, auth(s));
export const createEmployee = (s: StaffSession, input: EmployeeInput) =>
  apiRequest<EmployeeDetail>("/hr/employees", { ...auth(s), method: "POST", body: input });
export const updateEmployee = (s: StaffSession, id: string, input: EmployeeInput) =>
  apiRequest<EmployeeDetail>(`/hr/employees/${id}`, { ...auth(s), method: "PUT", body: input });
export const changeEmployeeStatus = (s: StaffSession, id: string, body: { status: EmploymentStatus; effectiveDate?: string; reason?: string }) =>
  apiRequest<EmployeeDetail>(`/hr/employees/${id}/status`, { ...auth(s), method: "POST", body });
export const linkEmployeeUser = (s: StaffSession, id: string, userId: string) =>
  apiRequest<EmployeeDetail>(`/hr/employees/${id}/link-user`, { ...auth(s), method: "POST", body: { userId } });
export const unlinkEmployeeUser = (s: StaffSession, id: string) =>
  apiRequest<EmployeeDetail>(`/hr/employees/${id}/link-user`, { ...auth(s), method: "DELETE" });
export const getLinkableUsers = (s: StaffSession) => apiRequest<LinkableUser[]>("/hr/employees/linkable-users", auth(s));
export const importStaffUsers = (s: StaffSession, body: { employmentType: EmploymentType; hireDate: string }) =>
  apiRequest<ImportResult>("/hr/employees/import-users", { ...auth(s), method: "POST", body });
export const getEmployeeAudit = (s: StaffSession, id: string) => apiRequest<AuditEntry[]>(`/hr/employees/${id}/audit`, auth(s));

export const getContracts = (s: StaffSession, employeeId: string) => apiRequest<Contract[]>(`/hr/employees/${employeeId}/contracts`, auth(s));
export const getContractsDue = (s: StaffSession, days = 30) => apiRequest<Contract[]>(`/hr/contracts/due${query({ days })}`, auth(s));
export const createContract = (s: StaffSession, employeeId: string, input: ContractInput) =>
  apiRequest<Contract>(`/hr/employees/${employeeId}/contracts`, { ...auth(s), method: "POST", body: input });
export const updateContract = (s: StaffSession, id: string, input: ContractInput) =>
  apiRequest<Contract>(`/hr/contracts/${id}`, { ...auth(s), method: "PUT", body: input });
export const renewContract = (s: StaffSession, id: string, input: ContractInput) =>
  apiRequest<Contract>(`/hr/contracts/${id}/renew`, { ...auth(s), method: "POST", body: input });
export const activateContract = (s: StaffSession, id: string) =>
  apiRequest<Contract>(`/hr/contracts/${id}/activate`, { ...auth(s), method: "POST" });
export const endContract = (s: StaffSession, id: string, body: { endDate?: string; reason: string }) =>
  apiRequest<Contract>(`/hr/contracts/${id}/end`, { ...auth(s), method: "POST", body });

export const getDocuments = (s: StaffSession, employeeId: string, includeArchived = false) =>
  apiRequest<EmployeeDocument[]>(`/hr/employees/${employeeId}/documents${query({ includeArchived })}`, auth(s));
export const archiveDocument = (s: StaffSession, id: string) =>
  apiRequest<EmployeeDocument>(`/hr/documents/${id}/archive`, { ...auth(s), method: "POST" });
export const downloadDocument = (s: StaffSession, id: string, fallbackName: string): Promise<DownloadedFile> =>
  downloadFile(s, `/hr/documents/${id}/download`, fallbackName);

export async function uploadDocument(
  s: StaffSession,
  employeeId: string,
  input: { file: File; title: string; documentType: DocumentType; expiresOn?: string; notes?: string },
): Promise<EmployeeDocument> {
  const form = new FormData();
  form.append("file", input.file);
  const params = query({ title: input.title, documentType: input.documentType, expiresOn: input.expiresOn, notes: input.notes });
  const response = await fetch(`${API_PREFIX}/hr/employees/${employeeId}/documents${params}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${s.accessToken}` },
    body: form,
    cache: "no-store",
  });
  if (!response.ok) throw await rawError(response, "Upload failed");
  return (await response.json()) as EmployeeDocument;
}

export const getPermissionCatalogue = (s: StaffSession) => apiRequest<PermissionInfo[]>("/hr/access/permissions", auth(s));
export const getUserGrants = (s: StaffSession) => apiRequest<UserGrants[]>("/hr/access/grants", auth(s));
export const saveUserGrants = (s: StaffSession, userId: string, permissions: string[]) =>
  apiRequest<UserGrants>(`/hr/access/grants/${userId}`, { ...auth(s), method: "PUT", body: { permissions } });

export const saveUserBranches = (s: StaffSession, userId: string, branchIds: string[]) =>
  apiRequest<UserGrants>(`/hr/access/branches/${userId}`, { ...auth(s), method: "PUT", body: { branchIds } });

export const getAssignments = (s: StaffSession, employeeId: string) =>
  apiRequest<BranchAssignment[]>(`/hr/employees/${employeeId}/assignments`, auth(s));
export const assignBranch = (
  s: StaffSession,
  employeeId: string,
  body: { branchId: string; assignmentType: Exclude<AssignmentType, "PRIMARY">; positionId?: string | null; startDate?: string; endDate?: string | null; reason?: string },
) => apiRequest<BranchAssignment>(`/hr/employees/${employeeId}/assignments`, { ...auth(s), method: "POST", body });
export const endAssignment = (s: StaffSession, id: string, body: { endDate?: string; reason?: string }) =>
  apiRequest<BranchAssignment>(`/hr/assignments/${id}/end`, { ...auth(s), method: "POST", body });
export const transferEmployee = (s: StaffSession, employeeId: string, body: { toBranchId: string | null; effectiveDate?: string; reason?: string }) =>
  apiRequest<EmployeeDetail>(`/hr/employees/${employeeId}/transfer`, { ...auth(s), method: "POST", body });
export const getBranchWorkforce = (s: StaffSession) => apiRequest<BranchWorkforce[]>("/hr/branches/workforce", auth(s));

// ---------------------------------------------------------------------------
// Scheduling (Phase 3)
// ---------------------------------------------------------------------------

export type ScheduleStatus = "DRAFT" | "PUBLISHED" | "CANCELLED";
export type ShiftType = "MORNING" | "AFTERNOON" | "EVENING" | "NIGHT" | "SPLIT" | "CUSTOM";
export const SHIFT_TYPES: ShiftType[] = ["MORNING", "AFTERNOON", "EVENING", "NIGHT", "SPLIT", "CUSTOM"];
export const WEEK_DAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"] as const;
export type WeekDay = (typeof WEEK_DAYS)[number];

export type ScheduleIssue = { code: string; severity: "BLOCKING" | "WARNING"; message: string };
export type SchedulingPolicy = { minRestMinutes: number; maxShiftMinutes: number; maxWeeklyMinutes: number; source: string };

export type Shift = {
  id: string; employeeId?: string | null; employeeName?: string | null; branchId: string; startsAt: string; endsAt: string;
  breakMinutes: number; paidMinutes: number; status: ScheduleStatus; templateId?: string | null; templateName?: string | null;
  departmentId?: string | null; positionId?: string | null; positionTitle?: string | null; notes?: string | null;
  publishedAt?: string | null; cancelReason?: string | null; version: number;
};
export type BoardEmployee = { id: string; name: string; positionTitle?: string | null; status: string; homeBranch: boolean };
export type CoverageCell = {
  date: string; templateId: string; templateName: string; minStaff: number; maxStaff?: number | null;
  assigned: number; open: number; state: "OK" | "UNDERSTAFFED" | "OVERSTAFFED";
};
export type ScheduleBoard = {
  branchId: string; from: string; to: string; shifts: Shift[]; employees: BoardEmployee[]; coverage: CoverageCell[]; policy: SchedulingPolicy;
};
export type ShiftInput = {
  employeeId?: string | null; branchId: string; templateId?: string | null; date?: string | null;
  startsAt?: string | null; endsAt?: string | null; breakMinutes?: number | null; positionId?: string | null;
  notes?: string; acknowledgeWarnings?: boolean; version?: number;
};
export type ShiftTemplate = {
  id: string; name: string; shiftType: ShiftType; branchId?: string | null; departmentId?: string | null; positionId?: string | null;
  startTime: string; endTime: string; overnight: boolean; breakMinutes: number; days: WeekDay[]; minStaff: number;
  maxStaff?: number | null; active: boolean;
};
export type ShiftTemplateInput = {
  name: string; shiftType: ShiftType; branchId?: string | null; departmentId?: string | null; positionId?: string | null;
  startTime: string; endTime: string; breakMinutes: number; days: WeekDay[]; minStaff: number; maxStaff?: number | null;
};
export type AvailabilityKind = "AVAILABLE" | "UNAVAILABLE" | "PREFERRED";
export type Availability = {
  id: string; employeeId: string; kind: AvailabilityKind; dayOfWeek?: WeekDay | null; specificDate?: string | null;
  startTime?: string | null; endTime?: string | null; effectiveFrom?: string | null; effectiveTo?: string | null;
  note?: string | null; createdAt: string;
};
export type AvailabilityInput = {
  kind: AvailabilityKind; dayOfWeek?: WeekDay | null; specificDate?: string | null; startTime?: string | null; endTime?: string | null;
  effectiveFrom?: string | null; effectiveTo?: string | null; note?: string;
};
export type SwapStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
export type SwapRequest = {
  id: string; scheduleId: string; branchId?: string | null; startsAt?: string | null; endsAt?: string | null;
  requesterEmployeeId: string; requesterName?: string | null; colleagueEmployeeId: string; colleagueName?: string | null;
  colleagueScheduleId?: string | null; colleagueStartsAt?: string | null; colleagueEndsAt?: string | null;
  reason: string; status: SwapStatus; decisionNote?: string | null; decidedAt?: string | null; createdAt: string;
};
export type SwapOption = {
  employeeId: string; name: string; positionTitle?: string | null;
  shifts: { id: string; branchId: string; startsAt: string; endsAt: string }[];
};

/** Thrown when the API refuses a schedule change with individual issues (409 blocking, 422 warnings to acknowledge). */
export class ScheduleIssuesError extends Error {
  readonly issues: ScheduleIssue[];
  readonly blocking: boolean;

  constructor(message: string, issues: ScheduleIssue[], blocking: boolean) {
    super(message);
    this.name = "ScheduleIssuesError";
    this.issues = issues;
    this.blocking = blocking;
  }
}

async function scheduleCall<T>(s: StaffSession, path: string, method: "POST" | "PUT", body: unknown): Promise<T> {
  const response = await fetch(`${API_PREFIX}${path}`, {
    method,
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${s.accessToken}` },
    body: JSON.stringify(body),
    cache: "no-store",
  });
  if (response.ok) return (await response.json()) as T;
  if (response.status === 409 || response.status === 422) {
    const payload = (await response.json().catch(() => null)) as { message?: string; issues?: ScheduleIssue[]; blocking?: boolean } | null;
    if (payload?.issues) throw new ScheduleIssuesError(payload.message ?? "Schedule problem", payload.issues, !!payload.blocking);
    throw new Error(payload?.message ?? `Request failed with status ${response.status}`);
  }
  throw await rawError(response, "Request failed");
}

export const getScheduleBoard = (s: StaffSession, branchId: string, from: string, to: string) =>
  apiRequest<ScheduleBoard>(`/hr/schedules${query({ branchId, from, to })}`, auth(s));
export const saveShift = (s: StaffSession, input: ShiftInput, id?: string) =>
  scheduleCall<{ shift: Shift; acknowledgedWarnings: ScheduleIssue[] }>(s, id ? `/hr/schedules/${id}` : "/hr/schedules", id ? "PUT" : "POST", input);
export const cancelShift = (s: StaffSession, id: string, reason?: string) =>
  apiRequest<Shift>(`/hr/schedules/${id}/cancel`, { ...auth(s), method: "POST", body: { reason } });
export const deleteDraftShift = (s: StaffSession, id: string) => apiRequest<void>(`/hr/schedules/${id}`, { ...auth(s), method: "DELETE" });
export const publishSchedule = (s: StaffSession, branchId: string, from: string, to: string) =>
  scheduleCall<{ published: number; openShifts: number; employeesNotified: number }>(s, "/hr/schedules/publish", "POST", { branchId, from, to });

export const getShiftTemplates = (s: StaffSession, branchId?: string, includeArchived = false) =>
  apiRequest<ShiftTemplate[]>(`/hr/shift-templates${query({ branchId, includeArchived })}`, auth(s));
export const saveShiftTemplate = (s: StaffSession, input: ShiftTemplateInput, id?: string) =>
  apiRequest<ShiftTemplate>(id ? `/hr/shift-templates/${id}` : "/hr/shift-templates", { ...auth(s), method: id ? "PUT" : "POST", body: input });
export const setShiftTemplateActive = (s: StaffSession, id: string, active: boolean) =>
  apiRequest<ShiftTemplate>(`/hr/shift-templates/${id}/${active ? "restore" : "archive"}`, { ...auth(s), method: "POST" });
export const getSchedulingPolicy = (s: StaffSession, branchId?: string) =>
  apiRequest<SchedulingPolicy>(`/hr/scheduling-policy${query({ branchId })}`, auth(s));
export const saveSchedulingPolicy = (
  s: StaffSession,
  body: { branchId?: string | null; minRestMinutes: number; maxShiftMinutes: number; maxWeeklyMinutes: number },
) => apiRequest<SchedulingPolicy>("/hr/scheduling-policy", { ...auth(s), method: "PUT", body });

export const getEmployeeAvailability = (s: StaffSession, employeeId: string) =>
  apiRequest<Availability[]>(`/hr/employees/${employeeId}/availability`, auth(s));
export const getMyAvailability = (s: StaffSession) => apiRequest<Availability[]>("/hr/me/availability", auth(s));
export const addMyAvailability = (s: StaffSession, input: AvailabilityInput) =>
  apiRequest<Availability>("/hr/me/availability", { ...auth(s), method: "POST", body: input });
export const deleteMyAvailability = (s: StaffSession, id: string) => apiRequest<void>(`/hr/me/availability/${id}`, { ...auth(s), method: "DELETE" });

export const getMyShifts = (s: StaffSession, from: string, to: string) => apiRequest<Shift[]>(`/hr/me/shifts${query({ from, to })}`, auth(s));
export const getMySwaps = (s: StaffSession) => apiRequest<SwapRequest[]>("/hr/me/swaps", auth(s));
export const getSwapOptions = (s: StaffSession, scheduleId: string) => apiRequest<SwapOption[]>(`/hr/me/swap-options${query({ scheduleId })}`, auth(s));
export const requestSwap = (s: StaffSession, body: { scheduleId: string; colleagueEmployeeId: string; colleagueScheduleId?: string | null; reason: string }) =>
  scheduleCall<SwapRequest>(s, "/hr/me/swaps", "POST", body);
export const cancelMySwap = (s: StaffSession, id: string) => apiRequest<SwapRequest>(`/hr/me/swaps/${id}/cancel`, { ...auth(s), method: "POST" });
export const getSwapRequests = (s: StaffSession, status?: SwapStatus) => apiRequest<SwapRequest[]>(`/hr/shift-swaps${query({ status })}`, auth(s));
export const decideSwap = (s: StaffSession, id: string, body: { approve: boolean; note?: string; acknowledgeWarnings?: boolean }) =>
  scheduleCall<SwapRequest>(s, `/hr/shift-swaps/${id}/decision`, "POST", body);

// ---------------------------------------------------------------------------
// Attendance (Phase 4)
// ---------------------------------------------------------------------------

export type ClockAction = "CLOCK_IN" | "CLOCK_OUT" | "BREAK_START" | "BREAK_END";
export type BreakType = "MEAL" | "REST" | "OTHER";
export type AttendanceMetrics = {
  elapsedMinutes: number; unpaidBreakMinutes: number; paidMinutes: number; lateMinutes: number;
  earlyLeaveMinutes: number; overtimeMinutes: number; missedClockOut: boolean;
};
export type AttendanceBreak = { id: string; type: BreakType; paid: boolean; startedAt: string; endedAt?: string | null; minutes: number };
export type AttendanceEntry = {
  id: string; employeeId?: string | null; employeeName?: string | null; branchId?: string | null; scheduleId?: string | null;
  shiftStartsAt?: string | null; shiftEndsAt?: string | null; clockInAt: string; clockOutAt?: string | null;
  source: string; clockOutSource?: string | null; terminalId?: string | null; unscheduled: boolean; onBreak: boolean;
  breaks: AttendanceBreak[]; metrics: AttendanceMetrics; status: "ON_SHIFT" | "ON_BREAK" | "COMPLETED" | "MISSED_CLOCK_OUT";
  editReason?: string | null;
};
export type ClockResult = { eventId: string; action: ClockAction; at: string; duplicate: boolean; entry?: AttendanceEntry | null; message: string };
export type RosterEntry = {
  employeeId: string; displayName: string; positionTitle?: string | null; hasPin: boolean; state: "OFF" | "ON_SHIFT" | "ON_BREAK";
  nextShiftAt?: string | null;
};
export type PosSummary = {
  employeeId: string; displayName: string; current?: AttendanceEntry | null;
  upcomingShifts: { id: string; branchId: string; startsAt: string; endsAt: string; breakMinutes: number }[];
};
export type AttendancePolicy = {
  lateGraceMinutes: number; earlyLeaveGraceMinutes: number; earlyClockInMinutes: number; unscheduledClockIn: "ALLOW" | "BLOCK";
  requireCorrectionApproval: boolean; mealBreakPaid: boolean; restBreakPaid: boolean; allowSelfClock: boolean;
  missedClockOutMinutes: number; source: string;
};
export type DayRow = {
  employeeId?: string | null; employeeName?: string | null; shiftStartsAt?: string | null; shiftEndsAt?: string | null;
  entry?: AttendanceEntry | null; status: string; late: boolean;
};
export type AttendanceDay = { branchId: string; date: string; rows: DayRow[]; counts: Record<string, number>; lateArrivals: number; policy: AttendancePolicy };
export type CorrectionStatus = "PENDING" | "APPLIED" | "REJECTED";
export type AttendanceCorrection = {
  id: string; timeEntryId?: string | null; employeeId: string; employeeName?: string | null; branchId?: string | null;
  originalClockIn?: string | null; originalClockOut?: string | null; originalBreakMinutes?: number | null;
  correctedClockIn: string; correctedClockOut?: string | null; correctedBreakMinutes: number; reason: string;
  status: CorrectionStatus; requestedBy?: string | null; decidedAt?: string | null; decisionNote?: string | null; createdAt: string;
};
export type PinStatus = { set: boolean; setAt?: string | null; lockedUntil?: string | null };

/** Unique request key for idempotent clock actions. Works on plain-HTTP POS tablets where crypto.randomUUID is unavailable. */
export function newRequestKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

export const getPosRoster = (s: StaffSession) => apiRequest<RosterEntry[]>("/hr/pos/roster", auth(s));
export const posClock = (
  s: StaffSession,
  body: { employeeId: string; pin: string; action: ClockAction; breakType?: BreakType; idempotencyKey: string; terminalId?: string },
) => apiRequest<ClockResult>("/hr/pos/clock", { ...auth(s), method: "POST", body });
export const posSummary = (s: StaffSession, body: { employeeId: string; pin: string }) =>
  apiRequest<PosSummary>("/hr/pos/me", { ...auth(s), method: "POST", body });

export const getAttendanceDay = (s: StaffSession, branchId: string, date: string) =>
  apiRequest<AttendanceDay>(`/hr/attendance/day${query({ branchId, date })}`, auth(s));
export const getAttendanceEntries = (s: StaffSession, branchId: string, from: string, to: string, employeeId?: string) =>
  apiRequest<AttendanceEntry[]>(`/hr/attendance/entries${query({ branchId, from, to, employeeId })}`, auth(s));
export const clockForEmployee = (
  s: StaffSession,
  body: { employeeId: string; branchId: string; action: ClockAction; breakType?: BreakType; idempotencyKey: string; reason?: string },
) => apiRequest<ClockResult>("/hr/attendance/clock", { ...auth(s), method: "POST", body });
export const getCorrections = (s: StaffSession, status?: CorrectionStatus) =>
  apiRequest<AttendanceCorrection[]>(`/hr/attendance/corrections${query({ status })}`, auth(s));
export const requestCorrection = (
  s: StaffSession,
  body: { timeEntryId?: string; employeeId?: string; branchId?: string; clockInAt: string; clockOutAt?: string | null; breakMinutes?: number; reason: string },
) => apiRequest<AttendanceCorrection>("/hr/attendance/corrections", { ...auth(s), method: "POST", body });
export const decideCorrection = (s: StaffSession, id: string, body: { approve: boolean; note?: string }) =>
  apiRequest<AttendanceCorrection>(`/hr/attendance/corrections/${id}/decision`, { ...auth(s), method: "POST", body });
export const getAttendancePolicy = (s: StaffSession, branchId?: string) =>
  apiRequest<AttendancePolicy>(`/hr/attendance-policy${query({ branchId })}`, auth(s));
export const saveAttendancePolicy = (s: StaffSession, body: Omit<AttendancePolicy, "source"> & { branchId?: string | null }) =>
  apiRequest<AttendancePolicy>("/hr/attendance-policy", { ...auth(s), method: "PUT", body });

export const getClockPin = (s: StaffSession, employeeId: string) => apiRequest<PinStatus>(`/hr/employees/${employeeId}/clock-pin`, auth(s));
export const setClockPin = (s: StaffSession, employeeId: string, pin: string) =>
  apiRequest<PinStatus>(`/hr/employees/${employeeId}/clock-pin`, { ...auth(s), method: "PUT", body: { pin } });
export const clearClockPin = (s: StaffSession, employeeId: string) =>
  apiRequest<PinStatus>(`/hr/employees/${employeeId}/clock-pin`, { ...auth(s), method: "DELETE" });

export const getMyAttendanceStatus = (s: StaffSession) => apiRequest<AttendanceEntry | undefined>("/hr/me/attendance/status", auth(s));
export const getMyAttendance = (s: StaffSession, from: string, to: string) =>
  apiRequest<AttendanceEntry[]>(`/hr/me/attendance${query({ from, to })}`, auth(s));
export const selfClock = (s: StaffSession, body: { action: ClockAction; breakType?: BreakType; idempotencyKey: string }) =>
  apiRequest<ClockResult>("/hr/me/clock", { ...auth(s), method: "POST", body });
export const getMyClockPin = (s: StaffSession) => apiRequest<PinStatus>("/hr/me/clock-pin", auth(s));
export const setMyClockPin = (s: StaffSession, body: { currentPin?: string; newPin: string }) =>
  apiRequest<PinStatus>("/hr/me/clock-pin", { ...auth(s), method: "PUT", body });

// ---------------------------------------------------------------------------
// Timesheets & overtime (Phase 5)
// ---------------------------------------------------------------------------

export type TimesheetStatus = "DRAFT" | "SUBMITTED" | "REVIEWED" | "APPROVED" | "LOCKED";
export type TimesheetSummary = {
  id: string; employeeId: string; employeeName?: string | null; branchId?: string | null; periodStart: string; periodEnd: string;
  status: TimesheetStatus; scheduledMinutes: number; paidMinutes: number; regularMinutes: number; overtimeComputedMinutes: number;
  overtimeApprovedMinutes?: number | null; payableOvertimeMinutes: number; overtimeRequiresApproval: boolean;
  holidayMinutes: number; weekendMinutes: number; nightMinutes: number; lateCount: number; absenceCount: number; version: number;
};
export type TimesheetLine = {
  id: string; workDate: string; branchId?: string | null; timeEntryId?: string | null; scheduleId?: string | null;
  shiftStart?: string | null; shiftEnd?: string | null; clockIn?: string | null; clockOut?: string | null;
  scheduledMinutes: number; workedMinutes: number; unpaidBreakMinutes: number; paidMinutes: number;
  lateMinutes: number; earlyLeaveMinutes: number; flags: string[];
};
export type TimesheetDetail = {
  summary: TimesheetSummary; workedMinutes: number; unpaidBreakMinutes: number; dailyOvertimeMinutes: number; weeklyOvertimeMinutes: number;
  preApprovedOvertimeMinutes: number; notes?: string | null; ruleSnapshot?: string | null; generatedAt: string; submittedAt?: string | null;
  reviewedAt?: string | null; approvedAt?: string | null; lockedAt?: string | null; lines: TimesheetLine[]; dailyPaidMinutes: Record<string, number>;
};
export type OvertimeRule = {
  id?: string | null; branchId?: string | null; effectiveFrom?: string | null; dailyThresholdMinutes?: number | null;
  weeklyThresholdMinutes?: number | null; dailyMultiplier: number; weeklyMultiplier: number; weekendDays: string[];
  weekendMultiplier?: number | null; holidayMultiplier?: number | null; nightStart?: string | null; nightEnd?: string | null;
  nightMultiplier?: number | null; requiresApproval: boolean; note?: string | null; createdAt?: string | null;
};
export type OvertimeRuleInput = {
  branchId?: string | null; effectiveFrom: string; dailyThresholdMinutes?: number | null; weeklyThresholdMinutes?: number | null;
  dailyMultiplier: string; weeklyMultiplier: string; weekendDays?: string; weekendMultiplier?: string | null; holidayMultiplier?: string | null;
  nightStart?: string | null; nightEnd?: string | null; nightMultiplier?: string | null; requiresApproval: boolean; note?: string;
};
export type PublicHoliday = { id: string; date: string; name: string; branchId?: string | null };
export type OvertimeRequestStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
export type OvertimeRequest = {
  id: string; employeeId: string; employeeName?: string | null; branchId?: string | null; workDate: string; minutes: number;
  reason: string; status: OvertimeRequestStatus; decisionNote?: string | null; decidedAt?: string | null; createdAt: string;
};

export const getTimesheets = (s: StaffSession, branchId: string, from: string, to: string, status?: TimesheetStatus) =>
  apiRequest<TimesheetSummary[]>(`/hr/timesheets${query({ branchId, from, to, status })}`, auth(s));
export const generateTimesheets = (s: StaffSession, body: { branchId: string; from: string; to: string }) =>
  apiRequest<{ created: number; refreshed: number; skipped: { employeeId: string; employeeName: string; reason: string }[] }>(
    "/hr/timesheets/generate", { ...auth(s), method: "POST", body });
export const getTimesheet = (s: StaffSession, id: string) => apiRequest<TimesheetDetail>(`/hr/timesheets/${id}`, auth(s));
export const timesheetAction = (
  s: StaffSession,
  id: string,
  action: "refresh" | "submit" | "review" | "approve" | "reopen" | "lock",
  body: Record<string, unknown> = {},
) => apiRequest<TimesheetDetail>(`/hr/timesheets/${id}/${action}`, { ...auth(s), method: "POST", body });
export const getMyTimesheets = (s: StaffSession) => apiRequest<TimesheetSummary[]>("/hr/me/timesheets", auth(s));

export const getOvertimeRules = (s: StaffSession, branchId?: string) =>
  apiRequest<OvertimeRule[]>(`/hr/overtime-rules${query({ branchId })}`, auth(s));
export const addOvertimeRule = (s: StaffSession, body: OvertimeRuleInput) =>
  apiRequest<OvertimeRule>("/hr/overtime-rules", { ...auth(s), method: "POST", body });
export const getHolidays = (s: StaffSession, year: number) => apiRequest<PublicHoliday[]>(`/hr/holidays${query({ year })}`, auth(s));
export const addHoliday = (s: StaffSession, body: { date: string; name: string; branchId?: string | null }) =>
  apiRequest<PublicHoliday>("/hr/holidays", { ...auth(s), method: "POST", body });
export const deleteHoliday = (s: StaffSession, id: string) => apiRequest<void>(`/hr/holidays/${id}`, { ...auth(s), method: "DELETE" });
export const getOvertimeRequests = (s: StaffSession, status?: OvertimeRequestStatus) =>
  apiRequest<OvertimeRequest[]>(`/hr/overtime-requests${query({ status })}`, auth(s));
export const requestOvertime = (s: StaffSession, body: { employeeId: string; branchId?: string; workDate: string; minutes: number; reason: string }) =>
  apiRequest<OvertimeRequest>("/hr/overtime-requests", { ...auth(s), method: "POST", body });
export const decideOvertime = (s: StaffSession, id: string, body: { approve: boolean; note?: string }) =>
  apiRequest<OvertimeRequest>(`/hr/overtime-requests/${id}/decision`, { ...auth(s), method: "POST", body });
