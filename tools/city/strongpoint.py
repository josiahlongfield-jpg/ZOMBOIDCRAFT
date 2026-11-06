"""The missing floor's kit (xf_), from refs/bunker (corridor, armoury, command room) and the
plated-up windows of fortified blocks: a security firm sealed a plant floor half way up an office
tower and made it a strongpoint.

- xf_plate: a heavy steel plate bolted over a window, rust weeping from every bolt; from the street
  the clue is one row of these across the tower's face;
- xf_slit: the same plate with a firing slit cut through it;
- xf_bricked: the stairwell's door bricked up with fresh blocks, NO ACCESS sprayed on;
- xf_sign: the firm's notice on the core wall inside.

    python3 tools/city/strongpoint.py   # writes the blocks into blocks.json (textures: photo_textures.py)
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


def plate(seed):
    """Steel plate bolted over the glass: a border of bolts, rust weeping down from each."""
    t = B.painted(seed, (70, 66, 60), "PaintedMetal001", 0.8, 0.3)
    F.edges(t, 1, 1, N - 1, N - 1, 2, light=1.12, dark=0.6, dh=0.2)
    rng = t.rng
    for x in (5, 22, 42, 59):
        for y in (5, 59):
            disc(t, x, y, 1.8, (120, 118, 110), dh=0.5)
    for y in (22, 42):
        for x in (5, 59):
            disc(t, x, y, 1.8, (120, 118, 110), dh=0.5)
    # rust weeping from the bolts and the top edge
    for x in (5, 22, 42, 59):
        for i in range(int(rng.uniform(10, 34))):
            a = 0.6 * (1 - i / 34)
            for dx in (0, 1):
                yy = min(N - 1, 7 + i)
                t.c[yy, x + dx - 1] = t.c[yy, x + dx - 1] * (1 - a) + np.array(B.RUST, np.float32) * a
    t.mix(np.clip(P.fbm(rng) * 1.7 - 0.45, 0, 1) * 0.75, B.RUST)
    B.chips(t, 0.05, (120, 116, 108))
    return F.wear(t, 0.25, 4)


def bricked(seed, text=True):
    """Concrete blocks laid in the doorway, the mortar still pale, NO ACCESS sprayed across the upper."""
    t = F.mat("Bricks101", (150, 148, 142), 0.7, span=0.5, seed=seed)
    t.mix(np.clip(P.fbm(t.rng) * 1.5 - 0.6, 0, 1) * 0.4, (190, 186, 176))
    if text:
        m = F.text_mask("NO|ACCESS", B.SANS, 11, N - 8, 34, jitter=0.4, seed=seed)
        F.paste(t, m, 4, 16, (170, 34, 28))
    return t


def sign(seed):
    """The firm's notice: a white enamel plate, its shield badge, the warning in black and red."""
    t = F.plain(seed, (226, 224, 216), 0.04, 0.6)
    t.f0[:] = 30
    F.edges(t, 1, 1, N - 1, N - 1, 1, light=1.05, dark=0.7)
    area(t, 4, 4, N - 4, 18, (30, 44, 74))
    m = F.text_mask("HALVARD SECURITY", B.SANS, 5, N - 10, 12, seed=seed)
    F.paste(t, m, 5, 5, (232, 230, 222))
    m = F.text_mask("LEVEL CLOSED", B.SANS, 6, N - 8, 14, seed=seed + 1)
    F.paste(t, m, 4, 24, (170, 30, 26))
    m = F.text_mask("AUTHORISED|STAFF ONLY", B.SANS, 6, N - 8, 18, seed=seed + 2)
    F.paste(t, m, 4, 40, (30, 30, 30))
    for x, y in ((3, 3), (N - 4, 3), (3, N - 4), (N - 4, N - 4)):
        disc(t, x, y, 1.2, (150, 150, 146), dh=0.4)
    return F.wear(t, 0.15, 2)


def textures():
    return {
        "xf_plate": lambda: plate(9960),
        "xf_bricked": lambda: bricked(9961),
        "xf_bricked_plain": lambda: bricked(9963, False),
        "xf_sign": lambda: sign(9962),
    }


def register(ns):
    ns.EXTRA.update(textures())


def blocks():
    out = {}
    out["xf_plate"] = {"tex": "xf_plate", "title": "Steel Plate", "sound": "METAL"}
    out["xf_slit"] = {"model": "boxes", "title": "Steel Plate", "sound": "METAL",
                      "boxes": [[0, 0, 0, 16, 8, 16, "xf_plate"], [0, 10, 0, 16, 16, 16, "xf_plate"]]}
    out["xf_bricked"] = {"tex": "xf_bricked", "title": "Bricked-Up Door", "sound": "STONE"}
    out["xf_bricked_plain"] = {"tex": "xf_bricked_plain", "title": "Bricked-Up Door", "sound": "STONE"}
    out["xf_sign"] = {"model": "boxes", "title": "Sign", "sound": "METAL", "rotate": "facing", "decor": True,
                      "boxes": [[2, 3, 15, 14, 13, 16, {"north": "xf_sign", "side": "bunk_plate"}]]}
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    kept = {k: made.get(k, v) for k, v in data["blocks"].items() if not k.startswith("xf_") or k in made}
    kept.update({k: v for k, v in made.items() if k not in kept})
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} strongpoint blocks in {path}")


if __name__ == "__main__":
    main()
