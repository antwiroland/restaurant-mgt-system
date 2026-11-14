"""Build the marketing site's images.

    python marketing/scripts/prepare_images.py

Reads the real application screenshots from documentation/screen shots, downloads the food
photographs from Unsplash (Unsplash License: free for commercial use), and writes resized WebP
files plus a typed manifest (src/data/images.ts) that the components import.

Screenshots are only resized or cropped, never edited.
"""
from __future__ import annotations

import io
import json
import os
import sys
import urllib.request
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SITE = ROOT / "marketing"
SHOTS = ROOT / "documentation" / "screen shots"
OUT = SITE / "public" / "images"
CACHE = SITE / ".image-cache"

# key: (file, crop box as fractions or None, alt text)
SCREENS = {
    "dashboard": ("Screenshot 2026-11-14 103400.png", None, "Owner dashboard with revenue, paid orders, best sellers, payment methods and busiest hours"),
    "pos": ("Screenshot 2026-11-14 103419.png", None, "Point of sale with dishes by category and the ticket with dine-in, pickup and delivery"),
    "orders": ("Screenshot 2026-11-14 103432.png", None, "Orders list across dine-in, pickup and delivery with status tabs and next-step buttons"),
    "kds": ("Screenshot 2026-11-14 103504.png", None, "Kitchen display with tickets in accepted, cooking and ready columns and waiting timers"),
    "floor": ("Screenshot 2026-11-14 103517.png", None, "Floor and tables view with available, occupied, reserved and closed counts by zone"),
    "reservations": ("Screenshot 2026-11-14 103529.png", None, "Reservation management with table, guest, time and status"),
    "delivery": ("Screenshot 2026-11-14 103610.png", None, "Delivery dispatch board with rider assignment and delivery zones"),
    "schedule": ("Screenshot 2026-11-14 103627.png", None, "Weekly staff rota and timesheet with worked, scheduled and overtime hours"),
    "payments": ("Screenshot 2026-11-14 103644.png", None, "Payment screen with cash, Mobile Money and card methods"),
    "receipts": ("Screenshot 2026-11-14 103717.png", None, "Receipt center showing an itemised receipt with service charge and taxes"),
    "financial": ("Screenshot 2026-11-14 103734.png", None, "Financial controls: manager PIN approval, discount, void, refund and close of day"),
    "shifts": ("Screenshot 2026-11-14 103752.png", None, "Cash shifts with opening float, open drawers and expected cash"),
    "reports": ("Screenshot 2026-11-14 103804.png", None, "Report exports for sales, reconciliation, audit trail, timesheets and walkouts"),
    "menu": ("Screenshot 2026-11-14 103816.png", None, "Menu builder with categories, dishes and availability switches"),
    "offers": ("Screenshot 2026-11-14 103829.png", None, "Offers: buy-X-get-Y, combos and happy hours"),
    "inventory": ("Screenshot 2026-11-14 103842.png", None, "Ingredients and stock with low-stock status"),
    "feedback": ("Screenshot 2026-11-14 103900.png", None, "Guest feedback and ratings by dish, staff and rider"),
    "hr": ("Screenshot 2026-11-14 103924.png", None, "HR dashboard with headcount, today's attendance and items waiting for approval"),
    "hrreports": ("Screenshot 2026-11-14 103936.png", None, "HR reports: employee register, joiners and leavers, leave, hours and payroll summary"),
    "devices": ("Screenshot 2026-11-14 104007.png", None, "Attendance devices overview: devices, gateways, scans to review"),
    "billing": ("Screenshot 2026-11-14 104027.png", None, "Service charge, tax and tips settings with NHIL, GETFund and VAT"),
    "branches": ("Screenshot 2026-11-14 104103.png", None, "Branch management"),
    "tips": ("Screenshot 2026-11-14 104133.png", None, "Tip and service charge pools"),
    "payroll": ("Screenshot 2026-11-14 104146.png", None, "Payroll runs with period, staff, gross, net and status"),
    "deductions": ("Screenshot 2026-11-14 104200.png", None, "Payroll deduction and employer contribution rules such as SSNIT and PAYE"),
    "attendance": ("Screenshot 2026-11-14 104216.png", None, "Attendance day board: working now, on break, late, absent and missed clock-outs"),
    "pickupboard": ("Screenshot 2026-11-14 104743.png", None, "Public pickup board showing order numbers ready to collect"),
    # table-QR guest pages, cropped to the phone-width column the guest sees
    "qr-welcome": ("Screenshot 2026-11-14 104422.png", (.325, .045, .685, 1), "Table QR welcome page asking for a phone number, no app or account needed"),
    "qr-menu": ("Screenshot 2026-11-14 104448.png", (.325, .045, .685, 1), "Table QR menu with dishes by category"),
    "qr-cart": ("Screenshot 2026-11-14 104513.png", (.375, .205, .628, .84), "Table QR order review with kitchen note and send to kitchen"),
    "qr-tracker": ("Screenshot 2026-11-14 104535.png", (.325, .045, .685, 1), "Table QR live order tracker with pay by Mobile Money and split the bill"),
    "qr-bill": ("Screenshot 2026-11-14 104547.png", (.385, 0, .615, .82), "Running bill: ordered, paid and left to pay"),
    "waiter-call": ("Screenshot 2026-11-14 104630.png", None, "Staff notification: Table 8 wants a waiter, with On my way and Done"),
    "qr-split": ("Screenshot 2026-11-14 104759.png", None, "Split the bill equally between the people at the table"),
    # customer and rider mobile app (status and gesture bars trimmed)
    "app-home": ("Screenshot_1794652296.png", "phone", "Customer app home with deals, table scan and booking"),
    "app-cart": ("Screenshot_1794652309.png", "phone", "Customer app cart with promo code and combo suggestion"),
    "app-checkout": ("Screenshot_1794652318.png", "phone", "Customer app checkout with dine-in, pickup or delivery and Mobile Money"),
    "app-checkout-2": ("Screenshot_1794652326.png", "phone", "Customer app checkout with tip, loyalty points and taxes"),
    "app-profile": ("Screenshot_1794652345.png", "phone", "Customer profile with loyalty points"),
    "app-orders": ("Screenshot_1794652362.png", "phone", "Customer order history with status and reorder"),
    "app-reserve": ("Screenshot_1794652409.png", "phone", "Customer app table reservation form"),
    "rider-list": ("Screenshot_1794655077.png", "phone", "Rider view with active deliveries"),
    "rider-delivery": ("Screenshot_1794655085.png", "phone", "Rider delivery screen with navigation, location sharing and mark delivered"),
}

# Unsplash photos (https://unsplash.com/license). key: (photo id, alt text)
FOOD = {
    "rice-pan": ("1512058564366-18510be2db19", "A pan of colourful seasoned rice with lemon wedges"),
    "served-plate": ("1414235077428-338989a2e8c0", "A plated dish being served at a candle-lit restaurant table"),
    "dining-group": ("1592861956120-e524fc739696", "Friends sharing plates of food at a busy restaurant"),
    "interior-modern": ("1517248135467-4c7edcad34c4", "A modern restaurant dining room with set tables"),
    "interior-hall": ("1555396273-367ea4eb4db5", "A large, bright restaurant hall with wooden tables"),
    "chef-flame": ("1600565193348-f74bd3c7ccdf", "A chef cooking over a flaming pan in a restaurant kitchen"),
    "chef-plating": ("1581349485608-9469926a8e5e", "A chef finishing a row of plated dishes at the pass"),
    "chef-lamps": ("1577219491135-ce391730fb2c", "A chef preparing food under warm heat lamps"),
    "burger-fries": ("1561758033-d89a9ad46330", "A cheeseburger with fries on a wooden board"),
    "cafe": ("1554118811-1e0d58224f24", "A bright café with plants and a coffee bar"),
    "coffee": ("1495474472287-4d71bcdd2085", "Two people toasting with lattes"),
    "hotel-terrace": ("1559339352-11d035aa65de", "A waterside hotel restaurant terrace at dusk"),
    "table-overhead": ("1466978913421-dad2ebd01d17", "Friends reaching for burgers and fries at a shared table"),
    "pizza": ("1513104890138-7c749659a591", "A wood-fired pizza with tomatoes and rosemary"),
    "pasta": ("1563379926898-05f4575a45d8", "Prawn spaghetti in a cast-iron pan"),
    "fried-rice": ("1603133872878-684f208fb84b", "A plate of fried rice with vegetables"),
    "cocktails": ("1551024709-8f23befc6f87", "Three colourful cocktails on a bar counter"),
    "bowl": ("1546069901-ba9599a7e63c", "A fresh salad bowl with salmon and vegetables"),
    "ribs": ("1544025162-d76694265947", "Grilled ribs with tomatoes and sides on a board"),
    "plates-spread": ("1504674900247-0877df9cc836", "An overhead spread of plated dishes with herbs and chilli"),
    "steak": ("1600891964092-4316c288032e", "A grilled steak with seasoned fries"),
}

SCREEN_WIDTHS = [960, 1600]
PHONE_WIDTHS = [360, 720]
FOOD_WIDTHS = [640, 1280]


def save_webp(im: Image.Image, folder: Path, key: str, widths: list[int], quality: int) -> dict:
    folder.mkdir(parents=True, exist_ok=True)
    w, h = im.size
    out = []
    for target in widths:
        tw = min(target, w)
        th = round(h * tw / w)
        im.resize((tw, th), Image.LANCZOS).save(folder / f"{key}-{tw}.webp", "WEBP", quality=quality, method=6)
        out.append(tw)
    return {"w": w, "h": h, "widths": sorted(set(out))}


def screens() -> dict:
    manifest = {}
    for key, (name, box, alt) in SCREENS.items():
        im = Image.open(SHOTS / name).convert("RGB")
        w, h = im.size
        kind = "desktop"
        if box == "phone":
            im = im.crop((0, int(h * .028), w, int(h * .965)))
            kind = "phone"
        elif box:
            im = im.crop((int(box[0] * w), int(box[1] * h), int(box[2] * w), int(box[3] * h)))
            kind = "guest"
        widths = PHONE_WIDTHS if kind in ("phone", "guest") else SCREEN_WIDTHS
        info = save_webp(im, OUT / "screens", key, widths, 86)
        manifest[key] = {**info, "alt": alt, "kind": kind, "dir": "screens"}
        print("screen", key, info)
    return manifest


def food() -> dict:
    manifest = {}
    CACHE.mkdir(exist_ok=True)
    for key, (pid, alt) in FOOD.items():
        cached = CACHE / f"{pid}.jpg"
        if not cached.exists():
            url = f"https://images.unsplash.com/photo-{pid}?w=1600&q=85&fm=jpg&fit=max"
            req = urllib.request.Request(url, headers={"User-Agent": "marketing-image-build"})
            cached.write_bytes(urllib.request.urlopen(req, timeout=60).read())
        im = Image.open(io.BytesIO(cached.read_bytes())).convert("RGB")
        info = save_webp(im, OUT / "food", key, FOOD_WIDTHS, 78)
        manifest[key] = {**info, "alt": alt, "kind": "photo", "dir": "food", "source": f"https://unsplash.com/photos/{pid}"}
        print("food", key, info)
    return manifest


def main() -> None:
    data = {"screens": screens(), "food": food()}
    ts = ["// Generated by scripts/prepare_images.py. Do not edit by hand.",
          "export type ImageInfo = { w: number; h: number; widths: number[]; alt: string; kind: string; dir: string; source?: string };",
          f"export const screens = {json.dumps(data['screens'], indent=2)} as const satisfies Record<string, ImageInfo>;",
          f"export const food = {json.dumps(data['food'], indent=2)} as const satisfies Record<string, ImageInfo>;",
          "export type ScreenKey = keyof typeof screens;",
          "export type FoodKey = keyof typeof food;", ""]
    (SITE / "src" / "data").mkdir(parents=True, exist_ok=True)
    (SITE / "src" / "data" / "images.ts").write_text("\n".join(ts), encoding="utf-8")
    print("manifest written")




def og_image() -> None:
    """1200x630 social card: food photo, real dashboard screenshot, KENVO credit."""
    from PIL import ImageDraw, ImageFilter, ImageFont
    W, H = 1200, 630
    card = Image.new("RGB", (W, H), (30, 24, 19))
    photo = Image.open(CACHE / f"{FOOD['rice-pan'][0]}.jpg").convert("RGB")
    scale = max(820 / photo.size[0], H / photo.size[1])
    photo = photo.resize((int(photo.size[0] * scale), int(photo.size[1] * scale)))
    card.paste(photo.crop((0, 0, 820, H)), (W - 820, 0))
    shade = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(shade)
    for x in range(W):
        d.line([(x, 0), (x, H)], fill=max(0, min(255, int(255 * (1 - (x - 380) / 520)))))
    card = Image.composite(Image.new("RGB", (W, H), (30, 24, 19)), card, shade)
    shot = Image.open(SHOTS / SCREENS["dashboard"][0]).convert("RGB")
    shot = shot.resize((560, int(shot.size[1] * 560 / shot.size[0])))
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((590, 330, 590 + 560, 330 + shot.size[1]), 14, fill=(0, 0, 0, 160))
    card = Image.alpha_composite(card.convert("RGBA"), sh.filter(ImageFilter.GaussianBlur(18))).convert("RGB")
    card.paste(shot, (575, 310))
    d = ImageDraw.Draw(card)
    serif = ImageFont.truetype("georgiab.ttf", 58)
    sans = ImageFont.truetype("segoeuib.ttf", 22)
    small = ImageFont.truetype("segoeui.ttf", 24)
    d.text((64, 70), "RESTAURANT MANAGEMENT SYSTEM", font=sans, fill=(201, 164, 92))
    d.text((64, 120), "Your Restaurant.", font=serif, fill=(251, 246, 238))
    d.text((64, 190), "Smarter. Faster.", font=serif, fill=(194, 86, 43))
    d.text((64, 260), "Better.", font=serif, fill=(251, 246, 238))
    d.text((64, 360), "QR ordering · POS · payments", font=small, fill=(230, 217, 199))
    d.text((64, 395), "attendance · payroll · reports", font=small, fill=(230, 217, 199))
    d.rounded_rectangle((64, 520, 470, 572), 26, fill=(7, 22, 51), outline=(47, 107, 255), width=2)
    d.text((88, 532), "by KENVO Integrated Systems", font=sans, fill=(255, 255, 255))
    card.save(SITE / "public" / "og-image.jpg", quality=86)
    print("og image written")


if __name__ == "__main__" and "--og" in sys.argv:
    og_image()


if __name__ == "__main__" and "--og" not in sys.argv:
    main()
    og_image()
