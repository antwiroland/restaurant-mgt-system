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
};

export type BranchRecord = {
  id: string;
  code: string;
  name: string;
  active: boolean;
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
  refreshToken: string;
  expiresIn: number;
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

  return JSON.parse(text) as T;
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
    refreshToken: response.refreshToken,
    expiresAtEpochMs: Date.now() + response.expiresIn * 1000,
  };
}

export async function logoutStaff(session: StaffSession): Promise<void> {
  await apiRequest<void>("/auth/logout", {
    method: "POST",
    token: session.accessToken,
    body: { refreshToken: session.refreshToken },
  });
}

export async function getOrders(session: StaffSession): Promise<OrderRecord[]> {
  return apiRequest<OrderRecord[]>("/orders", { token: session.accessToken });
}

export async function getBranches(session: StaffSession): Promise<BranchRecord[]> {
  return apiRequest<BranchRecord[]>("/branches", { token: session.accessToken });
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
