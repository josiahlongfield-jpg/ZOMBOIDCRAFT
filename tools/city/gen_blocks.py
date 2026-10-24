#!/usr/bin/env python3
"""Turns tools/city/blocks.json into the mod's real city blocks.

Writes, under src/main:
  java/com/zomboidcraft/world/blocks/CitySpecs.java   one line per block: kind, shape, light, sound
  resources/assets/zomboidcraft/blockstates|models/block/city|items/<name>.json
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
DECOR_PREFIX = ("graffiti_",)
DECOR = {"vines", "paper", "glass_shards", "blood_splat", "scorch", "leaf_litter", "banner_skull"}
SOUNDS = [  # first keyword in the block's name or textures wins
    (("window", "glass", "cabin"), "GLASS"),
    (("grass", "weeds", "vines", "ivy", "leaves", "leaf", "moss_carpet"), "GRASS"),
    (("crate", "boards", "boarded", "wood", "desk", "tree_log", "cardboard", "paper"), "WOOD"),
    (("carpet", "office_floor", "cubicle", "sandbag", "trash_bag"), "WOOL"),
    (("dirt", "burnt_ground", "rubble"), "GRAVEL"),
    (("car_", "steel", "metal", "rail", "grate", "chainlink", "barbed", "ladder", "hvac", "dumpster",
      "barrel", "shutter", "pole", "streetlight", "rebar", "elevator", "fire_door", "filing", "train",
      "water_tank", "lantern", "light", "rust", "girder", "chair"), "METAL"),
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


def element(b):
    x0, y0, z0, x1, y1, z1, tex = b
    faces = {}
    for f in DIRS:
        face = {"texture": "#" + tex[f]}
        edge = {"north": z0 == 0, "south": z1 == 16, "west": x0 == 0, "east": x1 == 16,
                "down": y0 == 0, "up": y1 == 16}[f]
        if edge:
            face["cullface"] = f
        faces[f] = face
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces}


def model_json(name, spec):
    if spec.get("model") == "cross":
        t = f"{NS}:block/city/{spec['tex']}"
        return {"parent": "minecraft:block/cross", "textures": {"cross": t, "particle": t}}
    bs = boxes(spec)
    used = sorted({t for b in bs for t in b[6].values()})
    tex = {t: f"{NS}:block/city/{t}" for t in used}
    tex["particle"] = tex[bs[0][6]["north"]]
    if spec.get("model", "cube") == "cube" and isinstance(spec["tex"], str) and spec.get("height", 16) == 16:
        return {"parent": "minecraft:block/cube_all", "textures": {"all": tex[spec["tex"]], "particle": tex[spec["tex"]]}}
    return {"parent": "minecraft:block/block", "textures": tex, "elements": [element(b) for b in bs]}


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
    words = [name] + textures(spec)
    for keys, s in SOUNDS:
        if any(k in w for w in words for k in keys):
            return s
    return "STONE"


def java_line(name, spec, tex_dir):
    model = spec.get("model", "cube")
    kind = {"facing": "FACING", "axis": "AXIS"}.get(spec.get("rotate"), "PLAIN")
    decor = model == "cross" or name in DECOR or name.startswith(DECOR_PREFIX)
    # partial shapes still hide what they cover (their shape is the occlusion shape); only
    # see-through pixels need the neighbours drawn behind them
    see_through = has_alpha(tex_dir, textures(spec))
    flags = []
    if see_through:
        flags.append("SEE_THROUGH")
    if decor:
        flags.append("DECOR")
    if model == "cross":
        shape = [2, 0, 2, 14, 13, 14]
    else:
        shape = [v for b in boxes(spec) for v in b[:6]]
    s = sound(name, spec)
    strength = 0.0 if decor else 0.3 if s == "GLASS" else 1.0 if s in ("WOOD", "WOOL", "GRASS", "GRAVEL") else 2.0
    nums = ", ".join(f"{v:g}" for v in shape)
    flag = " | ".join(flags) or "0"
    return (f'        s("{name}", {kind}, {flag}, {spec.get("light", 0)}, SoundType.{s}, {strength:g}f, {nums});')


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def title(name):
    words = ["weathered" if w == "b" else w for w in name.split("_")]
    return " ".join(w.capitalize() for w in words)


def main():
    tex_dir = Path(sys.argv[1])
    blocks = json.loads((ROOT / "tools/city/blocks.json").read_text())["blocks"]
    # start clean so renamed or dropped blocks don't linger (the old models name the old blocks)
    old = [p.stem for p in (ASSETS / "models/block/city").glob("*.json")]
    for d in (ASSETS / "models/block/city", ASSETS / "textures/block/city"):
        shutil.rmtree(d, ignore_errors=True)
    for name in old:
        for p in (ASSETS / f"blockstates/{name}.json", ASSETS / f"items/{name}.json",
                  DATA / f"loot_table/blocks/{name}.json"):
            p.unlink(missing_ok=True)

    used = set()
    lines = []
    for name, spec in blocks.items():
        write_json(ASSETS / f"models/block/city/{name}.json", model_json(name, spec))
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
    for name in blocks:
        lang[f"block.{NS}.{name}"] = title(name)
    lang[f"itemGroup.{NS}.city"] = "ZomboidCraft City"
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")

    JAVA.write_text(f"""package com.zomboidcraft.world.blocks;

import net.minecraft.world.level.block.SoundType;

/** Generated by tools/city/gen_blocks.py from tools/city/blocks.json; don't edit by hand. */
final class CitySpecs extends CityBlocks.Specs {{
    CitySpecs() {{
{chr(10).join(lines)}
    }}
}}
""")
    print(f"{len(blocks)} blocks, {len(used)} textures")


if __name__ == "__main__":
    main()
