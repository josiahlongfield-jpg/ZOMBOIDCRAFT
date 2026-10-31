"""The mf_ kit: Caldwell Army Airfield (Josiah, 2026-10-30: the northwest military zone, the airfield
first), after refs/airfield-mil: the revetments, the helicopter pads and the round-roofed hangars.
world/county/ArmyField sets them out.

- mf_bin: a revetment's wall, galvanised bins of corrugated sheet on their channel posts filled with
  earth, the fill showing on top (mf_bin_top, the wall's top course: the earth heaped over the rim,
  the grass come up in it); mf_bin_end, the end of a wall run, the bin's closed end with its
  bracing;
- mf_crib: the helicopter revetment's lower wall, timber cribbing planked across between posts,
  filled the same;
- mf_psp: pierced steel planking laid as a pad, the plank rows along x;
- mf_membrane: the tan fuel-proof membrane pad the helicopters stand on;
- the barrel-vault hangar's roof, a corrugated skin following the arc in section: mf_vault_<a>_<d>,
  a 2 px panel whose outside faces out from the vault, its slope a = 0..8 for -90 to +90 degrees
  every 22.5 (4 is the crown, flat; lower a leans its face west, higher east), set d = 0..8 at -8 to +8
  px out from the block's middle along that face, the ridge along z. ArmyField picks the piece for
  each block the arc passes, the way Round does a drum's wall in plan; mf_vault_end_<a>_<d> the same
  at the gable ends, the cladding filled in under the panel so the end wall meets the roof.

    python3 tools/city/armyfield.py          rewrites the mf_ entries in blocks.json

photo_textures.py draws the textures (armyfield.register()).
"""
import json
import math
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area  # noqa: E402

N = P.N
HERE = Path(__file__).parent
SLOPES = [-90 + 22.5 * i for i in range(9)]
DEPTHS = [-8, -6, -4, -2, 0, 2, 4, 6, 8]


# ---------------------------------------------------------------- textures

def galv(seed, grime=0.3):
    t = F.plain(seed, (150, 154, 152), 0.05, 0.4)
    t.f0[:] = 60
    t.streaks(0.25, 0.8)
    t.grime(grime, (70, 62, 50))
    return t


def bin_face(seed, end=False):
    """The bin's side: the sheet corrugated across in fine ribs, a channel post at each end of the
    bay, the bolts down it, rust run from the bolts and earth splashed up the foot."""
    t = galv(seed, 0.35)
    for y in range(0, N, 4):
        area(t, 0, y, N, y + 1, mul=(0.78, 0.78, 0.78), dh=-0.25)
        area(t, 0, y + 1, N, y + 2, mul=(1.12, 1.12, 1.12), dh=0.15)
    for x0 in ((0, 58) if not end else (0, 29, 58)):
        area(t, x0, 0, x0 + 6, N, col=(112, 116, 114), dh=0.35, smooth=0.35)
        area(t, x0 + 1, 0, x0 + 2, N, mul=(0.7, 0.7, 0.7))
        for y in range(6, N, 14):
            area(t, x0 + 2, y, x0 + 4, y + 2, col=(80, 74, 66), dh=0.2)
            area(t, x0 + 2, y + 2, x0 + 4, y + 9, mul=(1.0, 0.84, 0.7))
    if end:
        for k in range(N):
            for d in (-1, 0, 1):
                x = k + d
                if 0 <= x < N:
                    t.c[k, x] = t.c[k, x] * 0.5 + np.array((104, 108, 106)) * 0.5
                    t.c[k, N - 1 - x] = t.c[k, N - 1 - x] * 0.5 + np.array((104, 108, 106)) * 0.5
    yy = np.arange(N)[:, None] / N
    splash = np.clip((yy - 0.75) * 4, 0, 1) * np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.6))) * 1.2 + 0.6, 0, 1)
    t.mix(np.broadcast_to(splash, (N, N)), (92, 70, 48), 0.7)
    return t


def earth(seed, grass=0.25):
    """The fill heaped over the rim: brown earth, stones, the grass come up in it."""
    t = P.Tex(seed, (104, 82, 58))
    t.vary(0.18)
    t.speckle(0.05, (142, 132, 118), 0.7, 0.2)
    t.speckle(0.04, (64, 50, 36), 0.6, -0.1)
    g = np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.5))) * 1.4 + grass - 0.3, 0, 1)
    t.mix(g, (88, 104, 52), 0.8)
    t.smooth[:] = 0.1
    return t


def rim(seed):
    """The wall's top course seen from the side: the sheet's rolled top edge, the earth heaped over it."""
    t = bin_face(seed)
    e = earth(seed + 1)
    t.c[:10] = e.c[:10]
    t.h[:10] = e.h[:10] + 0.1
    area(t, 0, 10, N, 12, col=(170, 172, 168), dh=0.3, smooth=0.5)
    return t


def crib(seed):
    """The timber crib: weathered planks across between the posts, the bolts, the earth behind the gaps."""
    t = P.Tex(seed, (118, 98, 72))
    t.vary(0.12)
    for y in range(0, N, 10):
        area(t, 0, y, N, y + 1, col=(52, 40, 30), dh=-0.4)
        area(t, 0, y + 1, N, y + 3, mul=(1.08, 1.08, 1.08))
    for x0 in (0, 56):
        area(t, x0, 0, x0 + 8, N, col=(96, 78, 56), dh=0.4)
        area(t, x0 + 7, 0, x0 + 8, N, mul=(0.6, 0.6, 0.6))
        for y in range(4, N, 10):
            area(t, x0 + 3, y, x0 + 5, y + 2, col=(60, 58, 56))
    t.streaks(0.3, 0.7)
    t.grime(0.3, (70, 56, 40))
    return t


def psp(seed):
    """Pierced steel planking: the planks along x, a row of round holes punched down each, the
    raised ribs between, the earth showing through the holes, rust at the edges."""
    t = P.Tex(seed, (96, 102, 92))
    t.vary(0.08)
    t.f0[:] = 50
    yy, xx = np.mgrid[0:N, 0:N]
    for y0 in range(0, N, 16):
        area(t, 0, y0, N, y0 + 2, mul=(0.7, 0.7, 0.7), dh=-0.3)
        area(t, 0, y0 + 2, N, y0 + 3, mul=(1.2, 1.2, 1.2), dh=0.2)
        cy = y0 + 9
        for cx in range(4, N, 10):
            hole = (xx - cx) ** 2 + (yy - cy) ** 2 <= 9
            t.c[hole] = (74, 60, 44)
            t.h[hole] = 0.3
            ring = ((xx - cx) ** 2 + (yy - cy) ** 2 <= 16) & ~hole
            t.c[ring] *= 1.15
            t.h[ring] += 0.1
    t.speckle(0.03, (120, 70, 40), 0.6)
    t.grime(0.25, (70, 56, 40))
    return t


def membrane(seed):
    t = F.plain(seed, (176, 160, 122), 0.06, 0.35)
    for x in range(0, N, 32):
        area(t, x, 0, x + 2, N, mul=(0.85, 0.85, 0.85), dh=0.2)
    t.grime(0.4, (96, 80, 60))
    t.speckle(0.02, (60, 50, 40), 0.5)
    return t


def vault_skin(seed):
    """The hangar roof's corrugated skin, the ribs running down the curve (across x on the panel),
    a sheet lap every half block, rust streaks from the laps."""
    t = galv(seed, 0.3)
    for x in range(0, N, 4):
        area(t, x, 0, x + 1, N, mul=(0.8, 0.8, 0.8), dh=-0.2)
        area(t, x + 1, 0, x + 2, N, mul=(1.1, 1.1, 1.1), dh=0.15)
    for y in (0, 32):
        area(t, 0, y, N, y + 1, mul=(0.7, 0.7, 0.7), dh=0.2)
        area(t, 0, y + 1, N, y + 6, mul=(1.0, 0.9, 0.8))
    return t


def vault_in(seed):
    """Under the roof: the skin's back between the purlins, a purlin across each half block."""
    t = F.plain(seed, (120, 122, 118), 0.06, 0.3)
    for x in range(0, N, 4):
        area(t, x, 0, x + 1, N, mul=(0.85, 0.85, 0.85))
    for y in (12, 44):
        area(t, 0, y, N, y + 5, col=(80, 84, 86), dh=0.4)
        area(t, 0, y + 4, N, y + 5, mul=(0.6, 0.6, 0.6))
    t.grime(0.2, (50, 46, 40))
    return t


@lru_cache(maxsize=None)
def textures():
    return {
        "mf_bin": lambda: bin_face(32101),
        "mf_bin_end": lambda: bin_face(32102, end=True),
        "mf_bin_rim": lambda: rim(32103),
        "mf_earth": lambda: earth(32105),
        "mf_crib": lambda: crib(32106),
        "mf_psp": lambda: psp(32107),
        "mf_membrane": lambda: membrane(32108),
        "mf_vault": lambda: vault_skin(32109),
        "mf_vault_in": lambda: vault_in(32110),
    }


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

def faces(tex, side=None, **over):
    f = {d: tex for d in ("north", "south", "east", "west", "up", "down")}
    if side:
        f.update({d: side for d in ("north", "south", "east", "west")})
    f.update({k: v for k, v in over.items() if v})
    return f


def length(rel):
    """The panel's length across the section, long enough that neighbours meet (Round's rule)."""
    return 16.5 if rel == 0 else 24 if abs(rel) == 45 else 22


def vault(slope, depth, thick=2):
    """The roof panel in the x-y plane, the ridge along z: its outside face at depth px out from the
    block's middle along its normal (sin s, cos s), turned about z to the slope."""
    s = math.radians(slope)
    nx, ny = math.sin(s), math.cos(s)
    cx, cy = 8 + depth * nx, 8 + depth * ny
    if abs(slope) <= 45:
        rel = slope
        L = length(rel)
        box = [cx - L / 2, cy - thick, 0, cx + L / 2, cy, 16]
        f = faces("mf_vault_in", up="mf_vault")
    else:
        sign = 1 if slope > 0 else -1
        rel = slope - sign * 90
        L = length(rel)
        if sign > 0:
            box = [cx - thick, cy - L / 2, 0, cx, cy + L / 2, 16]
            f = faces("mf_vault_in", east="mf_vault")
        else:
            box = [cx, cy - L / 2, 0, cx + thick, cy + L / 2, 16]
            f = faces("mf_vault_in", west="mf_vault")
    out = [round(v, 3) for v in box] + [f]
    if rel:
        # Minecraft turns a positive angle about z from +x toward +y, which tips the face's normal toward -x
        out += [None, {"axis": "z", "angle": -rel, "origin": [round(cx, 3), round(cy, 3), 8]}]
    return out


def gable(slope, depth, thick=2):
    """The end wall's fill under the panel in the block the arc passes: a block's worth of cladding
    up to the panel's low edge (on the inner side of a steep one), so the gable meets the roof."""
    s = math.radians(slope)
    cx, cy = 8 + depth * math.sin(s), 8 + depth * math.cos(s)
    if abs(slope) <= 45:
        top = cy - thick - 8 * abs(math.tan(s))
        return [0, 0, 0, 16, round(top, 3), 16] if top > 0.5 else None
    rel = math.radians(abs(slope) - 90)
    reach = 8 * abs(math.tan(rel))
    if slope > 0:
        x1 = cx - thick - reach
        return [0, 0, 0, round(x1, 3), 16, 16] if x1 > 0.5 else None
    x0 = cx + thick + reach
    return [round(x0, 3), 0, 0, 16, 16, 16] if x0 < 15.5 else None


def vault_shape(slope, depth):
    """A rough collision box: the slab of the block the panel sits in."""
    s = math.radians(slope)
    cy = 8 + depth * math.cos(s)
    if abs(slope) <= 45:
        return [[0, max(0, min(14, cy - 2)), 0, 16, max(2, min(16, cy)), 16]]
    cx = 8 + depth * math.sin(s)
    return [[max(0, min(14, cx - 2)), 0, 0, max(2, min(16, cx)), 16, 16]]


def blocks():
    out = {}

    def add(name, spec, title, sound="METAL", **extra):
        spec = dict(spec, title=title, sound=sound)
        spec.update(extra)
        out[name] = spec

    add("mf_bin", {"tex": {"side": "mf_bin", "up": "mf_earth", "down": "mf_earth"}}, "Revetment")
    add("mf_bin_top", {"tex": {"side": "mf_bin_rim", "up": "mf_earth", "down": "mf_earth"}}, "Revetment")
    add("mf_bin_end", {"tex": {"side": "mf_bin_end", "up": "mf_earth", "down": "mf_earth"}}, "Revetment")
    add("mf_crib", {"tex": {"side": "mf_crib", "up": "mf_earth", "down": "mf_earth"}}, "Revetment", "WOOD")
    add("mf_psp", {"tex": {"up": "mf_psp", "side": "mf_earth", "down": "mf_earth"}}, "Steel Planking")
    add("mf_membrane", {"tex": {"up": "mf_membrane", "side": "mf_earth", "down": "mf_earth"}}, "Pad Membrane", "WOOL")
    for i, s in enumerate(SLOPES):
        for j, d in enumerate(DEPTHS):
            add(f"mf_vault_{i}_{j}", {"model": "boxes", "boxes": [vault(s, d)], "shape": vault_shape(s, d)}, "Hangar Roof")
            g = gable(s, d)
            add(f"mf_vault_end_{i}_{j}", {"model": "boxes", "boxes": [vault(s, d)] + ([g + ["corrugated_grey"]] if g else []),
                                         "shape": vault_shape(s, d)}, "Hangar Roof")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("mf_")}
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} army airfield blocks in {path}")


if __name__ == "__main__":
    main()
