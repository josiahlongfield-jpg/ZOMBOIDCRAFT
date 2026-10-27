"""Flat 16x16 icons for earning an aircraft (world/cars/AirReadiness): three flight manuals, a drum of jet fuel, an
aircraft battery and aircraft parts. Hand-placed pixels in the same flat style as the duct tape and gun parts, with a
dark outline and a light top-left edge.

Writes textures/item/<name>.png, models/item/<name>.json and items/<name>.json.

    python3 tools/items/gen_aircraft_items.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, k):
    return tuple(max(0, min(255, round(v * k))) for v in c[:3]) + (c[3],)


def outline(img, col=(28, 26, 24, 255)):
    """A one-pixel dark edge round everything drawn."""
    w, h = img.size
    px = img.load()
    filled = {(x, y) for x in range(w) for y in range(h) if px[x, y][3] > 0}
    for x, y in [(x, y) for x in range(w) for y in range(h)]:
        if (x, y) in filled:
            continue
        if any((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px[x, y] = col
    return img


def book(cover, emblem):
    """A manual seen slightly from the side: cover, spine on the left, pages along the right and bottom."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    c = hexc(cover)
    for y in range(2, 14):
        for x in range(3, 13):
            px[x, y] = c
    for y in range(2, 14):
        px[3, y] = shade(c, 0.7)          # spine
        px[4, y] = shade(c, 0.85)
    for y in range(3, 15):
        px[13, y] = hexc('#e8e2cf') if y % 2 else hexc('#cfc8b2')    # page edges
    for x in range(4, 14):
        px[x, 14] = hexc('#d9d2bd')
    for x in range(5, 12):
        px[x, 2] = shade(c, 1.25)         # light top edge
    # a white title strip near the top
    for x in range(6, 12):
        px[x, 4] = hexc('#e9e6dc')
    emblem(px)
    return outline(img)


def rotor(px):
    w = hexc('#e9e6dc')
    for x in range(5, 13):
        px[x, 8] = w
    for y in (9, 10):
        px[9, y] = w
    for x in range(7, 12):
        px[x, 11] = w


def jet(px):
    w = hexc('#e9e6dc')
    for y in range(6, 13):
        px[9, y] = w
    for x in range(6, 13):
        px[x, 9] = w
    px[7, 10] = px[11, 10] = w
    px[8, 12] = px[10, 12] = w


def sight(px):
    w = hexc('#e9e6dc')
    for k in range(6, 13):
        px[k, 9] = w
        px[9, k] = w
    for x, y in ((7, 7), (11, 7), (7, 11), (11, 11)):
        px[x, y] = w
    px[9, 9] = hexc('#8b1e1a')


def drum():
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    body, band = hexc('#4b5530'), hexc('#d8b21c')
    for y in range(2, 15):
        for x in range(4, 12):
            k = 1.18 if x == 5 else 0.78 if x == 10 else 1
            px[x, y] = shade(body, k)
    for y in (5, 11):                       # rolling hoops
        for x in range(4, 12):
            px[x, y] = shade(body, 0.62)
    for y in (7, 8):                        # yellow fuel band
        for x in range(4, 12):
            px[x, y] = shade(band, 1.1 if x == 5 else 0.8 if x == 10 else 1)
    for x in range(5, 11):
        px[x, 2] = shade(body, 1.35)       # lid rim
    px[9, 1] = hexc('#8a8a80')              # bung
    return outline(img)


def battery():
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    case = hexc('#2f3133')
    for y in range(5, 14):
        for x in range(2, 14):
            px[x, y] = shade(case, 1.2 if y == 5 else 0.85 if y == 13 else 1)
    for x in range(4, 12):                  # label
        for y in (8, 9, 10):
            px[x, y] = hexc('#c9a227') if y != 9 else hexc('#a8861c')
    for x, col in ((4, '#b0302a'), (11, '#9a9a96')):     # terminals
        px[x, 4] = hexc(col)
        px[x, 3] = shade(hexc(col), 1.2)
    for x in range(6, 10):                  # carry handle
        px[x, 3] = hexc('#4a4c4e')
    px[6, 4] = px[9, 4] = hexc('#4a4c4e')
    return outline(img)


def parts():
    """A turbine blade and a bolt on a coil of wire: the spares that keep an airframe flying."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    steel = hexc('#a7adb3')
    # gear
    cx, cy = 6, 9
    for y in range(16):
        for x in range(16):
            d2 = (x - cx) ** 2 + (y - cy) ** 2
            if d2 <= 13 or (d2 <= 20 and (x + y) % 2 == 0):
                px[x, y] = shade(steel, 1.15 if x < cx else 0.85)
    px[cx, cy] = px[cx + 1, cy] = hexc('#3a3d40')
    # turbine blade, angled
    blade = hexc('#c5b48a')
    for k in range(6):
        px[9 + k, 7 - k] = shade(blade, 1.1)
        px[10 + k, 7 - k] = blade
        if k < 5:
            px[10 + k, 8 - k] = shade(blade, 0.8)
    # red wire
    for x in range(8, 15):
        px[x, 13 if x % 3 else 12] = hexc('#a3302a')
    return outline(img)


ITEMS = {
    'rotary_wing_manual': lambda: book('#56603a', rotor),
    'jet_flight_manual': lambda: book('#25344f', jet),
    'aircraft_weapons_manual': lambda: book('#5a2420', sight),
    'jet_fuel_drum': drum,
    'aircraft_battery': battery,
    'aircraft_parts': parts,
}


def main():
    for name, make in ITEMS.items():
        make().save(ASSETS / 'textures/item' / f'{name}.png')
        (ASSETS / 'models/item' / f'{name}.json').write_text(json.dumps(
            {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'zomboidcraft:item/{name}'}}, indent=2) + '\n')
        (ASSETS / 'items' / f'{name}.json').write_text(json.dumps(
            {'model': {'type': 'minecraft:model', 'model': f'zomboidcraft:item/{name}'}}, indent=2) + '\n')
        print('wrote', name)


if __name__ == '__main__':
    main()
