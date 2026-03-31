export type Role = 'ADMIN' | 'MANAGER' | 'CASHIER' | 'CUSTOMER';

export type AuthResponse = {
  id: string;
  name: string;
  phone: string;
  role: Role;
  accessToken: string;
  refreshToken: string;
};

export type LoginResponse = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: { id: string; name: string; role: Role };
};

export type MenuCategory = {
  id: string;
  name: string;
  description?: string;
  displayOrder: number;
  items: MenuItem[];
};

export type MenuItem = {
  id: string;
  name: string;
  description: string;
  price: string;
  imageUrl?: string;
  available: boolean;
  categoryId: string;
};

export type MenuModifierOption = {
  id: string;
  name: string;
  priceDelta: string;
};

export type MenuModifierGroup = {
  id: string;
  name: string;
  selectionType: 'SINGLE' | 'MULTIPLE';
  required: boolean;
  minSelect?: number;
  maxSelect?: number;
  options: MenuModifierOption[];
};

export type Table = {
  id: string;
  number: string;
  capacity: number;
  zone: string;
  status: 'AVAILABLE' | 'OCCUPIED' | 'RESERVED' | 'CLOSED';
  qrToken: string;
};

export type TableScanResult = {
  tableId: string;
  tableNumber: string;
  status: string;
};

export type OrderItem = {
  id: string;
  menuItemId: string;
  menuItemName: string;
  quantity: number;
  unitPrice: string;
  totalPrice: string;
  participantId?: string;
  notes?: string;
  modifiers?: { id: string; groupName: string; optionName: string; priceDelta: string }[];
};

export type OrderType = 'DINE_IN' | 'PICKUP' | 'DELIVERY';
export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'PREPARING' | 'READY' | 'COMPLETED' | 'CANCELLED' | 'VOIDED';

export type Order = {
  id: string;
  status: OrderStatus;
  type: OrderType;
  subtotal: string;
  total: string;
  items: OrderItem[];
  tableId?: string;
  tableNumber?: string;
  deliveryAddress?: string;
  notes?: string;
  createdAt: string;
  pickupCode?: string;
};

export type CreateOrderRequest = {
  type: OrderType;
  tableId?: string;
  tableToken?: string;
  items: { menuItemId: string; quantity: number; notes?: string; modifierOptionIds?: string[] }[];
  promoCode?: string;
  groupSessionId?: string;
  deliveryAddress?: string;
  notes?: string;
};

export type PaymentMethod = 'MOBILE_MONEY' | 'CARD';
export type PaymentStatus = 'INITIATED' | 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED' | 'VOIDED';

export type Payment = {
  id: string;
  orderId: string;
  amount: string;
  status: PaymentStatus;
  method: PaymentMethod;
  paidAt?: string;
};

export type PaymentInitiateResponse = {
  paymentId: string;
  status: PaymentStatus;
  paystackReference: string;
  authorizationUrl: string;
  message: string;
};

export type Receipt = {
  receiptNumber: string;
  paymentId: string;
  orderId: string;
  subtotal: string;
  total: string;
  currency: string;
  paymentMethod: PaymentMethod;
  paidAt: string;
};

export type Reservation = {
  id: string;
  customerName: string;
  customerPhone: string;
  tableId: string;
  tableNumber: string;
  partySize: number;
  reservedAt: string;
  durationMins: number;
  notes?: string;
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'NO_SHOW';
};

export type CreateReservationRequest = {
  tableId: string;
  customerName: string;
  customerPhone: string;
  partySize: number;
  reservedAt: string;
  durationMins: number;
  notes?: string;
};

export type GroupSession = {
  sessionId: string;
  sessionCode: string;
  status: 'OPEN' | 'COMPLETED' | 'CANCELLED';
};

export type GroupSessionItem = {
  id: string;
  menuItemId: string;
  menuItemName: string;
  quantity: number;
  unitPrice: string;
  totalPrice: string;
  notes?: string;
};

export type GroupSessionDetail = {
  sessionCode: string;
  status: 'OPEN' | 'COMPLETED' | 'CANCELLED';
  participants: {
    participantId: string;
    displayName: string;
    items: GroupSessionItem[];
    subtotal: string;
  }[];
  groupTotal: string;
};

export type LoyaltyBalance = {
  customerId: string;
  points: number;
};

export type LoyaltyTransaction = {
  id: string;
  points: number;
  type: 'EARN' | 'REDEEM';
  orderId: string;
  createdAt: string;
};

export type PromoValidation = {
  code: string;
  discountType: 'PERCENTAGE' | 'FLAT';
  discountValue: string;
  valid: boolean;
};

export type ApiError = {
  status: number;
  error: string;
  message: string;
  timestamp: string;
};
