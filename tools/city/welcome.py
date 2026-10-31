"""The we_ kit: what greets you at the edge of a town and in its front yards (refs/suburb/
roadside-billboards: the painted "small town with a big welcome" board on its posts; picket-fences
and house-foundations-porches: the realtor's sign swinging in the yard). world/county/TownSite.welcome
and Homes set them out.

Every piece is drawn with the road on its north (z = 0), rotate=facing:

- we_{clean|spray}_{l|m|r}{0|1}: the welcome board, three blocks wide and two high, l/m/r as seen
  from the road (l is the +x end as drawn), row 0 at the bottom: cream boards in a green frame,
  WELCOME, the town's slogan and the year it was founded, the shingled cap over it, the timber
  posts at the ends, the brick planter along its foot; spray: the same with TURN BACK sprayed
  across it in red, running;
- we_sale: a realtor's sign hung from the arm of its white post, swinging, the post to the west;
- we_sale_down: the same sign lying in the grass where the post snapped.

    python3 tools/city/welcome.py      rewrites the we_ entries in blocks.json

photo_textures.py draws the textures (welcome.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from gasstation import weather  # noqa: E402
from bunker import chips  # noqa: E402

N = P.N
HERE = Path(__file__).parent
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
WOOD, CONC, MOSS, WIRE, GALV = "pole_wood", "concrete_dark", "moss", "tire", "st_galv"
CREAM, GREEN, RED = (228, 218, 190), (36, 74, 52), (176, 34, 30)
COLS, ROWS = "lmr", "01"


def board_art(spray):
    """The whole face, 3 x 2 blocks, as an RGBA picture of the paint: what goes on the cream."""
    W, H = 3 * N, 2 * N
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # the frame and the routed line inside it (board runs y 3/16 of row 0 to 14/16 of row 1)
    top, bot = int(2 / 16 * N), int(N + 13 / 16 * N)
    d.rectangle([0, top, W - 1, bot], outline=GREEN + (255,), width=5)
    d.rectangle([9, top + 9, W - 10, bot - 9], outline=GREEN + (255,), width=1)

    def centred(text, font, size, y, col):
        f = ImageFont.truetype(font, size)
        while d.textbbox((0, 0), text, font=f)[2] > W - 34:
            size -= 1
            f = ImageFont.truetype(font, size)
        x0, y0, x1, y1 = d.textbbox((0, 0), text, font=f)
        d.text(((W - (x1 - x0)) / 2 - x0, y - y0), text, font=f, fill=col + (255,))

    centred("WELCOME", SERIF, 36, top + 14, GREEN)
    centred("A SMALL TOWN", SANS, 14, top + 58, RED)
    centred("WITH A BIG HEART", SANS, 14, top + 75, RED)
    centred("EST. 1868", SERIF, 10, top + 96, GREEN)
    if spray:
        f = ImageFont.truetype(SANS, 26)
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        x0, y0, x1, y1 = ld.textbbox((0, 0), "TURN BACK", font=f)
        ld.text(((W - (x1 - x0)) / 2 - x0, 44 - y0), "TURN BACK", font=f, fill=(196, 26, 22, 235))
        layer = layer.rotate(6, center=(W / 2, H / 2))
        a = np.array(layer)
        rng = np.random.default_rng(7)
        # the paint ran down from the letters
        for _ in range(26):
            ys, xs = np.nonzero(a[:, :, 3] > 200)
            k = rng.integers(len(xs))
            x, y = xs[k], ys[k]
            ln = int(rng.integers(6, 22))
            a[y:min(H, y + ln), x:x + 1] = (180, 24, 20, 220)
        im = Image.alpha_composite(im, Image.fromarray(a))
    return np.array(im).astype(np.float32)


def board(seed, spray, col, row):
    """One block's piece of the face: cream paint over boards, the paint from board_art on it,
    chipped, weathered, mildew low down."""
    t = F.plain(seed, CREAM, 0.05, 0.35)
    for y in range(0, N, N // 4):
        t.c[y] *= 0.82
        t.h[y] -= 0.2
    art = board_art(spray)
    c0, r0 = "lmr".index(col) * N, (1 - int(row)) * N
    a = art[r0:r0 + N, c0:c0 + N]
    m = a[:, :, 3:4] / 255.0
    t.c = t.c * (1 - m) + a[:, :, :3] * m
    chips(t, 0.06, under=(150, 138, 116))
    weather(t, rust=0.0, streak=0.6, grime=0.35)
    if row == "0":
        t.speckle(0.04, (70, 86, 58), 0.5, 0.1)
    return t


def shingles(seed):
    """The cap's green asphalt shingles, curling, a few gone."""
    t = F.plain(seed, (52, 70, 58), 0.08, 0.2)
    for y in range(0, N, 8):
        t.c[y:y + 1] *= 0.5
        for x in range((y // 8 % 2) * 8, N, 16):
            t.c[y:y + 8, x:x + 1] *= 0.6
    t.speckle(0.05, (120, 120, 110), 0.5, 0.1)
    t.grime(0.3, (40, 40, 34))
    return t


def sale(seed):
    """The realtor's sign, both faces alike (painted on x 3..13, y 6..13 of the block): FOR SALE in
    red on white, the agency's name and number under it in blue, faded and rain-streaked."""
    t = F.plain(seed, (232, 230, 222), 0.03, 0.4)
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    x0, x1, y0 = 12, 52, 12   # the panel's area on the face, in pixels (u 3..13, v 3..10)
    d.rectangle([x0, y0, x1 - 1, 39], outline=(176, 30, 30, 255), width=2)

    def line(text, font, size, y, col):
        f = ImageFont.truetype(font, size)
        while d.textbbox((0, 0), text, font=f)[2] > x1 - x0 - 6 and size > 4:
            size -= 1
            f = ImageFont.truetype(font, size)
        a, b, c, e = d.textbbox((0, 0), text, font=f)
        d.text((x0 + (x1 - x0 - (c - a)) / 2 - a, y - b), text, font=f, fill=col + (255,))

    line("FOR SALE", SANS, 9, 15, (176, 30, 30))
    line("HARMON REALTY", SANS, 5, 27, (30, 58, 120))
    line("555-0142", SANS, 5, 33, (30, 58, 120))
    a = np.array(im).astype(np.float32)
    m = a[:, :, 3:4] / 255.0
    t.c = t.c * (1 - m) + a[:, :, :3] * m
    weather(t, rust=0.1, streak=0.7, grime=0.25)
    return t


def white(seed):
    t = F.plain(seed, (226, 224, 216), 0.05, 0.35)
    chips(t, 0.12, under=(140, 128, 108))
    weather(t, rust=0.0, streak=0.5, grime=0.35)
    return t


def textures():
    out = {"we_shingle": lambda: shingles(49800), "we_sale": lambda: sale(49801), "we_white": lambda: white(49802)}
    i = 0
    for spray in (False, True):
        for col in COLS:
            for row in ROWS:
                key = f"we_{'spray' if spray else 'clean'}_{col}{row}"
                out[key] = lambda spray=spray, col=col, row=row, i=i: board(49810 + i, spray, col, row)
                i += 1
    return out


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- models

def piece(kind, col, row):
    face = f"we_{kind}_{col}{row}"
    tex = {"north": face, "south": WOOD, "east": WOOD, "west": WOOD, "up": WOOD, "down": WOOD}
    b = []
    if row == "0":
        b.append([0, 3, 7, 16, 16, 9, tex])
        b += [[0, 0, 2, 16, 3, 6.5, CONC], [0.5, 3, 2.5, 15.5, 3.4, 6, MOSS]]
    else:
        b.append([0, 0, 7, 16, 14, 9, tex])
        # the shingled cap and its fascia
        b += [[0, 14, 5.5, 16, 15, 10.5, WOOD], [0, 15, 6, 16, 16, 10, {"up": "we_shingle", "north": "we_shingle", "south": "we_shingle",
                                                                       "east": "we_shingle", "west": "we_shingle", "down": WOOD}]]
    # the posts behind the face at the ends (l is the +x end as drawn, the viewer's left from the road)
    if col == "l":
        b.append([11.5, 0, 9, 15.5, 16 if row == "0" else 15, 13, WOOD])
    elif col == "r":
        b.append([0.5, 0, 9, 4.5, 16 if row == "0" else 15, 13, WOOD])
    return b


def sale_sign():
    b = [[1, 0, 7, 2.6, 15, 8.6, "we_white"], [0.6, 0, 6.6, 3, 1, 9, "we_white"],
         [1, 14, 7.3, 14.5, 15.2, 8.3, "we_white"], [14, 13.6, 7.2, 14.8, 15.4, 8.4, "we_white"]]
    panel = {"north": "we_sale", "south": "we_sale", "east": "we_white", "west": "we_white", "up": "we_white", "down": "we_white"}
    sw = {"axis": "x", "angle": -22.5, "origin": [8, 14, 8]}
    b += [[3, 6, 7.6, 13, 13, 8.2, panel, None, sw], [4, 13, 7.8, 4.4, 14, 8, WIRE, None, sw], [11.6, 13, 7.8, 12, 14, 8, WIRE, None, sw]]
    return b


def sale_down():
    panel = {"up": "we_sale", "down": "we_sale", "north": "we_white", "south": "we_white", "east": "we_white", "west": "we_white"}
    return [[2, 0, 3, 12, 0.6, 10, panel], [12.5, 0, 1, 14.1, 1.6, 15, "we_white"], [12, 0, 1, 15, 1, 2.5, "we_white"],
            [1, 0, 13, 3, 1.4, 16, "we_white", None, {"axis": "y", "angle": 22.5, "origin": [2, 0, 14]}]]


def blocks():
    out = {}

    def add(name, boxes, title, shape, sound="WOOD"):
        out[name] = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                     "title": title, "sound": sound, "rotate": "facing", "shape": shape, "render": "cutout"}

    for kind in ("clean", "spray"):
        for col in COLS:
            for row in ROWS:
                add(f"we_{kind}_{col}{row}", piece(kind, col, row), "Welcome Sign",
                    [[0, 0, 2, 16, 16, 13]] if row == "0" else [[0, 0, 5.5, 16, 16, 13]])
    add("we_sale", sale_sign(), "For Sale Sign", [[0.6, 0, 6.6, 15, 15.4, 9]])
    add("we_sale_down", sale_down(), "For Sale Sign", [[1, 0, 1, 15, 1.6, 16]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("we_")}
    made = blocks()
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} welcome blocks in {path}")


if __name__ == "__main__":
    main()
