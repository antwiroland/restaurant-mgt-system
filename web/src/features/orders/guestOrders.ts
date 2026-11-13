/**
 * The table orders this phone placed, so the guest can rate them after the table has moved on to the next party
 * (the table's own pages only ever show the current party). Only order numbers are kept, never items or amounts.
 *
 * The server decides how long an order can be rated (two hours after it is served) and answers 410 after that; the
 * phone then forgets it. As a backstop, entries older than MAX_AGE_MS are dropped here too, so the same customer
 * back at the table tomorrow is never prompted about yesterday.
 */

const STORAGE_KEY = "rm-guest-orders";
const MAX_AGE_MS = 8 * 60 * 60 * 1000; // a long meal (served late) plus the two-hour rating window

export type RememberedOrder = { orderId: string; tableToken: string; savedAt: number };

function read(): RememberedOrder[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? (JSON.parse(raw) as RememberedOrder[]) : [];
    return Array.isArray(parsed) ? parsed.filter((o) => o && typeof o.orderId === "string" && typeof o.savedAt === "number") : [];
  } catch {
    return [];
  }
}

function write(orders: RememberedOrder[]) {
  try {
    if (orders.length === 0) window.localStorage.removeItem(STORAGE_KEY);
    else window.localStorage.setItem(STORAGE_KEY, JSON.stringify(orders));
  } catch {
    // Private mode: nothing is remembered, which only means no rating prompt later.
  }
}

/** Orders this phone placed at this table that may still be rateable (old ones are dropped). */
export function rememberedOrders(tableToken: string, now = Date.now()): RememberedOrder[] {
  const all = read();
  const fresh = all.filter((o) => now - o.savedAt < MAX_AGE_MS);
  if (fresh.length !== all.length) write(fresh);
  return fresh.filter((o) => o.tableToken === tableToken);
}

export function rememberOrder(tableToken: string, orderId: string, now = Date.now()) {
  const others = read().filter((o) => o.orderId !== orderId && now - o.savedAt < MAX_AGE_MS);
  write([...others, { orderId, tableToken, savedAt: now }].slice(-20));
}

export function forgetOrder(orderId: string) {
  write(read().filter((o) => o.orderId !== orderId));
}
