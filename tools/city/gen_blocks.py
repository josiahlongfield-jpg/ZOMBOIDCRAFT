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
        return [b[6] for b in spec["boxes"]]
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
    return [b[:6] + [{f: b[6] for f in DIRS}] for b in spec["boxes"]]


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
    x0, y0, z0, x1, y1, z1, tex = b
    faces = {}
    for f in DIRS:
        face = {"texture": "#" + tex[f]}
        u0, v0, u1, v1 = AUTO_UV[f]((x0, y0, z0), (x1, y1, z1))
        if min(u0, v0) < 0 or max(u1, v1) > 16:
            (u0, u1), (v0, v1) = fit(u0, u1), fit(v0, v1)
            face["uv"] = [u0, v0, u1, v1]
        edge = {"north": z0 == 0, "south": z1 == 16, "west": x0 == 0, "east": x1 == 16,
                "down": y0 == 0, "up": y1 == 16}[f]
        if edge:
            face["cullface"] = f
        faces[f] = face
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces}


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


def blockstate(name, spec):
    model = f"{NS}:block/city/{name}"
    rot = spec.get("rotate")
    if rot == "facing":
        return {"variants": {f"facing={f}": ({"model": model, "y": y} if y else {"model": model})
                             for f, y in FACING_Y.items()}}
    if rot == "axis":
        return {"variants": {"axis=z": {"model": model}, "axis=x": {"model": model, "y": 90}}}
    return {"variants": {"": {"model": model}}}


def has_alpha(tex_dir, names):
    return any((np.array(Image.open(tex_dir / f"{t}.png").convert("RGBA"))[..., 3] < 250).any() for t in names)


def sound(name, spec):
    if "sound" in spec:
        return spec["sound"]
    words = [name] + textures(spec)
    for keys, s in SOUNDS:
        if any(k in w for w in words for k in keys):
            return s
    return "STONE"


def java_line(name, spec, tex_dir):
    if "door" in spec:
        s = "METAL" if spec["door"] == "steel" else "WOOD"
        flag = "SEE_THROUGH" if has_alpha(tex_dir, textures(spec)) else "0"
        return f'        s("{name}", DOOR, {flag}, 0, SoundType.{s}, 3f);'
    model = spec.get("model", "cube")
    kind = {"facing": "FACING", "axis": "AXIS"}.get(spec.get("rotate"), "PLAIN")
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
    if model == "cross":
        shape = [2, 0, 2, 14, 13, 14]
    else:
        shape = [v for b in boxes(spec) for v in b[:6]]
    s = sound(name, spec)
    strength = 0.0 if decor else 0.3 if s == "GLASS" else 1.0 if s in ("WOOD", "WOOL", "GRASS", "GRAVEL") else 2.0
    nums = ", ".join(f"{v:g}" for v in shape)
    flag = " | ".join(flags) or "0"
    line = f'        s("{name}", {kind}, {flag}, {spec.get("light", 0)}, SoundType.{s}, {strength:g}f, {nums});'
    if "loot" in spec:
        line += f'\n        loot("{name}", "{spec["loot"]}");'
    return line


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def title(name):
    if name.startswith("store_shelf_"):  # the shelf's name heads its loot screen: "Grocery Shelf"
        return name[len("store_shelf_"):].replace("_", " ").title() + " Shelf"
    if name.startswith("store_"):
        name = name[len("store_"):]
    words = ["weathered" if w == "b" else w for w in name.split("_")]
    return " ".join(w.capitalize() for w in words)


def main():
    tex_dir = Path(sys.argv[1])
    blocks = json.loads((ROOT / "tools/city/blocks.json").read_text())["blocks"]
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
        write_json(ASSETS / f"models/block/city/{name}.json", model_json(name, spec, tex_dir))
        write_json(ASSETS / f"blockstates/{name}.json", blockstate(name, spec))
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/city/{name}"}})
        write_json(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{NS}:blocks/{name}"})
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

    # a method's bytecode is capped at 64 KB, so the blocks go in parts of a few hundred
    parts = [lines[i:i + 250] for i in range(0, len(lines), 250)]
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
    print(f"{len(blocks)} blocks, {len(used)} textures, {heightmap_tags.write()} stop rain")


if __name__ == "__main__":
    main()
