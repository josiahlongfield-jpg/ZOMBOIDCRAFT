#!/usr/bin/env python3
"""Turns painted turnaround sheets (art-brief/zombie-template.png layout) into walker skins.

Each input is a 2048x1216-style sheet with FRONT, BACK, LEFT SIDE and RIGHT SIDE views on a
magenta background, one per outfit (police.png, farmer.png, ...). The figure in each view is
found by its bounding box, cut into the model's boxes, scaled to 4 px per model unit and wrapped
onto the 64x64 UV layout at 256x256. Hidden faces (tops, bottoms, inner sides) are filled
from their neighbours. Then the HD painter adds the brims/visor/pack/bone parts, the damage
levels and the LabPBR maps, and makes the other skin tones.

    python3 tools/import_concept_skins.py IN_DIR OUT_DIR [--preview DIR]
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import gen_hd_skins as hd  # noqa: E402
import gen_zombie_skins as base  # noqa: E402

S = hd.S
N = hd.N
# Each view's place on the template, in model units: (x0, width), and the figure is 32 tall.
VIEWS = {"front": (2, 16), "back": (22, 16), "left": (42, 8), "right": (54, 8)}
# Outfits whose hat or hood is painted onto the head itself, so no bare skull shows through it.
COVERED_HEADS = {"hoodie", "police", "construction", "firefighter", "farmer", "soldier", "survivor", "security"}
TEMPLATE_W, TEMPLATE_H, UNIT = 64, 38, 32
# Parts inside a view, in units relative to the figure's top-left: (x, y, w, h)
FRONT_PARTS = {"head": (4, 0, 8, 8), "body": (4, 8, 8, 12), "arm_vl": (0, 8, 4, 12), "arm_vr": (12, 8, 4, 12),
               "leg_vl": (4, 20, 4, 12), "leg_vr": (8, 20, 4, 12)}
SIDE_PARTS = {"head": (0, 0, 8, 8), "arm": (2, 8, 4, 12), "leg": (2, 20, 4, 12)}


def is_magenta(rgb):
    r, g, b = rgb[..., 0].astype(int), rgb[..., 1].astype(int), rgb[..., 2].astype(int)
    return (r > 170) & (b > 170) & (g < 110) & (np.abs(r - b) < 80)


def find_views(img):
    """The bounding box of the figure in each view's column of the sheet."""
    rgb = np.asarray(img.convert("RGB"))
    h, w = rgb.shape[:2]
    sx, sy = w / (TEMPLATE_W * UNIT), h / (TEMPLATE_H * UNIT)
    figure = ~is_magenta(rgb)
    figure[: int(3.5 * UNIT * sy)] = False  # titles
    figure[int(36.5 * UNIT * sy):] = False  # footer text
    boxes = {}
    for name, (ux, uw) in VIEWS.items():
        x0, x1 = int((ux - 1) * UNIT * sx), int((ux + uw + 1) * UNIT * sx)
        cols = figure[:, x0:x1]
        ys, xs = np.where(cols)
        if len(xs) == 0:
            raise ValueError(f"no figure found in the {name} view")
        # trim stray pixels: keep rows/columns with a real amount of paint
        col_counts, row_counts = cols.sum(0), cols.sum(1)
        good_x = np.where(col_counts > col_counts.max() * 0.05)[0]
        good_y = np.where(row_counts > row_counts.max() * 0.05)[0]
        boxes[name] = (x0 + good_x[0], good_y[0], x0 + good_x[-1] + 1, good_y[-1] + 1)
        aspect = (good_x[-1] - good_x[0] + 1) / sx / ((good_y[-1] - good_y[0] + 1) / sy)
        if abs(aspect / (uw / 32) - 1) > 0.15:
            print(f"  warning: the {name} view is {aspect * 32:.1f} x 32 units, expected {uw} x 32; check the cut")
    return boxes


def cut(img, box, part, units_w, out_w, out_h):
    """Cut a part (in units within a figure box that is units_w x 32 units) and scale it."""
    bx0, by0, bx1, by1 = box
    ux, uy, uw, uh = part
    kx, ky = (bx1 - bx0) / units_w, (by1 - by0) / 32
    crop = img.crop((int(bx0 + ux * kx), int(by0 + uy * ky), int(bx0 + (ux + uw) * kx), int(by0 + (uy + uh) * ky)))
    return np.asarray(crop.convert("RGB").resize((out_w, out_h), Image.LANCZOS), dtype=np.float32)


def put(canvas, box, face, pix):
    x, y, w, h = hd.face_rect(box, face)
    canvas[y:y + h, x:x + w, :3] = pix
    canvas[y:y + h, x:x + w, 3] = 255


def fill_from_edges(top_row, bottom_row, h):
    """A face nobody painted, blended from two edge rows."""
    t = np.linspace(0, 1, h)[:, None, None]
    return top_row[None] * (1 - t) + bottom_row[None] * t


def project(sheet, outfit, skin_seed):
    """Build the 256x256 skin (RGBA float) for one sheet, on top of the base skin's extra parts."""
    boxes = find_views(sheet)
    canvas = np.zeros((N, N, 4), np.float32)
    # extra parts (visor, brim, pack, bone stubs) come from the outfit painter
    sk = base.make(outfit, "pale", skin_seed, clean=True)
    extra = np.asarray(sk.img.resize((N, N), Image.NEAREST), dtype=np.float32)
    for part in ("visor", "brim", "pack", "rbone", "lbone"):
        for face in ("top", "bottom", "front", "back", "left", "right"):
            x, y, w, h = hd.face_rect(part, face)
            canvas[y:y + h, x:x + w] = extra[y:y + h, x:x + w]
    U = S  # texture pixels per model unit

    def front(p, w, h):
        return cut(sheet, boxes["front"], FRONT_PARTS[p], 16, w * U, h * U)

    def back(p, w, h):
        return cut(sheet, boxes["back"], FRONT_PARTS[p], 16, w * U, h * U)

    def side(view, p, w, h):
        return cut(sheet, boxes[view], SIDE_PARTS[p], 8, w * U, h * U)

    # head
    put(canvas, "head", "front", front("head", 8, 8))
    put(canvas, "head", "back", back("head", 8, 8))
    put(canvas, "head", "right", side("right", "head", 8, 8))
    put(canvas, "head", "left", side("left", "head", 8, 8))
    head_back = back("head", 8, 8)
    hair = head_back[:4].reshape(-1, 3).mean(0)
    top = np.clip(hair * (0.95 + 0.1 * hd.smooth_noise(np.random.default_rng(skin_seed), 8 * U, 8 * U, 3))[..., None], 0, 255)
    put(canvas, "head", "top", top)
    head_front = front("head", 8, 8)
    put(canvas, "head", "bottom", np.repeat(head_front[-1:] * 0.7, 8 * U, 0))
    # body
    bf, bb = front("body", 8, 12), back("body", 8, 12)
    put(canvas, "body", "front", bf)
    put(canvas, "body", "back", bb)
    for face, fcol, bcol in (("right", bf[:, :1], bb[:, -1:]), ("left", bf[:, -1:], bb[:, :1])):
        # side of the torso, hidden by the arms: blend the front and back edge columns
        t = np.linspace(0, 1, 4 * U)[None, :, None]
        put(canvas, "body", face, fcol * (1 - t) + bcol * t if face == "left" else bcol * (1 - t) + fcol * t)
    put(canvas, "body", "top", fill_from_edges(bb[0, ::-1], bf[0], 4 * U))  # the back view is mirrored
    put(canvas, "body", "bottom", fill_from_edges(bf[-1], bb[-1, ::-1], 4 * U))
    # limbs: the zombie's right arm is on the viewer's left in FRONT and on the right in BACK
    for limb, fpart, bpart, view, outer, inner, model in (
            ("arm", "arm_vl", "arm_vr", "right", "right", "left", "rarm"),
            ("arm", "arm_vr", "arm_vl", "left", "left", "right", "larm"),
            ("leg", "leg_vl", "leg_vr", "right", "right", "left", "rleg"),
            ("leg", "leg_vr", "leg_vl", "left", "left", "right", "lleg")):
        f, b = front(fpart, 4, 12), back(bpart, 4, 12)
        o = side(view, limb, 4, 12)
        put(canvas, model, "front", f)
        put(canvas, model, "back", b)
        put(canvas, model, outer, o)
        put(canvas, model, inner, o[:, ::-1] * 0.9)
        put(canvas, model, "top", fill_from_edges(b[0, ::-1], f[0], 4 * U))
        put(canvas, model, "bottom", fill_from_edges(f[-1], b[-1, ::-1], 4 * U) * 0.85)
    return canvas, sk


def classify(canvas, outfit):
    """Guess each pixel's material for the shine map: skin near the face's colour, blood, metal, cloth."""
    rgb = canvas[..., :3]
    mat = np.where(canvas[..., 3] > 0, hd.CLOTH, 0).astype(np.uint8)
    x, y, w, h = hd.face_rect("head", "front")
    cheeks = np.concatenate([rgb[y + 16:y + 21, x + 3:x + 8].reshape(-1, 3), rgb[y + 16:y + 21, x + 24:x + 29].reshape(-1, 3)])
    ref = np.median(cheeks, 0)
    def chroma(c):
        return c / np.maximum(c.sum(-1, keepdims=True), 1)
    near = (np.abs(chroma(rgb) - chroma(ref)).sum(-1) < 0.06) & (rgb.sum(-1) > ref.sum() * 0.35) & (rgb.sum(-1) < ref.sum() * 1.4)
    limbs_and_head = np.zeros_like(near)
    for b in ("head", "rarm", "larm", "rleg", "lleg"):
        for f in ("front", "back", "left", "right", "top", "bottom"):
            fx, fy, fw, fh = hd.face_rect(b, f)
            limbs_and_head[fy:fy + fh, fx:fx + fw] = True
    mat[near & limbs_and_head & (mat > 0)] = hd.SKIN
    red = (rgb[..., 0] > rgb[..., 1] * 1.8) & (rgb[..., 0] > rgb[..., 2] * 1.8) & (rgb[..., 0] > 50)
    mat[red & (mat > 0)] = hd.BLOOD
    return mat, ref


def retone(canvas, mat, ref, skin_name):
    """Shift the skin pixels to another skin tone, keeping the painted shading."""
    target = np.array(base.SKINS[skin_name][0], np.float32)
    out = canvas.copy()
    gain = target / np.maximum(ref, 1)
    sel = mat == hd.SKIN
    out[sel, :3] = np.clip(out[sel, :3] * gain, 0, 255)
    return out


def build(sheet_path, outfit, out_dir, preview=None):
    sheet = Image.open(sheet_path)
    i = list(base.OUTFITS).index(outfit)
    canvas, sk = project(sheet, outfit, 1000 + i * 10)
    mat, ref = classify(canvas, outfit)
    n = 0
    for j, skin_name in enumerate(base.SKINS):
        toned = retone(canvas, mat, ref, skin_name)
        for level in range(len(hd.DAMAGE)):
            seed = 1000 + i * 10 + j
            painter = hd.HD(sk, outfit, seed * 7 + level)
            painter.c = toned.copy()
            painter.mat = mat.copy()
            painter._metal_from_colour()
            painter.height = np.full((N, N), 235.0, np.float32)
            lum = painter.c[..., :3] @ np.array((0.3, 0.59, 0.11), np.float32)
            blurred = np.asarray(Image.fromarray(lum.astype(np.uint8)).resize((N // 4, N // 4), Image.BILINEAR).resize((N, N), Image.BILINEAR), np.float32)
            painter.height += np.clip(lum - blurred, -40, 40) * 0.6  # painted detail becomes bump
            dr = hd.random.Random(seed * 13 + level)
            (b0, b1), (r0, r1) = hd.DAMAGE[level]
            extra_blood = max(0.0, dr.uniform(b0, b1) - 0.8)  # the art already has moderate blood
            rot = dr.uniform(r0, r1)
            if level >= 1:
                painter.rot(rot * (level / 3))
            if extra_blood > 0:
                painter.blood(extra_blood)
            if level == 3:
                painter.exposed_bone(skull=outfit not in COVERED_HEADS)
            painter.bevel_and_light()
            name = f"{outfit}_{skin_name}_{level}"
            painter.albedo().save(out_dir / f"{name}.png")
            painter.specular().save(out_dir / f"{name}_s.png")
            painter.normal().save(out_dir / f"{name}_n.png")
            if preview:
                painter.normal(preview=True).save(preview / f"{name}_nrm_gl.png")
            n += 1
    return n


def main():
    args = sys.argv[1:]
    preview = None
    if "--preview" in args:
        k = args.index("--preview")
        preview = Path(args[k + 1])
        del args[k:k + 2]
        preview.mkdir(parents=True, exist_ok=True)
    in_dir, out_dir = Path(args[0]), Path(args[1])
    out_dir.mkdir(parents=True, exist_ok=True)
    total = 0
    for sheet in sorted(in_dir.glob("*.png")):
        outfit = sheet.stem.lower()
        if outfit not in base.OUTFITS:
            print(f"skipping {sheet.name}: not an outfit name")
            continue
        total += build(sheet, outfit, out_dir, preview)
        print(f"{outfit}: done")
    print(f"wrote {total} skins to {out_dir}")


if __name__ == "__main__":
    main()
