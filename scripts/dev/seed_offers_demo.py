#!/usr/bin/env python3
"""
Add one demo offer of each kind to a running local backend, to see offers on the menu and in the cart.

  * Buy one dish, get another free : Charcoal Chicken + a free Sobolo
  * Combo price                    : Waakye Bowl + Asaana for 105.00 (normally 120.00)
  * Happy hour percentage off      : 20% off Red Red, Mon-Fri 16:00-18:00
  * One branch only                : 10% off Bofrot & Caramel at the first branch

The seeder already adds "Kelewele 2 + 1" (buy 2, get 1 free). Needs the menu from seed_local.py
(password Passw0rd!). Safe to run again: offers that already exist (by name) are left alone.

    python scripts/dev/seed_offers_demo.py [--api http://localhost:8080]
"""
from __future__ import annotations

import argparse
import json
import urllib.error
import urllib.request

PASSWORD = "Passw0rd!"
ADMIN_PHONE = "+233200000001"


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


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api", default="http://localhost:8080", help="backend base URL")
    base = parser.parse_args().api.rstrip("/")

    token = call(base, "POST", "/auth/login", {"phone": ADMIN_PHONE, "password": PASSWORD})["accessToken"]
    menu = call(base, "GET", "/menu/items")
    menu = menu.get("content", menu) if isinstance(menu, dict) else menu
    dish = {item["name"]: item["id"] for item in menu}
    needed = ["Charcoal Chicken", "Sobolo", "Waakye Bowl", "Asaana", "Red Red", "Bofrot & Caramel"]
    missing = [name for name in needed if name not in dish]
    if missing:
        raise SystemExit(f"Menu is missing {', '.join(missing)}. Seed it first with seed_local.py.")
    branches = [b for b in call(base, "GET", "/branches", token=token) if b.get("active")]

    offers = [
        {"name": "Chicken + free Sobolo", "offerType": "BUY_X_GET_OTHER",
         "description": "Every Charcoal Chicken comes with a free Sobolo.",
         "menuItemId": dish["Charcoal Chicken"], "buyQuantity": 1,
         "freeMenuItemId": dish["Sobolo"], "freeQuantity": 1},
        {"name": "Waakye lunch combo", "offerType": "COMBO",
         "description": "A Waakye Bowl and a cold Asaana together.",
         "comboPrice": 105, "comboItems": [{"menuItemId": dish["Waakye Bowl"], "quantity": 1},
                                           {"menuItemId": dish["Asaana"], "quantity": 1}]},
        {"name": "Red Red happy hour", "offerType": "PERCENT_OFF",
         "description": "Weekday afternoons.", "menuItemId": dish["Red Red"], "percentOff": 20,
         "daysOfWeek": ["MON", "TUE", "WED", "THU", "FRI"], "dailyStartTime": "16:00", "dailyEndTime": "18:00"},
    ]
    if branches:
        offers.append({"name": f"{branches[0]['name']} dessert deal", "offerType": "PERCENT_OFF",
                       "menuItemId": dish["Bofrot & Caramel"], "percentOff": 10, "branchId": branches[0]["id"]})

    existing = {offer["name"] for offer in call(base, "GET", "/promotions/offers", token=token)}
    for offer in offers:
        if offer["name"] in existing:
            print(f"  exists   {offer['name']}")
            continue
        created = call(base, "POST", "/promotions/offers", offer, token)
        when = created.get("schedule") or "all day, every day"
        where = f", {created['branchName']} only" if created.get("branchName") else ""
        print(f"  created  {created['name']}: {created['summary']} ({when}{where})")
    print("See them on the menu (/explore), the dish pages, and as suggestions in the cart.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
