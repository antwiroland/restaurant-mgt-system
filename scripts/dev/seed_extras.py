"""
Demo data for the features seed_local.py and seed_hr.py leave empty, so every screen has something to show.

Called from seed_local.py after the orders, reservations and HR data exist. It covers:
  * restaurant profile, receipt/kitchen printers and a photo for every dish
  * East Legon order history (so the dashboard compares branches)
  * customer extras: saved addresses, favourites, loyalty points spent at checkout, a failed MoMo payment and its retry
  * a second cashier shift opened and closed with a cash shortfall, and yesterday's reconciliation signed off
  * financial controls: a manual discount, a void, a full refund and a part refund (manager PIN approval)
  * front of house: a split bill part-paid, a guest split on the QR page, waiter calls and a bill request,
    a table served and still unpaid, a walkout, guest feedback, a group order split by what each person ordered
  * manager replies to reviews, a customer's own reservation and a booking about to start
  * stock movements: a delivery, waste and a stock count, and one ingredient counted to zero (its dish leaves the menu)
  * a pickup still waiting for payment (it expires after 30 minutes, like the real thing)

All data is fictional test data for local development only.
"""
from __future__ import annotations

import random
import struct
import time
import urllib.error
import urllib.request
import uuid
import zlib
from datetime import datetime, timedelta, timezone


def _png(width: int, height: int, top: tuple, bottom: tuple, food: tuple, accent: tuple, seed: int) -> bytes:
    """A simple plated-dish illustration (no text): gradient table, cream plate, food in the middle, garnish dots."""
    rnd = random.Random(seed)
    cx, cy = width / 2, height / 2 + 6
    plate_r, food_r = min(width, height) * 0.40, min(width, height) * 0.25
    dots = [(cx + rnd.uniform(-food_r, food_r) * 0.7, cy + rnd.uniform(-food_r, food_r) * 0.7, rnd.uniform(5, 11)) for _ in range(9)]
    rows = []
    for y in range(height):
        t = y / (height - 1)
        bg = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        row = bytearray(b"\x00")
        for x in range(width):
            dx, dy = x - cx, y - cy
            d2 = dx * dx + dy * dy
            if d2 <= food_r * food_r:
                shade = 1 - 0.18 * (d2 / (food_r * food_r))
                px = tuple(int(c * shade) for c in food)
                for gx, gy, gr in dots:
                    if (x - gx) ** 2 + (y - gy) ** 2 <= gr * gr:
                        px = accent
                        break
            elif d2 <= plate_r * plate_r:
                rim = (d2 ** 0.5 - food_r) / (plate_r - food_r)
                px = (int(250 - 18 * rim), int(244 - 20 * rim), int(232 - 24 * rim))
            elif d2 <= (plate_r + 5) ** 2 and dy > 0:
                px = tuple(max(0, c - 40) for c in bg)  # plate shadow
            else:
                px = bg
            row.extend(px)
        rows.append(bytes(row))

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"".join(rows), 6)) + chunk(b"IEND", b""))


# Plate colours per category: (table top, table bottom), and per dish the food colour and garnish colour
TABLE_COLOURS = {"Small Plates": ((92, 58, 40), (60, 36, 24)), "Mains": ((70, 52, 42), (40, 28, 22)),
                 "Desserts": ((150, 112, 90), (110, 78, 62)), "Drinks": ((48, 72, 70), (28, 44, 44))}
FOOD_COLOURS = {
    "Kelewele": ((214, 128, 40), (120, 70, 30)), "Grilled Chichinga": ((128, 64, 36), (200, 60, 70)),
    "Garden Eggs & Shito": ((230, 222, 190), (90, 40, 30)), "Crab Croquettes": ((200, 140, 70), (240, 200, 120)),
    "Spring Rolls": ((215, 165, 85), (200, 40, 40)), "Jollof & Grilled Tilapia": ((205, 70, 35), (110, 150, 70)),
    "Waakye Bowl": ((120, 70, 70), (240, 220, 180)), "Light Soup & Fufu": ((200, 80, 50), (245, 238, 220)),
    "Banku & Okro Stew": ((90, 120, 50), (240, 235, 215)), "Charcoal Chicken": ((150, 80, 40), (230, 200, 90)),
    "Red Red": ((175, 60, 30), (230, 160, 60)), "Bofrot & Caramel": ((200, 140, 70), (120, 70, 30)),
    "Coconut Panna Cotta": ((245, 240, 228), (250, 200, 60)), "Sobolo": ((120, 20, 45), (200, 60, 90)),
    "Asaana": ((170, 110, 50), (230, 200, 160)), "Fresh Coconut": ((235, 235, 220), (110, 150, 70)),
    "Pineapple Ginger Juice": ((245, 200, 60), (250, 230, 150)),
}


def upload_image(base: str, token: str, item_id: str, filename: str, data: bytes) -> bool:
    boundary = "----seed" + uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
            f"Content-Type: image/png\r\n\r\n").encode() + data + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(f"{base}/menu/items/{item_id}/image", data=body, method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30):
            return True
    except urllib.error.HTTPError as err:
        print(f"  (photo for {filename} not uploaded: {err.code} {err.read().decode(errors='replace')[:160]})")
        return False


def _ok(res) -> bool:
    return not (isinstance(res, dict) and "_error" in res)


def seed_extras(api, ctx: dict, step=print) -> dict:
    """ctx keys: tokens, users, staff_phones, customers, items, modifier_groups, tables, elg_tables, zones, osu, legon,
    basket, pay, advance, h2_sql, db_url, road_order_id, now, iso."""
    # The seed runs longer than an access token lives (15 min): sign everyone in again first.
    for phone in list(ctx["tokens"]):
        ctx["tokens"][phone] = api.call("POST", "/auth/login", {"phone": phone, "password": ctx["password"]})["accessToken"]
        time.sleep(1.05)  # refresh tokens are second-granular
    A = ctx["tokens"]["+233200000001"]
    M = ctx["tokens"]["+233200000002"]
    C1, C2 = ctx["tokens"]["+233200000003"], ctx["tokens"]["+233200000004"]
    R2 = ctx["tokens"]["+233200000006"]
    items, tables, zones, osu, legon = ctx["items"], ctx["tables"], ctx["zones"], ctx["osu"], ctx["legon"]
    basket, pay, advance, sql, iso = ctx["basket"], ctx["pay"], ctx["advance"], ctx["h2_sql"], ctx["iso"]
    now = datetime.now(timezone.utc)
    summary: dict = {}

    def stamp(dt: datetime) -> str:
        return dt.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")

    def override(action: str) -> str:
        time.sleep(0.05)
        return api.call("POST", "/auth/pin/verify", {"pin": "2468", "actionType": action}, M)["overrideToken"]

    def pay_cash(order_id: str, token: str, tendered=None, split_id=None):
        body = {"orderId": order_id, "method": "CASH", "idempotencyKey": f"seed-cash-{uuid.uuid4()}"}
        if split_id:
            body["splitId"] = split_id
        if tendered is not None:
            body["amountTendered"] = tendered
        return api.call("POST", "/payments/initiate", body, token)

    def pay_momo(order_id: str, token: str, phone="+233244000111", split_id=None):
        body = {"orderId": order_id, "method": "MOBILE_MONEY", "momoPhone": phone, "idempotencyKey": f"seed-momo-{uuid.uuid4()}"}
        if split_id:
            body["splitId"] = split_id
        init = api.call("POST", "/payments/initiate", body, token)
        payment = api.call("GET", f"/payments/{init['paymentId']}", token=token)
        api.webhook_success(init["paystackReference"], payment["amount"])
        return init

    def table_status():
        return {t["id"]: t for t in api.call("GET", "/tables", token=A)}

    def free_osu_tables():
        current = table_status()
        return [t for t in tables if t["number"] != "7" and current.get(t["id"], {}).get("status") == "AVAILABLE"]

    def dine_in(table, token, n=2, notes=None):
        body = {"type": "DINE_IN", "tableId": table["id"], "items": basket(n)}
        if notes:
            body["notes"] = notes
        return api.call("POST", "/orders", body, token)

    # ------------------------------------------------------------ restaurant profile, printers, photos
    step("Extras: restaurant profile & printers")
    api.call("PUT", "/restaurant/profile", {
        "name": "Hearth Kitchen Accra", "tagline": "Ghanaian classics from the grill and the pot",
        "about": "A neighbourhood restaurant serving smoky jollof, charcoal-grilled fish and house-made drinks. "
                 "Two branches: Osu (Oxford Street) and East Legon. (Test data.)",
        "address": "12 Oxford Street, Osu, Accra", "phone": "+233302000100", "email": "hello@hearth.example.test",
        "websiteUrl": "https://hearth.example.test", "currency": "GHS",
        "openingHours": ["Mon-Thu 11:00-22:00", "Fri-Sat 11:00-23:30", "Sun 12:00-21:00"],
        "latitude": 5.5560, "longitude": -0.1820, "receiptFooter": "Thank you for dining with us! Medaase.",
    }, A, ok=False)
    for name, kind, host, branch in [("Osu front counter", "RECEIPT", "192.168.10.21", osu), ("Osu kitchen pass", "KITCHEN", "192.168.10.22", osu),
                                     ("East Legon counter", "RECEIPT", "192.168.20.21", legon)]:
        api.call("POST", "/printers", {"name": name, "kind": kind, "host": host, "port": 9100, "paperWidthMm": 80,
                                       "branchId": branch["id"], "active": True}, A, ok=False)

    step("Extras: dish photos")
    category_by_item = {item["id"]: item.get("categoryName", "") for item in items.values()}
    photos = 0
    for index, (name, item) in enumerate(items.items()):
        food, accent = FOOD_COLOURS.get(name, ((180, 120, 60), (240, 220, 180)))
        top, bottom = TABLE_COLOURS.get(category_by_item.get(item["id"], ""), ((80, 56, 44), (48, 32, 26)))
        slug = "".join(c if c.isalnum() else "-" for c in name.lower()).strip("-")
        photos += upload_image(api.base, A, item["id"], f"{slug}.png", _png(480, 360, top, bottom, food, accent, index))
    summary["photos"] = photos

    # ------------------------------------------------------------ East Legon history
    step("Extras: East Legon order history")
    elg_tables = [t for t in api.call("GET", "/tables", token=A) if t.get("branchId") == legon["id"]]
    elg_orders = []
    for i in range(18):
        table = elg_tables[i % len(elg_tables)]
        body = {"type": "DINE_IN", "tableId": table["id"], "items": basket(random.randint(1, 4))}
        if random.random() < 0.5:
            body["tipAmount"] = random.choice([5, 10, 20])
        order = api.call("POST", "/orders", body, A)
        pay(order, A)
        advance(order["id"], A, ["PREPARING", "READY", "COMPLETED"])
        elg_orders.append(order["id"])
    statements = []
    for order_id in elg_orders:
        when = (now - timedelta(days=random.randint(1, 28))).replace(hour=random.choice([12, 13, 19, 20]), minute=random.randint(0, 59), second=0, microsecond=0)
        ts = stamp(when)
        statements.append(f"UPDATE orders SET created_at=TIMESTAMP '{ts}', updated_at=TIMESTAMP '{ts}' + INTERVAL '50' MINUTE, "
                          f"served_at=TIMESTAMP '{ts}' + INTERVAL '40' MINUTE WHERE id='{order_id}'")
        statements.append(f"UPDATE payments SET created_at=TIMESTAMP '{ts}' + INTERVAL '45' MINUTE, updated_at=TIMESTAMP '{ts}' + INTERVAL '46' MINUTE, "
                          f"paid_at=CASE WHEN paid_at IS NULL THEN NULL ELSE TIMESTAMP '{ts}' + INTERVAL '46' MINUTE END WHERE order_id='{order_id}'")
    for chunk in range(0, len(statements), 40):
        sql(ctx["db_url"], ";".join(statements[chunk:chunk + 40]))
    summary["elg_orders"] = len(elg_orders)

    # ------------------------------------------------------------ customers
    step("Extras: customer addresses, favourites, loyalty, failed payment")
    customers = ctx["customers"]
    ctoken = {phone: ctx["tokens"][phone] for phone, _ in customers}
    first_phone, first_name = customers[0]
    api.call("PATCH", "/users/me", {"name": first_name, "email": "efua.quaye@example.test", "phone": first_phone}, ctoken[first_phone], ok=False)
    addresses = [(0, "Home", "14 Ring Road East", "Accra", "Opposite the Total filling station", True),
                 (0, "Work", "Airport City, Africa Trade House, 3rd floor", "Accra", "Ask at reception", False),
                 (1, "Home", "22 Lagos Avenue, East Legon", "Accra", "Blue gate", True),
                 (2, "Home", "7 Spintex Road", "Accra", "Near Palace Mall", True),
                 (2, "Mum's", "3 Asylum Down Close", "Accra", None, False)]
    for who, label, line_, city, landmark, default in addresses:
        phone = customers[who][0]
        api.call("POST", "/users/me/addresses", {"label": label, "addressLine": line_, "city": city, "landmark": landmark, "isDefault": default},
                 ctoken[phone], ok=False)
    favourites = ["Jollof & Grilled Tilapia", "Kelewele", "Sobolo", "Waakye Bowl", "Charcoal Chicken", "Red Red", "Bofrot & Caramel"]
    for index, (phone, _) in enumerate(customers):
        for dish in favourites[index:index + 3]:
            api.call("PUT", f"/users/me/favourites/{items[dish]['id']}", None, ctoken[phone], ok=False)

    # Loyalty points spent at checkout (customers earned points on their history orders)
    redeemed = 0
    for phone, _ in customers[:2]:
        me = api.call("GET", "/users/me", token=ctoken[phone])
        balance = api.call("GET", f"/loyalty/{me['id']}/balance", token=ctoken[phone], ok=False)
        points = int(balance.get("points", 0)) if _ok(balance) else 0
        if points < 10:
            continue
        spend = min(points, 60)
        order = api.call("POST", "/orders", {"type": "DELIVERY", "items": basket(3, float(zones[0].get("minOrderAmount") or 0)),
                                             "deliveryAddress": "14 Ring Road East, Accra", "deliveryZoneId": zones[0]["id"],
                                             "loyaltyPointsToRedeem": spend}, ctoken[phone], ok=False)
        if _ok(order):
            pay_momo(order["id"], ctoken[phone], phone)
            advance(order["id"], C1, ["PREPARING"])
            redeemed += spend
    summary["loyalty_redeemed"] = redeemed

    # A MoMo payment that fails (insufficient funds), then succeeds on retry
    fail_phone = customers[2][0]
    failed_order = api.call("POST", "/orders", {"type": "DELIVERY", "items": basket(2, float(zones[1].get("minOrderAmount") or 0)),
                                                "deliveryAddress": "22 Lagos Avenue, East Legon", "deliveryZoneId": zones[1]["id"]}, ctoken[fail_phone])
    init = api.call("POST", "/payments/initiate", {"orderId": failed_order["id"], "method": "MOBILE_MONEY", "momoPhone": fail_phone,
                                                   "idempotencyKey": f"seed-fail-{uuid.uuid4()}"}, ctoken[fail_phone])
    payment = api.call("GET", f"/payments/{init['paymentId']}", token=ctoken[fail_phone])
    api.webhook_event(init["paystackReference"], payment["amount"], "charge.failed", "failed", "Insufficient funds in wallet")
    # A second customer's attempt failed and was never retried: the order is still waiting for payment
    stuck = api.call("POST", "/orders", {"type": "DELIVERY", "items": basket(2, float(zones[0].get("minOrderAmount") or 0)),
                                         "deliveryAddress": "7 Spintex Road", "deliveryZoneId": zones[0]["id"]}, ctoken[customers[3][0]])
    init2 = api.call("POST", "/payments/initiate", {"orderId": stuck["id"], "method": "MOBILE_MONEY", "momoPhone": customers[3][0],
                                                    "idempotencyKey": f"seed-fail-{uuid.uuid4()}"}, ctoken[customers[3][0]])
    p2 = api.call("GET", f"/payments/{init2['paymentId']}", token=ctoken[customers[3][0]])
    api.webhook_event(init2["paystackReference"], p2["amount"], "charge.failed", "failed", "Customer declined the prompt")
    retry = api.call("POST", f"/payments/{init['paymentId']}/retry", {"momoPhone": fail_phone, "idempotencyKey": f"seed-retry-{uuid.uuid4()}"},
                     ctoken[fail_phone], ok=False)
    if _ok(retry) and retry.get("paystackReference"):
        p = api.call("GET", f"/payments/{retry['paymentId']}", token=ctoken[fail_phone])
        api.webhook_success(retry["paystackReference"], p["amount"])
    else:
        pay_momo(failed_order["id"], ctoken[fail_phone], fail_phone)

    # ------------------------------------------------------------ second cashier shift, financial controls
    step("Extras: cashier shift with cash sales, discount, void, refunds")
    shift = api.call("POST", "/shifts/open", {"openingCash": 200, "branchId": osu["id"], "notes": "Afternoon float"}, C2, ok=False)
    cash_total = 0.0
    for _ in range(3):
        order = api.call("POST", "/orders", {"type": "PICKUP", "items": basket(2)}, C2)
        due = float(api.call("GET", f"/orders/{order['id']}", token=C2)["total"])
        tendered = float(int(due / 50) + 1) * 50
        pay_cash(order["id"], C2, tendered)
        cash_total += due
        advance(order["id"], C2, ["PREPARING", "READY", "COMPLETED"])

    # Manual discount (regular guest), then paid
    free = free_osu_tables()
    order = dine_in(free[0], C2, 3, "Regular guest - manager approved 10%")
    api.call("POST", "/financial/discount", {"overrideToken": override("DISCOUNT"), "orderId": order["id"], "mode": "PERCENTAGE",
                                             "value": 10, "reason": "Regular guest (test data)"}, C2)
    discounted = api.call("GET", f"/orders/{order['id']}", token=C2)
    pay_cash(order["id"], C2, float(int(float(discounted["total"]) / 50) + 1) * 50)
    cash_total += float(discounted["total"])
    advance(order["id"], C2, ["PREPARING", "READY", "COMPLETED"])

    # Void: rung up on the wrong table
    order = dine_in(free[1], C2, 2)
    api.call("POST", "/financial/void", {"overrideToken": override("VOID"), "orderId": order["id"], "reason": "Entered on the wrong table"}, C2)

    # Full refund (MoMo) and part refund (cash)
    order = api.call("POST", "/orders", {"type": "PICKUP", "items": basket(2)}, C2)
    momo = pay_momo(order["id"], C2)
    advance(order["id"], C2, ["PREPARING", "READY", "COMPLETED"])
    api.call("POST", "/financial/refund", {"overrideToken": override("REFUND"), "paymentId": momo["paymentId"],
                                           "reason": "Wrong dish sent, customer left (test data)"}, C2)
    order = api.call("POST", "/orders", {"type": "PICKUP", "items": basket(3)}, C2)
    due = float(api.call("GET", f"/orders/{order['id']}", token=C2)["total"])
    cash = pay_cash(order["id"], C2, due)
    advance(order["id"], C2, ["PREPARING", "READY", "COMPLETED"])
    api.call("POST", "/financial/refund", {"overrideToken": override("REFUND"), "paymentId": cash["paymentId"], "amount": 25,
                                           "reason": "One drink was missing"}, C2)
    cash_total -= 25
    if _ok(shift):
        api.call("POST", f"/shifts/{shift['id']}/close", {"closingCash": round(200 + cash_total - 20, 2),
                                                          "notes": "GHS 20 short - change given twice? (test data)"}, C2, ok=False)
    yesterday = (now - timedelta(days=1)).date().isoformat()
    signed = api.call("POST", f"/financial/reconciliation/{yesterday}/sign-off",
                      {"overrideToken": override("DISCOUNT"), "branchId": osu["id"], "countedCash": 850, "notes": "Counted by Yaw (test data)"}, M, ok=False)
    summary["signed_off"] = yesterday if _ok(signed) else None

    # ------------------------------------------------------------ front of house
    step("Extras: split bill, waiter calls, payment watch, walkout")
    free = free_osu_tables()
    split_table, watch_table, walkout_table = free[0], free[1], free[2]

    # Split equally three ways; one guest has paid cash
    order = dine_in(split_table, C1, 4, "Birthday - three friends splitting")
    advance(order["id"], C1, ["CONFIRMED", "PREPARING", "READY", "COMPLETED"])
    shares = api.call("POST", f"/orders/{order['id']}/splits", {"mode": "EQUAL", "parts": 3, "names": ["Kojo", "Aba", "Nana"]}, C1)
    pay_cash(order["id"], C1, float(int(float(shares[0]["amount"]) / 50) + 1) * 50, shares[0]["id"])
    # ...and asked for the bill 12 minutes ago
    request = api.call("POST", f"/tables/public/{split_table['qrToken']}/requests", {"type": "BILL", "note": "We'd like to pay the rest"})

    # Served 35 minutes ago and not paid
    order = dine_in(watch_table, C1, 3)
    advance(order["id"], C1, ["CONFIRMED", "PREPARING", "READY", "COMPLETED"])
    served = now - timedelta(minutes=35)
    statements = [f"UPDATE orders SET served_at=TIMESTAMP '{stamp(served)}', created_at=TIMESTAMP '{stamp(served - timedelta(minutes=40))}' WHERE id='{order['id']}'"]
    if _ok(request):
        statements.append(f"UPDATE service_requests SET created_at=TIMESTAMP '{stamp(now - timedelta(minutes=12))}' WHERE id='{request['id']}'")
    sql(ctx["db_url"], ";".join(statements))

    # Walkout: two guests ordered as walk-ins (QR, with their phone), were served and left
    walk_orders = [api.call("POST", "/orders/public/dine-in", {"tableToken": walkout_table["qrToken"], "guestName": "Guest", "guestPhone": "+233209990001",
                                                               "items": basket(2)}) for _ in range(2)]
    for o in walk_orders:
        advance(o["id"], C1, ["CONFIRMED", "PREPARING", "READY", "COMPLETED"])
    walkout = api.call("POST", f"/tables/{walkout_table['id']}/walkout", {"reason": "WALKED_OUT", "note": "Left while the waiter was at the bar (test data)"}, M, ok=False)
    summary["walkout"] = walkout.get("amountOwed") if _ok(walkout) else None

    # Guest QR table: waiter call open, an acknowledged one, and a resolved request on another table
    guest_table = next(t for t in tables if t["number"] == "7")
    api.call("POST", f"/tables/public/{guest_table['qrToken']}/requests", {"type": "CALL_WAITER", "note": "Can we get more napkins?"})
    ack = api.call("POST", f"/tables/public/{guest_table['qrToken']}/requests", {"type": "OTHER", "note": "Is the kelewele spicy?"})
    api.call("PATCH", f"/service-requests/{ack['id']}", {"status": "ACKNOWLEDGED"}, C1, ok=False)
    done = api.call("POST", f"/tables/public/{watch_table['qrToken']}/requests", {"type": "OTHER", "note": "A high chair please"})
    api.call("PATCH", f"/service-requests/{done['id']}", {"status": "RESOLVED"}, C1, ok=False)

    # Guest split on the QR page (no accounts): two shares, one paid by MoMo
    step("Extras: guest split, guest feedback, group order split")
    token = guest_table["qrToken"]
    guest_order = api.call("POST", "/orders/public/dine-in", {"tableToken": token, "guestName": "Selasi", "guestPhone": "+233209990002", "items": basket(3)})
    advance(guest_order["id"], C1, ["CONFIRMED", "PREPARING"])
    shares = api.call("POST", f"/orders/public/dine-in/tables/{token}/orders/{guest_order['id']}/splits", {"parts": 2, "names": ["Selasi", "Dela"]}, ok=False)
    if _ok(shares) and shares:
        init = api.call("POST", f"/orders/public/dine-in/tables/{token}/orders/{guest_order['id']}/splits/{shares[0]['id']}/pay",
                        {"momoPhone": "+233209990002", "idempotencyKey": f"seed-guest-{uuid.uuid4()}"})
        api.webhook_success(init["paystackReference"], str(shares[0]["amount"]))

    # A guest paid the whole order on the QR page, ate, and left a rating
    rated = api.call("POST", "/orders/public/dine-in", {"tableToken": token, "guestName": "Kwame", "guestPhone": "+233209990003", "items": basket(2)})
    init = api.call("POST", f"/orders/public/dine-in/tables/{token}/orders/{rated['id']}/pay",
                    {"momoPhone": "+233209990003", "idempotencyKey": f"seed-guest-{uuid.uuid4()}"})
    api.webhook_success(init["paystackReference"], str(api.call("GET", f"/payments/{init['paymentId']}", token=C1)["amount"]))
    advance(rated["id"], C1, ["PREPARING", "READY", "COMPLETED"])
    api.call("POST", f"/orders/public/dine-in/tables/{token}/orders/{rated['id']}/feedback",
             {"rating": 4, "foodRating": 5, "serviceRating": 4, "comment": "Ordering from the table was easy. Lovely sobolo."}, ok=False)

    # Group delivery order split by what each person ordered; the host has paid their share
    host_phone, host_name = customers[3]
    friend_phone, friend_name = customers[4]
    session = api.call("POST", "/orders/group/sessions", {"displayName": host_name}, ctoken[host_phone])
    code = session["sessionCode"]
    joined = api.call("POST", f"/orders/group/sessions/{code}/join", {"displayName": friend_name}, ctoken[friend_phone])
    api.call("POST", f"/orders/group/sessions/{code}/items", {"participantId": session["hostParticipantId"], "items": basket(2)}, ctoken[host_phone])
    api.call("POST", f"/orders/group/sessions/{code}/items", {"participantId": joined["participantId"], "items": basket(2)}, ctoken[friend_phone])
    group_order = api.call("POST", f"/orders/group/sessions/{code}/finalize",
                           {"type": "DELIVERY", "deliveryAddress": "Airport City, Africa Trade House", "deliveryZoneId": zones[1]["id"],
                            "notes": "Office lunch"}, ctoken[host_phone], ok=False)
    if _ok(group_order):
        shares = api.call("POST", f"/orders/{group_order['id']}/splits", {"mode": "BY_ITEMS"}, ctoken[host_phone], ok=False)
        if _ok(shares) and shares:
            mine = next((s for s in shares if s.get("participantId") == session["hostParticipantId"]), shares[0])
            pay_momo(group_order["id"], ctoken[host_phone], host_phone, mine["id"])
    summary["group_split"] = code

    # Rider location on the delivery that is on the road
    if ctx.get("road_order_id"):
        for lat, lng in [(5.5600, -0.1850), (5.5680, -0.1790), (5.5750, -0.1740)]:
            api.call("POST", f"/delivery/orders/{ctx['road_order_id']}/location", {"latitude": lat, "longitude": lng}, R2, ok=False)

    # ------------------------------------------------------------ feedback replies, reservations
    step("Extras: review replies, reservations")
    replies = {1: "We're very sorry about the wait. We've added a second pickup counter at peak times.",
               2: "Sorry your food arrived cold - we've switched to insulated delivery bags. Your next delivery is on us.",
               5: "Thank you! We'll pass this on to the kitchen."}
    replied = 0
    for fb in api.call("GET", "/feedback", token=M) or []:
        reply = replies.get(fb.get("rating"))
        if reply and not fb.get("response"):
            replied += _ok(api.call("POST", f"/feedback/{fb['id']}/respond", {"response": reply}, M, ok=False))
    summary["replies"] = replied

    elg_free = [t for t in elg_tables if t["capacity"] >= 2]
    soon = api.call("POST", "/reservations", {"tableId": elg_free[-1]["id"], "customerName": "Kwabena Owusu", "customerPhone": "+233201110007",
                                              "partySize": 4, "reservedAt": iso(now + timedelta(minutes=12)), "durationMins": 90,
                                              "notes": "Arriving shortly"}, ok=False)
    if _ok(soon):
        api.call("PATCH", f"/reservations/{soon['id']}/status", {"status": "CONFIRMED"}, A, ok=False)
    api.call("POST", "/reservations", {"tableId": elg_free[0]["id"], "customerName": customers[0][1], "customerPhone": customers[0][0],
                                       "partySize": 2, "reservedAt": iso((now + timedelta(days=3)).replace(hour=19, minute=30, second=0, microsecond=0)),
                                       "durationMins": 120, "notes": "Anniversary dinner"}, ctoken[customers[0][0]], ok=False)
    cancelled = api.call("POST", "/reservations", {"tableId": elg_free[1]["id"], "customerName": "Yoofi Mensah", "customerPhone": "+233201110008",
                                                   "partySize": 2, "reservedAt": iso((now + timedelta(days=2)).replace(hour=13, minute=0, second=0, microsecond=0))},
                         ok=False)
    if _ok(cancelled):
        api.call("DELETE", f"/reservations/{cancelled['id']}", None, A, ok=False)

    # ------------------------------------------------------------ stock movements
    step("Extras: stock delivery, waste, stock count")
    ingredients = {i["name"]: i for i in api.call("GET", "/inventory/ingredients", token=A)}
    api.call("POST", f"/inventory/ingredients/{ingredients['Rice']['id']}/restock", {"quantity": 25, "note": "Weekly delivery (50 kg bags x 0.5)"}, M)
    api.call("POST", f"/inventory/ingredients/{ingredients['Chicken']['id']}/restock", {"quantity": 20, "note": "Supplier: Kumasi farms (test)"}, M)
    api.call("POST", f"/inventory/ingredients/{ingredients['Tilapia']['id']}/adjust", {"reason": "WASTE", "delta": -3, "note": "Fridge left open overnight"}, M)
    api.call("POST", f"/inventory/ingredients/{ingredients['Groundnuts']['id']}/adjust", {"reason": "ADJUSTMENT", "countedQuantity": 9.5, "note": "Monthly stock count"}, M)

    # ------------------------------------------------------------ pickup awaiting payment (last: it expires in 30 min)
    step("Extras: pickup waiting for payment")
    api.call("POST", "/orders", {"type": "PICKUP", "items": basket(2), "notes": "Customer paying at the counter"}, C1)
    # Hibiscus counted to zero: Sobolo is taken off the menu until it is restocked
    api.call("POST", f"/inventory/ingredients/{ingredients['Hibiscus leaves']['id']}/adjust",
             {"reason": "ADJUSTMENT", "countedQuantity": 0, "note": "Stock count: none left"}, M)
    return summary


def print_extras_summary(summary: dict):
    print("  Extras:")
    print(f"    {summary.get('photos', 0)} dish photos; restaurant profile; 3 printers (fake IPs, test print will fail)")
    print(f"    East Legon: {summary.get('elg_orders', 0)} past orders (dashboard branch comparison)")
    print(f"    Loyalty points spent at checkout: {summary.get('loyalty_redeemed', 0)}; a MoMo payment failed then retried; another failed and is unpaid")
    print("    Abena's shift closed GHS 20 short; discount, void, full MoMo refund and part cash refund in /financial (PIN 2468)")
    print(f"    Reconciliation signed off for {summary.get('signed_off') or '(not signed off)'} (Osu)")
    print("    Floor plan: one table served 35 min ago and unpaid, one split 3 ways (1 paid) asking for the bill,")
    print(f"         walkout recorded (GHS {summary.get('walkout')}), waiter calls on table 7, guest split + guest rating on table 7")
    print(f"    Group order {summary.get('group_split')} split by items (host paid); {summary.get('replies', 0)} review replies")
    print("    Stock: delivery, waste, count; Hibiscus counted to zero so Sobolo is off the menu (restock to bring it back)")
    print("    A pickup waits for payment at the counter (auto-cancelled after 30 minutes)")
