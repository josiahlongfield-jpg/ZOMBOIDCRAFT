"""The sf_ kit: the building site's steel frame and its tube-and-board scaffolding, in place of
whole blocks of rust and vanilla scaffolding on spruce slabs (Josiah, 2026-10-29: "Everything in
this world must be of the same level of detail and effort"). world/county/CityCivic.construction
puts up the tower's frame with it and lays the scaffolding up its face.

The frame, in red-oxide primer (sf_primer), the fabricator's chalk on it:

- sf_col: a wide-flange column, the flanges east and west; sf_col_base: on its base plate and
  bolts; sf_col_joint: where the floor's beams frame in, the cleats and the seat plates;
- sf_beam: an I-beam (rotate=axis, drawn along z) hung at the top of its block, so the slab or
  the deck that comes later sits on it.

The scaffolding:

every piece is drawn with the open side on its north (z = 0) and the building to the south,
rotate=facing. Galvanised tube (st_galv), 2 px:

- sf_std: a pair of standards, front and back, up the middle of the block;
- sf_bay: the guard rail along the front between standards;
- sf_brace: the bay with a face brace running corner to corner across it;
- sf_deck / sf_deck_std: a lift: the ledgers front and back, three boards across them, the toe
  board standing up at the front (with the standards in the _std piece).

    python3 tools/city/scaffold.py      rewrites the sf_ entries in blocks.json

photo_textures.py draws the board (scaffold.register()).
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from gasstation import weather  # noqa: E402
from diner import peel  # noqa: E402

N = P.N
HERE = Path(__file__).parent
TUBE = "st_galv"


def board(seed):
    """A scaffold board: rough sawn spruce gone grey and muddy, the grain along it, a steel band
    nailed round each end."""
    t = F.mat("Wood049", (138, 122, 98), 0.6, seed=seed)
    t.vary(0.06)
    for x in (2, N - 6):
        t.rect(x, 0, x + 4, N, col=(150, 152, 150), h=0.95, smooth=0.5)
        t.rect(x + 1, 0, x + 2, N, col=(110, 112, 110), h=0.8)
    t.grime(0.3, (70, 60, 46))
    t.streaks(0.5, 0.8)
    return t


def primer(seed):
    """Red-oxide primer as it came from the fabricator: a flat coat gone chalky, rust bloom through
    it and running from the edges, scuffed bright where the chains went round it, the erector's
    chalk marks."""
    t = F.plain(seed, (110, 56, 44), 0.06, 0.2)
    weather(t, rust=0.8, streak=0.7, grime=0.35)
    peel(t, 0.6, under=(92, 90, 86))
    chalk = (178, 174, 164)
    t.rect(8, 40, 8 + 12, 41, col=chalk, h=0.55)
    t.rect(8, 44, 8 + 7, 45, col=chalk, h=0.55)
    t.rect(8, 40, 9, 45, col=chalk, h=0.55)
    return t


def textures():
    return {"sf_board": lambda: board(49420), "sf_primer": lambda: primer(49421)}


def register(ns):
    ns.EXTRA.update(textures())


def standards():
    return [[7, 0, 1, 9, 16, 3, TUBE], [7, 0, 13, 9, 16, 15, TUBE]]


def rail():
    return [[0, 7, 1, 16, 9, 3, TUBE]]


def brace():
    return [[-3.3, 7, 3, 19.3, 9, 5, TUBE, None, {"axis": "z", "angle": 45, "origin": [8, 8, 4]}]]


def deck():
    b = [[0, 12, 1, 16, 14, 3, TUBE], [0, 12, 13, 16, 14, 15, TUBE]]
    for z0 in (1.2, 6.2, 11.2):
        b.append([0, 14, z0, 16, 16, z0 + 4.6, {"up": "sf_board", "down": "sf_board", "side": "sf_board"}])
    b.append([0, 16, 0.6, 16, 19, 1.4, {"north": "sf_board", "south": "sf_board", "side": "sf_board", "up": "sf_board",
                                         "down": "sf_board"}])
    return b


PRIMER = "sf_primer"


def column():
    return [[3, 0, 3, 13, 16, 4.5, PRIMER], [3, 0, 11.5, 13, 16, 13, PRIMER], [7.25, 0, 4.5, 8.75, 16, 11.5, PRIMER]]


def column_base():
    b = column() + [[1.5, 0, 1.5, 14.5, 1, 14.5, PRIMER]]
    for x, z in ((2.5, 2.5), (12.5, 2.5), (2.5, 12.5), (12.5, 12.5)):
        b.append([x - 0.6, 1, z - 0.6, x + 0.6, 2.2, z + 0.6, "st_galv"])
    return b


def column_joint():
    """The column where the floor's beams frame in: the seat plates under them both ways, a cleat
    bolted to the web each side, the beams' ends sitting on them."""
    b = column()
    b += [[0, 2, 4.5, 3, 3.5, 11.5, PRIMER], [13, 2, 4.5, 16, 3.5, 11.5, PRIMER],
          [4.5, 2, 0, 11.5, 3.5, 3, PRIMER], [4.5, 2, 13, 11.5, 3.5, 16, PRIMER],
          [5.5, 3.5, 0, 10.5, 14.5, 1.5, PRIMER], [5.5, 3.5, 14.5, 10.5, 14.5, 16, PRIMER],
          [0, 3.5, 7.25, 3, 14.5, 8.75, PRIMER], [13, 3.5, 7.25, 16, 14.5, 8.75, PRIMER],
          [0, 14.5, 4, 3, 16, 12, PRIMER], [13, 14.5, 4, 16, 16, 12, PRIMER],
          [4, 14.5, 0, 12, 16, 3, PRIMER], [4, 14.5, 13, 12, 16, 16, PRIMER]]
    return b


def beam():
    """An I-beam along z, its top flange flush with the top of the block: flanges 8 wide, the web
    between, a stiffener across the middle, the bolt heads at the splice."""
    return [[4, 14.5, 0, 12, 16, 16, PRIMER], [7.25, 3.5, 0, 8.75, 14.5, 16, PRIMER], [4, 2, 0, 12, 3.5, 16, PRIMER],
            [4.5, 3.5, 7.4, 11.5, 14.5, 8.6, PRIMER],
            [6.2, 5, 1, 7.25, 6, 2, "st_galv"], [6.2, 11, 1, 7.25, 12, 2, "st_galv"],
            [8.75, 5, 1, 9.8, 6, 2, "st_galv"], [8.75, 11, 1, 9.8, 12, 2, "st_galv"]]


def blocks():
    out = {}

    def add(name, boxes, shape, cutout=True, title="Scaffolding", rotate="facing"):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": "METAL", "shape": shape}
        if rotate:
            spec["rotate"] = rotate
        if cutout:
            spec["render"] = "cutout"
        out[name] = spec

    add("sf_col", column(), [[3, 0, 3, 13, 16, 13]], False, "Steel Column", None)
    add("sf_col_base", column_base(), [[1.5, 0, 1.5, 14.5, 16, 14.5]], False, "Steel Column", None)
    add("sf_col_joint", column_joint(), [[0, 0, 0, 16, 16, 16]], False, "Steel Column", None)
    add("sf_beam", beam(), [[4, 2, 0, 12, 16, 16]], False, "Steel Beam", "axis")

    add("sf_std", standards(), [[7, 0, 1, 9, 16, 15]])
    add("sf_bay", rail(), [[0, 7, 1, 16, 9, 3]])
    add("sf_brace", rail() + brace(), [[0, 7, 1, 16, 9, 3]])
    add("sf_deck", deck(), [[0, 12, 1, 16, 16, 15]])
    add("sf_deck_std", standards() + deck(), [[0, 12, 1, 16, 16, 15], [7, 0, 1, 9, 16, 15]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("sf_")}
    made = blocks()
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} scaffold blocks in {path}")


if __name__ == "__main__":
    main()
