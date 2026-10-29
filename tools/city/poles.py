"""The pl_ kit: what is left of the power line where a pole came down, and the transformer cans up
the standing ones, in place of a row of stripped spruce logs and a lightning rod (Josiah,
2026-10-29: "Everything in this world must be of the same level of detail and effort").
world/county/Kit.powerLine sets them out. They go with the pole_wood / pole_top pieces.

- pl_stump: the pole snapped a block up, the split wood standing in spikes, the step bolts;
- pl_down: a length of the pole lying on the ground along z (rotate=facing, drawn along z);
- pl_down_top: the pole's top lying on the ground, the crossarm on its side with its glass
  insulators, the top toward the south as drawn (rotate=facing: north when it fell south);
- pl_wire: the wires lying slack in the verge where they came down, rotate=axis (drawn along z);
- pl_transformer: a length of pole with the transformer can strapped to it on the north, its lid,
  the bushings and the leads up, the number stencilled on it (rotate=facing).

    python3 tools/city/poles.py      rewrites the pl_ entries in blocks.json

photo_textures.py draws the textures (poles.register()).
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from gasstation import weather  # noqa: E402
from bunker import stencil  # noqa: E402

N = P.N
HERE = Path(__file__).parent
WOOD, GALV, GLASS, WIRE = "pole_wood", "st_galv", "lamp_glass", "tire"


def can(seed, text=None):
    """The transformer can: grey enamel over steel, rust weeping from the seams and the lid, the
    pole number and the kVA stencilled on the face in black."""
    t = F.plain(seed, (128, 134, 132), 0.06, 0.5)
    t.f0[:] = 30
    if text:
        stencil(t, text, (30, 30, 30), 6, x=N // 6, y=N // 4, w=2 * N // 3, h=N // 2, chip=0.2)
    weather(t, rust=0.4, streak=0.8, grime=0.3)
    return t


def splinter(seed):
    """The broken end of a pole: pale split wood under the creosote, the fibres torn."""
    t = F.mat("Wood065", (170, 146, 108), 0.7, rot=False, seed=seed)
    t.grime(0.3, (70, 56, 40))
    return t


def textures():
    return {"pl_can": lambda: can(49500, "T-4417|25 KVA"), "pl_can_side": lambda: can(49502), "pl_splinter": lambda: splinter(49501)}


def register(ns):
    ns.EXTRA.update(textures())


def stump():
    split = {"up": "pl_splinter", "side": WOOD, "down": WOOD}
    b = [[5.5, 0, 5.5, 10.5, 9, 10.5, split]]
    for x0, z0, h in ((5.5, 5.5, 13), (8, 6.2, 15.5), (6.5, 8.5, 11.5), (9, 8.8, 12.5)):
        b.append([x0, 9, z0, x0 + 1.5, h, z0 + 1.6, {"side": "pl_splinter", "up": "pl_splinter", "down": WOOD}])
    b += [[10.5, 4, 7.6, 12, 4.6, 8.2, GALV], [4, 7, 7.8, 5.5, 7.6, 8.4, GALV]]
    return b


def down():
    return [[5.5, 0, 0, 10.5, 5, 16, WOOD], [10.5, 2, 9, 12, 2.6, 9.6, GALV]]


def down_top():
    b = [[5.5, 0, 0, 10.5, 5, 9, {"side": WOOD, "south": "pl_splinter", "north": WOOD, "up": WOOD, "down": WOOD}],
         [0, 0, 9.5, 16, 3, 12.5, WOOD],
         [7, 3, 9.5, 9, 6, 12.5, GALV]]
    for x in (1.5, 13):
        b += [[x, 3, 10.3, x + 1.5, 4, 11.7, GALV], [x - 0.2, 4, 10.1, x + 1.7, 6.5, 11.9, GLASS]]
    b.append([2, 0.2, 12.5, 2.6, 0.7, 16, WIRE])
    return b


def wire():
    b = [[3.7, 0, 0, 4.2, 0.5, 6, WIRE], [3.7, 0, 6, 4.2, 0.5, 11, WIRE, None, {"axis": "y", "angle": 22.5, "origin": [4, 0, 6]}],
         [5.6, 0, 10, 6.1, 0.5, 16, WIRE],
         [11.6, 0, 0, 12.1, 0.5, 9, WIRE], [11.6, 0, 9, 12.1, 0.5, 16, WIRE, None, {"axis": "y", "angle": -22.5, "origin": [12, 0, 9]}],
         [9.5, 0.3, 4, 12, 0.8, 4.5, WIRE]]
    return b


def transformer():
    body = {"north": "pl_can", "side": "pl_can_side", "up": GALV, "down": GALV}
    b = [[5.5, 0, 5.5, 10.5, 16, 10.5, WOOD],
         [3.6, 2, 1.6, 12.4, 12, 5.4, body], [4.6, 2, 0.6, 11.4, 12, 6.4, body],
         [4, 12, 1, 12, 13, 6, GALV], [4.6, 1, 1.2, 11.4, 2, 5.8, GALV],
         [4.5, 4, 5.4, 11.5, 4.8, 11, GALV], [4.5, 9.5, 5.4, 11.5, 10.3, 11, GALV],
         [7.4, 13, 2.6, 8.6, 15.5, 3.8, GLASS], [5, 13, 3, 6, 14.5, 4, GLASS], [10, 13, 3, 11, 14.5, 4, GLASS],
         [7.8, 15.5, 3, 8.2, 16, 3.4, WIRE]]
    return b


def blocks():
    out = {}

    def add(name, boxes, title, shape, rotate="facing", sound="WOOD"):
        out[name] = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                     "title": title, "sound": sound, "rotate": rotate, "shape": shape, "render": "cutout"}

    add("pl_stump", stump(), "Power Pole", [[5.5, 0, 5.5, 10.5, 12, 10.5]])
    add("pl_down", down(), "Power Pole", [[5.5, 0, 0, 10.5, 5, 16]])
    add("pl_down_top", down_top(), "Power Pole", [[5.5, 0, 0, 10.5, 5, 9], [0, 0, 9, 16, 6.5, 13]])
    add("pl_wire", wire(), "Downed Wires", [[3, 0, 0, 13, 1, 16]], "axis", "METAL")
    add("pl_transformer", transformer(), "Transformer", [[3.6, 0, 0.6, 12.4, 16, 10.5]], sound="METAL")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("pl_")}
    made = blocks()
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} pole blocks in {path}")


if __name__ == "__main__":
    main()
