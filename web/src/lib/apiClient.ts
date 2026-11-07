export type TokenPair = { accessToken: string; refreshToken: string; expiresAtEpochMs: number };

export type StaffRole = "ADMIN" | "MANAGER" | "CASHIER" | "CUSTOMER" | "RIDER";

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

export type AnalyticsRevenuePoint = {
  bucket: string;
  revenue: string;
};

export type AnalyticsRevenueRecord = {
  points: AnalyticsRevenuePoint[];
};

export type AnalyticsTopItemRecord = {
  itemName: string;
  quantitySold: number;
  revenue: string;
};

export type AnalyticsPeakHourRecord = {
  hour: number;
  orders: number;
};

export type AnalyticsAverageOrderValueRecord = {
  averageOrderValue: string;
  orderCount: number;
};

export type AnalyticsBranchPerformanceRecord = {
  branchId: string;
  branchCode: string;
  branchName: string;
  revenue: string;
  orderCount: number;
  averageOrderValue: string;
};

export type AnalyticsPaymentMethodRecord = {
  method: PaymentMethod;
  revenue: string;
  paymentCount: number;
};

export type AnalyticsOrderTypeRecord = {
  type: "DINE_IN" | "PICKUP" | "DELIVERY";
  revenue: string;
  orderCount: number;
};

export type AnalyticsOrderStatusRecord = {
  status: "PENDING" | "CONFIRMED" | "PREPARING" | "READY" | "OUT_FOR_DELIVERY" | "DELIVERED" | "COMPLETED" | "CANCELLED";
  orderCount: number;
};

export type AnalyticsOverviewRecord = {
  totalRevenue: string;
  paidOrderCount: number;
  averageOrderValue: string;
  repeatCustomers: number;
  revenue: AnalyticsRevenueRecord;
  topItems: AnalyticsTopItemRecord[];
  peakHours: AnalyticsPeakHourRecord[];
  branches: AnalyticsBranchPerformanceRecord[];
  paymentMethods: AnalyticsPaymentMethodRecord[];
  orderTypes: AnalyticsOrderTypeRecord[];
  orderStatuses: AnalyticsOrderStatusRecord[];
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
  discountTotal?: string;
  deliveryFee?: string;
  serviceCharge?: string;
  taxTotal?: string;
  taxInclusive?: boolean;
  taxLines?: TaxLineRecord[];
  tipAmount?: string;
  amountPaid?: string;
  refundedAmount?: string;
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

export type PromoDiscountType = "PERCENTAGE" | "FLAT";

export type PromoCodeRecord = {
  id: string;
  code: string;
  description?: string;
  discountType: PromoDiscountType;
  discountValue: string;
  minOrderAmount: string;
  maxDiscount?: string;
  expiryDate?: string;
  usageLimit?: number;
  usageCount: number;
  active: boolean;
  createdAt: string;
};

export type PromoCodeUpsertRequest = {
  code: string;
  description?: string;
  discountType: PromoDiscountType;
  discountValue: number;
  minOrderAmount: number;
  maxDiscount?: number;
  expiryDate?: string;
  usageLimit?: number;
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
  modifiers?: { id: string; groupName: string; optionName: string; priceDelta: string }[];
};

export type OrderRecord = {
  id: string;
  customerUserId?: string;
  type: "DINE_IN" | "PICKUP" | "DELIVERY";
  status: "PENDING" | "CONFIRMED" | "PREPARING" | "READY" | "OUT_FOR_DELIVERY" | "DELIVERED" | "COMPLETED" | "CANCELLED" | "VOIDED";
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
  promoCode?: string | null;
  promoDiscount?: string;
  offerDiscount?: string;
  loyaltyPointsRedeemed?: number;
  loyaltyDiscount?: string;
  manualDiscount?: string;
  deliveryFee?: string;
  discountTotal?: string;
  voidedAt?: string | null;
  deliveryZoneId?: string | null;
  riderUserId?: string | null;
  serviceCharge?: string;
  taxTotal?: string;
  taxInclusive?: boolean;
  tipAmount?: string;
  taxLines?: TaxLineRecord[];
};

export type TaxLineRecord = { code: string; name: string; rate: string; amount: string };

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

export async function deleteTable(session: StaffSession, tableId: string): Promise<void> {
  return apiRequest<void>(`/tables/${tableId}`, {
    method: "DELETE",
    token: session.accessToken,
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

export async function getPromoCodes(session: StaffSession): Promise<PromoCodeRecord[]> {
  return apiRequest<PromoCodeRecord[]>("/promotions/codes", { token: session.accessToken });
}

export async function createPromoCode(
  session: StaffSession,
  payload: PromoCodeUpsertRequest,
): Promise<PromoCodeRecord> {
  return apiRequest<PromoCodeRecord>("/promotions/codes", {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export async function updatePromoCode(
  session: StaffSession,
  id: string,
  payload: PromoCodeUpsertRequest,
): Promise<PromoCodeRecord> {
  return apiRequest<PromoCodeRecord>(`/promotions/codes/${id}`, {
    method: "PUT",
    token: session.accessToken,
    body: payload,
  });
}

export async function updatePromoCodeStatus(
  session: StaffSession,
  id: string,
  active: boolean,
): Promise<PromoCodeRecord> {
  return apiRequest<PromoCodeRecord>(`/promotions/codes/${id}/status`, {
    method: "PATCH",
    token: session.accessToken,
    body: { active },
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
    promoCode?: string;
    deliveryZoneId?: string;
    tipAmount?: number;
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

export type DiscountMode = "FLAT" | "PERCENTAGE";

export type FinancialActionResult = {
  status: string;
  actionType: OverrideActionType;
  approverUserId: string;
};

export type DiscountResult = FinancialActionResult & {
  orderId: string;
  discountAmount: string;
  subtotal: string;
  newTotal: string;
};

export type VoidResult = FinancialActionResult & {
  orderId: string;
  voidedAmount: string;
  paymentsVoided: number;
  voidedAt: string;
};

export type RefundResult = FinancialActionResult & {
  refundId: string;
  paymentId: string;
  orderId: string;
  amount: string;
  totalRefunded: string;
  remainingRefundable: string;
  paymentStatus: string;
  providerStatus: string;
};

export async function applyOrderDiscount(
  session: StaffSession,
  payload: { overrideToken: string; orderId: string; mode: DiscountMode; value: number; reason?: string },
): Promise<DiscountResult> {
  return apiRequest<DiscountResult>("/financial/discount", { method: "POST", token: session.accessToken, body: payload });
}

export async function voidOrder(
  session: StaffSession,
  payload: { overrideToken: string; orderId: string; reason?: string },
): Promise<VoidResult> {
  return apiRequest<VoidResult>("/financial/void", { method: "POST", token: session.accessToken, body: payload });
}

export async function refundPayment(
  session: StaffSession,
  payload: { overrideToken: string; paymentId: string; amount?: number; reason?: string },
): Promise<RefundResult> {
  return apiRequest<RefundResult>("/financial/refund", { method: "POST", token: session.accessToken, body: payload });
}

export type ReconciliationReport = {
  businessDate: string;
  branchId?: string | null;
  grossSales: string;
  totalRefunds: string;
  totalVoids: string;
  totalDiscounts: string;
  netSales: string;
  expectedCash: string;
  paidOrders: number;
  voidedOrders: number;
  refundCount: number;
  byMethod: { method: string; amount: string; count: number }[];
  shifts: { closedShifts: number; countedCash: string; expectedCash: string; variance: string };
  signOff?: {
    id: string;
    signedBy: string;
    signedAt: string;
    countedCash?: string | null;
    cashVariance?: string | null;
    notes?: string | null;
  } | null;
};

export async function getReconciliation(session: StaffSession, businessDate: string, branchId?: string): Promise<ReconciliationReport> {
  const suffix = branchId ? `?branchId=${encodeURIComponent(branchId)}` : "";
  return apiRequest<ReconciliationReport>(`/financial/reconciliation/${businessDate}${suffix}`, { token: session.accessToken });
}

export async function signOffReconciliation(
  session: StaffSession,
  businessDate: string,
  payload: { overrideToken: string; branchId?: string; countedCash?: number; notes?: string },
): Promise<ReconciliationReport> {
  return apiRequest<ReconciliationReport>(`/financial/reconciliation/${businessDate}/sign-off`, {
    method: "POST",
    token: session.accessToken,
    body: payload,
  });
}

export type FinancialAuditRecord = {
  id: string;
  action: "DISCOUNT_APPLIED" | "ORDER_VOIDED" | "PAYMENT_REFUNDED" | "RECONCILIATION_SIGNED_OFF";
  actorId?: string | null;
  actorName?: string | null;
  entityType?: string | null;
  entityId?: string | null;
  metadata?: string | null;
  createdAt: string;
};

export async function getFinancialAudit(session: StaffSession, action?: string): Promise<FinancialAuditRecord[]> {
  const suffix = action ? `?action=${encodeURIComponent(action)}` : "";
  return apiRequest<FinancialAuditRecord[]>(`/financial/audit${suffix}`, { token: session.accessToken });
}

export type MenuOfferRecord = {
  id: string;
  name: string;
  description?: string | null;
  menuItemId: string;
  menuItemName: string;
  buyQuantity: number;
  freeQuantity: number;
  startsAt?: string | null;
  endsAt?: string | null;
  active: boolean;
  running: boolean;
  createdAt: string;
};

export type MenuOfferUpsertRequest = {
  name: string;
  description?: string;
  menuItemId: string;
  buyQuantity: number;
  freeQuantity: number;
  startsAt?: string;
  endsAt?: string;
  active?: boolean;
};

export async function getMenuOffers(session: StaffSession): Promise<MenuOfferRecord[]> {
  return apiRequest<MenuOfferRecord[]>("/promotions/offers", { token: session.accessToken });
}

export async function createMenuOffer(session: StaffSession, payload: MenuOfferUpsertRequest): Promise<MenuOfferRecord> {
  return apiRequest<MenuOfferRecord>("/promotions/offers", { method: "POST", token: session.accessToken, body: payload });
}

export async function updateMenuOffer(session: StaffSession, id: string, payload: MenuOfferUpsertRequest): Promise<MenuOfferRecord> {
  return apiRequest<MenuOfferRecord>(`/promotions/offers/${id}`, { method: "PUT", token: session.accessToken, body: payload });
}

export async function setMenuOfferActive(session: StaffSession, id: string, active: boolean): Promise<MenuOfferRecord> {
  return apiRequest<MenuOfferRecord>(`/promotions/offers/${id}/status`, {
    method: "PATCH",
    token: session.accessToken,
    body: { active },
  });
}

export async function deleteMenuOffer(session: StaffSession, id: string): Promise<void> {
  await apiRequest<void>(`/promotions/offers/${id}`, { method: "DELETE", token: session.accessToken });
}

export type PromoValidationRecord = { code: string; discountType: "FLAT" | "PERCENTAGE"; discountValue: string; valid: boolean };

export async function validatePromoCode(session: StaffSession, code: string, subtotal: number): Promise<PromoValidationRecord> {
  return apiRequest<PromoValidationRecord>(
    `/promotions/codes/validate/${encodeURIComponent(code.trim().toUpperCase())}?subtotal=${subtotal.toFixed(2)}`,
    { token: session.accessToken },
  );
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
  payload: { orderId: string; method: PaymentMethod; momoPhone: string; idempotencyKey: string; splitId?: string },
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

export async function getAnalyticsRevenue(
  session: StaffSession,
  params?: { from?: string; to?: string; branchId?: string; period?: "DAY" | "WEEK" | "MONTH" },
): Promise<AnalyticsRevenueRecord> {
  const query = new URLSearchParams();
  if (params?.from) query.set("from", params.from);
  if (params?.to) query.set("to", params.to);
  if (params?.branchId) query.set("branchId", params.branchId);
  if (params?.period) query.set("period", params.period);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return apiRequest<AnalyticsRevenueRecord>(`/analytics/revenue${suffix}`, { token: session.accessToken });
}

export async function getAnalyticsTopItems(
  session: StaffSession,
  params?: { from?: string; to?: string; branchId?: string; limit?: number },
): Promise<AnalyticsTopItemRecord[]> {
  const query = new URLSearchParams();
  if (params?.from) query.set("from", params.from);
  if (params?.to) query.set("to", params.to);
  if (params?.branchId) query.set("branchId", params.branchId);
  if (params?.limit) query.set("limit", String(params.limit));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return apiRequest<AnalyticsTopItemRecord[]>(`/analytics/top-items${suffix}`, { token: session.accessToken });
}

export async function getAnalyticsPeakHours(
  session: StaffSession,
  params?: { from?: string; to?: string; branchId?: string },
): Promise<AnalyticsPeakHourRecord[]> {
  const query = new URLSearchParams();
  if (params?.from) query.set("from", params.from);
  if (params?.to) query.set("to", params.to);
  if (params?.branchId) query.set("branchId", params.branchId);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return apiRequest<AnalyticsPeakHourRecord[]>(`/analytics/peak-hours${suffix}`, { token: session.accessToken });
}

export async function getAnalyticsAverageOrderValue(
  session: StaffSession,
  params?: { from?: string; to?: string; branchId?: string },
): Promise<AnalyticsAverageOrderValueRecord> {
  const query = new URLSearchParams();
  if (params?.from) query.set("from", params.from);
  if (params?.to) query.set("to", params.to);
  if (params?.branchId) query.set("branchId", params.branchId);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return apiRequest<AnalyticsAverageOrderValueRecord>(`/analytics/average-order-value${suffix}`, { token: session.accessToken });
}

export async function getAnalyticsOverview(
  session: StaffSession,
  params?: { from?: string; to?: string; branchId?: string; period?: "DAY" | "WEEK" | "MONTH" },
): Promise<AnalyticsOverviewRecord> {
  const query = new URLSearchParams();
  if (params?.from) query.set("from", params.from);
  if (params?.to) query.set("to", params.to);
  if (params?.branchId) query.set("branchId", params.branchId);
  if (params?.period) query.set("period", params.period);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return apiRequest<AnalyticsOverviewRecord>(`/analytics/overview${suffix}`, { token: session.accessToken });
}

export type TableSessionRecord = {
  tableId: string;
  tableNumber: string;
  active: boolean;
  sessionCount: number;
  lastSeenAt?: string | null;
};

/** Public: called when a guest opens a table QR link so staff can see who is ordering at the table. */
export async function linkTableSession(tableToken: string, sessionId: string): Promise<TableSessionRecord> {
  return apiRequest<TableSessionRecord>("/qr/sessions/link", { method: "POST", body: { tableToken, sessionId } });
}

export async function getActiveTableSessions(session: StaffSession): Promise<TableSessionRecord[]> {
  return apiRequest<TableSessionRecord[]>("/qr/sessions/active", { token: session.accessToken });
}

export async function getTableSession(session: StaffSession, tableNumber: string): Promise<TableSessionRecord> {
  return apiRequest<TableSessionRecord>(`/qr/sessions/${encodeURIComponent(tableNumber)}/active`, { token: session.accessToken });
}

export async function closeTableSessions(session: StaffSession, tableNumber: string): Promise<void> {
  await apiRequest<void>(`/qr/sessions/${encodeURIComponent(tableNumber)}`, { method: "DELETE", token: session.accessToken });
}

export async function downloadTableQrPdf(session: StaffSession, tableNumber: string): Promise<Blob> {
  const response = await fetch(`${API_PREFIX}/qr/tables/${encodeURIComponent(tableNumber)}/pdf`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
    cache: "no-store",
  });
  if (!response.ok) {
    throw new Error(`Could not download QR card (status ${response.status})`);
  }
  return response.blob();
}

export type DeliveryZoneRecord = {
  id: string;
  name: string;
  description?: string | null;
  branchId?: string | null;
  fee: string;
  minOrderAmount: string;
  estimatedMinutes: number;
  active: boolean;
};

export type DeliveryZoneUpsertRequest = {
  name: string;
  description?: string;
  branchId?: string;
  fee: number;
  minOrderAmount?: number;
  estimatedMinutes?: number;
  active?: boolean;
};

export type RiderRecord = { id: string; name: string; phone: string; activeDeliveries: number };

export type DeliveryRecord = {
  orderId: string;
  status: OrderRecord["status"];
  deliveryAddress?: string | null;
  total: string;
  deliveryFee: string;
  deliveryZoneId?: string | null;
  estimatedDeliveryTime?: string | null;
  riderId?: string | null;
  riderName?: string | null;
  riderPhone?: string | null;
  riderAssignedAt?: string | null;
  riderLatitude?: number | null;
  riderLongitude?: number | null;
  riderLocationAt?: string | null;
  deliveredAt?: string | null;
  customerPhone?: string | null;
  notes?: string | null;
  createdAt: string;
};

export async function getDeliveryZones(): Promise<DeliveryZoneRecord[]> {
  return apiRequest<DeliveryZoneRecord[]>("/delivery/zones");
}

export async function getAllDeliveryZones(session: StaffSession): Promise<DeliveryZoneRecord[]> {
  return apiRequest<DeliveryZoneRecord[]>("/delivery/zones/all", { token: session.accessToken });
}

export async function createDeliveryZone(session: StaffSession, payload: DeliveryZoneUpsertRequest): Promise<DeliveryZoneRecord> {
  return apiRequest<DeliveryZoneRecord>("/delivery/zones", { method: "POST", token: session.accessToken, body: payload });
}

export async function updateDeliveryZone(session: StaffSession, id: string, payload: DeliveryZoneUpsertRequest): Promise<DeliveryZoneRecord> {
  return apiRequest<DeliveryZoneRecord>(`/delivery/zones/${id}`, { method: "PUT", token: session.accessToken, body: payload });
}

export async function setDeliveryZoneActive(session: StaffSession, id: string, active: boolean): Promise<DeliveryZoneRecord> {
  return apiRequest<DeliveryZoneRecord>(`/delivery/zones/${id}/status`, { method: "PATCH", token: session.accessToken, body: { active } });
}

export async function getRiders(session: StaffSession): Promise<RiderRecord[]> {
  return apiRequest<RiderRecord[]>("/delivery/riders", { token: session.accessToken });
}

export async function assignRider(session: StaffSession, orderId: string, riderId: string): Promise<DeliveryRecord> {
  return apiRequest<DeliveryRecord>(`/delivery/orders/${orderId}/assign`, { method: "POST", token: session.accessToken, body: { riderId } });
}

export async function getRiderQueue(session: StaffSession): Promise<DeliveryRecord[]> {
  return apiRequest<DeliveryRecord[]>("/delivery/rider/orders", { token: session.accessToken });
}

export async function markDeliveryPickedUp(session: StaffSession, orderId: string): Promise<DeliveryRecord> {
  return apiRequest<DeliveryRecord>(`/delivery/orders/${orderId}/pickup`, { method: "POST", token: session.accessToken });
}

export async function markDeliveryDelivered(session: StaffSession, orderId: string): Promise<DeliveryRecord> {
  return apiRequest<DeliveryRecord>(`/delivery/orders/${orderId}/delivered`, { method: "POST", token: session.accessToken });
}

export async function sendRiderLocation(session: StaffSession, orderId: string, latitude: number, longitude: number): Promise<DeliveryRecord> {
  return apiRequest<DeliveryRecord>(`/delivery/orders/${orderId}/location`, {
    method: "POST",
    token: session.accessToken,
    body: { latitude, longitude },
  });
}

export async function getDeliveryTracking(session: StaffSession, orderId: string): Promise<DeliveryRecord> {
  return apiRequest<DeliveryRecord>(`/delivery/orders/${orderId}/tracking`, { token: session.accessToken });
}

// ---------------------------------------------------------------------------
// Downloads and uploads (raw fetch: not JSON)
// ---------------------------------------------------------------------------

export type DownloadedFile = { blob: Blob; filename: string };

async function rawError(response: Response, fallback: string): Promise<Error> {
  const payload = await parseResponse<{ message?: string } | string | undefined>(response).catch(() => undefined);
  const message = typeof payload === "object" && payload?.message ? payload.message : `${fallback} (status ${response.status})`;
  return new Error(message);
}

async function downloadFile(session: StaffSession, path: string, fallbackName: string): Promise<DownloadedFile> {
  const response = await fetch(`${API_PREFIX}${path}`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
    cache: "no-store",
  });
  if (!response.ok) {
    throw await rawError(response, "Download failed");
  }
  const disposition = response.headers.get("content-disposition") ?? "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  return { blob: await response.blob(), filename: match ? decodeURIComponent(match[1]) : fallbackName };
}

/** Saves a downloaded file through a temporary object URL. */
export function saveDownloadedFile(file: DownloadedFile): void {
  const url = URL.createObjectURL(file.blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = file.filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

// ---------------------------------------------------------------------------
// Billing: service charge, taxes, tips
// ---------------------------------------------------------------------------

export type TaxRateRecord = {
  id: string;
  code: string;
  name: string;
  rate: string;
  compound: boolean;
  displayOrder: number;
  active: boolean;
};

export type BillingConfigRecord = {
  serviceChargePercent: string;
  serviceChargeDineInOnly: boolean;
  pricesIncludeTax: boolean;
  taxDeliveryFee: boolean;
  tipsEnabled: boolean;
  taxes: TaxRateRecord[];
};

export type BillingSettingsRequest = {
  serviceChargePercent: number;
  serviceChargeDineInOnly: boolean;
  pricesIncludeTax: boolean;
  taxDeliveryFee: boolean;
  tipsEnabled: boolean;
};

export type TaxRateRequest = {
  code: string;
  name: string;
  rate: number;
  compound: boolean;
  displayOrder?: number;
  active?: boolean;
};

export async function getBillingConfig(): Promise<BillingConfigRecord> {
  return apiRequest<BillingConfigRecord>("/billing/settings");
}

export async function updateBillingSettings(session: StaffSession, payload: BillingSettingsRequest): Promise<BillingConfigRecord> {
  return apiRequest<BillingConfigRecord>("/billing/settings", { method: "PUT", token: session.accessToken, body: payload });
}

export async function createTaxRate(session: StaffSession, payload: TaxRateRequest): Promise<TaxRateRecord> {
  return apiRequest<TaxRateRecord>("/billing/taxes", { method: "POST", token: session.accessToken, body: payload });
}

export async function updateTaxRate(session: StaffSession, id: string, payload: TaxRateRequest): Promise<TaxRateRecord> {
  return apiRequest<TaxRateRecord>(`/billing/taxes/${id}`, { method: "PUT", token: session.accessToken, body: payload });
}

export async function deleteTaxRate(session: StaffSession, id: string): Promise<void> {
  await apiRequest<void>(`/billing/taxes/${id}`, { method: "DELETE", token: session.accessToken });
}

// ---------------------------------------------------------------------------
// Split payments
// ---------------------------------------------------------------------------

export type SplitMode = "BY_ITEMS" | "EQUAL";

export type PaymentSplitRecord = {
  id: string;
  orderId: string;
  participantId?: string | null;
  userId?: string | null;
  displayName: string;
  amount: string;
  status: "PENDING" | "PAID" | "CANCELLED";
  paymentId?: string | null;
  paidAt?: string | null;
};

export async function getOrderSplits(session: StaffSession, orderId: string): Promise<PaymentSplitRecord[]> {
  return apiRequest<PaymentSplitRecord[]>(`/orders/${orderId}/splits`, { token: session.accessToken });
}

export async function createOrderSplits(
  session: StaffSession,
  orderId: string,
  payload: { mode: SplitMode; parts?: number; names?: string[] },
): Promise<PaymentSplitRecord[]> {
  return apiRequest<PaymentSplitRecord[]>(`/orders/${orderId}/splits`, { method: "POST", token: session.accessToken, body: payload });
}

// ---------------------------------------------------------------------------
// Inventory
// ---------------------------------------------------------------------------

export type IngredientRecord = {
  id: string;
  name: string;
  unit: string;
  stockQuantity: string;
  lowStockThreshold: string;
  costPerUnit?: string | null;
  low: boolean;
  active: boolean;
  updatedAt: string;
};

export type IngredientRequest = {
  name: string;
  unit: string;
  lowStockThreshold?: number;
  costPerUnit?: number;
  initialStock?: number;
  active?: boolean;
};

export type StockMovementReason = "RESTOCK" | "ORDER" | "ORDER_CANCELLED" | "WASTE" | "ADJUSTMENT";

export type StockMovementRecord = {
  id: string;
  changeQuantity: string;
  balanceAfter: string;
  reason: StockMovementReason;
  orderId?: string | null;
  note?: string | null;
  actorUserId?: string | null;
  createdAt: string;
};

export type RecipeLineRecord = { ingredientId: string; ingredientName: string; unit: string; quantity: string };

export async function getIngredients(session: StaffSession): Promise<IngredientRecord[]> {
  return apiRequest<IngredientRecord[]>("/inventory/ingredients", { token: session.accessToken });
}

export async function getLowStock(session: StaffSession): Promise<IngredientRecord[]> {
  return apiRequest<IngredientRecord[]>("/inventory/low-stock", { token: session.accessToken });
}

export async function createIngredient(session: StaffSession, payload: IngredientRequest): Promise<IngredientRecord> {
  return apiRequest<IngredientRecord>("/inventory/ingredients", { method: "POST", token: session.accessToken, body: payload });
}

export async function updateIngredient(session: StaffSession, id: string, payload: IngredientRequest): Promise<IngredientRecord> {
  return apiRequest<IngredientRecord>(`/inventory/ingredients/${id}`, { method: "PUT", token: session.accessToken, body: payload });
}

export async function restockIngredient(session: StaffSession, id: string, quantity: number, note?: string): Promise<IngredientRecord> {
  return apiRequest<IngredientRecord>(`/inventory/ingredients/${id}/restock`, {
    method: "POST",
    token: session.accessToken,
    body: { quantity, note },
  });
}

export async function adjustIngredient(
  session: StaffSession,
  id: string,
  payload: { reason: "WASTE" | "ADJUSTMENT"; delta?: number; countedQuantity?: number; note?: string },
): Promise<IngredientRecord> {
  return apiRequest<IngredientRecord>(`/inventory/ingredients/${id}/adjust`, { method: "POST", token: session.accessToken, body: payload });
}

export async function getStockMovements(session: StaffSession, id: string): Promise<StockMovementRecord[]> {
  return apiRequest<StockMovementRecord[]>(`/inventory/ingredients/${id}/movements`, { token: session.accessToken });
}

export async function getRecipe(session: StaffSession, menuItemId: string): Promise<RecipeLineRecord[]> {
  return apiRequest<RecipeLineRecord[]>(`/inventory/menu-items/${menuItemId}/recipe`, { token: session.accessToken });
}

export async function setRecipe(
  session: StaffSession,
  menuItemId: string,
  lines: { ingredientId: string; quantity: number }[],
): Promise<RecipeLineRecord[]> {
  return apiRequest<RecipeLineRecord[]>(`/inventory/menu-items/${menuItemId}/recipe`, { method: "PUT", token: session.accessToken, body: lines });
}

// ---------------------------------------------------------------------------
// Menu item images
// ---------------------------------------------------------------------------

export async function uploadMenuItemImage(session: StaffSession, menuItemId: string, file: File): Promise<{ menuItemId: string; imageUrl: string }> {
  const form = new FormData();
  form.append("file", file);
  const response = await fetch(`${API_PREFIX}/menu/items/${menuItemId}/image`, {
    method: "POST",
    headers: { Authorization: `Bearer ${session.accessToken}` },
    body: form,
    cache: "no-store",
  });
  if (!response.ok) {
    throw await rawError(response, "Upload failed");
  }
  return parseResponse<{ menuItemId: string; imageUrl: string }>(response);
}

export async function removeMenuItemImage(session: StaffSession, menuItemId: string): Promise<void> {
  await apiRequest<void>(`/menu/items/${menuItemId}/image`, { method: "DELETE", token: session.accessToken });
}

// ---------------------------------------------------------------------------
// Customer feedback
// ---------------------------------------------------------------------------

export type FeedbackRecord = {
  id: string;
  orderId: string;
  customerUserId?: string | null;
  customerName?: string | null;
  rating: number;
  foodRating?: number | null;
  serviceRating?: number | null;
  deliveryRating?: number | null;
  comment?: string | null;
  response?: string | null;
  respondedAt?: string | null;
  createdAt: string;
};

export type FeedbackSummaryRecord = {
  count: number;
  averageRating?: string | null;
  averageFood?: string | null;
  averageService?: string | null;
  averageDelivery?: string | null;
  distribution: Record<string, number>;
  lowRatings: number;
  responded: number;
};

export type FeedbackRequest = {
  rating: number;
  foodRating?: number;
  serviceRating?: number;
  deliveryRating?: number;
  comment?: string;
  guestName?: string;
};

export async function getFeedback(
  session: StaffSession,
  params: { from?: string; to?: string; maxRating?: number } = {},
): Promise<FeedbackRecord[]> {
  return apiRequest<FeedbackRecord[]>(`/feedback${query(params)}`, { token: session.accessToken });
}

export async function getFeedbackSummary(session: StaffSession, params: { from?: string; to?: string } = {}): Promise<FeedbackSummaryRecord> {
  return apiRequest<FeedbackSummaryRecord>(`/feedback/summary${query(params)}`, { token: session.accessToken });
}

export async function respondToFeedback(session: StaffSession, id: string, response: string): Promise<FeedbackRecord> {
  return apiRequest<FeedbackRecord>(`/feedback/${id}/respond`, { method: "POST", token: session.accessToken, body: { response } });
}

export async function getOrderFeedback(session: StaffSession, orderId: string): Promise<FeedbackRecord | undefined> {
  return apiRequest<FeedbackRecord | undefined>(`/orders/${orderId}/feedback`, { token: session.accessToken });
}

/** Public: a dine-in guest rates their order from the table QR page. */
export async function submitGuestFeedback(tableToken: string, orderId: string, payload: FeedbackRequest): Promise<FeedbackRecord> {
  return apiRequest<FeedbackRecord>(
    `/orders/public/dine-in/tables/${encodeURIComponent(tableToken)}/orders/${orderId}/feedback`,
    { method: "POST", body: payload },
  );
}

// ---------------------------------------------------------------------------
// Report exports (CSV / PDF)
// ---------------------------------------------------------------------------

export type ReportFormat = "CSV" | "PDF";
export type ReportKind = "sales" | "reconciliation" | "audit" | "timesheet";

export async function downloadReport(
  session: StaffSession,
  kind: ReportKind,
  params: { from?: string; to?: string; date?: string; branchId?: string; format: ReportFormat },
): Promise<DownloadedFile> {
  const ext = params.format === "PDF" ? "pdf" : "csv";
  return downloadFile(session, `/reports/${kind}${query(params)}`, `${kind}-report.${ext}`);
}

// ---------------------------------------------------------------------------
// Workforce: scheduling and timesheets
// ---------------------------------------------------------------------------

export type ScheduleRecord = {
  id: string;
  userId: string;
  userName: string;
  branchId?: string | null;
  startsAt: string;
  endsAt: string;
  minutes: number;
  position?: string | null;
  notes?: string | null;
};

export type ScheduleRequest = {
  userId: string;
  branchId?: string;
  startsAt: string;
  endsAt: string;
  position?: string;
  notes?: string;
};

export type TimeEntryRecord = {
  id: string;
  userId: string;
  scheduleId?: string | null;
  clockInAt: string;
  clockOutAt?: string | null;
  breakMinutes: number;
  workedMinutes: number;
  lateMinutes: number;
  notes?: string | null;
  editReason?: string | null;
  open: boolean;
};

export type TimesheetRowRecord = {
  userId: string;
  name: string;
  role: string;
  workedMinutes: number;
  scheduledMinutes: number;
  overtimeMinutes: number;
  lateArrivals: number;
  missedShifts: number;
  entryCount: number;
  entries: TimeEntryRecord[];
};

export async function getSchedules(session: StaffSession, from: string, to: string, userId?: string): Promise<ScheduleRecord[]> {
  return apiRequest<ScheduleRecord[]>(`/scheduling/shifts${query({ from, to, userId })}`, { token: session.accessToken });
}

export async function getMySchedules(session: StaffSession, from: string, to: string): Promise<ScheduleRecord[]> {
  return apiRequest<ScheduleRecord[]>(`/scheduling/my-shifts${query({ from, to })}`, { token: session.accessToken });
}

export async function createSchedule(session: StaffSession, payload: ScheduleRequest): Promise<ScheduleRecord> {
  return apiRequest<ScheduleRecord>("/scheduling/shifts", { method: "POST", token: session.accessToken, body: payload });
}

export async function updateSchedule(session: StaffSession, id: string, payload: ScheduleRequest): Promise<ScheduleRecord> {
  return apiRequest<ScheduleRecord>(`/scheduling/shifts/${id}`, { method: "PUT", token: session.accessToken, body: payload });
}

export async function deleteSchedule(session: StaffSession, id: string): Promise<void> {
  await apiRequest<void>(`/scheduling/shifts/${id}`, { method: "DELETE", token: session.accessToken });
}

export async function getClockStatus(session: StaffSession): Promise<TimeEntryRecord | undefined> {
  return apiRequest<TimeEntryRecord | undefined>("/timeclock/status", { token: session.accessToken });
}

export async function clockIn(session: StaffSession, notes?: string): Promise<TimeEntryRecord> {
  return apiRequest<TimeEntryRecord>("/timeclock/clock-in", { method: "POST", token: session.accessToken, body: { notes } });
}

export async function clockOut(session: StaffSession, breakMinutes?: number): Promise<TimeEntryRecord> {
  return apiRequest<TimeEntryRecord>("/timeclock/clock-out", { method: "POST", token: session.accessToken, body: { breakMinutes } });
}

export async function editTimeEntry(
  session: StaffSession,
  id: string,
  payload: { clockInAt?: string; clockOutAt?: string; breakMinutes?: number; reason: string },
): Promise<TimeEntryRecord> {
  return apiRequest<TimeEntryRecord>(`/timeclock/entries/${id}`, { method: "PUT", token: session.accessToken, body: payload });
}

export async function getTimesheet(session: StaffSession, from: string, to: string, userId?: string): Promise<TimesheetRowRecord[]> {
  return apiRequest<TimesheetRowRecord[]>(`/timeclock/timesheet${query({ from, to, userId })}`, { token: session.accessToken });
}

export async function getMyTimesheet(session: StaffSession, from: string, to: string): Promise<TimesheetRowRecord[]> {
  return apiRequest<TimesheetRowRecord[]>(`/timeclock/my-timesheet${query({ from, to })}`, { token: session.accessToken });
}

// ---------------------------------------------------------------------------
// Account recovery
// ---------------------------------------------------------------------------

export async function requestPasswordReset(emailOrPhone: string): Promise<{ message: string }> {
  return apiRequest<{ message: string }>("/auth/password/forgot", { method: "POST", body: { emailOrPhone } });
}

export async function resetPassword(token: string, newPassword: string): Promise<void> {
  await apiRequest<void>("/auth/password/reset", { method: "POST", body: { token, newPassword } });
}

export async function changePassword(session: StaffSession, currentPassword: string, newPassword: string): Promise<void> {
  await apiRequest<void>("/auth/password/change", { method: "POST", token: session.accessToken, body: { currentPassword, newPassword } });
}

export async function requestEmailVerification(session: StaffSession): Promise<void> {
  await apiRequest<void>("/auth/email/verification", { method: "POST", token: session.accessToken });
}

export async function verifyEmail(token: string): Promise<void> {
  await apiRequest<void>("/auth/email/verify", { method: "POST", body: { token } });
}

export async function adminResetUserPassword(session: StaffSession, userId: string): Promise<void> {
  await apiRequest<void>(`/users/${userId}/password-reset`, { method: "POST", token: session.accessToken });
}

export type StaffMemberRecord = { id: string; name: string; role: StaffRole; branchId?: string | null };

/** Staff who can be put on the rota (managers and admins). */
export async function getSchedulableStaff(session: StaffSession): Promise<StaffMemberRecord[]> {
  return apiRequest<StaffMemberRecord[]>("/scheduling/staff", { token: session.accessToken });
}
