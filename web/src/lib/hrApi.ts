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
