#!/usr/bin/env python3
"""Map colours for every ZomboidCraft block: what the top of each block looks like from above.

Our blocks carry no Minecraft map colour, so the in-game map (client/map/CountyAtlas) reads this table instead:
one line per block, "name RRGGBB prop", where the colour is the average of the texture on the block's highest upward
face and prop is 1 when the model covers under half the block from above (pots, signs, lamps: things a map leaves out).

    python3 tools/map/map_colours.py    # rewrites src/main/resources/assets/zomboidcraft/map_colours.txt
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets")
NS = "zomboidcraft"
OUT = os.path.join(ROOT, NS, "map_colours.txt")
TOP_KEYS = ("up", "top", "end", "all", "texture", "particle")

models, textures = {}, {}


def path(ref, kind, ext):
    ns, name = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    return os.path.join(ROOT, ns, kind, name + ext)


def model(ref):
    """The model with its parents' textures and elements folded in; None outside our files."""
    if ref in models:
        return models[ref]
    p = path(ref, "models", ".json")
    out = None
    if os.path.exists(p):
        with open(p) as f:
            m = json.load(f)
        tex, elements = {}, None
        if "parent" in m:
            parent = model(m["parent"])
            if parent:
                tex.update(parent["textures"])
                elements = parent["elements"]
        tex.update(m.get("textures", {}))
        out = {"textures": tex, "elements": m.get("elements", elements)}
    models[ref] = out
    return out


def plain(v):
    return v.get("sprite") if isinstance(v, dict) else v


def resolve(tex, key, depth=0):
    v = plain(tex.get(key))
    while v and v.startswith("#") and depth < 8:
        v = plain(tex.get(v[1:]))
        depth += 1
    return v


def average(ref):
    if ref in textures:
        return textures[ref]
    p = path(ref, "textures", ".png")
    col = None
    if os.path.exists(p):
        im = Image.open(p).convert("RGBA")
        w = im.width
        im = im.crop((0, 0, w, min(w, im.height)))  # animated strips: the first frame
        px = [c for c in (im.get_flattened_data() if hasattr(im, "get_flattened_data") else im.getdata()) if c[3] > 128]
        if px:
            col = tuple(round(sum(c[i] for c in px) / len(px)) for i in range(3))
    textures[ref] = col
    return col


def first_model(state):
    if "variants" in state:
        v = next(iter(state["variants"].values()))
    elif "multipart" in state:
        v = state["multipart"][0]["apply"]
    else:
        return None
    if isinstance(v, list):
        v = v[0]
    return v.get("model")


def main():
    lines = []
    folder = os.path.join(ROOT, NS, "blockstates")
    for file in sorted(os.listdir(folder)):
        if not file.endswith(".json"):
            continue
        name = file[:-5]
        with open(os.path.join(folder, file)) as f:
            ref = first_model(json.load(f))
        m = model(ref) if ref else None
        if not m:
            continue
        tex, elements = m["textures"], m["elements"]
        top, cover = None, 1.0
        if elements:
            # the highest element with a face upward; how much of the block they all cover from above
            grid = [[False] * 16 for _ in range(16)]
            best = -1
            for e in elements:
                (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
                if x1 - x0 < 0.01 or z1 - z0 < 0.01:
                    continue
                for gx in range(max(0, int(x0)), min(16, int(-(-x1 // 1)))):
                    for gz in range(max(0, int(z0)), min(16, int(-(-z1 // 1)))):
                        grid[gx][gz] = True
                up = e.get("faces", {}).get("up")
                if up and y1 > best:
                    best = y1
                    top = resolve(tex, up["texture"][1:]) if up["texture"].startswith("#") else up["texture"]
            cover = sum(map(sum, grid)) / 256
        if top is None:
            for k in TOP_KEYS:
                top = resolve(tex, k)
                if top:
                    break
        col = average(top) if top else None
        if col is None:
            for k in tex:
                col = average(resolve(tex, k) or "")
                if col:
                    break
        if col is None:
            continue
        lines.append("%s %02X%02X%02X %d" % (name, col[0], col[1], col[2], 1 if cover < 0.5 else 0))
    with open(OUT, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(len(lines), "blocks ->", OUT, file=sys.stderr)


if __name__ == "__main__":
    main()
