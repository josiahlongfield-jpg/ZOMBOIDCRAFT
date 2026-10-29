"""Builds the air strike bomb's item model (models/item/strike_bomb.json): a Mk 82 500 lb low-drag bomb.

    python3 tools/cars/strike_bomb.py

AirStrikeFx draws it scaled 2.2 with the nose at +z, so the 15-pixel body comes out about 2 m long like the real
one. Round sections are crossed bars turned 0, 22.5, 45 and -22.5 degrees about the axis, so they come out round
(the small fuzes use two turns). Profile, front to back: nose fuze, tangent-ogive nose, yellow band, the body with its two suspension lugs and fuze well, a second band, the
boat-tail cone and the four swept fins in an X.
"""
import json
import math
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/zomboidcraft/models/item/strike_bomb.json"
C = 8.0
R = 1.1          # body radius, pixels
FACES = ("north", "south", "east", "west", "up", "down")


def box(lo, hi, tex, angle=0.0):
    e = {"from": [round(v, 3) for v in lo], "to": [round(v, 3) for v in hi],
         "faces": {f: {"texture": tex} for f in FACES}}
    if angle:
        e["rotation"] = {"angle": angle, "axis": "z", "origin": [C, C, 8]}
    return e


def ring(z0, z1, r, tex, sides=16):
    """A round section of radius r from z0 to z1: crosses of two bars turned about the axis, each bar r long and
    r tan(180/sides) wide, so its corners sit just 2% (16 sides) or 8% (8 sides) outside the circle."""
    w = r * math.tan(math.pi / sides)
    out = []
    for a in ((0, 22.5, 45, -22.5) if sides == 16 else (0, 45)):
        out.append(box((C - r, C - w, z0), (C + r, C + w, z1), tex, a))
        out.append(box((C - w, C - r, z0), (C + w, C + r, z1), tex, a))
    return out


def main():
    els = []
    # nose fuze: a stubby body and its rounded tip
    els += ring(14.45, 15.0, 0.42, "#fuze", 8)
    els += ring(15.0, 15.45, 0.24, "#fuze", 8)
    # tangent ogive from the body's radius down to the fuze, in steps
    length, start = 3.65, 10.8
    rho = (R * R + length * length) / (2 * R)
    steps = 10
    for i in range(steps):
        z0, z1 = start + length * i / steps, start + length * (i + 1) / steps
        x = (z0 + z1) / 2 - start
        r = math.sqrt(rho * rho - x * x) + R - rho
        els += ring(z0, z1, max(r, 0.45), "#body")
    # the yellow bands of a live HE bomb, just proud of the skin
    els += ring(11.0, 11.4, R + 0.04, "#band")
    els += ring(3.7, 4.1, R + 0.04, "#band")
    # the body
    els += ring(3.0, 10.8, R, "#body")
    # suspension lugs 14 in apart on top, and the charging well between them
    for z in (5.0, 8.2):
        els.append(box((C - 0.22, C + R - 0.05, z), (C + 0.22, C + R + 0.38, z + 0.55), "#fin"))
        els.append(box((C - 0.12, C + R + 0.3, z - 0.1), (C + 0.12, C + R + 0.45, z + 0.65), "#fin"))
    els.append(box((C - 0.35, C + R - 0.04, 6.4), (C + 0.35, C + R + 0.06, 7.1), "#fuze"))
    # boat tail, narrowing to the tail fuze plug
    for i in range(6):
        z1 = 3.0 - 0.433 * i
        els += ring(z1 - 0.433, z1, R - 0.09 - 0.085 * i, "#fin")
    els += ring(0.15, 0.4, 0.32, "#fuze", 8)
    # four fins in an X, swept: each step is a pair of thin plates through the axis, turned +-45 degrees
    for z0, z1, span in ((0.25, 1.05, 1.75), (1.05, 1.8, 1.6), (1.8, 2.5, 1.35), (2.5, 3.1, 1.12)):
        for a in (45, -45):
            els.append(box((C - 0.07, C - span, z0), (C + 0.07, C + span, z1), "#fin", a))
    model = {
        "textures": {"body": "zomboidcraft:block/city/navy_bomb", "band": "zomboidcraft:block/city/navy_yellow",
                     "fin": "zomboidcraft:block/city/navy_od", "fuze": "zomboidcraft:block/city/navy_alu",
                     "particle": "zomboidcraft:block/city/navy_bomb"},
        "elements": els,
    }
    OUT.write_text(json.dumps(model, indent=1) + "\n")
    print(len(els), "elements")


if __name__ == "__main__":
    main()
