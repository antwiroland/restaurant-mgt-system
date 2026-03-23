# Offline Strategy

## Scope

Ghana's mobile internet is reliable in urban areas but can drop indoors and in dense locations. This document defines exactly what works offline, what is deferred, and how the system syncs when connectivity returns.

---

## What Works Offline

### Mobile App (Expo)

| Feature                        | Offline Behaviour                                                    |
|--------------------------------|----------------------------------------------------------------------|
| Browse menu                    | Served from local cache (last fetched copy). Prices/availability may be stale — show "last updated" timestamp. |
| Build a cart                   | Fully functional offline. Items held in local state.                 |
| View past orders               | Cached order history is readable.                                    |
| View a receipt                 | Cached receipt is readable.                                          |
| View reservation details       | Cached reservation is readable.                                      |

### Mobile App — Deferred (Queued for sync)

| Feature                        | Behaviour                                                            |
|--------------------------------|----------------------------------------------------------------------|
| Submit an order                | Order is saved to a local queue with status `QUEUED`. UI shows "Order will be placed when you're back online." |
| Make a reservation             | Queued for sync. User warned of possible double-booking.             |

### Mobile App — Requires Connectivity

| Feature                        | Reason                                                               |
|--------------------------------|----------------------------------------------------------------------|
| Payment                        | Paystack requires live API call. Blocked with clear message.         |
| Real-time order tracking       | WebSocket/SSE unavailable. Show last known status from cache.        |
| QR code scan                   | Token validation requires server. Show friendly error.               |

---

### Staff App (Next.js)

The staff app is a web app and does not have a robust offline mode. However:

| Feature                        | Offline Behaviour                                                    |
|--------------------------------|----------------------------------------------------------------------|
| POS — build an order           | Cached menu allows cart building.                                    |
| POS — submit order             | Blocked. Show "No connection — cannot submit order."                 |
| View table map                 | Last rendered state shown (no live updates).                         |
| View receipts                  | Previously loaded receipts are accessible via browser cache.         |

---

## Cache Strategy

### Mobile App (Expo — AsyncStorage + MMKV)

| Data            | Cache Duration   | Invalidation Trigger                           |
|-----------------|------------------|------------------------------------------------|
| Menu            | 1 hour           | Staff triggers menu update (push event)        |
| Order history   | 24 hours         | New order created or status changes            |
| Receipts        | 7 days           | Never (immutable once issued)                  |
| Reservations    | 2 hours          | Reservation status change event                |
| User profile    | Session lifetime | User updates profile                           |

### Staff App (Next.js — SWR / React Query)

| Data            | Cache Duration   | Notes                                          |
|-----------------|------------------|------------------------------------------------|
| Menu            | 5 minutes        | Revalidated on window focus                    |
| Active orders   | Real-time (WS)   | Fallback to 10-second polling                  |
| Tables          | Real-time (WS)   | Fallback to 30-second polling                  |

---

## Offline Queue (Mobile App)

Deferred actions are stored in a local queue (persistent, survives app restart):

```
Queue entry shape:
{
  id: "local-uuid",
  action: "CREATE_ORDER" | "CREATE_RESERVATION",
  payload: { ... },
  createdAt: "2026-03-23T14:00:00Z",
  retryCount: 0
}
```

**Sync on reconnect:**
1. App detects connectivity via `NetInfo` listener.
2. Process queue in FIFO order.
3. On success: remove from queue, update local state with server response.
4. On conflict (e.g., table already taken): notify user, offer alternatives.
5. On failure after 3 retries: mark as failed, notify user.

---

## Connectivity Detection

```
Mobile App:
- Use `@react-native-community/netinfo`
- Show offline banner (yellow) when disconnected
- Show "Reconnecting..." when attempting
- Show "Back online — syncing..." when reconnected

Staff App:
- Use `navigator.onLine` + fetch probe to `/api/v1/health`
- Toast notification on status change
```

---

## User-Facing Messages

| State               | Message shown to user                                   |
|---------------------|---------------------------------------------------------|
| No connection       | "You're offline. Menu shown from last update."          |
| Order queued        | "Order saved. It will be placed when you reconnect."    |
| Payment blocked     | "Payment requires internet connection."                 |
| Syncing queue       | "Back online — placing your order now..."               |
| Sync conflict       | "That table was taken. Please choose another."          |
| Sync failed         | "Could not place order. Please try again."              |

---

## Excluded from Offline Support

- Payment initiation (security + real-time requirement)
- Manager PIN verification (security requirement)
- QR token validation
- Real-time group ordering (multi-user coordination)
- Analytics and reports
