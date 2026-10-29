"""The tb_ kit: plain track laid on ballast, the sidings, the subway's and the elevated's tracks, in
place of a bare steel bar on gravel and dark oak logs for sleepers (Josiah, 2026-10-29:
"Everything in this world must be of the same level of detail and effort"). Placed on top of the
ballast block, rotate=axis, drawn with the rails along z:

- tb_rail: two creosoted sleepers across the block, the rail down the middle on its tie plates,
  a spike each side of the foot;
- tb_sleeper: the sleepers alone, the middle of the track between its rails;
- tb_crossing: a level crossing's timber deck, flush with the rail heads (a full block).

    python3 tools/city/trackbed.py      rewrites the tb_ entries in blocks.json

photo_textures.py draws the textures (trackbed.register()).
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from gasstation import weather  # noqa: E402

N = P.N
HERE = Path(__file__).parent


def sleeper(seed):
    """A creosoted oak sleeper: near black, the grain along it opened into checks, grey where the
    sun has bleached the top, ballast dust in the cracks."""
    t = F.mat("Wood049", (58, 48, 38), 0.8, seed=seed)
    t.vary(0.08)
    for y in range(3, N, 9):
        t.rect(0, y, N, y + 1, col=(26, 22, 18), h=0.3)
    t.grime(0.35, (118, 114, 106))
    t.streaks(0.4, 0.8)
    return t


def rust(seed):
    """The rail: rolled steel gone to an even brown rust since the trains stopped, darker under the
    head, flaking."""
    t = F.plain(seed, (112, 70, 46), 0.08, 0.35)
    weather(t, rust=0.8, streak=0.4, grime=0.3)
    return t


def crossing(seed):
    """A level crossing's planks: heavy timbers across the road, worn grey in the wheel tracks."""
    t = F.mat("Wood049", (104, 92, 76), 0.7, seed=seed)
    t.vary(0.06)
    for y in range(0, N, N // 4):
        t.rect(0, y, N, y + 1, col=(34, 30, 26), h=0.2)
    t.grime(0.4, (96, 92, 84))
    return t


def textures():
    return {"tb_sleeper": lambda: sleeper(49430), "tb_rust": lambda: rust(49431), "tb_crossing": lambda: crossing(49432)}


def register(ns):
    ns.EXTRA.update(textures())


def sleepers():
    """The two sleepers, the ballast tamped up into the cribs between them each side of the middle."""
    crib = {"up": "rw_ballast", "side": "rw_ballast_side", "down": "rw_subballast"}
    return [[0, 0, z, 16, 2.5, z + 4.5, "tb_sleeper"] for z in (1.5, 9.5)] + [
        [0, 0, 0, 7.6, 1.6, 16, crib], [8.4, 0, 0, 16, 1.6, 16, crib]]


def rail():
    b = sleepers()
    for z in (1.5, 9.5):
        b.append([4.5, 2.5, z + 0.5, 11.5, 3, z + 4, "tb_rust"])
        for x in (5, 10.2):
            b.append([x, 3, z + 1.6, x + 0.8, 3.8, z + 2.4, "tb_rust"])
    b += [[5.6, 3, 0, 10.4, 3.6, 16, "tb_rust"], [7.4, 3.6, 0, 8.6, 5.6, 16, "tb_rust"],
          [6.8, 5.6, 0, 9.2, 7, 16, "tb_rust"]]
    return b


def blocks():
    out = {}

    def add(name, boxes, title, shape):
        out[name] = {"model": "boxes", "boxes": boxes, "title": title, "sound": "WOOD", "rotate": "axis", "shape": shape}

    add("tb_rail", rail(), "Track", [[0, 0, 0, 16, 2.5, 16], [6.8, 2.5, 0, 9.2, 7, 16]])
    add("tb_sleeper", sleepers(), "Track", [[0, 0, 0, 16, 2.5, 16]])
    out["tb_crossing"] = {"tex": {"up": "tb_crossing", "side": "tb_sleeper", "down": "tb_sleeper"}, "title": "Level Crossing",
                          "sound": "WOOD"}
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("tb_")}
    made = blocks()
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} track blocks in {path}")


if __name__ == "__main__":
    main()
