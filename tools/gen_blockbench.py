#!/usr/bin/env python3
"""Writes one Blockbench project (.bbmodel) per walker skin, with the texture embedded.

Open a file at https://web.blockbench.net, paint on the model, then File > Export texture
(or right-click the texture > Save As) and drop the PNG into textures/entity/walker_hd.
The _s and _n shine and bump maps are not in the project; repaint those separately if needed.
The model matches WalkerModel: player-layout limbs, the hat layer, and the visor, brim and
backpack parts the outfit uses. Run from the repo root after gen_zombie_skins.py:

    python3 tools/gen_blockbench.py OUT_DIR
"""
import base64
import json
import sys
import uuid
from pathlib import Path

from PIL import Image

SKINS_DIR = Path("src/main/resources/assets/zomboidcraft/textures/entity/walker_hd")
LEVELS = ("0", "1", "2")  # damage levels: light, medium, heavy
PARTS = json.loads(Path("tools/mockup/walker_parts.json").read_text())


def uid():
    return str(uuid.uuid4())


def face_uvs(u, v, w, h, d):
    """Box-UV rectangles in Blockbench face names. North is the model's front."""
    return {
        "north": [u + d, v + d, u + d + w, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "east": [u, v + d, u + d, v + d + h],
        "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "up": [u + d + w, v + d, u + d, v],
        "down": [u + d + 2 * w, v, u + d + w, v + d],
    }


def cube(name, frm, to, origin, uv, inflate=0.0, box_uv=True, size=None):
    u, v = uv
    w, h, d = size or (to[0] - frm[0], to[1] - frm[1], to[2] - frm[2])
    return {
        "name": name, "box_uv": box_uv, "rescale": False, "locked": False,
        "from": frm, "to": to, "autouv": 0, "color": 0, "inflate": inflate,
        "origin": origin, "uv_offset": [u, v],
        "faces": {f: {"uv": r, "texture": 0} for f, r in face_uvs(u, v, int(w), max(1, int(h)), int(d)).items()},
        "type": "cube", "uuid": uid(),
    }


def group(name, origin, children):
    return {"name": name, "origin": origin, "rotation": [0, 0, 0], "uuid": uid(), "export": True,
            "isOpen": True, "visibility": True, "children": children}


def model(outfit, skin, level):
    parts = PARTS[outfit]
    els, outliner = [], []

    def add(gname, origin, cubes):
        els.extend(cubes)
        outliner.append(group(gname, origin, [c["uuid"] for c in cubes]))

    head = [cube("head", [-4, 24, -4], [4, 32, 4], [0, 24, 0], (0, 0)),
            cube("hat", [-4, 24, -4], [4, 32, 4], [0, 24, 0], (32, 0), inflate=0.5)]
    if parts.get("visor"):
        head.append(cube("visor", [-4, 29.5, -8], [4, 30, -4], [0, 24, 0], (0, 32), size=(8, 1, 4)))
    if parts.get("brim"):
        # The game scales a 10-wide brim; here the cube is drawn at its real width.
        half = 5 * parts["brim"]
        b = cube("brim", [-half, 29, -half], [half, 29.5, half], [0, 24, 0], (0, 32), box_uv=False, size=(10, 1, 10))
        head.append(b)
    add("head", [0, 24, 0], head)
    body = [cube("body", [-4, 12, -2], [4, 24, 2], [0, 24, 0], (16, 16))]
    if parts.get("pack"):
        body.append(cube("backpack", [-4, 15, 2], [4, 23, 5], [0, 24, 0], (42, 37)))
    add("body", [0, 24, 0], body)
    add("right_arm", [5, 22, 0], [cube("right_arm", [4, 12, -2], [8, 24, 2], [5, 22, 0], (40, 16))])
    add("left_arm", [-5, 22, 0], [cube("left_arm", [-8, 12, -2], [-4, 24, 2], [-5, 22, 0], (32, 48))])
    add("right_leg", [1.9, 12, 0], [cube("right_leg", [-0.1, 0, -2], [3.9, 12, 2], [1.9, 12, 0], (0, 16))])
    add("left_leg", [-1.9, 12, 0], [cube("left_leg", [-3.9, 0, -2], [0.1, 12, 2], [-1.9, 12, 0], (16, 48))])

    name = f"{outfit}_{skin}_{level}"
    png = (SKINS_DIR / f"{name}.png").read_bytes()
    size = Image.open(SKINS_DIR / f"{name}.png").size[0]
    return {
        "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
        "name": f"walker_{name}", "model_identifier": "walker", "modded_entity_version": "Fabric 1.17+",
        "visible_box": [1, 2, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
        "resolution": {"width": 64, "height": 64},
        "elements": els, "outliner": outliner,
        "textures": [{
            "path": "", "name": f"{name}.png", "folder": "", "namespace": "", "id": "0",
            "width": size, "height": size, "uv_width": 64, "uv_height": 64, "particle": False,
            "render_mode": "default", "render_sides": "auto", "visible": True, "mode": "bitmap",
            "saved": False, "uuid": uid(), "source": "data:image/png;base64," + base64.b64encode(png).decode(),
        }],
    }


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "build/blockbench")
    out.mkdir(parents=True, exist_ok=True)
    n = 0
    for outfit in PARTS:
        for skin in ("pale", "olive", "dark"):
            for level in LEVELS:
                (out / f"walker_{outfit}_{skin}_{level}.bbmodel").write_text(json.dumps(model(outfit, skin, level)))
                n += 1
    print(f"wrote {n} Blockbench files to {out}")


if __name__ == "__main__":
    main()
