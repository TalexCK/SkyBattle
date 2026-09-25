#!/usr/bin/env python3
"""Regenerates the SkyBattle resource pack assets (textures, item models, pack.png).

Run from the repository root:  python3 resourcepack/tools/generate_pack.py
Requires Pillow. The Maven build zips resourcepack/pack into the plugin jar.
"""
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "pack"
NAMESPACE = "skybattle"

# name -> (kind, base colour, accent colour)
ITEMS = {
    "timed_orb_of_harming": ("orb", (196, 30, 58), (255, 214, 102)),
    "orb_of_poison": ("orb", (78, 147, 49), (30, 60, 20)),
    "orb_of_slowness": ("orb", (90, 108, 129), (200, 215, 235)),
    "orb_of_cleansing": ("orb", (120, 220, 245), (255, 255, 255)),
    "spark_of_levitation": ("spark", (235, 230, 255), (170, 150, 255)),
    "spark_of_regeneration": ("spark", (255, 120, 190), (255, 220, 235)),
    "spark_of_speed": ("spark", (80, 225, 255), (220, 250, 255)),
}


def shade(colour, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in colour)


def orb(base, accent, name):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = image.load()
    cx, cy, radius = 7.5, 7.5, 6.6
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            distance = math.hypot(dx, dy)
            if distance > radius:
                continue
            # Light from the top-left.
            light = 1.15 - 0.55 * ((dx + dy) / (2 * radius) + 0.5)
            colour = shade(base, light)
            if distance > radius - 1.0:
                colour = shade(base, 0.45)
            pixels[x, y] = colour + (255,)
    draw = ImageDraw.Draw(image)
    # Specular highlight.
    draw.point([(5, 4), (4, 5), (5, 5)], fill=(255, 255, 255, 230))
    draw.point([(6, 4), (4, 6)], fill=(255, 255, 255, 140))
    if name == "timed_orb_of_harming":
        # Clock hands: the orb explodes on a timer.
        draw.line([(8, 8), (8, 5)], fill=accent + (255,))
        draw.line([(8, 8), (10, 9)], fill=accent + (255,))
    elif name == "orb_of_poison":
        for point in [(9, 6), (10, 10), (6, 10), (8, 12)]:
            draw.point(point, fill=accent + (255,))
    elif name == "orb_of_slowness":
        draw.line([(6, 8), (10, 8)], fill=accent + (255,))
        draw.line([(6, 10), (10, 10)], fill=accent + (255,))
    elif name == "orb_of_cleansing":
        draw.line([(9, 7), (9, 11)], fill=accent + (255,))
        draw.line([(7, 9), (11, 9)], fill=accent + (255,))
    return image


def spark(base, accent):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = image.load()
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - cx), abs(y - cy)
            # Four point star: thin long rays on the axes, thicker near the core.
            thin, thick = min(dx, dy), max(dx, dy)
            star = (thin <= 0.5 and thick < 7.2) or (thin <= 1.5 and thick < 4.2)
            glow = math.hypot(dx, dy) < 2.9
            if star or glow:
                t = max(0.0, 1.0 - math.hypot(dx, dy) / 7.5)
                colour = tuple(int(a * t + b * (1 - t)) for a, b in zip(accent, base))
                alpha = 255 if star else 170
                pixels[x, y] = colour + (alpha,)
    draw = ImageDraw.Draw(image)
    draw.point([(7, 7), (8, 8), (7, 8), (8, 7)], fill=(255, 255, 255, 255))
    for point in [(2, 3), (13, 12), (12, 2)]:
        draw.point(point, fill=accent + (200,))
    return image


def icon():
    image = Image.new("RGBA", (64, 64), (24, 36, 70, 255))
    draw = ImageDraw.Draw(image)
    for y in range(64):
        colour = (40 + y, 90 + y, 170 + min(80, y))
        draw.line([(0, y), (63, y)], fill=colour + (255,))
    draw.ellipse([10, 30, 54, 44], fill=(90, 160, 70, 255))
    draw.rectangle([18, 36, 46, 52], fill=(120, 85, 50, 255))
    draw.ellipse([22, 8, 42, 28], fill=(255, 255, 255, 220))
    return image


def main():
    textures = ROOT / "assets" / NAMESPACE / "textures" / "item"
    models = ROOT / "assets" / NAMESPACE / "models" / "item"
    definitions = ROOT / "assets" / NAMESPACE / "items"
    for folder in (textures, models, definitions):
        folder.mkdir(parents=True, exist_ok=True)
    for name, (kind, base, accent) in ITEMS.items():
        image = orb(base, accent, name) if kind == "orb" else spark(base, accent)
        image.save(textures / f"{name}.png")
        (models / f"{name}.json").write_text(json.dumps({
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NAMESPACE}:item/{name}"},
        }, indent=2) + "\n")
        # 1.21.4+ item model definition, referenced by the item_model component.
        (definitions / f"{name}.json").write_text(json.dumps({
            "model": {"type": "minecraft:model", "model": f"{NAMESPACE}:item/{name}"},
        }, indent=2) + "\n")
    icon().save(ROOT / "pack.png")
    (ROOT / "pack.mcmeta").write_text(json.dumps({
        "pack": {
            "description": "SkyBattle Resource",
            "pack_format": 75,
            "min_format": 75,
            "max_format": 999,
        }
    }, indent=2) + "\n")


if __name__ == "__main__":
    main()
