#!/usr/bin/env python3
"""Preview stand-ins for the vanilla blocks the county generator places (grass, logs, stairs,
fences...), so tools/city/mesh.py can draw a county dump. In game these are Minecraft's own
blocks; here they get photo textures from the CC0 library graded toward vanilla's colours, and
block specs in blocks.json's format (plus the stairs/slab/fence/wall/trapdoor/log/flat/vine
models mesh.py knows for vanilla shapes).

    python3 tools/county/vanilla.py CC0_DIR TEX_DIR DUMP [DUMP ...]

Reads the palettes of the dumps (CountyPreview's DUMP.json), writes a texture per material to
TEX_DIR (named mc_*) and the specs to TEX_DIR/vanilla_blocks.json for mesh.py --extra. City
textures already in TEX_DIR are reused where a vanilla block looks the same (bricks, moss).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent.parent / "city"))
import paint_textures as P  # noqa: E402
import photo_textures as PT  # noqa: E402

N = P.N
TEX = None
MADE = set()


def tone(t, rgb, contrast=1.0):
    """Recolours a texture to a mean colour, keeping its light and dark detail."""
    lum = t.c @ np.array([0.299, 0.587, 0.114], np.float32)
    rel = (lum / max(float(lum.mean()), 1.0) - 1) * contrast + 1
    t.c = np.clip(rel[..., None] * np.array(rgb, np.float32), 0, 255)
    return t


def ph(material, rgb=None, span=1.0, offset=(0.2, 0.3), contrast=1.0, rot=False, smooth=None):
    def make():
        t = PT.photo(material, span=span, offset=offset, sat=0.8, bright=1.0)
        if rot:
            t.c, t.h, t.smooth = np.rot90(t.c).copy(), np.rot90(t.h).copy(), np.rot90(t.smooth).copy()
        if rgb:
            tone(t, rgb, contrast)
        if smooth is not None:
            t.smooth[:] = smooth
        return t
    return make


def painted(rgb, vary=0.12, speckle=None, seed=1):
    def make():
        t = P.Tex(seed, rgb)
        t.vary(vary)
        if speckle:
            t.speckle(*speckle)
        return t
    return make


def city(name, rgb=None, contrast=1.0):
    """A city texture already in TEX_DIR, optionally recoloured."""
    def make():
        from PIL import Image
        t = P.Tex(0, (0, 0, 0))
        a = np.asarray(Image.open(TEX / f"{name}.png").convert("RGBA").resize((N, N), Image.NEAREST), np.float32)
        t.c, t.alpha = a[..., :3].copy(), a[..., 3].copy()
        if rgb:
            m = t.alpha > 0
            keep = t.c.copy()
            tone(t, rgb, contrast)
            t.c[~m] = keep[~m]
        return t
    return make


def grid(rgb, line, cells=2, seed=3):
    """Cut copper and the like: square plates with dark seams."""
    def make():
        t = P.Tex(seed, rgb)
        t.vary(0.1)
        step = N // cells
        for i in range(cells):
            t.rect(i * step, 0, i * step + 2, N, col=line, h=0.5)
            t.rect(0, i * step, N, i * step + 2, col=line, h=0.5)
        t.smooth[:] = 0.45
        return t
    return make


def rings(core, bark):
    def make():
        t = P.Tex(5, core)
        y, x = np.mgrid[0:N, 0:N] - (N - 1) / 2
        r = np.hypot(x, y)
        t.c *= (0.88 + 0.12 * np.sin(r * 0.9))[..., None]
        edge = r > N * 0.43
        t.c[edge] = bark
        t.vary(0.06)
        return t
    return make


def grass_side(top_rgb, dirt):
    def make():
        t = dirt()
        g = PT.photo("Grass004", span=2, sat=0.8, bright=1.0)
        tone(g, top_rgb)
        depth = (N * 0.2 + 4 * np.sin(np.arange(N) * 0.7) + 3 * np.sin(np.arange(N) * 2.3)).astype(int)
        for x in range(N):
            t.c[:depth[x], x] = g.c[:depth[x], x]
        return t
    return make


def door(rgb, seed):
    """A panelled wooden door half: planks with a sunken frame (and a window on the top half)."""
    def make(top):
        t = ph("old_planks_02", rgb, span=2)()
        P.Tex.frame(t, 4, tuple(np.array(rgb) * 0.7), h=0.9)
        if top:
            t.rect(14, 10, 50, 34, col=(40, 48, 52), smooth=0.9)
        return t
    return make


GRASS = (98, 116, 64)        # the muted field green the biomes tint grass to
LEAF = {"oak": (62, 82, 42), "birch": (88, 104, 56), "dark_oak": (46, 62, 32), "spruce": (44, 60, 44),
        "pale_oak": (150, 156, 140), "jungle": (60, 92, 40), "acacia": (92, 100, 46), "mangrove": (62, 86, 40),
        "cherry": (200, 150, 170), "azalea": (80, 100, 50)}
PLANK = {"oak": (156, 124, 76), "spruce": (110, 80, 48), "dark_oak": (66, 44, 22), "pale_oak": (214, 204, 200),
         "birch": (190, 172, 120), "jungle": (160, 112, 78), "acacia": (168, 90, 50), "mangrove": (118, 54, 48),
         "cherry": (222, 170, 160), "bamboo": (196, 176, 90), "crimson": (100, 50, 70), "warped": (44, 104, 100)}
BARK = {"oak": ((98, 76, 48), "Bark014"), "spruce": ((58, 40, 24), "pine_bark"), "dark_oak": ((56, 40, 24), "Bark014"),
        "pale_oak": ((96, 90, 86), "Bark011"), "birch": ((210, 206, 196), "Bark006"), "jungle": ((86, 68, 34), "Bark014"),
        "acacia": ((96, 90, 80), "Bark002"), "mangrove": ((80, 60, 40), "Bark014"), "cherry": ((54, 34, 40), "Bark014")}

# full cube materials, by vanilla block name
CUBE = {
    "stone": ph("Rock059", (118, 118, 116), span=2),
    "andesite": ph("Rock059", (128, 128, 126), span=2, offset=(0.6, 0.6)),
    "deepslate": ph("Rock059", (70, 70, 74), span=2),
    "bedrock": ph("Rock064", (52, 52, 52), span=2),
    "dirt": ph("Ground071", (112, 82, 58), span=2),
    "coarse_dirt": ph("Ground111", (100, 76, 56), span=2),
    "mud": ph("mud_forest", (58, 50, 46), span=2),
    "clay": ph("dry_mud_field_001", (150, 154, 166), span=2),
    "gravel": ph("Gravel043", (126, 120, 116), span=2),
    "podzol": ph("brown_mud_leaves_01", (98, 70, 40), span=2),
    "moss_block": ph("Moss002", (78, 96, 46), span=2, contrast=1.6),
    "dirt_path": ph("gravel_road", (140, 112, 70), span=2),
    "farmland": ph("farm_soil", (84, 58, 38), span=2),
    "sand": ph("damp_sand", (196, 178, 136), span=2),
    "oak_planks": None, "spruce_planks": None, "dark_oak_planks": None, "pale_oak_planks": None,
    "birch_planks": None, "jungle_planks": None, "acacia_planks": None,
    "bricks": city("brick_red"),
    "stone_bricks": ph("stone_brick_wall_001", (120, 120, 118)),
    "mossy_stone_bricks": ph("stone_brick_wall_001", (110, 118, 100)),
    "cobblestone": ph("Rock064", (116, 116, 114), span=2),
    "mossy_cobblestone": ph("Rock064", (104, 114, 96), span=2),
    "cobbled_deepslate": ph("Rock064", (66, 66, 70), span=2),
    "deepslate_tiles": ph("roof_slates_03", (58, 58, 62), span=2, contrast=1.4),
    "deepslate_bricks": ph("stone_brick_wall_001", (72, 72, 76)),
    "mud_bricks": city("brick_buff", (140, 112, 84)),
    "tuff_bricks": city("brick_buff", (104, 108, 100)),
    "tuff": ph("Rock059", (104, 108, 100), span=2),
    "blackstone": ph("Rock064", (44, 40, 46), span=2),
    "polished_blackstone_bricks": ph("stone_brick_wall_001", (46, 42, 48)),
    "exposed_cut_copper": grid((160, 116, 92), (110, 76, 60)),
    "weathered_cut_copper": grid((104, 144, 110), (70, 104, 80)),
    "oxidized_cut_copper": grid((80, 150, 130), (54, 110, 96)),
    "cut_copper": grid((190, 110, 80), (140, 76, 56)),
    "white_glazed_terracotta": painted((216, 220, 214), 0.05),
    "black_terracotta": painted((40, 28, 24), 0.08),
    "white_terracotta": painted((200, 172, 156), 0.08),
    "terracotta": painted((150, 92, 66), 0.08),
    "hay_block": ph("ThatchedRoof002B", (176, 150, 60), span=2),
    "bookshelf": painted((96, 70, 44), 0.25),
    "crafting_table": painted((120, 90, 56), 0.2),
    "barrel": ph("old_planks_02", (110, 80, 50), span=2),
    "iron_block": painted((200, 200, 200), 0.05),
    "smooth_stone": painted((156, 156, 156), 0.05),
    "polished_andesite": painted((130, 132, 130), 0.06),
    "snow_block": painted((236, 240, 244), 0.03),
    "glass": None,
    "sponge": painted((190, 180, 70)),
    "packed_mud": ph("Ground071", (140, 108, 80), span=2),
    "polished_blackstone": painted((48, 44, 50), 0.06),
    "prismarine_bricks": ph("stone_brick_wall_001", (80, 130, 120)),
    "smooth_quartz": painted((226, 222, 214), 0.03),
    "quartz_block": painted((226, 222, 214), 0.04),
    "chiseled_stone_bricks": ph("stone_brick_wall_001", (128, 128, 124)),
}
WOODS = list(PLANK)
for w in WOODS:
    CUBE[f"{w}_planks"] = ph("old_planks_02", PLANK[w], span=2, rot=True)

# small plants drawn as an X of two quads
CROSS = {
    "short_grass": city("grass_tuft", GRASS), "tall_grass": city("weeds_tall", GRASS),
    "fern": city("grass_tuft", (70, 96, 52)), "large_fern": city("weeds_tall", (70, 96, 52)),
    "bush": city("leaves_dark", (66, 86, 46)), "short_dry_grass": city("grass_tuft", (150, 136, 90)),
    "tall_dry_grass": city("weeds_tall", (150, 136, 90)), "dead_bush": city("grass_tuft", (110, 84, 56)),
    "sugar_cane": city("weeds_tall", (120, 150, 80)), "sweet_berry_bush": city("leaves_dark", (60, 80, 40)),
    "oxeye_daisy": city("grass_tuft", (190, 196, 170)), "cornflower": city("grass_tuft", (90, 110, 150)),
    "dandelion": city("grass_tuft", (190, 176, 70)), "poppy": city("grass_tuft", (170, 70, 50)),
    "azure_bluet": city("grass_tuft", (190, 196, 190)), "wheat": city("weeds_tall", (180, 160, 80)),
    "carrots": city("grass_tuft", (90, 130, 60)), "potatoes": city("grass_tuft", (90, 130, 60)),
    "beetroots": city("grass_tuft", (100, 120, 60)), "pumpkin_stem": city("grass_tuft", (90, 120, 60)),
    "cobweb": painted((220, 220, 220)), "brown_mushroom": city("grass_tuft", (130, 100, 80)),
    "lilac": city("weeds_tall", (150, 120, 150)), "rose_bush": city("weeds_tall", (120, 60, 60)),
    "peony": city("weeds_tall", (180, 140, 160)), "sunflower": city("weeds_tall", (180, 160, 60)),
}


def material(prefix):
    """The cube block a stairs/slab/wall/fence family is cut from."""
    for name in (prefix + "_planks", prefix + "s", prefix, prefix.replace("waxed_", "")):
        if name in CUBE and CUBE[name]:
            return name
    if prefix.endswith("_brick"):
        return material(prefix + "s")
    return None


def tex_of(block):
    """The preview texture name for a cube block, making it on first use."""
    name = "mc_" + block
    if block in ("bricks",):
        return "brick_red"
    if name not in MADE:
        make = CUBE.get(block)
        save(name, make() if make else unknown(block))
    return name


def unknown(block):
    h = abs(hash(block)) % 997
    print("no preview texture for", block)
    return P.Tex(h, (90 + h % 80, 90 + h * 7 % 80, 90 + h * 13 % 80))


def save(name, t):
    t.albedo().save(TEX / f"{name}.png")
    t.specular().save(TEX / f"{name}_s.png")
    t.normal().save(TEX / f"{name}_n.png")
    t.normal(gl=True).save(TEX / f"{name}_nrm_gl.png")
    MADE.add(name)


def made(name, make):
    if name not in MADE:
        save(name, make())
    return name


def spec(base):
    """blocks.json-style spec for a vanilla block (without the minecraft: prefix)."""
    if base in CROSS:
        return {"model": "cross", "tex": made("mc_" + base, CROSS[base])}
    if base == "water":
        def water():
            t = P.Tex(9, (44, 62, 66))
            t.vary(0.08)
            t.alpha[:] = 170
            t.smooth[:] = 0.95
            return t
        return {"model": "cube", "height": 14, "tex": made("mc_water", water)}
    if base == "leaf_litter":
        return {"model": "flat", "height": 1, "tex": made("mc_leaf_litter", city("leaf_litter"))}
    if base.endswith("_carpet"):
        col = base[:-7]
        return {"model": "flat", "height": 1, "tex": made("mc_" + base, painted(COLOURS.get(col, (120, 110, 100)), 0.1))}
    if base == "vine":
        return {"model": "vine", "tex": made("mc_vine", city("ivy", (60, 80, 40)))}
    if base.endswith("_leaves"):
        wood = base[:-7]
        return {"model": "cube", "tex": made("mc_" + base, city("leaves_dark", LEAF.get(wood, LEAF["oak"])))}
    if base.endswith(("_log", "_wood")) or base.startswith("stripped_"):
        wood = base.replace("stripped_", "")[:-4]
        rgb, photo = BARK.get(wood, BARK["oak"])
        if base.startswith("stripped_"):
            rgb, photo = PLANK.get(wood, PLANK["oak"]), "Wood049"
        side = made(f"mc_{base}", ph(photo, rgb, span=2))
        top = side if base.endswith("_wood") else made(f"mc_{base}_top", rings(PLANK.get(wood, PLANK["oak"]), rgb))
        return {"model": "log", "tex": {"top": top, "side": side}}
    if base == "grass_block":
        top = made("mc_grass_top", ph("Grass004", GRASS, span=2))
        side = made("mc_grass_side", grass_side(GRASS, CUBE["dirt"]))
        return {"model": "cube", "tex": {"up": top, "down": tex_of("dirt"), "side": side}}
    for suffix, model in (("_stairs", "stairs"), ("_slab", "slab"), ("_fence_gate", "gate"), ("_fence", "fence"),
                          ("_wall", "wall"), ("_trapdoor", "trapdoor"), ("_door", "door")):
        if base.endswith(suffix):
            prefix = base[: -len(suffix)]
            m = material(prefix) or material(prefix.replace("_brick", "_bricks"))
            t = tex_of(m) if m else made("mc_" + base, lambda: unknown(base))
            if model == "door":
                rgb = PLANK.get(prefix, (120, 120, 124))
                make = door(rgb, 7)
                return {"door": True, "tex": {"top": made(f"mc_{base}_top", lambda: make(True)),
                                              "bottom": made(f"mc_{base}_bottom", lambda: make(False))}}
            return {"model": model, "tex": t}
    if base.endswith("_terracotta") and not base.endswith("glazed_terracotta") and base[:-11] in COLOURS:
        rgb = tuple(np.array(COLOURS[base[:-11]]) * 0.75 + np.array((150, 100, 80)) * 0.25)
        return {"model": "cube", "tex": made("mc_" + base, painted(rgb, 0.08))}
    if base.endswith("_concrete") and base[:-9] in COLOURS:
        return {"model": "cube", "tex": made("mc_" + base, painted(COLOURS[base[:-9]], 0.04))}
    if base in ("iron_chain", "lightning_rod", "end_rod"):
        t = made("mc_" + base, painted((64, 64, 68) if base == "iron_chain" else (170, 110, 80), 0.1))
        return {"model": "boxes", "tex": t, "boxes": [[7, 0, 7, 9, 16, 9, t]]}
    if base == "lily_pad":
        return {"model": "flat", "height": 1, "tex": made("mc_lily_pad", painted((60, 96, 40), 0.15))}
    if base == "campfire":
        t = made("mc_campfire", painted((60, 46, 34), 0.2))
        return {"model": "boxes", "tex": t, "boxes": [[1, 0, 3, 15, 4, 7, t], [1, 0, 9, 15, 4, 13, t], [3, 3, 1, 7, 7, 15, t], [9, 3, 1, 13, 7, 15, t]]}
    if base == "iron_bars":
        return {"model": "pane", "tex": made("mc_iron_bars", painted((70, 72, 74), 0.1))}
    if base.endswith("glass_pane"):
        return {"model": "pane", "tex": "window"}
    if base == "chest":
        return {"model": "boxes", "tex": "x", "rotate": "facing",
                "boxes": [[1, 0, 1, 15, 14, 15, made("mc_chest", ph("old_planks_02", (120, 84, 44), span=2, rot=True))]]}
    if base.endswith("_bed"):
        return {"model": "boxes", "tex": "x", "rotate": "facing",
                "boxes": [[0, 3, 0, 16, 9, 16, made("mc_" + base, painted(COLOURS.get(base[:-4], (150, 40, 40))))],
                          [0, 0, 0, 3, 3, 3, tex_of("oak_planks")], [13, 0, 13, 16, 3, 16, tex_of("oak_planks")]]}
    if base == "cauldron":
        t = made("mc_cauldron", painted((60, 60, 64), 0.1))
        return {"model": "boxes", "tex": t, "boxes": [[0, 3, 0, 16, 16, 2, t], [0, 3, 14, 16, 16, 16, t],
                                                       [0, 3, 2, 2, 16, 14, t], [14, 3, 2, 16, 16, 14, t],
                                                       [0, 3, 0, 16, 4, 16, t], [0, 0, 0, 4, 3, 4, t], [12, 0, 12, 16, 3, 16, t]]}
    if base == "flower_pot" or base.startswith("potted_"):
        t = made("mc_flower_pot", painted((130, 70, 50), 0.1))
        return {"model": "boxes", "tex": t, "boxes": [[5, 0, 5, 11, 6, 11, t]]}
    if base in ("lantern", "soul_lantern"):
        t = made("mc_lantern", painted((200, 150, 80), 0.1))
        return {"model": "boxes", "tex": t, "light": 15, "boxes": [[5, 0, 5, 11, 9, 11, t]]}
    if base in ("torch", "wall_torch"):
        t = made("mc_torch", painted((150, 110, 60), 0.1))
        return {"model": "boxes", "tex": t, "boxes": [[7, 0, 7, 9, 10, 9, t]]}
    if base == "lever" or base.endswith("_button") or base.endswith("_pressure_plate"):
        return {"model": "flat", "height": 1, "tex": tex_of("stone")}
    if base == "ladder":
        return {"model": "trapdoor_open", "tex": made("mc_ladder", ph("old_planks_02", (130, 100, 60), span=2))}
    if base in ("composter", "hopper", "anvil", "grindstone", "smoker", "furnace", "blast_furnace",
                "lectern", "loom", "stonecutter", "jukebox", "note_block", "beehive", "scaffolding"):
        return {"model": "cube", "tex": made("mc_" + base, painted((100, 92, 84), 0.15))}
    if base in ("pumpkin", "carved_pumpkin", "melon"):
        return {"model": "cube", "tex": made("mc_" + base, painted((170, 100, 30) if "pumpkin" in base else (100, 140, 40), 0.15))}
    return {"model": "cube", "tex": tex_of(base)}


COLOURS = {"white": (200, 200, 196), "light_gray": (140, 140, 136), "gray": (70, 74, 78), "black": (28, 28, 32),
           "brown": (100, 66, 40), "red": (140, 40, 36), "orange": (200, 110, 30), "yellow": (210, 180, 50),
           "lime": (110, 170, 40), "green": (80, 100, 40), "cyan": (40, 120, 130), "light_blue": (80, 150, 200),
           "blue": (50, 60, 140), "purple": (110, 50, 150), "magenta": (170, 70, 160), "pink": (210, 130, 160)}


def main():
    global TEX
    PT.CC0 = Path(sys.argv[1])
    TEX = Path(sys.argv[2])
    names = set()
    for dump in sys.argv[3:]:
        meta = json.load(open(dump if dump.endswith(".json") else dump + ".json"))
        for n in meta["palette"]:
            if n.startswith("minecraft:"):
                names.add(n.split("[")[0][10:])
    out = TEX / "vanilla_blocks.json"
    specs = json.load(open(out))["blocks"] if out.exists() else {}
    MADE.update(p.stem for p in TEX.glob("mc_*.png") if not p.stem.endswith(("_s", "_n", "_nrm_gl")))
    for n in sorted(names):
        if "minecraft:" + n not in specs:
            specs["minecraft:" + n] = spec(n)
    json.dump({"blocks": specs}, open(out, "w"), indent=1)
    print(f"{len(specs)} vanilla specs, {len(MADE)} textures")


if __name__ == "__main__":
    main()
