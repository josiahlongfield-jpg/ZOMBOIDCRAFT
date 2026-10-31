#!/usr/bin/env python3
"""Turns tools/city/blocks.json into the mod's real city blocks.

Writes, under src/main:
  java/com/zomboidcraft/world/blocks/CitySpecs.java   one line per block: kind, shape, light, sound
  resources/assets/zomboidcraft/blockstates|models/block/city|items/<name>.json
    (doors: vanilla's eight door models per door, plus an item model and icon)
  resources/assets/zomboidcraft/textures/block/city/<tex>.png (+ LabPBR _n and _s maps)
  resources/data/zomboidcraft/loot_table/blocks/<name>.json
and the block names in lang/en_us.json.

    python3 tools/city/gen_blocks.py TEX_DIR

TEX_DIR is the output of photo_textures.py (or paint_textures.py): 64px textures plus maps.
Shapes and models face north like the preview; blockstates turn them with y rotations, so a
block's facing or axis means the same thing in game as in tools/city/mesh.py.
"""
import json
import re
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "src/main"
ASSETS = MAIN / "resources/assets/zomboidcraft"
DATA = MAIN / "resources/data/zomboidcraft"
JAVA = MAIN / "java/com/zomboidcraft/world/blocks/CitySpecs.java"
NS = "zomboidcraft"

FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
DIRS = ["north", "south", "east", "west", "up", "down"]
# blocks you walk through and can build over: plants, wall and floor decals
DECOR_PREFIX = ("graffiti_", "sign_", "blade_", "neon_")
DECOR = {"store_spill", "vines", "paper", "glass_shards", "blood_splat", "scorch", "leaf_litter", "banner_skull",
         "camo_net", "power_line", "clock_face", "windfall"}
SOUNDS = [  # first keyword in the block's name or textures wins
    (("window", "glass", "cabin"), "GLASS"),
    (("grass", "weeds", "vines", "ivy", "leaves", "leaf", "moss_carpet", "corn", "straw", "bale"), "GRASS"),
    (("tree_",), "WOOD"),
    (("crate", "boards", "boarded", "wood", "desk", "tree_log", "cardboard", "paper", "siding", "barn"), "WOOD"),
    (("carpet", "office_floor", "cubicle", "sandbag", "trash_bag", "tarp", "camo", "hesco"), "WOOL"),
    (("dirt", "burnt_ground", "rubble"), "GRAVEL"),
    (("car_", "steel", "metal", "rail", "grate", "chainlink", "barbed", "ladder", "hvac", "dumpster",
      "barrel", "shutter", "pole", "streetlight", "rebar", "elevator", "fire_door", "filing", "train",
      "water_tank", "lantern", "light", "rust", "girder", "chair", "sign_", "blade_", "corrugated", "container",
      "hull", "deck", "truss", "plate_", "hazard", "garage", "power_line", "mailbox", "pump", "propane", "tank_",
      "lamp_", "stop_sign", "sign_post", "clock", "bin_ribbed", "windmill", "silo_blue"), "METAL"),
]


def textures(spec):
    if "boxes" in spec:
        return [t for b in spec["boxes"] for t in ([b[6]] if isinstance(b[6], str) else b[6].values())]
    t = spec["tex"]
    return [t] if isinstance(t, str) else list(t.values())


def face_tex(spec, face):
    t = spec["tex"]
    if isinstance(t, str):
        return t
    if face in t:
        return t[face]
    if face in ("north", "south", "east", "west") and "side" in t:
        return t["side"]
    return t.get("side") or next(iter(t.values()))


def boxes(spec):
    """Every box of the block as [x0,y0,z0,x1,y1,z1,face->texture]."""
    model = spec.get("model", "cube")
    if model == "cube":
        h = spec.get("height", 16)
        return [[0, 0, 0, 16, h, 16, {f: face_tex(spec, f) for f in DIRS}]]
    if model == "slab":
        return [[0, 0, 0, 16, 8, 16, {f: spec["tex"] for f in DIRS}]]
    # a box's texture is one name for every face, or a dict of faces like a cube's tex; an inked
    # box (tools/city/ink.py) adds an eighth item, each face's own rectangle on its atlas
    # a ninth item turns the box about a point: {"axis": "x"|"y"|"z", "angle": 45 or 22.5 (or minus),
    # "origin": [8, 8, 8]}, with None for the eighth when the box isn't inked
    return [b[:6] + [{f: (b[6] if isinstance(b[6], str) else face_tex({"tex": b[6]}, f)) for f in DIRS}] + b[7:9]
            for b in spec["boxes"]]


# the texture rectangle Minecraft picks for each face of a box (from, to in 16ths)
AUTO_UV = {
    "down": lambda f, t: [f[0], 16 - t[2], t[0], 16 - f[2]],
    "up": lambda f, t: [f[0], f[2], t[0], t[2]],
    "north": lambda f, t: [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
    "south": lambda f, t: [f[0], 16 - t[1], t[0], 16 - f[1]],
    "west": lambda f, t: [f[2], 16 - t[1], t[2], 16 - f[1]],
    "east": lambda f, t: [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
}


def fit(a0, a1):
    """Keeps one axis of a face's UVs on its sprite: a box poking out of the block (a stove's back
    panel, a lamp's shade) would otherwise sample the neighbouring sprites in the atlas."""
    span = a1 - a0
    if span > 16:
        return 0, 16
    if a0 < 0:
        return 0, span
    if a1 > 16:
        return 16 - span, 16
    return a0, a1


def element(b):
    x0, y0, z0, x1, y1, z1, tex = b[:7]
    uvs = b[7] if len(b) > 7 else None
    rot = b[8] if len(b) > 8 else None
    faces = {}
    for f in DIRS:
        if uvs is not None and f not in uvs:
            continue  # a face the rest of the object covers (tools/props/props.py)
        face = {"texture": "#" + tex[f]}
        u0, v0, u1, v1 = AUTO_UV[f]((x0, y0, z0), (x1, y1, z1))
        if uvs:
            face["uv"] = uvs[f]
        elif min(u0, v0) < 0 or max(u1, v1) > 16:
            (u0, u1), (v0, v1) = fit(u0, u1), fit(v0, v1)
            face["uv"] = [u0, v0, u1, v1]
        edge = {"north": z0 == 0, "south": z1 == 16, "west": x0 == 0, "east": x1 == 16,
                "down": y0 == 0, "up": y1 == 16}[f]
        if edge and not rot:
            face["cullface"] = f
        faces[f] = face
    el = {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces}
    if rot:
        el["rotation"] = {"origin": rot.get("origin", [8, 8, 8]), "axis": rot["axis"], "angle": rot["angle"]}
    return el


def sprite(t, tex_dir):
    """A texture reference. 26.3 picks a block's render layer from its sprites: see-through pixels
    give cutout, and only sprites marked force_translucent (like vanilla glass) blend."""
    ref = f"{NS}:block/city/{t}"
    a = np.array(Image.open(tex_dir / f"{t}.png").convert("RGBA"))[..., 3]
    if ((a > 8) & (a < 247)).mean() > 0.02:
        return {"sprite": ref, "force_translucent": True}
    return ref


def model_json(name, spec, tex_dir):
    if spec.get("model") == "cross":
        t = f"{NS}:block/city/{spec['tex']}"
        return {"parent": "minecraft:block/cross", "textures": {"cross": sprite(spec["tex"], tex_dir), "particle": t}}
    bs = boxes(spec)
    used = sorted({t for b in bs for t in b[6].values()})
    tex = {t: sprite(t, tex_dir) for t in used}
    tex["particle"] = f"{NS}:block/city/{bs[0][6]['north']}"
    if spec.get("model", "cube") == "cube" and isinstance(spec["tex"], str) and spec.get("height", 16) == 16:
        return {"parent": "minecraft:block/cube_all", "textures": {"all": tex[spec["tex"]], "particle": tex["particle"]}}
    return {"parent": "minecraft:block/block", "textures": tex, "elements": [element(b) for b in bs]}


# doors use vanilla's door models and turns: closed doors by facing, open ones a quarter turn
# toward their hinge
DOOR_Y = {"east": 0, "south": 90, "west": 180, "north": 270}


def door_models(name, spec, tex_dir):
    """The eight models of a door: bottom and top half, left and right hinge, shut and open."""
    tex = {"bottom": sprite(spec["tex"]["bottom"], tex_dir), "top": sprite(spec["tex"]["top"], tex_dir),
           "particle": f"{NS}:block/city/{spec['tex']['bottom']}"}
    return {f"{name}_{half}_{hinge}{suffix}": {"parent": f"minecraft:block/door_{half}_{hinge}{suffix}", "textures": tex}
            for half in ("bottom", "top") for hinge in ("left", "right") for suffix in ("", "_open")}


def door_blockstate(name):
    variants = {}
    for facing, y in DOOR_Y.items():
        for half, part in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for is_open in (False, True):
                    turn = (y + (0 if not is_open else 90 if hinge == "left" else 270)) % 360
                    v = {"model": f"{NS}:block/city/{name}_{part}_{hinge}{'_open' if is_open else ''}"}
                    if turn:
                        v["y"] = turn
                    variants[f"facing={facing},half={half},hinge={hinge},open={str(is_open).lower()}"] = v
    return {"variants": variants}


def door_icon(spec, tex_dir, out):
    """A door's inventory icon: both halves stacked and shrunk, like vanilla's door items."""
    top = Image.open(tex_dir / f"{spec['tex']['top']}.png").convert("RGBA")
    bottom = Image.open(tex_dir / f"{spec['tex']['bottom']}.png").convert("RGBA")
    door = Image.new("RGBA", (top.width, top.height * 2))
    door.paste(top, (0, 0))
    door.paste(bottom, (0, top.height))
    door = door.resize((16, 32), Image.BOX)
    a = np.array(door)
    a[..., 3] = np.where(a[..., 3] > 0, np.maximum(a[..., 3], 200), 0)  # icons don't blend
    icon = Image.new("RGBA", (32, 32))
    icon.paste(Image.fromarray(a), (8, 0))
    out.parent.mkdir(parents=True, exist_ok=True)
    icon.save(out)


# trapdoors use vanilla's orientable trapdoor templates and turns
TRAPDOOR_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def trapdoor_models(name, spec, tex_dir):
    tex = {"texture": sprite(spec["tex"], tex_dir), "particle": f"{NS}:block/city/{spec['tex']}"}
    return {f"{name}_{part}": {"parent": f"minecraft:block/template_orientable_trapdoor_{part}", "textures": tex}
            for part in ("bottom", "top", "open")}


def trapdoor_blockstate(name):
    variants = {}
    for facing, y in TRAPDOOR_Y.items():
        for half in ("bottom", "top"):
            for is_open in (False, True):
                v = {"model": f"{NS}:block/city/{name}_{'open' if is_open else half}"}
                if is_open and half == "top":
                    v["x"] = 180
                    v["y"] = (y + 180) % 360
                elif y:
                    v["y"] = y
                if v.get("y") == 0:
                    del v["y"]
                variants[f"facing={facing},half={half},open={str(is_open).lower()}"] = v
    return {"variants": variants}


def climbable(blocks):
    """Ladders: the climbable block tag lists the city blocks marked climb."""
    names = [f"{NS}:{n}" for n, spec in blocks.items() if spec.get("climb")]
    write_json(MAIN / "resources/data/minecraft/tags/block/climbable.json", {"replace": False, "values": names})
    return len(names)


def lit_spec(spec):
    """A flickering lamp's lit model: its spec with the dark textures swapped for the lit ones."""
    swap = spec["flicker"]["on"]
    s = json.loads(json.dumps(spec))

    def sw(t):
        return swap.get(t, t) if isinstance(t, str) else {k: swap.get(v, v) for k, v in t.items()}

    for b in s.get("boxes", []):
        b[6] = sw(b[6])
    if "tex" in s:
        s["tex"] = sw(s["tex"])
    return s


def signal_spec(spec, key):
    """A traffic signal's model for one of its lit looks (spec["signal"]["on"][key])."""
    return lit_spec({**spec, "flicker": {"on": spec["signal"]["on"][key]}})


def signal_blockstate(name, spec):
    """Variants for each facing and each of Signal's phases, as spec["signal"]["states"] gives them."""
    out = {}
    for f, y in FACING_Y.items():
        for phase, key in enumerate(spec["signal"]["states"][f]):
            v = {"model": f"{NS}:block/city/{name}{'_' + key if key else ''}"}
            if y:
                v["y"] = y
            out[f"facing={f},phase={phase}"] = v
    return {"variants": out}


def flicker_blockstate(name, spec):
    """Variants for each turn, lit and dark."""
    out = {}
    rot = spec.get("rotate")
    turns = ({f"facing={f}": y for f, y in FACING_Y.items()} if rot == "facing"
             else {"axis=z": 0, "axis=x": 90} if rot == "axis" else {"": 0})
    for key, y in turns.items():
        for lit in (False, True):
            v = {"model": f"{NS}:block/city/{name}{'_on' if lit else ''}"}
            if y:
                v["y"] = y
            out[(key + "," if key else "") + f"lit={str(lit).lower()}"] = v
    return {"variants": out}


def blockstate(name, spec):
    model = f"{NS}:block/city/{name}"
    rot = spec.get("rotate")
    if rot == "facing":
        return {"variants": {f"facing={f}": ({"model": model, "y": y} if y else {"model": model})
                             for f, y in FACING_Y.items()}}
    if rot == "axis":
        return {"variants": {"axis=z": {"model": model}, "axis=x": {"model": model, "y": 90}}}
    if spec.get("spin"):
        # a tile that would line up block to block (leaves): each block turned its own way
        return {"variants": {"": [dict({"model": model}, **({"x": x} if x else {}), **({"y": y} if y else {}))
                                  for x in (0, 90, 180, 270) for y in (0, 90)]}}
    return {"variants": {"": {"model": model}}}


BRANCH_TURNS = [("north", {}), ("south", {"y": 180}), ("east", {"y": 90}), ("west", {"y": 270}),
                ("up", {"x": 270}), ("down", {"x": 90})]


def branch_models(name, spec, tex_dir):
    """A tree limb: its post (the spec's one box) and one arm reaching from it to the north face,
    which the blockstate turns toward each neighbour the limb joins."""
    core = spec["boxes"][0]
    a, b, tex = core[0], core[3], core[6]
    arm = dict(spec, boxes=[[a, a, 0, b, b, a, tex]])
    return {name: model_json(name, spec, tex_dir), f"{name}_arm": model_json(name, arm, tex_dir)}


def branch_blockstate(name):
    parts = [{"apply": {"model": f"{NS}:block/city/{name}"}}]
    for side, turn in BRANCH_TURNS:
        parts.append({"when": {side: "true"}, "apply": dict({"model": f"{NS}:block/city/{name}_arm"}, **turn)})
    return {"multipart": parts}


def loot_json(name, spec):
    """What a block drops: itself, unless the spec names another item (a tree's bark gives logs)
    or nothing ("")."""
    item = spec.get("drops", f"{NS}:{name}")
    pools = [] if item == "" else [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": item}],
                                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]
    return {"type": "minecraft:block", "pools": pools, "random_sequence": f"{NS}:blocks/{name}"}


def has_alpha(tex_dir, names):
    return any((np.array(Image.open(tex_dir / f"{t}.png").convert("RGBA"))[..., 3] < 250).any() for t in names)


# the SoundType constants the game has (a name it lacks only shows up as a compile error on CI)
GAME_SOUNDS = {"STONE", "WOOD", "GRAVEL", "GRASS", "METAL", "GLASS", "WOOL", "SAND", "CHAIN", "LANTERN", "BAMBOO",
          "COPPER", "NETHER_BRICKS", "DEEPSLATE", "TUFF", "MUD_BRICKS", "PACKED_MUD", "LADDER", "SCAFFOLDING",
          "CHERRY_WOOD", "NETHER_WOOD", "BAMBOO_WOOD", "SNOW", "CANDLE", "HANGING_SIGN", "CHISELED_BOOKSHELF"}


def sound(name, spec):
    if "sound" in spec:
        if spec["sound"] not in GAME_SOUNDS:
            sys.exit(f"{name}: no SoundType.{spec['sound']} in the game (pick one of {sorted(GAME_SOUNDS)})")
        return spec["sound"]
    words = [name] + textures(spec)
    for keys, s in SOUNDS:
        if any(k in w for w in words for k in keys):
            return s
    return "STONE"


def java_line(name, spec, tex_dir):
    if "trapdoor" in spec:
        flag = "SEE_THROUGH" if has_alpha(tex_dir, textures(spec)) else "0"
        return f'        s("{name}", TRAPDOOR, {flag}, 0, SoundType.{sound(name, spec) if "sound" in spec else "METAL"}, 3f);'
    if "door" in spec:
        s = "METAL" if spec["door"] == "steel" else "WOOD"
        flag = "SEE_THROUGH" if has_alpha(tex_dir, textures(spec)) else "0"
        return f'        s("{name}", DOOR, {flag}, 0, SoundType.{s}, 3f);'
    model = spec.get("model", "cube")
    kind = "BRANCH" if "branch" in spec else {"facing": "FACING", "axis": "AXIS"}.get(spec.get("rotate"), "PLAIN")
    decor = model == "cross" or spec.get("decor") or name in DECOR or name.startswith(DECOR_PREFIX)
    # partial shapes still hide what they cover (their shape is the occlusion shape); only
    # see-through pixels need the neighbours drawn behind them
    see_through = has_alpha(tex_dir, textures(spec))
    flags = []
    if see_through:
        flags.append("SEE_THROUGH")
    if decor:
        flags.append("DECOR")
    if "loot" in spec:
        flags.append("LOOT")
    if "flicker" in spec:
        flags.append("FLICKER")
    if "signal" in spec:
        flags.append("SIGNAL")
    if spec.get("switch"):
        flags.append("SWITCH")
    if spec.get("offset"):
        flags.append("OFFSET")
    if model == "cross":
        shape = [2, 0, 2, 14, 13, 14]
    elif "shape" in spec:
        # a block you see through in places walks through there too: its own collision boxes
        shape = [v for b in spec["shape"] for v in b[:6]]
    else:
        shape = [v for b in boxes(spec) for v in b[:6]]
        if name.startswith(("ext_", "front_")):
            # gutters, sills, aerials and cornices reach out of the block; their collision stays in it
            shape = [min(16, max(0, v)) for v in shape]
    s = sound(name, spec)
    strength = 0.0 if decor else 0.3 if s == "GLASS" else 1.0 if s in ("WOOD", "WOOL", "GRASS", "GRAVEL") else 2.0
    nums = ", ".join(f"{v:g}" for v in shape)
    flag = " | ".join(flags) or "0"
    light = spec["flicker"]["light"] if "flicker" in spec else spec["signal"]["light"] if "signal" in spec else spec.get("light", 0)
    # an empty shape (walk straight through) passes no boxes at all
    line = f'        s("{name}", {kind}, {flag}, {light}, SoundType.{s}, {strength:g}f{", " + nums if nums else ""});'
    if "loot" in spec:
        line += f'\n        loot("{name}", "{spec["loot"]}");'
    return line


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def title(name):
    if name.startswith("store_shelf_"):  # the shelf's name heads its loot screen: "Grocery Shelf"
        return name[len("store_shelf_"):].removesuffix("_top").replace("_", " ").title() + " Shelf"
    if name.startswith("store_"):
        name = name[len("store_"):]
    words = ["weathered" if w == "b" else w for w in name.split("_")]
    return " ".join(w.capitalize() for w in words)


# RULE (Josiah 2026-10-27): a rotate="axis" model is drawn running along z, so axis= names the
# way a row runs and a row of rails, bars or fences joins up (paint_textures.along_z turns one
# drawn along x). These were drawn along x before the rule and every placement turned to suit;
# anything else drawn along x stops the build.
AXIS_DRAWN_X = {"fit_beam", "fit_girder", "fit_beam_duct", "fit_beam_sprinkler", "fit_troffer", "fit_troffer_on",
                "fit_troffer_flicker", "vessel_lash", "vessel_lash_open", "bunk_tripwire", "yd_line_post"}


def axis_rule(blocks):
    bad = []
    for name, spec in blocks.items():
        if spec.get("rotate") != "axis" or "boxes" not in spec or name in AXIS_DRAWN_X:
            continue
        bx = spec["boxes"]
        fx = sum(1 for b in bx if b[0] <= 0.5 and b[3] >= 15.5 and b[5] - b[2] < 15)
        fz = sum(1 for b in bx if b[2] <= 0.5 and b[5] >= 15.5 and b[3] - b[0] < 15)
        if fx > fz:
            bad.append(name)
    if bad:
        sys.exit("axis models drawn along x (turn them with paint_textures.along_z): " + " ".join(bad))


def main():
    tex_dir = Path(sys.argv[1])
    blocks = json.loads((ROOT / "tools/city/blocks.json").read_text())["blocks"]
    # the game refuses a resource path with anything else in it (capitals broke server start once)
    bad = [n for n in blocks if not re.fullmatch(r"[a-z0-9_./-]+", n)]
    if bad:
        sys.exit(f"block names must be lower case a-z, 0-9, _ . / -: {bad[:8]}")
    axis_rule(blocks)
    # the prop packs' atlases come whole from tools/props/props.py, their LabPBR maps with them
    for p in (ROOT / "tools/props/tex").glob("*.png"):
        shutil.copy(p, tex_dir / p.name)
    # start clean so renamed or dropped blocks don't linger (the old models name the old blocks)
    old = sorted({p.stem.split("_bottom_")[0].split("_top_")[0] for p in (ASSETS / "models/block/city").glob("*.json")})
    for d in (ASSETS / "models/block/city", ASSETS / "textures/block/city"):
        shutil.rmtree(d, ignore_errors=True)
    for name in old:
        for p in (ASSETS / f"blockstates/{name}.json", ASSETS / f"items/{name}.json",
                  DATA / f"loot_table/blocks/{name}.json"):
            p.unlink(missing_ok=True)

    used = set()
    lines = []
    for name, spec in blocks.items():
        if "trapdoor" in spec:
            for model, obj in trapdoor_models(name, spec, tex_dir).items():
                write_json(ASSETS / f"models/block/city/{model}.json", obj)
            write_json(ASSETS / f"blockstates/{name}.json", trapdoor_blockstate(name))
            write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model",
                                                                 "model": f"{NS}:block/city/{name}_bottom"}})
            write_json(DATA / f"loot_table/blocks/{name}.json", {
                "type": "minecraft:block",
                "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                           "conditions": [{"condition": "minecraft:survives_explosion"}]}],
                "random_sequence": f"{NS}:blocks/{name}"})
            used.update(textures(spec))
            lines.append(java_line(name, spec, tex_dir))
            continue
        if "door" in spec:
            for model, obj in door_models(name, spec, tex_dir).items():
                write_json(ASSETS / f"models/block/city/{model}.json", obj)
            write_json(ASSETS / f"blockstates/{name}.json", door_blockstate(name))
            write_json(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated",
                                                             "textures": {"layer0": f"{NS}:item/{name}"}})
            write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
            door_icon(spec, tex_dir, ASSETS / f"textures/item/{name}.png")
            write_json(DATA / f"loot_table/blocks/{name}.json", {
                "type": "minecraft:block",
                "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}",
                           "conditions": [{"condition": "minecraft:block_state_property", "block": f"{NS}:{name}",
                                           "properties": {"half": "lower"}}]}],
                           "conditions": [{"condition": "minecraft:survives_explosion"}]}],
                "random_sequence": f"{NS}:blocks/{name}"})
            used.update(textures(spec))
            lines.append(java_line(name, spec, tex_dir))
            continue
        if "branch" in spec:
            for model, obj in branch_models(name, spec, tex_dir).items():
                write_json(ASSETS / f"models/block/city/{model}.json", obj)
            write_json(ASSETS / f"blockstates/{name}.json", branch_blockstate(name))
            write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/city/{name}"}})
            write_json(DATA / f"loot_table/blocks/{name}.json", loot_json(name, spec))
            used.update(textures(spec))
            lines.append(java_line(name, spec, tex_dir))
            continue
        write_json(ASSETS / f"models/block/city/{name}.json", model_json(name, spec, tex_dir))
        if "flicker" in spec:
            write_json(ASSETS / f"models/block/city/{name}_on.json", model_json(name, lit_spec(spec), tex_dir))
            write_json(ASSETS / f"blockstates/{name}.json", flicker_blockstate(name, spec))
            used.update(textures(lit_spec(spec)))
        elif "signal" in spec:
            for key in spec["signal"]["on"]:
                write_json(ASSETS / f"models/block/city/{name}_{key}.json", model_json(name, signal_spec(spec, key), tex_dir))
                used.update(textures(signal_spec(spec, key)))
            write_json(ASSETS / f"blockstates/{name}.json", signal_blockstate(name, spec))
        else:
            write_json(ASSETS / f"blockstates/{name}.json", blockstate(name, spec))
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/city/{name}"}})
        write_json(DATA / f"loot_table/blocks/{name}.json", loot_json(name, spec))
        used.update(textures(spec))
        lines.append(java_line(name, spec, tex_dir))

    out = ASSETS / "textures/block/city"
    out.mkdir(parents=True, exist_ok=True)
    for t in sorted(used):
        for suffix in ("", "_n", "_s"):
            src = tex_dir / f"{t}{suffix}.png"
            if src.exists():
                shutil.copy(src, out / src.name)

    lang_path = ASSETS / "lang/en_us.json"
    lang = json.loads(lang_path.read_text())
    lang = {k: v for k, v in lang.items() if not (k.startswith(f"block.{NS}.") and k[len(f"block.{NS}."):] in old)}
    for name, spec in blocks.items():
        lang[f"block.{NS}.{name}"] = spec.get("title") or title(name)
    lang[f"itemGroup.{NS}.city"] = "ZomboidCraft City"
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")

    # a method's bytecode is capped at 64 KB, so the blocks go in parts: each call costs about
    # 40 bytes and each of its box numbers about 9 more; a part stops well short of the cap
    parts, size = [[]], 0
    for line in lines:
        cost = 40 + 9 * line.count(",")
        if parts[-1] and size + cost > 36000:
            parts.append([])
            size = 0
        parts[-1].append(line)
        size += cost
    calls = "".join(f"        part{i}();\n" for i in range(len(parts)))
    bodies = "".join(f"\n    private void part{i}() {{\n" + "\n".join(p) + "\n    }\n" for i, p in enumerate(parts))
    JAVA.write_text(f"""package com.zomboidcraft.world.blocks;

import net.minecraft.world.level.block.SoundType;

/** Generated by tools/city/gen_blocks.py from tools/city/blocks.json; don't edit by hand. */
final class CitySpecs extends CityBlocks.Specs {{
    CitySpecs() {{
{calls}    }}
{bodies}}}
""")
    import heightmap_tags
    print(f"{len(blocks)} blocks, {len(used)} textures, {heightmap_tags.write()} stop rain, {climbable(blocks)} climbable")


if __name__ == "__main__":
    main()
