# WebSocket & SSE Event Specification

## Overview

The system uses two real-time channels:
- **WebSocket (STOMP over SockJS)** — bidirectional, used for staff dashboard and kitchen display
- **Server-Sent Events (SSE)** — unidirectional push, used for customer order tracking in the mobile app

Both channels require a valid JWT. The token is passed as a query parameter on connect:
`wss://api.restaurantmanager.com/ws?token=<accessToken>`

---

## WebSocket Topics (STOMP)

### Subscribe destinations

| Destination                          | Who subscribes        | Description                              |
|--------------------------------------|-----------------------|------------------------------------------|
| `/topic/orders.new`                  | Kitchen, Cashier      | New order created                        |
| `/topic/orders.status`               | Kitchen, Cashier      | Any order status change                  |
| `/topic/tables`                      | Cashier, Manager      | Table status changes                     |
| `/user/queue/order.{orderId}`        | Customer (own order)  | Status updates for a specific order      |
| `/user/queue/group.{sessionCode}`    | Group participants    | Group cart updates (item added/removed)  |
| `/user/queue/payment.{paymentId}`    | Customer              | Payment status updates                   |
| `/topic/kitchen`                     | Kitchen display       | All order events for kitchen view        |

### Publish destinations (client → server)

| Destination                          | Who publishes         | Description                              |
|--------------------------------------|-----------------------|------------------------------------------|
| `/app/group.{sessionCode}.addItem`   | Group participant     | Add item to group cart                   |
| `/app/group.{sessionCode}.removeItem`| Group participant     | Remove item from group cart              |

---

## Event Payloads

### `ORDER_CREATED`
```json
{
  "event": "ORDER_CREATED",
  "orderId": "uuid",
  "orderNumber": "ORD-20260323-0042",
  "type": "DINE_IN",
  "tableNumber": "T3",
  "status": "PENDING",
  "itemCount": 3,
  "total": "75.00",
  "createdAt": "2026-03-23T14:00:00Z"
}
```

### `ORDER_STATUS_CHANGED`
```json
{
  "event": "ORDER_STATUS_CHANGED",
  "orderId": "uuid",
  "orderNumber": "ORD-20260323-0042",
  "previousStatus": "CONFIRMED",
  "newStatus": "PREPARING",
  "updatedAt": "2026-03-23T14:05:00Z"
}
```

### `TABLE_STATUS_CHANGED`
```json
{
  "event": "TABLE_STATUS_CHANGED",
  "tableId": "uuid",
  "tableNumber": "T3",
  "previousStatus": "AVAILABLE",
  "newStatus": "OCCUPIED",
  "updatedAt": "2026-03-23T14:00:00Z"
}
```

### `GROUP_CART_UPDATED`
```json
{
  "event": "GROUP_CART_UPDATED",
  "sessionCode": "GRP-4829",
  "participantId": "uuid",
  "displayName": "Ama",
  "action": "ITEM_ADDED",
  "item": {
    "menuItemId": "uuid",
    "name": "Jollof Rice",
    "quantity": 1,
    "price": "25.00"
  },
  "groupTotal": "75.00",
  "updatedAt": "2026-03-23T14:03:00Z"
}
```

### `PAYMENT_STATUS_CHANGED`
```json
{
  "event": "PAYMENT_STATUS_CHANGED",
  "paymentId": "uuid",
  "orderId": "uuid",
  "previousStatus": "PENDING",
  "newStatus": "SUCCESS",
  "amount": "75.00",
  "method": "MOBILE_MONEY",
  "updatedAt": "2026-03-23T14:06:00Z"
}
```

### `PAYMENT_FAILED`
```json
{
  "event": "PAYMENT_FAILED",
  "paymentId": "uuid",
  "orderId": "uuid",
  "reason": "Insufficient funds",
  "updatedAt": "2026-03-23T14:06:30Z"
}
```

---

## SSE Endpoint (Mobile App Fallback)

Used when WebSocket is unavailable (e.g., unstable mobile data).

**Endpoint:** `GET /api/v1/events/orders/{orderId}`
**Headers:** `Authorization: Bearer <token>`
**Response:** `text/event-stream`

```
data: {"event":"ORDER_STATUS_CHANGED","orderId":"uuid","newStatus":"PREPARING"}

data: {"event":"ORDER_STATUS_CHANGED","orderId":"uuid","newStatus":"READY"}
```

The mobile app attempts WebSocket first; falls back to SSE polling every 10 seconds if the WebSocket connection fails.

---

## Reconnection Strategy

| Channel   | Strategy                                                                 |
|-----------|--------------------------------------------------------------------------|
| WebSocket | Exponential backoff: 1s, 2s, 4s, 8s, max 30s. Show "reconnecting" UI.  |
| SSE       | Retry header set to 5000ms by server. Client retries automatically.      |

On reconnect, client should re-fetch the current order status via REST to reconcile any missed events.

