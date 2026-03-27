export type TokenPair = { accessToken: string; refreshToken: string; expiresAtEpochMs: number };

export type StaffRole = "ADMIN" | "MANAGER" | "CASHIER" | "CUSTOMER";

export type StaffUser = {
  id: string;
  name: string;
  role: StaffRole;
};

export type StaffSession = TokenPair & {
  user: StaffUser;
};

export type TableRecord = {
  id: string;
  number: string;
  capacity: number;
  zone: string;
  status: "AVAILABLE" | "OCCUPIED" | "RESERVED" | "CLOSED";
  qrToken: string;
  branchId?: string;
  branchName?: string;
};

export type TableQrRecord = {
  tableId: string;
  tableNumber: string;
  qrToken: string;
  qrUrl: string;
};

export type TableBillRecord = {
  tableId: string;
  tableNumber: string;
  tableStatus: TableRecord["status"];
  activeOrderCount: number;
  totalOrdered: string;
  totalPaid: string;
  outstandingTotal: string;
};

export type PublicOrderTrackingRecord = {
  orderId: string;
  tableNumber: string;
  status: OrderRecord["status"];
  notes?: string;
  cancelReason?: string;
  createdAt: string;
  updatedAt: string;
};

export type BranchRecord = {
  id: string;
  code: string;
  name: string;
  active: boolean;
};

export type UserRecord = {
  id: string;
  name: string;
  phone: string;
  email?: string;
  role: StaffRole;
  active: boolean;
  branchId?: string;
  branchName?: string;
};

export type AuditRecord = {
  id?: string;
  actorId: string;
  actorName?: string;
  action: string;
  entityType?: string;
  entityId?: string;
  ipAddress?: string;
  createdAt: string;
  metadata: string;
};

export type ReservationStatus = "PENDING" | "CONFIRMED" | "CANCELLED";

export type ReservationRecord = {
  id: string;
  tableId: string;
  tableNumber: string;
  customerName?: string;
  customerPhone?: string;
  partySize: number;
  reservedAt: string;
  durationMins: number;
  status: ReservationStatus;
  notes?: string;
};

export type OverrideActionType = "DISCOUNT" | "VOID" | "REFUND";

export type PinVerifyResponse = {
  overrideToken: string;
  expiresIn: number;
  actionType: OverrideActionType;
  lockedUntil?: string;
};

export type PaymentMethod = "MOBILE_MONEY" | "CARD" | "CASH";
export type PaymentStatus = "INITIATED" | "PENDING" | "SUCCESS" | "FAILED" | "REFUNDED" | "VOIDED" | "PAYMENT_PENDING";

export type PaymentRecord = {
  id: string;
  orderId: string;
  amount: string;
  currency: string;
  method: PaymentMethod;
  status: PaymentStatus;
  paystackReference?: string;
  authorizationUrl?: string;
  momoPhone?: string;
  providerMessage?: string;
  paidAt?: string;
  createdAt: string;
};

export type PaymentInitiateRecord = {
  paymentId: string;
  status: PaymentStatus;
  paystackReference?: string;
  authorizationUrl?: string;
  message?: string;
};

export type ReceiptRecord = {
  receiptNumber: string;
  paymentId: string;
  orderId: string;
  subtotal: string;
  total: string;
  currency: string;
  paymentMethod: PaymentMethod;
  paidAt?: string;
};

export type ShiftStatus = "OPEN" | "CLOSED";

export type ShiftRecord = {
  id: string;
  cashierUserId: string;
  cashierName: string;
  branchId?: string;
  branchName?: string;
  status: ShiftStatus;
  openingCash: string;
  closingCash?: string;
  expectedCash?: string;
  variance?: string;
  notes?: string;
  openedAt: string;
  closedAt?: string;
};

export type TableScanRecord = {
  tableId: string;
  tableNumber: string;
  status: "AVAILABLE" | "OCCUPIED" | "RESERVED" | "CLOSED";
};

export type MenuItemRecord = {
  id: string;
  categoryId: string;
  categoryName: string;
  name: string;
  description?: string;
  price: string;
  imageUrl?: string;
  available: boolean;
  active: boolean;
};

export type CategoryRecord = {
  id: string;
  name: string;
  description?: string;
  displayOrder: number;
  active: boolean;
};

export type MenuModifierOptionRecord = {
  id: string;
  name: string;
  priceDelta: string;
};

export type MenuModifierGroupRecord = {
  id: string;
  name: string;
  selectionType: "SINGLE" | "MULTIPLE";
  required: boolean;
  minSelect?: number;
  maxSelect?: number;
  options: MenuModifierOptionRecord[];
};

export type ModifierGroupUpsertRequest = {
  name: string;
  selectionType: "SINGLE" | "MULTIPLE";
  required: boolean;
  minSelect?: number;
  maxSelect?: number;
  displayOrder: number;
  active?: boolean;
};

export type ModifierOptionUpsertRequest = {
  name: string;
  priceDelta: number;
  displayOrder: number;
  active?: boolean;
};

export type OrderItemRecord = {
  id: string;
  menuItemId: string;
  participantId?: string;
  name: string;
  price: string;
  quantity: number;
  notes?: string;
  modifiers?: { id: string; groupName: string; optionName: string; priceDelta: string }[];
};

export type OrderRecord = {
  id: string;
  customerUserId?: string;
  type: "DINE_IN" | "PICKUP" | "DELIVERY";
  status: "PENDING" | "CONFIRMED" | "PREPARING" | "READY" | "COMPLETED" | "CANCELLED" | "VOIDED";
  tableId?: string;
  tableNumber?: string;
  deliveryAddress?: string;
  pickupCode?: string;
  notes?: string;
  cancelReason?: string;
  subtotal: string;
  total: string;
  createdAt: string;
  items: OrderItemRecord[];
  branchId?: string;
  branchName?: string;
};

export type GroupParticipantRecord = {
  participantId: string;
  userId?: string;
  displayName: string;
};

export type GroupSessionRecord = {
  sessionId: string;
  sessionCode: string;
  status: "OPEN" | "COMPLETED" | "CANCELLED";
  hostUserId?: string;
  hostParticipantId: string;
};

export type GroupViewRecord = {
  sessionId: string;
  sessionCode: string;
  status: "OPEN" | "COMPLETED" | "CANCELLED";
  groupTotal: string;
  participants: {
    participantId: string;
    userId?: string;
    displayName: string;
    subtotal: string;
    items: {
      itemId: string;
      menuItemId: string;
      name: string;
      price: string;
      quantity: number;
      notes?: string;
    }[];
  }[];
};

export type KdsOrderCard = {
  orderId: string;
  tableNumber: string;
  branchName?: string;
  status: "PENDING" | "CONFIRMED" | "PREPARING" | "READY";
  notes?: string;
  createdAt: string;
  items: { name: string; quantity: number; notes?: string; modifiers: string[] }[];
};

export type KdsBoardRecord = {
  columns: Record<"PENDING" | "CONFIRMED" | "PREPARING" | "READY", KdsOrderCard[]>;
};

type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: StaffUser;
};

type RefreshResponse = {
  accessToken: string;
  expiresIn: number;
};

type RegisterResponse = {
  id: string;
  name: string;
  phone: string;
  role: StaffRole;
  accessToken: string;
  refreshToken: string;
};

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  token?: string;
};

const API_PREFIX = "/api/rm";

export function withRetryAfterRefresh(statusCode: number): boolean {
  return statusCode === 401;
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }

  try {
    return JSON.parse(text) as T;
  } catch {
    return text as T;
  }
}

async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers();
  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (options.token) {
    headers.set("Authorization", `Bearer ${options.token}`);
  }

  const response = await fetch(`${API_PREFIX}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    cache: "no-store",
  });

  if (!response.ok) {
    const payload = await parseResponse<{ message?: string } | undefined>(response).catch(() => undefined);
    throw new Error(payload?.message ?? `Request failed with status ${response.status}`);
  }

  return parseResponse<T>(response);
}

export async function loginStaff(phone: string, password: string): Promise<StaffSession> {
  const response = await apiRequest<AuthResponse>("/auth/login", {
    method: "POST",
    body: { phone, password },
  });

  return {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    expiresAtEpochMs: Date.now() + response.expiresIn * 1000,
    user: response.user,
  };
}

export async function refreshStaffSession(refreshToken: string): Promise<TokenPair> {
  const response = await apiRequest<RefreshResponse>("/auth/refresh", {
    method: "POST",
    body: { refreshToken },
  });

  return {
    accessToken: response.accessToken,
    // Backend refresh returns only a new access token; keep current refresh token.
    refreshToken,
    expiresAtEpochMs: Date.now() + response.expiresIn * 1000,
  };
}

export async function registerUser(payload: {
  name: string;
  phone: string;
  email?: string;
  password: string;
}): Promise<{ id: string; name: string; phone: string; role: StaffRole }> {
  const response = await apiRequest<RegisterResponse>("/auth/register", {
    method: "POST",
    body: payload,
  });
  return { id: response.id, name: response.name, phone: response.phone, role: response.role };
}

export async function logoutStaff(session: StaffSession): Promise<void> {
  await apiRequest<void>("/auth/logout", {
    method: "POST",
    token: session.accessToken,
    body: { refreshToken: session.refreshToken },
  });
}

export async function getUsers(session: StaffSession): Promise<UserRecord[]> {
  return apiRequest<UserRecord[]>("/users", { token: session.accessToken });
}

export async function assignUserRole(
  session: StaffSession,
  userId: string,
  payload: { role: StaffRole; branchId?: string },
): Promise<UserRecord> {
  return apiRequest<UserRecord>(`/users/${userId}/role`, {
    method: "PATCH",
    token: session.accessToken,
    body: payload,
  });
}

export async function setUserPin(
  session: StaffSession,
  userId: string,
  pin: string,
): Promise<void> {
  return apiRequest<void>(`/users/${userId}/pin`, {
    method: "POST",
    token: session.accessToken,
    body: { pin },
  });
}

export async function getOrders(session: StaffSession): Promise<OrderRecord[]> {
  return apiRequest<OrderRecord[]>("/orders", { token: session.accessToken });
}

export async function getBranches(session: StaffSession): Promise<BranchRecord[]> {
  return apiRequest<BranchRecord[]>("/branches", { token: session.accessToken });
}

export async function createBranch(
  session: StaffSession,
  payload: { code: string; name: string; active: boolean },
): Promise<BranchRecord> {
  return apiRequest<BranchRecord>("/branches", {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function updateBranch(
  session: StaffSession,
  id: string,
  payload: { code: string; name: string; active: boolean },
): Promise<BranchRecord> {
  return apiRequest<BranchRecord>(`/branches/${id}`, {
    method: "PUT",
    token: session.accessToken,
    body: payload,
  });
}

export async function getActiveShifts(session: StaffSession): Promise<ShiftRecord[]> {
  return apiRequest<ShiftRecord[]>("/shifts/active", { token: session.accessToken });
}

export async function openShift(
  session: StaffSession,
  payload: { openingCash: number; branchId?: string; notes?: string },
): Promise<ShiftRecord> {
  return apiRequest<ShiftRecord>("/shifts/open", {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function closeShift(
  session: StaffSession,
  shiftId: string,
  payload: { closingCash: number; notes?: string },
): Promise<ShiftRecord> {
  return apiRequest<ShiftRecord>(`/shifts/${shiftId}/close`, {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function getKdsBoard(session: StaffSession, branchId?: string): Promise<KdsBoardRecord> {
  const query = branchId ? `?branchId=${encodeURIComponent(branchId)}` : "";
  return apiRequest<KdsBoardRecord>(`/kds/board${query}`, { token: session.accessToken });
}

export async function updateOrderStatus(session: StaffSession, orderId: string, status: OrderRecord["status"]): Promise<OrderRecord> {
  return apiRequest<OrderRecord>(`/orders/${orderId}/status`, {
    method: "PATCH",
    token: session.accessToken,
    body: { status },
  });
}

export async function getTables(session: StaffSession): Promise<TableRecord[]> {
  return apiRequest<TableRecord[]>("/tables", { token: session.accessToken });
}

export async function createTable(
  session: StaffSession,
  payload: { number: string; capacity: number; zone?: string; branchId?: string },
): Promise<TableRecord> {
  return apiRequest<TableRecord>("/tables", {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function updateTable(
  session: StaffSession,
  tableId: string,
  payload: { number: string; capacity: number; zone?: string; branchId?: string },
): Promise<TableRecord> {
  return apiRequest<TableRecord>(`/tables/${tableId}`, {
    method: "PUT",
    token: session.accessToken,
    body: payload,
  });
}

export async function updateTableStatus(
  session: StaffSession,
  tableId: string,
  status: TableRecord["status"],
): Promise<TableRecord> {
  return apiRequest<TableRecord>(`/tables/${tableId}/status`, {
    method: "PATCH",
    token: session.accessToken,
    body: { status },
  });
}

export async function getTableQr(session: StaffSession, tableId: string): Promise<TableQrRecord> {
  return apiRequest<TableQrRecord>(`/tables/${tableId}/qr`, { token: session.accessToken });
}

export type TablePublicStatusView = {
  number: string;
  capacity: number;
  zone: string | null;
  status: TableRecord["status"];
};

export async function getPublicTableStatus(): Promise<TablePublicStatusView[]> {
  return apiRequest<TablePublicStatusView[]>("/tables/public");
}

export type OrderPublicStatusView = {
  orderId: string;
  tableNumber: string | null;
  type: "DINE_IN" | "PICKUP" | "DELIVERY";
  status: "PENDING" | "CONFIRMED" | "PREPARING" | "READY" | "OUT_FOR_DELIVERY" | "DELIVERED";
  createdAt: string;
  updatedAt: string;
};

export async function getPublicOrderStatus(): Promise<OrderPublicStatusView[]> {
  return apiRequest<OrderPublicStatusView[]>("/orders/public/status");
}

export async function getTableScan(tableToken: string): Promise<TableScanRecord> {
  return apiRequest<TableScanRecord>("/tables/scan", {
    method: "POST",
    body: { qrToken: tableToken },
  });
}

export async function getPublicMenuItems(): Promise<MenuItemRecord[]> {
  return apiRequest<MenuItemRecord[]>("/menu/items");
}

export async function getMenuItemModifiers(menuItemId: string): Promise<MenuModifierGroupRecord[]> {
  return apiRequest<MenuModifierGroupRecord[]>(`/menu/items/${menuItemId}/modifiers`);
}

export async function createMenuModifierGroup(
  session: StaffSession,
  menuItemId: string,
  payload: ModifierGroupUpsertRequest,
): Promise<MenuModifierGroupRecord> {
  return apiRequest<MenuModifierGroupRecord>(`/menu/items/${menuItemId}/modifiers`, {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function updateMenuModifierGroup(
  session: StaffSession,
  menuItemId: string,
  groupId: string,
  payload: ModifierGroupUpsertRequest,
): Promise<MenuModifierGroupRecord> {
  return apiRequest<MenuModifierGroupRecord>(`/menu/items/${menuItemId}/modifiers/${groupId}`, {
    method: "PUT",
    token: session.accessToken,
    body: payload,
  });
}

export async function deleteMenuModifierGroup(session: StaffSession, menuItemId: string, groupId: string): Promise<void> {
  return apiRequest<void>(`/menu/items/${menuItemId}/modifiers/${groupId}`, {
    method: "DELETE",
    token: session.accessToken,
  });
}

export async function createMenuModifierOption(
  session: StaffSession,
  menuItemId: string,
  groupId: string,
  payload: ModifierOptionUpsertRequest,
): Promise<MenuModifierOptionRecord> {
  return apiRequest<MenuModifierOptionRecord>(`/menu/items/${menuItemId}/modifiers/${groupId}/options`, {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function updateMenuModifierOption(
  session: StaffSession,
  menuItemId: string,
  groupId: string,
  optionId: string,
  payload: ModifierOptionUpsertRequest,
): Promise<MenuModifierOptionRecord> {
  return apiRequest<MenuModifierOptionRecord>(`/menu/items/${menuItemId}/modifiers/${groupId}/options/${optionId}`, {
    method: "PUT",
    token: session.accessToken,
    body: payload,
  });
}

export async function deleteMenuModifierOption(
  session: StaffSession,
  menuItemId: string,
  groupId: string,
  optionId: string,
): Promise<void> {
  return apiRequest<void>(`/menu/items/${menuItemId}/modifiers/${groupId}/options/${optionId}`, {
    method: "DELETE",
    token: session.accessToken,
  });
}

export async function createPublicDineInOrder(payload: {
  tableToken: string;
  notes?: string;
  items: { menuItemId: string; quantity: number; notes?: string; modifierOptionIds?: string[] }[];
}): Promise<OrderRecord> {
  return apiRequest<OrderRecord>("/orders/public/dine-in", {
    method: "POST",
    body: payload,
  });
}

export async function getMenuItems(session: StaffSession): Promise<MenuItemRecord[]> {
  return apiRequest<MenuItemRecord[]>("/menu/items", { token: session.accessToken });
}

export async function getMenuCategories(): Promise<CategoryRecord[]> {
  return apiRequest<CategoryRecord[]>("/menu/categories");
}

export async function createMenuCategory(
  session: StaffSession,
  payload: { name: string; description?: string; displayOrder: number; active: boolean },
): Promise<CategoryRecord> {
  return apiRequest<CategoryRecord>("/menu/categories", {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function updateMenuCategory(
  session: StaffSession,
  id: string,
  payload: { name: string; description?: string; displayOrder: number; active: boolean },
): Promise<CategoryRecord> {
  return apiRequest<CategoryRecord>(`/menu/categories/${id}`, {
    method: "PUT",
    token: session.accessToken,
    body: payload,
  });
}

export async function deleteMenuCategory(session: StaffSession, id: string): Promise<void> {
  return apiRequest<void>(`/menu/categories/${id}`, {
    method: "DELETE",
    token: session.accessToken,
  });
}

export async function createMenuItem(
  session: StaffSession,
  payload: {
    categoryId: string;
    name: string;
    description?: string;
    price: number;
    imageUrl?: string;
    available: boolean;
  },
): Promise<MenuItemRecord> {
  return apiRequest<MenuItemRecord>("/menu/items", {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function updateMenuItem(
  session: StaffSession,
  id: string,
  payload: {
    categoryId: string;
    name: string;
    description?: string;
    price: number;
    imageUrl?: string;
    available: boolean;
  },
): Promise<MenuItemRecord> {
  return apiRequest<MenuItemRecord>(`/menu/items/${id}`, {
    method: "PUT",
    token: session.accessToken,
    body: payload,
  });
}

export async function updateMenuItemAvailability(
  session: StaffSession,
  id: string,
  available: boolean,
): Promise<MenuItemRecord> {
  return apiRequest<MenuItemRecord>(`/menu/items/${id}/availability`, {
    method: "PATCH",
    token: session.accessToken,
    body: { available },
  });
}

export async function deleteMenuItem(session: StaffSession, id: string): Promise<void> {
  return apiRequest<void>(`/menu/items/${id}`, {
    method: "DELETE",
    token: session.accessToken,
  });
}

export async function createOrder(
  session: StaffSession,
  payload: {
    type: "DINE_IN" | "PICKUP" | "DELIVERY";
    tableId?: string;
    deliveryAddress?: string;
    notes?: string;
    items: { menuItemId: string; quantity: number; modifierOptionIds?: string[] }[];
  },
): Promise<OrderRecord> {
  return apiRequest<OrderRecord>("/orders", {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function getOrderById(session: StaffSession, id: string): Promise<OrderRecord> {
  return apiRequest<OrderRecord>(`/orders/${id}`, { token: session.accessToken });
}

export async function cancelOrder(
  session: StaffSession,
  id: string,
  payload?: { reason?: string; overrideToken?: string },
): Promise<void> {
  return apiRequest<void>(`/orders/${id}`, {
    method: "DELETE",
    token: session.accessToken,
    body: payload,
  });
}

export async function getOrderByPickupCode(session: StaffSession, pickupCode: string): Promise<OrderRecord> {
  return apiRequest<OrderRecord>(`/orders/pickup/${encodeURIComponent(pickupCode)}`, { token: session.accessToken });
}

export async function getTableBill(session: StaffSession, tableId: string): Promise<TableBillRecord> {
  return apiRequest<TableBillRecord>(`/orders/dine-in/tables/${tableId}/bill`, { token: session.accessToken });
}

export async function getPublicTableBill(tableToken: string): Promise<TableBillRecord> {
  return apiRequest<TableBillRecord>(`/orders/public/dine-in/tables/${encodeURIComponent(tableToken)}/bill`);
}

export async function getPublicTableTracking(tableToken: string): Promise<PublicOrderTrackingRecord[]> {
  return apiRequest<PublicOrderTrackingRecord[]>(`/orders/public/dine-in/tables/${encodeURIComponent(tableToken)}/tracking`);
}

export async function closeTableSession(session: StaffSession, tableId: string): Promise<void> {
  return apiRequest<void>(`/orders/dine-in/tables/${tableId}/close`, {
    method: "POST",
    token: session.accessToken,
  });
}

export async function reverseTableSession(session: StaffSession, tableId: string): Promise<void> {
  return apiRequest<void>(`/orders/dine-in/tables/${tableId}/reverse`, {
    method: "POST",
    token: session.accessToken,
  });
}

export async function createGroupSession(
  session: StaffSession,
  payload?: { displayName?: string },
): Promise<GroupSessionRecord> {
  return apiRequest<GroupSessionRecord>("/orders/group/sessions", {
    method: "POST",
    token: session.accessToken,
    body: payload ?? {},
  });
}

export async function joinGroupSession(
  session: StaffSession,
  code: string,
  payload?: { displayName?: string },
): Promise<{ sessionId: string; participantId: string; participants: GroupParticipantRecord[] }> {
  return apiRequest<{ sessionId: string; participantId: string; participants: GroupParticipantRecord[] }>(`/orders/group/sessions/${encodeURIComponent(code)}/join`, {
    method: "POST",
    token: session.accessToken,
    body: payload ?? {},
  });
}

export async function addGroupItems(
  session: StaffSession,
  code: string,
  payload: { participantId: string; items: { menuItemId: string; quantity: number; notes?: string; modifierOptionIds?: string[] }[] },
): Promise<GroupViewRecord> {
  return apiRequest<GroupViewRecord>(`/orders/group/sessions/${encodeURIComponent(code)}/items`, {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function getGroupSession(session: StaffSession, code: string): Promise<GroupViewRecord> {
  return apiRequest<GroupViewRecord>(`/orders/group/sessions/${encodeURIComponent(code)}`, { token: session.accessToken });
}

export async function finalizeGroupSession(
  session: StaffSession,
  code: string,
  payload: {
    type: "DINE_IN" | "PICKUP" | "DELIVERY";
    tableId?: string;
    tableToken?: string;
    deliveryAddress?: string;
    notes?: string;
  },
): Promise<OrderRecord> {
  return apiRequest<OrderRecord>(`/orders/group/sessions/${encodeURIComponent(code)}/finalize`, {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function getAuditEvents(
  session: StaffSession,
  params?: { action?: string; from?: string; to?: string },
): Promise<AuditRecord[]> {
  const query = new URLSearchParams();
  if (params?.action) query.set("action", params.action);
  if (params?.from) query.set("from", params.from);
  if (params?.to) query.set("to", params.to);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return apiRequest<AuditRecord[]>(`/audit${suffix}`, { token: session.accessToken });
}

export async function verifyManagerPin(
  session: StaffSession,
  payload: { pin: string; actionType: OverrideActionType },
): Promise<PinVerifyResponse> {
  return apiRequest<PinVerifyResponse>("/auth/pin/verify", {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function applyFinancialAction(
  session: StaffSession,
  action: "discount" | "void" | "refund",
  overrideToken: string,
): Promise<{ ok?: boolean; actionType?: OverrideActionType; approverRole?: string; approverUserId?: string }> {
  return apiRequest<{ ok?: boolean; actionType?: OverrideActionType; approverRole?: string; approverUserId?: string }>(`/financial/${action}`, {
    method: "POST",
    token: session.accessToken,
    body: { overrideToken },
  });
}

export async function createReservation(
  session: StaffSession,
  payload: {
    tableId: string;
    customerName?: string;
    customerPhone?: string;
    partySize: number;
    reservedAt: string;
    durationMins?: number;
    notes?: string;
  },
): Promise<ReservationRecord> {
  return apiRequest<ReservationRecord>("/reservations", {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function getReservations(
  session: StaffSession,
  params?: { date?: string; tableId?: string },
): Promise<ReservationRecord[]> {
  const query = new URLSearchParams();
  if (params?.date) query.set("date", params.date);
  if (params?.tableId) query.set("tableId", params.tableId);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return apiRequest<ReservationRecord[]>(`/reservations${suffix}`, { token: session.accessToken });
}

export async function updateReservationStatus(
  session: StaffSession,
  reservationId: string,
  status: ReservationStatus,
): Promise<ReservationRecord> {
  return apiRequest<ReservationRecord>(`/reservations/${reservationId}/status`, {
    method: "PATCH",
    token: session.accessToken,
    body: { status },
  });
}

export async function cancelReservation(session: StaffSession, reservationId: string): Promise<void> {
  return apiRequest<void>(`/reservations/${reservationId}`, {
    method: "DELETE",
    token: session.accessToken,
  });
}

export async function initiatePayment(
  session: StaffSession,
  payload: { orderId: string; method: PaymentMethod; momoPhone: string; idempotencyKey: string },
): Promise<PaymentInitiateRecord> {
  return apiRequest<PaymentInitiateRecord>("/payments/initiate", {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function getPayment(session: StaffSession, paymentId: string): Promise<PaymentRecord> {
  return apiRequest<PaymentRecord>(`/payments/${paymentId}`, { token: session.accessToken });
}

export async function verifyPayment(session: StaffSession, paymentId: string): Promise<PaymentRecord> {
  return apiRequest<PaymentRecord>(`/payments/${paymentId}/verify`, { token: session.accessToken });
}

export async function retryPayment(
  session: StaffSession,
  paymentId: string,
  payload: { momoPhone: string; idempotencyKey: string },
): Promise<PaymentInitiateRecord> {
  return apiRequest<PaymentInitiateRecord>(`/payments/${paymentId}/retry`, {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function getReceiptByPaymentId(session: StaffSession, paymentId: string): Promise<ReceiptRecord> {
  return apiRequest<ReceiptRecord>(`/payments/${paymentId}/receipt`, { token: session.accessToken });
}

export async function getReceiptByOrderId(session: StaffSession, orderId: string): Promise<ReceiptRecord> {
  return apiRequest<ReceiptRecord>(`/payments/orders/${orderId}/receipt`, { token: session.accessToken });
}
