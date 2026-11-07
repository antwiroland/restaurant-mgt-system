#!/usr/bin/env python3
"""
Regenerate the app icon, Android adaptive icon, splash image and favicon in
the "Hearth" brand style (terracotta monogram tile with a gold rule on
espresso). Dependency-free: draws anti-aliased shapes with signed distance
functions and writes PNGs with zlib, so no Pillow is needed.

    python mobile/scripts/generate-brand-assets.py

Colours mirror mobile/src/theme.ts.
"""
import math
import os
import struct
import zlib

ESPRESSO = (0x23, 0x1A, 0x15)
TERRACOTTA = (0xB4, 0x53, 0x2A)
GOLD = (0xC9, 0xA4, 0x5C)
IVORY = (0xFB, 0xF7, 0xF0)

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets")


def sd_round_box(px, py, cx, cy, hw, hh, r):
    """Signed distance to a rounded rectangle centred on (cx, cy)."""
    qx = abs(px - cx) - (hw - r)
    qy = abs(py - cy) - (hh - r)
    outside = math.hypot(max(qx, 0.0), max(qy, 0.0))
    inside = min(max(qx, qy), 0.0)
    return outside + inside - r


def coverage(d):
    return max(0.0, min(1.0, 0.5 - d))


def mark_layers(cx, cy, size):
    """The monogram tile, as (signed-distance function, colour) layers drawn in order."""
    half = size / 2
    radius = size * 0.22
    stem_w, stem_h = size * 0.13, size * 0.50
    gap = size * 0.17                       # distance from centre to each stem's centre
    bar_h = size * 0.095
    rule_w, rule_h = size * 0.30, size * 0.035
    glyph_cy = cy - size * 0.03
    layers = [
        (lambda x, y: sd_round_box(x, y, cx, cy, half, half, radius), TERRACOTTA),
        (lambda x, y: min(
            sd_round_box(x, y, cx - gap, glyph_cy, stem_w / 2, stem_h / 2, stem_w * 0.12),
            sd_round_box(x, y, cx + gap, glyph_cy, stem_w / 2, stem_h / 2, stem_w * 0.12),
            sd_round_box(x, y, cx, glyph_cy, gap, bar_h / 2, 0),
        ), IVORY),
        (lambda x, y: sd_round_box(x, y, cx, cy + size * 0.33, rule_w / 2, rule_h / 2, rule_h / 2), GOLD),
    ]
    return layers, (cx - half - 2, cy - half - 2, cx + half + 2, cy + half + 2)


def render(width, height, background, mark_center, mark_size):
    """background=None renders a transparent canvas (for the adaptive-icon foreground)."""
    layers, (x0, y0, x1, y1) = mark_layers(mark_center[0], mark_center[1], mark_size)
    rows = []
    bg = (*background, 255) if background else (0, 0, 0, 0)
    for y in range(height):
        row = bytearray()
        py = y + 0.5
        for x in range(width):
            r, g, b, a = bg
            if x0 <= x <= x1 and y0 <= y <= y1:
                px = x + 0.5
                for sdf, colour in layers:
                    c = coverage(sdf(px, py))
                    if c <= 0:
                        continue
                    # "over" compositing
                    out_a = c + (a / 255) * (1 - c)
                    if out_a > 0:
                        r = (colour[0] * c + r * (a / 255) * (1 - c)) / out_a
                        g = (colour[1] * c + g * (a / 255) * (1 - c)) / out_a
                        b = (colour[2] * c + b * (a / 255) * (1 - c)) / out_a
                    a = out_a * 255
            row += bytes((int(round(r)), int(round(g)), int(round(b)), int(round(a))))
        rows.append(bytes(row))
    return rows


def write_png(path, width, height, rows):
    raw = b"".join(b"\x00" + row for row in rows)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as fh:
        fh.write(png)
    print(f"wrote {os.path.relpath(path)} ({width}x{height})")


def main():
    # App icon: full-bleed espresso, monogram tile centred.
    write_png(os.path.join(ASSETS, "icon.png"), 1024, 1024, render(1024, 1024, ESPRESSO, (512, 512), 640))
    # Android adaptive foreground: transparent, tile inside the 66% safe zone (background colour set in app.json).
    write_png(os.path.join(ASSETS, "adaptive-icon.png"), 1024, 1024, render(1024, 1024, None, (512, 512), 520))
    # Splash: espresso canvas with a modest tile (resizeMode "contain", backgroundColor matches).
    write_png(os.path.join(ASSETS, "splash.png"), 1242, 2436, render(1242, 2436, ESPRESSO, (621, 1218), 360))
    # Web favicon.
    write_png(os.path.join(ASSETS, "favicon.png"), 48, 48, render(48, 48, ESPRESSO, (24, 24), 44))


if __name__ == "__main__":
    main()
