"""The mine adit's kit (mn_), from refs/bunker/mine-adit-entrance: the timbered portals of the old
workings (Winfield, Kootenay Lake, the drain tunnel portal), the narrow-gauge rails running out on the
spoil, the rock-cut adits running back into the hill (Eylesbarrow, Ciltwllan).

- mn_post_old, mn_cap_old: the old timber sets, grey and split, the bark long gone;
- mn_post_new, mn_cap_new: the props the prepper put in, sawn pine still yellow (the clue, seen
  through the gaps in the boards);
- mn_boards: the boards nailed across the portal, gappy, one hanging;
- mn_rail: the narrow-gauge track, rust on rotten sleepers;
- mn_cart: an ore tub, rusted through;
- mn_sign: DANGER / OLD WORKINGS / KEEP OUT, hand-painted on a board.

    python3 tools/city/mine.py   # writes the blocks into blocks.json (textures: photo_textures.py)
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import bunker as B  # noqa: E402
from furniture import area, disc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None


def timber(seed, new=False, across=False):
    """A squared timber's side: grain along it, checks split down it, grey with age (or sawn fresh).
    Up the image for a post; across=True for a cap, its grain along the beam."""
    rgb = (214, 178, 112) if new else (128, 120, 106)
    t = F.mat("Wood089" if new else "Wood049", rgb, 1.1, rot=not across, seed=seed, smooth=0.12)
    rng = t.rng
    if not new:
        for _ in range(4):   # the checks: splits along the grain, wandering, deepest in the middle
            x = rng.uniform(6, N - 6)
            y0 = rng.uniform(0, N / 2)
            ln = int(rng.uniform(14, 36))
            for k in range(ln):
                x += rng.normal(0, 0.25)
                d = 0.45 + 0.4 * abs(k / ln - 0.5)
                if across:
                    area(t, y0 + k, x, y0 + k + 1, x + 1, mul=d, dh=-0.3)
                else:
                    area(t, x, y0 + k, x + 1, y0 + k + 1, mul=d, dh=-0.3)
        t.mix(np.clip(P.fbm(rng) * 1.6 - 0.35, 0, 1), (86, 90, 74), 0.35)   # mould and mine damp
    else:
        for _ in range(2):   # a knot or two, and resin weeping from one
            disc(t, rng.uniform(8, N - 8), rng.uniform(8, N - 8), rng.uniform(1.5, 2.8), (150, 104, 54), dh=-0.1)
    return F.wear(t, 0.3 if not new else 0.1, 1)


def end_grain(seed, new=False):
    """The sawn end: rings round the heart, cracks out from it."""
    rgb = (206, 170, 110) if new else (120, 112, 100)
    t = F.plain(seed, rgb, 0.06, 0.15)
    for r in range(28, 2, -4):
        disc(t, N / 2, N / 2, r, mul=0.86, ring=1)
    if not new:
        for a in t.rng.uniform(0, 6.28, 4):
            for k in range(4, 28):
                area(t, N / 2 + np.cos(a) * k, N / 2 + np.sin(a) * k, N / 2 + np.cos(a) * k + 1, N / 2 + np.sin(a) * k + 1, mul=0.4)
    return F.wear(t, 0.3, 0)


def sign(seed):
    t = F.mat("Wood049", (150, 140, 122), 1.0, rot=True, seed=seed, smooth=0.12)
    m = F.text_mask("DANGER", B.SANS, 11, N - 6, 16, jitter=0.3, seed=seed)
    F.paste(t, m, 3, 12, (150, 30, 24))
    m = F.text_mask("OLD WORKINGS|KEEP OUT", B.SANS, 6, N - 6, 20, jitter=0.3, seed=seed + 1)
    F.paste(t, m, 3, 29, (150, 30, 24))
    t.mix(np.clip(P.fbm(t.rng) * 1.6 - 0.35, 0, 1), (86, 90, 74), 0.3)
    return t


def textures():
    T = {}
    T["mn_timber_old"] = lambda: timber(9940)
    T["mn_timber_new"] = lambda: timber(9941, True)
    T["mn_beam_old"] = lambda: timber(9945, across=True)
    T["mn_beam_new"] = lambda: timber(9946, True, across=True)
    T["mn_end_old"] = lambda: end_grain(9942)
    T["mn_end_new"] = lambda: end_grain(9943, True)
    T["mn_sign"] = lambda: sign(9944)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, **extra):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        spec.update(extra)
        out[name] = spec

    for age in ("old", "new"):
        side, end, beam = f"mn_timber_{age}", f"mn_end_{age}", f"mn_beam_{age}"
        # the post stands against the rock; the cap lies across the post tops under the roof (drawn
        # along z, as axis models are), the lagging above it
        add(f"mn_post_{age}", [[3, 0, 3, 13, 16, 13, {"side": side, "up": end, "down": end}]],
            "Mine Prop", "WOOD", rotate=None)
        add(f"mn_cap_{age}", [[3, 0, 0, 13, 10, 16, {"side": beam, "up": side, "down": side, "north": end, "south": end}]],
            "Mine Cap", "WOOD", rotate="axis")
    # the prepper's joists under his own ceilings, hung from the rock
    add("mn_joist_new", [[3, 8, 0, 13, 16, 16, {"side": "mn_beam_new", "up": "mn_timber_new", "down": "mn_timber_new",
                                                  "north": "mn_end_new", "south": "mn_end_new"}]],
        "Joist", "WOOD", rotate="axis")
    # boards nailed across the portal, gaps between them, the bottom one hanging by a nail
    add("mn_boards", [[0, 13, 14, 16, 16, 16, "mn_beam_old"], [0, 8.5, 14, 16, 11.5, 16, "mn_beam_old"],
                      [0, 4, 14.2, 16, 6.5, 16, "mn_beam_old"], [1, 0, 14.4, 11, 2.5, 16, "mn_beam_old"]],
        "Boards", "WOOD")
    add("mn_rail", [[1, 0, 1, 15, 1.5, 4, "mn_timber_old"], [1, 0, 9, 15, 1.5, 12, "mn_timber_old"],
                    [3.5, 1.5, 0, 4.5, 2.7, 16, "rust"], [11.5, 1.5, 0, 12.5, 2.7, 16, "rust"]],
        "Mine Rail", "METAL", rotate="axis", decor=True)
    add("mn_cart", [[2, 3, 1, 14, 13, 15, {"side": "rust", "up": "rust", "down": "rust"}],
                    [1.5, 12, 0.5, 14.5, 13.5, 15.5, "rust"],
                    [1, 0, 2.5, 2.5, 4, 6.5, "bunk_plate_rust"], [13.5, 0, 2.5, 15, 4, 6.5, "bunk_plate_rust"],
                    [1, 0, 9.5, 2.5, 4, 13.5, "bunk_plate_rust"], [13.5, 0, 9.5, 15, 4, 13.5, "bunk_plate_rust"]],
        "Ore Cart", "METAL", rotate="axis")
    add("mn_sign", [[1, 2, 14.5, 15, 12, 16, {"north": "mn_sign", "side": "mn_timber_old"}]], "Sign", "WOOD", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    kept = {k: made.get(k, v) for k, v in data["blocks"].items() if not k.startswith("mn_") or k in made}
    kept.update({k: v for k, v in made.items() if k not in kept})
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} mine blocks in {path}")


if __name__ == "__main__":
    main()
