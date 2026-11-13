#!/usr/bin/env python3
"""
Seed a LOCAL backend with realistic, related test data.

Everything goes through the real REST API (same validation, pricing,
inventory, loyalty and realtime paths as production). Two local-only steps
use the H2 shell, because no API exists for them:
  * promoting the first account to ADMIN (there is no admin bootstrap), and
  * back-dating finished orders across the last 30 days so the dashboard
    and reports show trends instead of a single day.

Payments use the Paystack stub. They are confirmed by sending the same
signed `charge.success` webhook Paystack would send, signed with the
local development secret (PAYSTACK_SECRET_KEY default).

HR data (departments, employees, contracts, documents, scheduling, attendance, leave, timesheets,
payroll, tip pools and recruitment) is added by seed_hr.py.

Usage (backend started with scripts/dev/run-backend-local.sh):
  python scripts/dev/seed_local.py                    # http://localhost:8080
  python scripts/dev/seed_local.py --base http://localhost:8082

All accounts below are TEST accounts for local development only.
Password for every seeded account: Passw0rd!
"""
from __future__ import annotations

import argparse
import glob
import hashlib
import hmac
import json
import os
import random
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PASSWORD = "Passw0rd!"

STAFF = [
    # phone, name, role, email
    ("+233200000001", "Ama Mensah", "ADMIN", "ama.owner@example.test"),
    ("+233200000002", "Yaw Owusu", "MANAGER", "yaw.manager@example.test"),
    ("+233200000003", "Kofi Boateng", "CASHIER", None),
    ("+233200000004", "Abena Asante", "CASHIER", None),
    ("+233200000005", "Kwame Darko", "RIDER", None),
    ("+233200000006", "Esi Appiah", "RIDER", None),
    ("+233200000007", "Efua Nyarko", "HR", "efua.hr@example.test"),  # HR officer: HR module only, every branch
]
CUSTOMERS = [
    ("+233240000001", "Efua Quaye"),
    ("+233240000002", "Kojo Antwi"),
    ("+233240000003", "Adwoa Sarpong"),
    ("+233240000004", "Nana Yeboah"),
    ("+233240000005", "Akosua Frimpong"),
]

MENU = {
    ("Small Plates", "To share, or not."): [
        ("Kelewele", "Spiced ripe plantain, ginger, chilli, roasted groundnuts", "45.00", True),
        ("Grilled Chichinga", "Suya-spiced beef skewers, pickled onion, yaji salt", "65.00", True),
        ("Garden Eggs & Shito", "Charred garden eggs, house shito, toasted sesame", "40.00", True),
        ("Crab Croquettes", "Lagoon crab, scotch bonnet aioli, lime", "75.00", False),
        ("Spring Rolls", "Vegetable spring rolls, sweet chilli", "38.00", True),
    ],
    ("Mains", "From the grill and the pot."): [
        ("Jollof & Grilled Tilapia", "Smoky party jollof, whole tilapia, green pepper sauce", "140.00", True),
        ("Waakye Bowl", "Rice and beans, wele, boiled egg, gari, shito, salad", "95.00", True),
        ("Light Soup & Fufu", "Goat light soup, pounded cassava and plantain", "120.00", True),
        ("Banku & Okro Stew", "Fermented corn dough, okro, seafood medley", "130.00", True),
        ("Charcoal Chicken", "Half chicken, citrus marinade, yam chips, pepper sauce", "125.00", True),
        ("Red Red", "Black-eyed bean stew, palm oil, fried plantain", "85.00", True),
    ],
    ("Desserts", "Something sweet to finish."): [
        ("Bofrot & Caramel", "Warm puff-puff, salted caramel, vanilla cream", "50.00", True),
        ("Coconut Panna Cotta", "Toasted coconut, pineapple, lime zest", "55.00", True),
    ],
    ("Drinks", "Made in-house daily."): [
        ("Sobolo", "Hibiscus, ginger, clove, pineapple, iced", "25.00", True),
        ("Asaana", "Caramelised corn drink, served cold", "25.00", True),
        ("Fresh Coconut", "Chilled whole coconut", "30.00", True),
        ("Pineapple Ginger Juice", "Cold-pressed, no added sugar", "28.00", True),
    ],
}

MODIFIERS = {
    "Jollof & Grilled Tilapia": [
        ("Spice level", "SINGLE", True, 1, 1, [("Mild", "0"), ("Medium", "0"), ("Hot", "0")]),
        ("Extras", "MULTIPLE", False, 0, 3, [("Fried plantain", "15.00"), ("Extra shito", "5.00"), ("Avocado", "12.00")]),
    ],
    "Charcoal Chicken": [
        ("Portion", "SINGLE", True, 1, 1, [("Half chicken", "0"), ("Whole chicken", "95.00")]),
        ("Side", "SINGLE", True, 1, 1, [("Yam chips", "0"), ("Jollof rice", "10.00"), ("Fried plantain", "8.00")]),
    ],
    "Waakye Bowl": [
        ("Add protein", "MULTIPLE", False, 0, 2, [("Fried fish", "20.00"), ("Wele", "10.00"), ("Extra egg", "6.00")]),
    ],
    "Sobolo": [("Size", "SINGLE", True, 1, 1, [("Regular", "0"), ("Large", "10.00")])],
}

INGREDIENTS = [
    # name, unit, low threshold, cost/unit, initial stock
    ("Rice", "kg", 10, 18, 80),
    ("Tilapia", "pcs", 8, 35, 60),
    ("Chicken", "pcs", 6, 45, 40),
    ("Ripe plantain", "pcs", 20, 4, 150),
    ("Groundnuts", "kg", 2, 30, 12),
    ("Hibiscus leaves", "kg", 1, 40, 6),
    ("Palm oil", "L", 3, 25, 2),          # deliberately low → low-stock alert
    ("Black-eyed beans", "kg", 4, 20, 25),
    ("Garden eggs", "pcs", 15, 2, 90),
]
# dish, modifier group, option, ingredient, extra quantity per portion (on top of the recipe)
OPTION_RECIPES = [
    ("Charcoal Chicken", "Portion", "Whole chicken", "Chicken", "1"),
    ("Sobolo", "Size", "Large", "Hibiscus leaves", "0.01"),
]
RECIPES = {
    "Jollof & Grilled Tilapia": [("Rice", "0.25"), ("Tilapia", "1")],
    "Charcoal Chicken": [("Chicken", "1")],
    "Kelewele": [("Ripe plantain", "2"), ("Groundnuts", "0.05")],
    "Sobolo": [("Hibiscus leaves", "0.02")],
    "Red Red": [("Black-eyed beans", "0.2"), ("Palm oil", "0.05"), ("Ripe plantain", "1")],
    "Garden Eggs & Shito": [("Garden eggs", "3")],
}

TABLES = [
    ("1", 2, "Window"), ("2", 2, "Window"), ("3", 4, "Main room"), ("4", 4, "Main room"),
    ("5", 6, "Main room"), ("6", 4, "Main room"), ("7", 8, "Terrace"), ("8", 4, "Terrace"),
    ("9", 6, "Terrace"), ("10", 2, "Bar"), ("11", 2, "Bar"), ("12", 10, "Private room"),
]
EAST_LEGON_TABLES = [("EL1", 4, "Garden"), ("EL2", 4, "Garden"), ("EL3", 2, "Lounge"), ("EL4", 6, "Lounge")]


class Api:
    def __init__(self, base: str, secret: str):
        self.base = base.rstrip("/")
        self.secret = secret
        self._retries = 0

    def call(self, method, path, body=None, token=None, ok=True, raw=None, headers=None):
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        if token:
            req.add_header("Authorization", "Bearer " + token)
        for key, value in (headers or {}).items():
            req.add_header(key, value)
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                text = response.read().decode() or "null"
                return json.loads(text) if text.strip().startswith(("{", "[")) else text
        except urllib.error.HTTPError as err:
            detail = err.read().decode(errors="replace")
            if err.code == 429 and self._retries < 12:
                # The backend rate-limits auth/OTP routes (20/min per IP by default). Wait out the window.
                self._retries += 1
                print("  (rate limited, waiting 15 s)", flush=True)
                time.sleep(15)
                try:
                    return self.call(method, path, body, token, ok, raw, headers)
                finally:
                    self._retries -= 1
            if ok:
                raise SystemExit(f"\n{method} {path} -> {err.code}: {detail[:400]}")
            return {"_error": err.code, "_detail": detail}

    def webhook_success(self, reference: str, amount: str):
        self.webhook_event(reference, amount, "charge.success", "success")

    def webhook_event(self, reference: str, amount: str, event: str, status: str, gateway_response: str | None = None):
        data = {"id": f"seed-{event}-{reference}", "reference": reference, "status": status,
                "amount": int(round(float(amount) * 100)), "currency": "GHS"}
        if gateway_response:
            data["gateway_response"] = gateway_response
        payload = json.dumps({"event": event, "data": data}, separators=(",", ":"))
        signature = hmac.new(self.secret.encode(), payload.encode(), hashlib.sha512).hexdigest()
        self.call("POST", "/payments/webhook", raw=payload.encode(), headers={"X-Paystack-Signature": signature})


def find_h2_jar() -> str:
    jars = sorted(glob.glob(os.path.expanduser("~/.m2/repository/com/h2database/h2/*/h2-*.jar")))
    jars = [j for j in jars if "sources" not in j and "javadoc" not in j]
    if not jars:
        raise SystemExit("H2 jar not found in ~/.m2 (run the backend once so Maven downloads it).")
    return jars[-1]


PG_PREFIX = "docker-postgres:"


def database_target(pg_container: str | None, h2_path: str) -> str:
    """Where direct SQL goes: PostgreSQL in a Docker container, or the local H2 file database."""
    if pg_container:
        return PG_PREFIX + pg_container
    return f"jdbc:h2:file:{os.path.abspath(h2_path).replace(os.sep, '/')};MODE=PostgreSQL;AUTO_SERVER=TRUE"


def h2_sql(db_url: str, sql: str) -> str:
    """Runs SQL directly against the dev database (H2 shell, or psql inside the Postgres container)."""
    if db_url.startswith(PG_PREFIX):
        container = db_url[len(PG_PREFIX):]
        out = subprocess.run(
            ["docker", "exec", "-i", container, "psql", "-v", "ON_ERROR_STOP=1", "-q", "-U", os.environ.get("DB_USERNAME", "postgres"),
             "-d", os.environ.get("DB_NAME", "restaurant_manager")],
            input=sql + ";\n", capture_output=True, text=True,
        )
        if out.returncode != 0:
            raise SystemExit("psql failed: " + (out.stderr or out.stdout)[:400])
        return out.stdout
    out = subprocess.run(
        ["java", "-cp", find_h2_jar(), "org.h2.tools.Shell", "-url", db_url, "-user", "sa", "-password", "", "-sql", sql],
        capture_output=True, text=True,
    )
    if out.returncode != 0:
        raise SystemExit("H2 shell failed: " + (out.stderr or out.stdout)[:400])
    return out.stdout


def read_otp(log_path: str, phone: str, attempts: int = 20) -> str:
    pattern = re.compile(r"SMS \(not delivered\) to " + re.escape(phone) + r":.*?(\d{4,8})")
    for _ in range(attempts):
        try:
            with open(log_path, encoding="utf-8", errors="replace") as fh:
                matches = pattern.findall(fh.read().replace("\x00", ""))
            if matches:
                return matches[-1]
        except FileNotFoundError:
            pass
        time.sleep(0.5)
    raise SystemExit(f"OTP for {phone} not found in {log_path}. Is SMS_PROVIDER=log and is this the backend log?")


def ensure_account(api: Api, log_path: str, phone: str, name: str, email: str | None):
    login = api.call("POST", "/auth/login", {"phone": phone, "password": PASSWORD}, ok=False)
    if "_error" not in login:
        return login
    api.call("POST", "/auth/otp/request", {"phone": phone, "purpose": "REGISTER"})
    code = read_otp(log_path, phone)
    verified = api.call("POST", "/auth/otp/verify", {"phone": phone, "code": code})
    body = {"name": name, "phone": phone, "password": PASSWORD, "verificationToken": verified["verificationToken"]}
    if email:
        body["email"] = email
    api.call("POST", "/auth/register", body)
    time.sleep(1.1)  # refresh tokens are second-granular; avoid a same-second collision on the next login
    return api.call("POST", "/auth/login", {"phone": phone, "password": PASSWORD})


def iso(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--log", default=os.path.join(ROOT, "backend", "target", "dev-backend.log"))
    parser.add_argument("--db", default=os.path.join(ROOT, "backend", "target", "dev-db", "rm"), help="H2 file path (without .mv.db)")
    parser.add_argument("--pg", metavar="CONTAINER", nargs="?", const="restaurant_postgres",
                        help="Use PostgreSQL in this Docker container (default restaurant_postgres) instead of H2")
    parser.add_argument("--secret", default=os.environ.get("PAYSTACK_SECRET_KEY", "test_paystack_secret"))
    parser.add_argument("--no-backdate", action="store_true")
    parser.add_argument("--reset", action="store_true",
                        help="Empty every table except users first (local H2 only). Restart the backend afterwards to clear its in-memory caches.")
    args = parser.parse_args()

    random.seed(7)
    api = Api(args.base, args.secret)
    db_url = database_target(args.pg, args.db)
    now = datetime.now(timezone.utc)

    if args.reset and args.pg:
        raise SystemExit("--reset is for H2 only. For PostgreSQL recreate the database: "
                         "docker compose down postgres && docker volume rm restaurant-mgt-system_postgres_data")
    if args.reset:
        step("Resetting data (keeping user accounts)")
        listing = h2_sql(db_url, "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'")
        names = [l.split("|")[0].strip() for l in listing.splitlines()[1:] if l.strip() and not l.startswith("(")]
        keep = {"USERS"}
        sql = ["SET REFERENTIAL_INTEGRITY FALSE"] + [f'TRUNCATE TABLE "{n}"' for n in names if n.upper() not in keep] +               ["UPDATE users SET branch_id=NULL", "SET REFERENTIAL_INTEGRITY TRUE"]
        h2_sql(db_url, ";".join(sql))
    elif api.call("GET", "/menu/categories"):
        raise SystemExit("This backend already has menu data. Start from an empty database (RESET_DB=1) before seeding.")

    # ---------------------------------------------------------------- accounts
    step("Accounts")
    sessions = {}
    for phone, name, _role, email in STAFF:
        sessions[phone] = ensure_account(api, args.log, phone, name, email)
    for phone, name in CUSTOMERS:
        sessions[phone] = ensure_account(api, args.log, phone, name, None)
    admin_phone = STAFF[0][0]
    h2_sql(db_url, f"UPDATE users SET role='ADMIN' WHERE phone='{admin_phone}'")
    time.sleep(1.1)
    admin = api.call("POST", "/auth/login", {"phone": admin_phone, "password": PASSWORD})
    A = admin["accessToken"]

    # ---------------------------------------------------------------- branches
    step("Branches")
    existing = {b["code"]: b for b in api.call("GET", "/branches", token=A)}
    osu = existing.get("OSU") or api.call("POST", "/branches", {"code": "OSU", "name": "Osu (Oxford Street)", "active": True}, A)
    legon = existing.get("ELG") or api.call("POST", "/branches", {"code": "ELG", "name": "East Legon", "active": True}, A)

    users = {u["phone"]: u for u in api.call("GET", "/users", token=A)}
    for phone, _name, role, _email in STAFF[1:]:
        # an HR login without a branch is central HR; everyone else works at Osu
        api.call("PATCH", f"/users/{users[phone]['id']}/role", {"role": role, "branchId": None if role == "HR" else osu["id"]}, A)
    api.call("POST", f"/users/{users[STAFF[1][0]]['id']}/pin", {"pin": "2468"}, A)

    tokens = {}
    for phone, *_ in STAFF + [(c[0],) for c in CUSTOMERS]:
        time.sleep(0.05)
        tokens[phone] = api.call("POST", "/auth/login", {"phone": phone, "password": PASSWORD})["accessToken"]
        time.sleep(1.05)
    A = tokens[admin_phone]
    M = tokens["+233200000002"]
    C1, C2 = tokens["+233200000003"], tokens["+233200000004"]
    riders = [users["+233200000005"]["id"], users["+233200000006"]["id"]]

    # ---------------------------------------------------------------- billing
    step("Billing & tax")
    api.call("PUT", "/billing/settings", {"serviceChargePercent": 5, "serviceChargeDineInOnly": True,
                                           "pricesIncludeTax": False, "taxDeliveryFee": False, "tipsEnabled": True}, A)
    for order_, (code, name, rate, compound) in enumerate([("NHIL", "NHIL", 2.5, False), ("GETFUND", "GETFund Levy", 2.5, False), ("VAT", "VAT", 15, True)]):
        # ok=False: on PostgreSQL, Flyway already seeds these tax codes
        api.call("POST", "/billing/taxes", {"code": code, "name": name, "rate": rate, "compound": compound, "displayOrder": order_, "active": True}, A, ok=False)

    # ---------------------------------------------------------------- menu
    step("Menu, modifiers")
    items = {}
    for position, ((cat, desc), dishes) in enumerate(MENU.items()):
        category = api.call("POST", "/menu/categories", {"name": cat, "description": desc, "displayOrder": position, "active": True}, A)
        for name, d, price, available in dishes:
            items[name] = api.call("POST", "/menu/items", {"categoryId": category["id"], "name": name, "description": d, "price": price, "available": available}, A)
    modifier_groups = {}
    for dish, groups in MODIFIERS.items():
        modifier_groups[dish] = []
        for g_index, (gname, kind, required, mn, mx, options) in enumerate(groups):
            group = api.call("POST", f"/menu/items/{items[dish]['id']}/modifiers",
                             {"name": gname, "selectionType": kind, "required": required, "minSelect": mn, "maxSelect": mx, "displayOrder": g_index, "active": True}, A)
            opts = [api.call("POST", f"/menu/items/{items[dish]['id']}/modifiers/{group['id']}/options",
                             {"name": o, "priceDelta": p, "displayOrder": i, "active": True}, A) for i, (o, p) in enumerate(options)]
            modifier_groups[dish].append({"required": required, "kind": kind, "options": [o["id"] for o in opts]})

    # ---------------------------------------------------------------- inventory
    step("Inventory & recipes")
    # Stock is per branch: the opening stock is Osu's; East Legon gets its own (smaller) delivery.
    ingredients = {}
    for name, unit, low, cost, stock in INGREDIENTS:
        ingredients[name] = api.call("POST", f"/inventory/ingredients?branchId={osu['id']}",
                                     {"name": name, "unit": unit, "lowStockThreshold": low, "costPerUnit": cost, "initialStock": stock, "active": True}, A)
    for name, unit, low, cost, stock in INGREDIENTS:
        if name == "Palm oil":
            continue  # East Legon does not stock palm oil: it is not tracked there
        api.call("POST", f"/inventory/ingredients/{ingredients[name]['id']}/restock",
                 {"quantity": round(stock / 2, 3), "unitCost": round(cost * 1.05, 2), "supplier": "Makola Market (test)",
                  "note": "Opening delivery", "branchId": legon["id"]}, A)
    for dish, lines in RECIPES.items():
        api.call("PUT", f"/inventory/menu-items/{items[dish]['id']}/recipe",
                 [{"ingredientId": ingredients[i]["id"], "quantity": q} for i, q in lines], A)

    # Options that use more than the base recipe.
    def option_id(dish, group_name, option_name):
        for g_index, (gname, *_rest, options) in enumerate(MODIFIERS[dish]):
            if gname == group_name:
                return modifier_groups[dish][g_index]["options"][[o for o, _p in options].index(option_name)]
        raise KeyError((dish, group_name, option_name))

    for dish, group_name, option_name, ingredient, quantity in OPTION_RECIPES:
        api.call("PUT", f"/inventory/modifier-options/{option_id(dish, group_name, option_name)}/recipe",
                 [{"ingredientId": ingredients[ingredient]["id"], "quantity": quantity}], A)

    # ---------------------------------------------------------------- tables, zones, promos
    step("Tables, delivery zones, promotions")
    tables = [api.call("POST", "/tables", {"number": n, "capacity": c, "zone": z, "branchId": osu["id"]}, A) for n, c, z in TABLES]
    for n, c, z in EAST_LEGON_TABLES:
        api.call("POST", "/tables", {"number": n, "capacity": c, "zone": z, "branchId": legon["id"]}, A)
    zones = [api.call("POST", "/delivery/zones", {"name": n, "description": d, "branchId": osu["id"], "fee": f, "minOrderAmount": m, "estimatedMinutes": e, "active": True}, A)
             for n, d, f, m, e in [("Osu & Labone", "Within 3 km", 15, 50, 30), ("Airport & Cantonments", "3-8 km", 25, 80, 40), ("Spintex & Tema", "8-20 km", 40, 120, 60)]]
    # ok=False: on PostgreSQL, Flyway (V7) already seeds some promo codes
    api.call("POST", "/promotions/codes", {"code": "WELCOME10", "description": "10% off your first order", "discountType": "PERCENTAGE", "discountValue": 10, "minOrderAmount": 0, "maxDiscount": 50, "usageLimit": 500, "active": True}, A, ok=False)
    api.call("POST", "/promotions/codes", {"code": "FEAST20", "description": "GHS 20 off orders over GHS 200", "discountType": "FLAT", "discountValue": 20, "minOrderAmount": 200, "active": True}, A, ok=False)
    api.call("POST", "/promotions/codes", {"code": "EXPIRED5", "description": "Expired test code", "discountType": "FLAT", "discountValue": 5, "minOrderAmount": 0, "expiryDate": iso(now - timedelta(days=3)), "active": True}, A, ok=False)
    api.call("POST", "/promotions/offers", {"name": "Kelewele 2 + 1", "description": "Buy two Kelewele, get one free", "menuItemId": items["Kelewele"]["id"], "buyQuantity": 2, "freeQuantity": 1, "active": True}, A)

    # ---------------------------------------------------------------- cash shift + schedule
    step("Cash shifts & rota")
    api.call("POST", "/shifts/open", {"openingCash": 300, "branchId": osu["id"], "notes": "Morning float"}, C1)
    base_day = now.replace(hour=0, minute=0, second=0, microsecond=0)
    rota = [("+233200000002", "Manager", 9, 18), ("+233200000003", "Cashier", 9, 17), ("+233200000004", "Cashier", 14, 22),
            ("+233200000005", "Rider", 11, 20), ("+233200000006", "Rider", 16, 23)]
    for day in range(0, 7):
        for phone, position, start, end in rota:
            if (day + len(phone)) % 6 == 0:
                continue  # a day off now and then
            api.call("POST", "/scheduling/shifts", {"userId": users[phone]["id"], "branchId": osu["id"],
                                                    "startsAt": iso(base_day + timedelta(days=day, hours=start)),
                                                    "endsAt": iso(base_day + timedelta(days=day, hours=end)), "position": position}, M)
    for token in (C1, M):
        api.call("POST", "/timeclock/clock-in", {}, token, ok=False)

    # ---------------------------------------------------------------- orders
    step("Orders, payments, kitchen flow")
    sellable = [n for n, i in items.items() if i["available"]]

    def line(dish):
        entry = {"menuItemId": items[dish]["id"], "quantity": random.choice([1, 1, 1, 2])}
        picks = []
        for group in modifier_groups.get(dish, []):
            if group["required"]:
                picks.append(random.choice(group["options"]))
            elif random.random() < 0.4:
                picks.append(random.choice(group["options"]))
        if picks:
            entry["modifierOptionIds"] = picks
        if random.random() < 0.12:
            entry["notes"] = random.choice(["No onions", "Extra spicy", "Sauce on the side", "Well done"])
        return entry

    prices = {name: float(item["price"]) for name, item in items.items()}

    def basket(n=None, minimum=0.0):
        dishes = random.sample(sellable, n or random.randint(1, 4))
        lines = [line(d) for d in dishes]
        # Delivery zones have a minimum order; top up with mains until it's met.
        while sum(prices[next(k for k, v in items.items() if v["id"] == l["menuItemId"])] * l["quantity"] for l in lines) < minimum:
            extra = random.choice([d for d in sellable if prices[d] >= 85])
            lines.append({"menuItemId": items[extra]["id"], "quantity": 1, **({"modifierOptionIds": [g["options"][0] for g in modifier_groups.get(extra, []) if g["required"]]} if modifier_groups.get(extra) else {})})
        return lines

    def zone_min(zone):
        return float(zone.get("minOrderAmount") or 0)

    # Cash needs an open shift (the drawer it goes into). Staff who take test-data cash without one of their own get
    # a seed drawer, closed balanced before the extras run.
    auto_shifts = {}

    def ensure_shift(token):
        if token in auto_shifts:
            return
        opened = api.call("POST", "/shifts/open", {"openingCash": 0, "notes": "Seed drawer for test-data cash sales"}, token, ok=False)
        auto_shifts[token] = opened.get("id") if isinstance(opened, dict) and "_error" not in opened else None

    def close_auto_shifts():
        for token, shift_id in auto_shifts.items():
            if shift_id:
                expected = api.call("GET", f"/shifts/{shift_id}", token=token)["expectedCash"]
                api.call("POST", f"/shifts/{shift_id}/close", {"closingCash": expected, "notes": "Seed drawer, balanced"}, token)

    def pay(order, token, method=None):
        method = method or random.choice(["MOBILE_MONEY", "MOBILE_MONEY", "CASH", "CARD"])
        if method == "CASH":
            ensure_shift(token)
        init = api.call("POST", "/payments/initiate", {"orderId": order["id"], "method": method, "momoPhone": "+233244000111",
                                                       "idempotencyKey": f"seed-{order['id']}"}, token)
        payment = api.call("GET", f"/payments/{init['paymentId']}", token=token)
        api.webhook_success(init["paystackReference"], payment["amount"])

    def advance(order_id, token, stops):
        for status in stops:
            api.call("PATCH", f"/orders/{order_id}/status", {"status": status}, token)

    finished, counts = [], {"completed": 0, "active": 0, "cancelled": 0}
    # Historical orders (back-dated later): mostly completed.
    for i in range(70):
        token = random.choice([C1, C2])
        kind = random.choices(["DINE_IN", "PICKUP", "DELIVERY"], weights=[5, 3, 2])[0]
        zone = random.choice(zones)
        body = {"type": kind, "items": basket(minimum=zone_min(zone) if kind == "DELIVERY" else 0)}
        if kind == "DINE_IN":
            body["tableId"] = random.choice(tables)["id"]
            if random.random() < 0.6:
                body["tipAmount"] = random.choice([5, 10, 10, 15, 20, 30])  # shared later through HR tip pools
        if kind == "DELIVERY":
            body.update({"deliveryAddress": random.choice(["12 Oxford St, Osu", "4 Liberation Rd, Airport", "Spintex Rd, near Palace Mall"]),
                         "deliveryZoneId": zone["id"]})
        if random.random() < 0.1:
            body["promoCode"] = "WELCOME10"
        order = api.call("POST", "/orders", body, token)
        if random.random() < 0.05:
            api.call("DELETE", f"/orders/{order['id']}", {"reason": "Customer left"}, token, ok=False)
            counts["cancelled"] += 1
            finished.append(order["id"])
            continue
        pay(order, token)
        if kind == "DELIVERY":
            advance(order["id"], token, ["PREPARING", "READY"])
            api.call("POST", f"/delivery/orders/{order['id']}/assign", {"riderId": random.choice(riders)}, token)
            assigned = api.call("GET", f"/orders/{order['id']}", token=token)
            rider_token = tokens["+233200000005"] if assigned.get("riderUserId") == riders[0] else tokens["+233200000006"]
            api.call("POST", f"/delivery/orders/{order['id']}/pickup", {}, rider_token)
            api.call("POST", f"/delivery/orders/{order['id']}/delivered", {}, rider_token)
        else:
            advance(order["id"], token, ["PREPARING", "READY", "COMPLETED"])
        counts["completed"] += 1
        finished.append(order["id"])

    # Customer app orders (repeat customers, loyalty, feedback)
    feedback_texts = [(5, "Best jollof in Accra, the tilapia was perfect."), (4, "Lovely food, delivery was a little slow."),
                      (5, "Friendly staff and great kelewele."), (2, "Food arrived cold this time."), (3, "Good but portion was small."),
                      (1, "Waited over an hour for pickup.")]
    for c_index, (phone, name) in enumerate(CUSTOMERS):
        ctoken = tokens[phone]
        for k in range(random.randint(2, 4)):
            kind = random.choice(["PICKUP", "DELIVERY"])
            body = {"type": kind, "items": basket(random.randint(1, 3), zone_min(zones[c_index % 3]) if kind == "DELIVERY" else 0)}
            if kind == "DELIVERY":
                body.update({"deliveryAddress": f"{10 + c_index} Ring Road, Accra", "deliveryZoneId": zones[c_index % 3]["id"]})
            order = api.call("POST", "/orders", body, ctoken)
            pay(order, ctoken, "MOBILE_MONEY")
            # App orders get a branch from their delivery zone (or are a shared queue), so the cashier can process them.
            advance(order["id"], C1, ["PREPARING", "READY"])
            if kind == "DELIVERY":
                api.call("POST", f"/delivery/orders/{order['id']}/assign", {"riderId": riders[0]}, C1)
                api.call("POST", f"/delivery/orders/{order['id']}/pickup", {}, tokens["+233200000005"])
                api.call("POST", f"/delivery/orders/{order['id']}/delivered", {}, tokens["+233200000005"])
            else:
                advance(order["id"], C1, ["COMPLETED"])
            finished.append(order["id"])
            counts["completed"] += 1
            if k == 0:
                rating, comment = feedback_texts[c_index % len(feedback_texts)]
                api.call("POST", f"/orders/{order['id']}/feedback", {"rating": rating, "foodRating": rating, "serviceRating": max(1, rating - 1), "comment": comment}, ctoken, ok=False)

    # Live service right now (not back-dated): kitchen tickets at every stage, occupied tables.
    step("Live service (today)")
    live_tables = random.sample(tables, 6)
    stages = [[], [], ["PREPARING"], ["PREPARING"], ["PREPARING", "READY"], ["PREPARING", "READY"]]
    for table, stage in zip(live_tables, stages):
        order = api.call("POST", "/orders", {"type": "DINE_IN", "tableId": table["id"], "items": basket(random.randint(2, 4)),
                                             "notes": random.choice([None, "Birthday table", "Allergic to peanuts"])}, C1)
        if stage or random.random() < 0.5:
            pay(order, C1)
            advance(order["id"], C1, stage)
        counts["active"] += 1
    # Guest QR orders on a terrace table (unpaid, waiting to be accepted)
    guest_table = next(t for t in tables if t["number"] == "7")
    for guest in ("Efua", "Kwesi"):
        api.call("POST", "/orders/public/dine-in", {"tableToken": guest_table["qrToken"], "notes": f"Guest: {guest}", "items": basket(2)})
        counts["active"] += 1
    # A pickup ready for collection and a delivery on the road
    pickup = api.call("POST", "/orders", {"type": "PICKUP", "items": basket(2)}, C2)
    pay(pickup, C2)
    advance(pickup["id"], C2, ["PREPARING", "READY"])
    road = api.call("POST", "/orders", {"type": "DELIVERY", "items": basket(2, zone_min(zones[1])), "deliveryAddress": "7 Cantonments Rd", "deliveryZoneId": zones[1]["id"]}, C2)
    pay(road, C2)
    advance(road["id"], C2, ["PREPARING", "READY"])
    api.call("POST", f"/delivery/orders/{road['id']}/assign", {"riderId": riders[1]}, C2)
    api.call("POST", f"/delivery/orders/{road['id']}/pickup", {}, tokens["+233200000006"])
    ready_delivery = api.call("POST", "/orders", {"type": "DELIVERY", "items": basket(2, zone_min(zones[2])), "deliveryAddress": "21 Spintex Rd", "deliveryZoneId": zones[2]["id"]}, C2)
    pay(ready_delivery, C2)
    advance(ready_delivery["id"], C2, ["PREPARING", "READY"])
    counts["active"] += 3

    # ---------------------------------------------------------------- reservations
    step("Reservations")
    guests = [("Mr & Mrs Ofori", "+233201110001", 2), ("Selasi Agbeko", "+233201110002", 4), ("Team lunch (Hubtel)", "+233201110003", 8),
              ("Dede Lamptey", "+233201110004", 2), ("Ekow Mensah", "+233201110005", 6), ("Afua Koranteng", "+233201110006", 4)]
    for day, (gname, gphone, party) in enumerate(guests):
        table = next(t for t in tables if t["capacity"] >= party and t["number"] not in [lt["number"] for lt in live_tables])
        when = base_day + timedelta(days=1 + day // 2, hours=19 if day % 2 else 13)
        res = api.call("POST", "/reservations", {"tableId": table["id"], "customerName": gname, "customerPhone": gphone, "partySize": party,
                                                 "reservedAt": iso(when), "durationMins": 90, "notes": "Window seat please" if party == 2 else None})
        if day % 3 == 0:
            api.call("PATCH", f"/reservations/{res['id']}/status", {"status": "CONFIRMED"}, C1)
        if day == 5:
            api.call("POST", f"/reservations/{res['id']}/guest-cancel", {"phone": gphone})

    # ---------------------------------------------------------------- group ordering
    step("Group ordering")
    host = tokens[CUSTOMERS[0][0]]
    session = api.call("POST", "/orders/group/sessions", {"displayName": CUSTOMERS[0][1]}, host)
    code = session["sessionCode"]
    joined = api.call("POST", f"/orders/group/sessions/{code}/join", {"displayName": CUSTOMERS[1][1]}, tokens[CUSTOMERS[1][0]])
    api.call("POST", f"/orders/group/sessions/{code}/items", {"participantId": session["hostParticipantId"], "items": basket(2)}, host)
    api.call("POST", f"/orders/group/sessions/{code}/items", {"participantId": joined["participantId"], "items": basket(1)}, tokens[CUSTOMERS[1][0]])

    # ---------------------------------------------------------------- back-date history
    if not args.no_backdate and finished:
        step("Back-dating history over 30 days")
        statements = []
        for order_id in finished:
            days_ago = random.choices(range(1, 31), weights=[3 if d < 8 else 2 if d < 15 else 1 for d in range(1, 31)])[0]
            hour = random.choices([12, 13, 14, 18, 19, 20, 21, 11, 16], weights=[5, 6, 4, 4, 6, 6, 3, 2, 2])[0]
            stamp = (now - timedelta(days=days_ago)).replace(hour=hour, minute=random.randint(0, 59), second=0, microsecond=0)
            ts = stamp.strftime("%Y-%m-%d %H:%M:%S")
            statements.append(f"UPDATE orders SET created_at=TIMESTAMP '{ts}', updated_at=TIMESTAMP '{ts}' + INTERVAL '45' MINUTE WHERE id='{order_id}'")
            statements.append(f"UPDATE payments SET created_at=TIMESTAMP '{ts}' + INTERVAL '2' MINUTE, updated_at=TIMESTAMP '{ts}' + INTERVAL '3' MINUTE, "
                              f"paid_at=CASE WHEN paid_at IS NULL THEN NULL ELSE TIMESTAMP '{ts}' + INTERVAL '3' MINUTE END WHERE order_id='{order_id}'")
        for chunk in range(0, len(statements), 40):
            h2_sql(db_url, ";".join(statements[chunk:chunk + 40]))

    close_auto_shifts()

    # ---------------------------------------------------------------- HR
    from seed_hr import print_hr_summary, seed_hr
    hr_summary = seed_hr(api, A, {"OSU": osu, "ELG": legon}, users, step=step, db_url=db_url)

    # ---------------------------------------------------------------- biometric attendance devices
    from seed_devices import print_devices_summary, seed_devices
    devices_summary = seed_devices(api, A, {"OSU": osu, "ELG": legon}, step=step)

    # ---------------------------------------------------------------- everything else (financial, splits, photos…)
    from seed_extras import print_extras_summary, seed_extras
    extras_summary = seed_extras(api, {
        "tokens": tokens, "customers": CUSTOMERS, "items": items, "tables": tables, "zones": zones, "osu": osu, "legon": legon,
        "basket": basket, "pay": pay, "advance": advance, "h2_sql": h2_sql, "db_url": db_url, "road_order_id": road["id"], "iso": iso, "password": PASSWORD,
    }, step=step)

    print("\nDone.")
    print(f"  Orders: {counts['completed']} finished (history), {counts['active']} live today, {counts['cancelled']} cancelled")
    print(f"  Accounts (password {PASSWORD}):")
    for phone, name, role, _ in STAFF:
        print(f"    {role:<8} {name:<14} {phone}")
    print(f"    CUSTOMER {len(CUSTOMERS)} accounts: {CUSTOMERS[0][0]} … {CUSTOMERS[-1][0]}")
    print("  Manager PIN (financial approvals): 2468")
    print(f"  Promo codes: WELCOME10, FEAST20 (expired: EXPIRED5)   Open group session: {code}")
    print(f"  Guest QR link: /scan/{guest_table['qrToken']}")
    print_hr_summary(hr_summary)
    print_devices_summary(devices_summary)
    print_extras_summary(extras_summary)


def step(label):
    print(f"- {label}", flush=True)


if __name__ == "__main__":
    sys.exit(main())
