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
TEMPLATE_W, TEMPLATE_H, FIGURE_TOP = 64, 38, 4
# Parts inside a view, in units relative to the figure's top-left: (x, y, w, h)
FRONT_PARTS = {"head": (4, 0, 8, 8), "body": (4, 8, 8, 12), "arm_vl": (0, 8, 4, 12), "arm_vr": (12, 8, 4, 12),
               "leg_vl": (4, 20, 4, 12), "leg_vr": (8, 20, 4, 12)}
SIDE_PARTS = {"head": (0, 0, 8, 8), "arm": (2, 8, 4, 12), "leg": (2, 20, 4, 12)}


def is_magenta(rgb):
    r, g, b = rgb[..., 0].astype(int), rgb[..., 1].astype(int), rgb[..., 2].astype(int)
    return (r > 170) & (b > 170) & (g < 110) & (np.abs(r - b) < 80)


def find_views(img):
    """Where each view sits on the sheet: the template's own rectangles, scaled to the image.

    Art AIs follow the template's positions closely but paint things outside the rectangles
    (hat brims), so a bounding box of the paint is less reliable than the template itself.
    """
    rgb = np.asarray(img.convert("RGB"))
    h, w = rgb.shape[:2]
    kx, ky = w / TEMPLATE_W, h / TEMPLATE_H
    figure = ~is_magenta(rgb)
    boxes = {}
    for name, (ux, uw) in VIEWS.items():
        box = (round(ux * kx), round(FIGURE_TOP * ky), round((ux + uw) * kx), round((FIGURE_TOP + 32) * ky))
        # the body and legs (or, in a side view, the arm and leg) should be fully painted
        x0, y0, x1, y1 = box
        c0, c1 = (4, 12) if uw == 16 else (2, 6)
        part = figure[round((FIGURE_TOP + 8) * ky):y1, x0 + round(c0 * kx):x0 + round(c1 * kx)]
        if part.mean() < 0.9:
            print(f"  warning: the {name} view is only {part.mean():.0%} painted where the body should be")
        boxes[name] = box
    return boxes


def facing(img, box):
    """Which way a side view faces, from where the white of the eye is: -1 left, 1 right, 0 unsure."""
    rgb = np.asarray(img.convert("RGB")).astype(int)
    x0, y0, x1, y1 = box
    head = rgb[y0:y0 + (y1 - y0) // 4, x0:x1]
    white = (head.min(-1) > 200) & (head.max(-1) - head.min(-1) < 40)
    xs = np.where(white)[1]
    if len(xs) < 20:
        return 0
    f = xs.mean() / head.shape[1]
    return -1 if f < 0.35 else 1 if f > 0.65 else 0


def side_views(img, boxes):
    """Map the two side views to the zombie's own left and right side.

    A view facing right shows the zombie's right side. Art AIs often swap the two, so the eyes
    decide. Returns {"left": (box, mirrored), "right": (box, mirrored)}.
    """
    a, b = facing(img, boxes["left"]), facing(img, boxes["right"])
    if a == 0 and b == 0:
        a, b = -1, 1  # trust the labels
    elif a == 0:
        a = -b
    elif b == 0:
        b = -a
    if a != b:
        return {"left": (boxes["left"] if a < 0 else boxes["right"], False),
                "right": (boxes["right"] if b > 0 else boxes["left"], False)}
    # both face the same way: one of them is a mirror image
    same = boxes["left"] if a < 0 else boxes["right"]
    other = boxes["right"] if a < 0 else boxes["left"]
    return ({"left": (same, False), "right": (other, True)} if a < 0
            else {"right": (same, False), "left": (other, True)})


INSET = 0.1  # units trimmed off each cut, to drop the anti-aliased edge against the magenta


def fill_magenta(pix):
    """Replace leftover background pixels with the average of their painted neighbours."""
    hole = is_magenta(pix) | (pix[..., 0] + pix[..., 2] > 2.2 * pix[..., 1] + 160)
    if hole.all():
        return pix
    pix = pix.copy()
    for _ in range(64):
        if not hole.any():
            break
        total = np.zeros_like(pix)
        count = np.zeros(hole.shape, np.float32)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            src = np.roll(pix, (dy, dx), (0, 1))
            ok = ~np.roll(hole, (dy, dx), (0, 1))
            total += src * ok[..., None]
            count += ok
        fill = hole & (count > 0)
        pix[fill] = total[fill] / count[fill][:, None]
        hole &= ~fill
    return pix


def cut(img, box, part, units_w, out_w, out_h):
    """Cut a part (in units within a figure box that is units_w x 32 units) and scale it."""
    bx0, by0, bx1, by1 = box
    ux, uy, uw, uh = part
    kx, ky = (bx1 - bx0) / units_w, (by1 - by0) / 32
    crop = img.crop((round(bx0 + (ux + INSET) * kx), round(by0 + (uy + INSET) * ky),
                     round(bx0 + (ux + uw - INSET) * kx), round(by0 + (uy + uh - INSET) * ky)))
    pix = fill_magenta(np.asarray(crop.convert("RGB"), dtype=np.float32))
    small = Image.fromarray(pix.clip(0, 255).astype(np.uint8)).resize((out_w, out_h), Image.LANCZOS)
    return np.asarray(small, dtype=np.float32)


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

    sides = side_views(sheet, boxes)

    def side(view, p, w, h):
        box, mirrored = sides[view]
        pix = cut(sheet, box, SIDE_PARTS[p], 8, w * U, h * U)
        return pix[:, ::-1] if mirrored else pix

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


def head_and_limbs():
    region = np.zeros((N, N), bool)
    for b in ("head", "rarm", "larm", "rleg", "lleg"):
        for f in ("front", "back", "left", "right", "top", "bottom"):
            fx, fy, fw, fh = hd.face_rect(b, f)
            region[fy:fy + fh, fx:fx + fw] = True
    return region


def chroma(c):
    return c / np.maximum(c.sum(-1, keepdims=True), 1)


def skin_weight(canvas):
    """How much each pixel looks like bare skin (0 to 1), and the typical skin colour.

    Skin is the tan, mid-saturation colour found on the head and limbs. Matching is soft so a
    retone doesn't leave speckles where shading or grime pushes a pixel over a hard line.
    """
    rgb = canvas[..., :3]
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    region = head_and_limbs() & (canvas[..., 3] > 0)
    candidate = region & (r > g) & (g > b) & ((r - b) / np.maximum(r, 1) > 0.18) & ((r - b) / np.maximum(r, 1) < 0.5) \
        & (r > 110) & (r < 240)
    if candidate.sum() < 50:
        return np.zeros((N, N), np.float32), np.array(base.SKINS["pale"][0], np.float32)
    ref = np.median(rgb[candidate], 0)
    d = np.abs(chroma(rgb) - chroma(ref)).sum(-1)
    lum, ref_lum = rgb.sum(-1), ref.sum()
    w = np.clip((0.13 - d) / 0.06, 0, 1) * (lum > ref_lum * 0.3) * (lum < ref_lum * 1.5) * region
    return w.astype(np.float32), ref


def classify(canvas, weight):
    """Guess each pixel's material for the shine map: skin, blood, cloth (metal is found later)."""
    rgb = canvas[..., :3]
    mat = np.where(canvas[..., 3] > 0, hd.CLOTH, 0).astype(np.uint8)
    mat[(weight > 0.5) & (mat > 0)] = hd.SKIN
    red = (rgb[..., 0] > rgb[..., 1] * 1.8) & (rgb[..., 0] > rgb[..., 2] * 1.8) & (rgb[..., 0] > 50)
    mat[red & (mat > 0)] = hd.BLOOD
    return mat


# The painted skin is kept for the first tone; the others shift it, keeping the shading.
TONES = {"pale": None, "olive": base.SKINS["olive"][0], "dark": base.SKINS["dark"][0]}


def retone(canvas, weight, ref, skin_name):
    target = TONES[skin_name]
    if target is None:
        return canvas.copy()
    out = canvas.copy()
    gain = np.array(target, np.float32) / np.maximum(ref, 1)
    out[..., :3] = np.clip(out[..., :3] * (1 + (gain - 1) * weight[..., None]), 0, 255)
    return out


def build(sheet_path, outfit, out_dir, preview=None):
    sheet = Image.open(sheet_path)
    i = list(base.OUTFITS).index(outfit)
    canvas, sk = project(sheet, outfit, 1000 + i * 10)
    weight, ref = skin_weight(canvas)
    mat = classify(canvas, weight)
    n = 0
    for j, skin_name in enumerate(base.SKINS):
        toned = retone(canvas, weight, ref, skin_name)
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
