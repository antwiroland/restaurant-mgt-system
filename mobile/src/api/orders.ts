import { apiClient } from './client';
import type { Order, OrderItem, CreateOrderRequest, Receipt } from '../types/api';

type OrderItemResponse = {
  id: string;
  menuItemId: string;
  participantId?: string;
  name: string;
  price: string;
  quantity: number;
  notes?: string;
};

type OrderResponse = {
  id: string;
  type: Order['type'];
  status: Order['status'];
  subtotal: string;
  total: string;
  items: OrderItemResponse[];
  tableId?: string;
  tableNumber?: string;
  deliveryAddress?: string;
  notes?: string;
  createdAt: string;
  pickupCode?: string;
};

type ReceiptResponse = {
  receiptNumber: string;
  paymentId: string;
  orderId: string;
  subtotal: string;
  total: string;
  currency: string;
  paymentMethod: Receipt['paymentMethod'];
  paidAt: string;
};

function toOrderItem(item: OrderItemResponse): OrderItem {
  const unitPrice = String(item.price);
  return {
    id: item.id,
    menuItemId: item.menuItemId,
    menuItemName: item.name,
    unitPrice,
    totalPrice: (parseFloat(unitPrice) * item.quantity).toFixed(2),
    quantity: item.quantity,
    participantId: item.participantId,
    notes: item.notes,
  };
}

function toOrder(order: OrderResponse): Order {
  return {
    id: order.id,
    type: order.type,
    status: order.status,
    subtotal: String(order.subtotal),
    total: String(order.total),
    items: order.items.map(toOrderItem),
    tableId: order.tableId,
    tableNumber: order.tableNumber,
    deliveryAddress: order.deliveryAddress,
    notes: order.notes,
    createdAt: order.createdAt,
    pickupCode: order.pickupCode,
  };
}

export async function createOrder(req: CreateOrderRequest): Promise<Order> {
  const { data } = await apiClient.post<OrderResponse>('/orders', req);
  return toOrder(data);
}

export async function fetchMyOrders(params?: { status?: string; type?: string }): Promise<Order[]> {
  const { data } = await apiClient.get<OrderResponse[]>('/orders', { params });
  return data.map(toOrder);
}

export async function fetchOrder(id: string): Promise<Order> {
  const { data } = await apiClient.get<OrderResponse>(`/orders/${id}`);
  return toOrder(data);
}

export async function cancelOrder(id: string): Promise<void> {
  await apiClient.delete(`/orders/${id}`);
}

export async function fetchReceipt(orderId: string): Promise<Receipt> {
  const { data } = await apiClient.get<ReceiptResponse>(`/orders/${orderId}/receipt`);
  return {
    receiptNumber: data.receiptNumber,
    paymentId: data.paymentId,
    orderId: data.orderId,
    subtotal: String(data.subtotal),
    total: String(data.total),
    currency: data.currency,
    paymentMethod: data.paymentMethod,
    paidAt: data.paidAt,
  };
}
