#!/usr/bin/env python3
"""
Create fresh pickup orders on a running local backend, for testing pickup at the counter.

The main seeder's "pickup awaiting payment" is cancelled 30 minutes after seeding (UnpaidPickupExpiryJob), so
run this whenever you need pickups to test with. It adds, for the seeded customer Efua Quaye:

  * a pickup awaiting payment (also cancelled after 30 minutes if nobody pays it)
  * a pickup that is paid (cash) and READY, to try the handover at Orders > Pickup: it needs the customer's
    pickup code, printed below (in real life the customer shows it from their app or receipt)

Needs the database seeded by seed_local.py (password Passw0rd!). Safe to run repeatedly: each run adds new orders.

    python scripts/dev/seed_pickup_demo.py [--api http://localhost:8080]
"""
from __future__ import annotations

import argparse
import json
import urllib.error
import urllib.request

PASSWORD = "Passw0rd!"
ADMIN_PHONE = "+233200000001"
CUSTOMER_PHONE = "+233240000001"


def call(base: str, method: str, path: str, body=None, token: str | None = None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            text = response.read().decode() or "null"
            return json.loads(text) if text.strip().startswith(("{", "[")) else text
    except urllib.error.HTTPError as err:
        raise SystemExit(f"{method} {path} -> {err.code}: {err.read().decode(errors='replace')[:400]}")
    except urllib.error.URLError as err:
        raise SystemExit(f"Cannot reach {base} ({err.reason}). Is the backend running?")


def login(base: str, phone: str) -> str:
    return call(base, "POST", "/auth/login", {"phone": phone, "password": PASSWORD})["accessToken"]


def pick_dishes(base: str) -> list[dict]:
    """Two available dishes without required modifiers, so the order needs no choices."""
    menu = call(base, "GET", "/menu/items")
    items = menu.get("content", menu) if isinstance(menu, dict) else menu
    simple = []
    for item in items:
        if not item.get("available", True):
            continue
        groups = call(base, "GET", f"/menu/items/{item['id']}/modifiers") or []
        if any(group.get("required") for group in groups):
            continue
        simple.append(item)
        if len(simple) == 2:
            break
    if not simple:
        raise SystemExit("No available dish without required modifiers. Seed the menu first (seed_local.py).")
    return simple


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api", default="http://localhost:8080", help="backend base URL")
    base = parser.parse_args().api.rstrip("/")

    admin = login(base, ADMIN_PHONE)
    me = call(base, "GET", "/users/me", token=admin)
    customer = login(base, CUSTOMER_PHONE)
    lines = [{"menuItemId": dish["id"], "quantity": 1} for dish in pick_dishes(base)]

    unpaid = call(base, "POST", "/orders", {"type": "PICKUP", "items": lines,
                                           "notes": "Customer paying at the counter"}, customer)

    ready = call(base, "POST", "/orders", {"type": "PICKUP", "items": lines}, customer)
    # Cash goes into the admin's drawer: open one if they have none (it stays open; close it on the Shifts page).
    if not any(shift["cashierUserId"] == me["id"] for shift in call(base, "GET", "/shifts/active", token=admin)):
        call(base, "POST", "/shifts/open", {"openingCash": 0, "notes": "Pickup demo drawer"}, admin)
    call(base, "POST", "/payments/initiate", {"orderId": ready["id"], "method": "CASH",
                                             "idempotencyKey": f"pickup-demo-{ready['id']}",
                                             "amountTendered": float(ready["total"])}, admin)
    for status in ("PREPARING", "READY"):
        call(base, "PATCH", f"/orders/{ready['id']}/status", {"status": status}, admin)

    number = lambda order: order["id"][:8].upper()
    print("Pickup demo orders (customer Efua Quaye):")
    print(f"  Awaiting payment : #{number(unpaid)}  total {unpaid['total']}  (cancelled after 30 min if unpaid)")
    print(f"  Paid and READY   : #{number(ready)}  total {ready['total']}  pickup code {ready['pickupCode']}")
    print("Try the handover at Orders > Pickup: search the order number, then enter the pickup code.")
    print("As a cashier the code is required; an admin or manager can also hand over without it.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
