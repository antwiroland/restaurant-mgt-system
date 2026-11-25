#!/usr/bin/env python3
"""
Course dining demo data on a running local backend (after seed_local.py), so every course-dining screen has
something to show:

  * drink categories marked as beverages and prepared at the BAR (they are never held for a course), and
    dessert-like categories prepared at a PASTRY station: try the station filter on the Kitchen Display
  * Table A (guest on the QR page): drinks now, starter, main and dessert. The cashier released the starter
    (on the KDS with "Sent by"), and the guest has tapped "Request next course" for the main: it waits on the
    Courses page and the cashier's screen
  * Table B (ordered by the manager at the POS): a main and a dessert, the dessert put on hold
  * Table C (guest): the starter was released, cooked and served; the main is released and cooking

Safe to re-run only on tables that are free: each run picks AVAILABLE tables at the first branch.
Password for the seeded accounts: Passw0rd!

    python scripts/dev/seed_dining_demo.py [--api http://localhost:8080]
"""

import argparse
import json
import re
import urllib.error
import urllib.request

PASSWORD = "Passw0rd!"
ADMIN_PHONE = "+233200000001"
MANAGER_PHONE = "+233200000002"
CASHIER_PHONE = "+233200000003"
DRINKS = re.compile(r"drink|beverage|juice|smoothie|cocktail|mocktail|wine|beer|soda|soft|coffee|tea|bar", re.I)
SWEETS = re.compile(r"dessert|sweet|pastr|cake|snack", re.I)


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


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api", default="http://localhost:8080", help="backend base URL")
    base = parser.parse_args().api.rstrip("/")

    admin, manager, cashier = login(base, ADMIN_PHONE), login(base, MANAGER_PHONE), login(base, CASHIER_PHONE)

    # 1. Categories: drinks skip courses and go to the bar; desserts to the pastry station.
    for category in call(base, "GET", "/menu/categories"):
        beverage = bool(DRINKS.search(category["name"]))
        station = "BAR" if beverage else "PASTRY" if SWEETS.search(category["name"]) else ""
        call(base, "PUT", f"/menu/categories/{category['id']}", {
            "name": category["name"], "description": category.get("description"),
            "displayOrder": category["displayOrder"], "active": category["active"],
            "beverage": beverage, "kitchenStation": station,
        }, admin)

    # 2. Dishes without required options, sorted into drinks / sweets / food.
    menu = call(base, "GET", "/menu/items")
    menu = menu.get("content", menu) if isinstance(menu, dict) else menu
    categories = {c["id"]: c for c in call(base, "GET", "/menu/categories")}
    drinks, sweets, food = [], [], []
    for item in menu:
        if not item.get("available", True) or not item.get("active", True):
            continue
        if any(group.get("required") for group in call(base, "GET", f"/menu/items/{item['id']}/modifiers") or []):
            continue
        category = categories.get(item.get("categoryId"), {})
        (drinks if category.get("beverage") else sweets if category.get("kitchenStation") == "PASTRY" else food).append(item)
    if len(food) < 3 or not drinks:
        raise SystemExit(f"Need at least 3 dishes and 1 drink without required options (found {len(food)} and {len(drinks)}).")
    dessert = sweets[0] if sweets else food[-1]

    courses = {c["name"]: c["id"] for c in call(base, "GET", "/dining/courses", token=admin)}
    starter, main_course, dessert_course = courses.get("Starter"), courses.get("Main Course"), courses.get("Dessert")
    if not (starter and main_course and dessert_course):
        raise SystemExit(f"Expected the Starter, Main Course and Dessert courses; found {sorted(courses)}")

    # 3. Three free tables at the first branch.
    tables = call(base, "GET", "/tables", token=admin)
    first_branch = next((t.get("branchId") for t in tables if t.get("branchId")), None)
    free = sorted((t for t in tables if t["status"] == "AVAILABLE" and t.get("branchId") == first_branch),
                  key=lambda t: int(t["number"]) if t["number"].isdigit() else 999)
    if len(free) < 3:
        raise SystemExit("Need 3 AVAILABLE tables at the first branch. Settle some bills on Floor & Tables, then re-run.")
    table_a, table_b, table_c = free[0], free[1], free[2]

    def item(dish, course=None, qty=1, notes=None):
        line = {"menuItemId": dish["id"], "quantity": qty, "courseId": course}
        if notes:
            line["notes"] = notes
        return line

    def guest_order(table, items, name, phone):
        call(base, "POST", f"/dining/public/tables/{table['qrToken']}/items",
             {"items": items, "guestName": name, "guestPhone": phone, "sessionRef": f"demo-{table['number']}"})
        return call(base, "GET", f"/dining/tables/{table['id']}/active-order", token=cashier)

    def course_of(meal, name):
        return next(c for c in meal["courses"] if c["courseName"] == name)

    def advance(course, statuses):
        for batch in course["batches"]:
            for status in statuses:
                call(base, "PATCH", f"/orders/{batch['orderId']}/status", {"status": status}, cashier)

    # Table A: guest, drinks now + three courses; starter released by the cashier; main requested by the guest.
    meal_a = guest_order(table_a, [
        item(drinks[0], qty=2),
        item(food[0], starter, 2),
        item(food[1], main_course, 2, "One without pepper"),
        item(dessert, dessert_course, 2),
    ], "Akua Demo", "+233245550101")
    call(base, "POST", f"/dining/order-courses/{course_of(meal_a, 'Starter')['id']}/release", token=cashier)
    call(base, "POST", f"/dining/public/tables/{table_a['qrToken']}/request-next-course",
         {"sessionRef": f"demo-{table_a['number']}"})

    # Table B: the manager orders at the POS; the dessert is held until the table says so.
    meal_b = call(base, "POST", f"/dining/tables/{table_b['id']}/items", {"items": [
        item(drinks[min(1, len(drinks) - 1)]),
        item(food[2], main_course, 3),
        item(dessert, dessert_course, 3),
    ], "notes": "Birthday table: candle with the dessert"}, manager)
    call(base, "POST", f"/dining/order-courses/{course_of(meal_b, 'Dessert')['id']}/hold", token=manager)

    # Table C: guest; starter released, cooked, ready and served; main released and cooking.
    meal_c = guest_order(table_c, [item(food[1], starter), item(food[0], main_course)], "Kwesi Demo", "+233245550102")
    call(base, "POST", f"/dining/order-courses/{course_of(meal_c, 'Starter')['id']}/release", token=cashier)
    meal_c = call(base, "GET", f"/dining/tables/{table_c['id']}/active-order", token=cashier)
    advance(course_of(meal_c, "Starter"), ["CONFIRMED", "PREPARING", "READY"])
    call(base, "POST", f"/dining/order-courses/{course_of(meal_c, 'Starter')['id']}/serve", token=cashier)
    call(base, "POST", f"/dining/order-courses/{course_of(meal_c, 'Main Course')['id']}/release", token=manager)
    meal_c = call(base, "GET", f"/dining/tables/{table_c['id']}/active-order", token=cashier)
    advance(course_of(meal_c, "Main Course"), ["CONFIRMED", "PREPARING"])

    pending = call(base, "GET", "/dining/course-release-requests/pending", token=cashier)
    print("Course dining demo:")
    print(f"  Table {table_a['number']}: starter sent by the cashier; guest asked for the main ({len(pending)} request(s) waiting)")
    print(f"         guest page: /scan/{table_a['qrToken']}")
    print(f"  Table {table_b['number']}: manager's order, main waiting, dessert on hold")
    print(f"  Table {table_c['number']}: starter served, main cooking")
    print(f"         guest page: /scan/{table_c['qrToken']}")
    print("Staff: Service > Courses; Kitchen Display (filter by station: Bar, Pastry).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
