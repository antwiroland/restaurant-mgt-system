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

export type OrderItemRecord = {
  id: string;
  menuItemId: string;
  participantId?: string;
  name: string;
  price: string;
  quantity: number;
  notes?: string;
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
  status: "CONFIRMED" | "PREPARING" | "READY";
  notes?: string;
  createdAt: string;
  items: { name: string; quantity: number; notes?: string; modifiers: string[] }[];
};

export type KdsBoardRecord = {
  columns: Record<"CONFIRMED" | "PREPARING" | "READY", KdsOrderCard[]>;
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
  method?: "GET" | "POST" | "PATCH" | "DELETE";
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

export async function createPublicDineInOrder(payload: {
  tableToken: string;
  notes?: string;
  items: { menuItemId: string; quantity: number; notes?: string }[];
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
    items: { menuItemId: string; quantity: number }[];
  },
): Promise<OrderRecord> {
  return apiRequest<OrderRecord>("/orders", {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}
