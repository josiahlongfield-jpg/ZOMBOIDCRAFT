"""The ha_ kit: Zomboid's house alarms (world/county/House.alarm, world/gen/HouseAlarms), drawn from the photos
in refs/alarm.

- ha_bell_box: the old kind, a red gong bell on a grey steel backplate with the striker housing in its
  middle, under the eaves out front (rotate=facing; the wall to the south as drawn);
- ha_siren_box: the newer kind (refs/alarm/house-alarm-bell-box): a white plastic box with a blue strobe
  lens along its bottom edge;
- ha_keypad: the keypad inside by the front door (refs/alarm/alarm-keypad-panel): a beige plastic case, its
  green LCD, twelve keys and the ARMED and READY lamps.

Hold G at any of them with Electrical 5 and a screwdriver to cut the alarm's wires.

    python3 tools/city/housealarm.py      rewrites the ha_ entries in blocks.json

photo_textures.py draws the textures (housealarm.register()).
"""
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from gasstation import weather  # noqa: E402

N = P.N
HERE = Path(__file__).parent
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


# ---------------------------------------------------------------- textures

def draw(seed, base, paint, smooth=0.4):
    """A texture drawn with PIL on a base colour: paint(draw) gets an ImageDraw over N x N."""
    from PIL import Image, ImageDraw
    im = Image.new("RGB", (N, N), base)
    paint(ImageDraw.Draw(im))
    t = F.plain(seed, base, 0.03, smooth)
    t.c = np.array(im).astype(np.float32)
    t.vary(0.03)
    return t


def siren_face(seed):
    """The white box's front: a faint moulded line, the blue lens across the bottom (refs: house-alarm-bell-box)."""
    def paint(d):
        d.rounded_rectangle([1, 1, N - 2, N - 2], 6, outline=(214, 214, 208))
        d.rounded_rectangle([8, 40, N - 9, 54], 7, fill=(28, 44, 120), outline=(16, 24, 70))
        d.line([14, 44, N - 15, 44], fill=(70, 96, 190), width=2)
    t = draw(seed, (236, 236, 230), paint, 0.55)
    weather(t, rust=0.0, streak=0.35, grime=0.3)
    return t


def white(seed):
    t = F.plain(seed, (232, 232, 226), 0.04, 0.5)
    weather(t, rust=0.0, streak=0.3, grime=0.25)
    return t


def lens(seed):
    t = F.plain(seed, (34, 54, 140), 0.06, 0.85)
    t.c[:, 20:26] = t.c[:, 20:26] * 0.6 + np.array((120, 150, 230), np.float32) * 0.4
    return t


def gong(seed):
    """The bell's red enamel: rings round the dome where it's turned, a pale highlight, weathered dull."""
    t = F.plain(seed, (170, 26, 22), 0.05, 0.6)
    yy, xx = np.mgrid[0:N, 0:N]
    r = np.hypot(xx - 31.5, yy - 31.5)
    t.c[(np.abs(r - 12) < 1.0) | (np.abs(r - 22) < 1.0)] *= 0.75
    hi = np.exp(-((xx - 22) ** 2 + (yy - 20) ** 2) / 60.0)[..., None]
    t.c = t.c * (1 - hi * 0.5) + np.array((240, 170, 160), np.float32) * hi * 0.5
    weather(t, rust=0.15, streak=0.3, grime=0.3)
    return t


def plate(seed):
    """The steel backplate, grey hammer paint, a screw at each corner, rust weeping from them."""
    t = F.plain(seed, (118, 120, 118), 0.06, 0.35)
    for x, y in ((6, 6), (57, 6), (6, 57), (57, 57)):
        t.c[y - 2:y + 2, x - 2:x + 2] = (60, 60, 58)
    weather(t, rust=0.3, streak=0.4, grime=0.3)
    return t


def keypad(seed):
    """Beige case, the LCD, twelve keys, ARMED (red) and READY (green) lamps (refs: alarm-keypad-panel)."""
    from PIL import ImageFont
    f = ImageFont.truetype(SANS, 5)

    def paint(d):
        d.rounded_rectangle([1, 1, N - 2, N - 2], 5, outline=(160, 150, 126))
        d.rectangle([9, 7, N - 10, 19], fill=(80, 104, 70), outline=(48, 52, 44))
        d.text((13, 10), "SYSTEM", font=f, fill=(30, 44, 28))
        d.ellipse([10, 23, 14, 27], fill=(150, 30, 26))
        d.text((16, 23), "ARM", font=f, fill=(90, 84, 70))
        d.ellipse([31, 23, 35, 27], fill=(40, 140, 50))
        d.text((37, 23), "RDY", font=f, fill=(90, 84, 70))
        keys = "123456789*0#"
        for i, k in enumerate(keys):
            x, y = 12 + (i % 3) * 14, 31 + (i // 3) * 8
            d.rounded_rectangle([x, y, x + 11, y + 6], 2, fill=(224, 218, 200), outline=(150, 142, 120))
            d.text((x + 4, y), k, font=f, fill=(50, 46, 40))
    t = draw(seed, (206, 196, 168), paint, 0.45)
    t.grime(0.25, (90, 80, 60))
    return t


def textures():
    return {
        "ha_siren_face": lambda: siren_face(88200),
        "ha_white": lambda: white(88201),
        "ha_lens": lambda: lens(88202),
        "ha_gong": lambda: gong(88203),
        "ha_plate": lambda: plate(88204),
        "ha_keypad": lambda: keypad(88205),
    }


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- models (the wall to the south, +z)

EDGES = {"up": [0, 0, 16, 1], "down": [0, 0, 16, 1], "east": [0, 0, 1, 16], "west": [0, 0, 1, 16]}
FULL = dict(EDGES, north=[0, 0, 16, 16], south=[0, 0, 16, 16])


def disc_strips(r, cx, cy, step=1.5):
    """Horizontal strips filling a circle of radius r round (cx, cy): (y0, y1, x0, x1)."""
    out = []
    y = cy - r
    while y < cy + r - 0.01:
        y1 = min(cy + r, y + step)
        m = (y + y1) / 2 - cy
        h = math.sqrt(max(0.0, r * r - m * m))
        out.append((round(y, 2), round(y1, 2), round(cx - h, 2), round(cx + h, 2)))
        y = y1
    return out


def bell_box():
    """Backplate on the wall, the gong standing off it in three steps to its crown, the striker on top of
    the dome, the conduit going up into the soffit."""
    b = [[3, 2, 15, 13, 14, 16, "ha_plate", FULL]]
    for (r, z0, z1) in ((5.2, 12.5, 15), (4.4, 11.5, 12.5), (3.0, 10.8, 11.5)):
        for y0, y1, x0, x1 in disc_strips(r, 8, 8):
            b.append([x0, y0, z0, x1, y1, z1, "ha_gong"])
    b += [[7, 7, 10.2, 9, 9, 10.8, "painted_steel"],      # the striker housing
          [7.5, 14, 15, 8.5, 16, 16, "painted_steel"]]   # the conduit up into the eaves
    return b


def siren_box():
    """The white box, its front a little tapered (a frame round a slightly smaller front), the lens below."""
    face = {"north": "ha_siren_face", "south": "ha_white", "side": "ha_white", "up": "ha_white", "down": "ha_white"}
    return [[3, 4, 13, 13, 12, 16, "ha_white"],
            [3.6, 4.4, 12, 12.4, 11.6, 13, face, dict(FULL, north=[0, 0, 16, 16])],
            [5, 5, 11.6, 11, 6.6, 12, "ha_lens"],
            [7.5, 12, 15, 8.5, 16, 16, "ha_white"]]        # the cable cover up to the soffit


def keypad_model():
    return [[5, 3, 15, 11, 11, 16, {"north": "ha_keypad", "side": "home_plastic_black", "south": "ha_white",
                                    "up": "ha_white", "down": "ha_white"}, dict(FULL, north=[0, 0, 16, 16])]]


def blocks():
    out = {}

    def add(name, boxes, title, sound, **extra):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound, "rotate": "facing", "decor": True}
        spec.update(extra)
        out[name] = spec

    add("ha_bell_box", bell_box(), "Alarm Bell", "METAL")
    add("ha_siren_box", siren_box(), "Alarm Siren", "STONE")
    add("ha_keypad", keypad_model(), "Alarm Keypad", "STONE")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    blocks_ = data["blocks"]
    made = blocks()
    for k in [k for k in blocks_ if k.startswith("ha_") and k not in made]:
        del blocks_[k]
    blocks_.update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} house alarm blocks in {path}")


if __name__ == "__main__":
    main()
