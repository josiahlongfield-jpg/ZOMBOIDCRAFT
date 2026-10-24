#!/usr/bin/env python3
"""HD walker skins: 256x256 (4x), plus LabPBR shine (_s) and normal (_n) maps for Photon.

Starts from the 64x64 layout in gen_zombie_skins.py (colours, outfit details, hats) painted
without blood, scales it up 4x, then paints the detail that the extra pixels allow: bevelled
edges, cloth weave, folds, seams, mottled skin and veins, fingers, a full 32x32 face, frayed
hems, tears and fresh blood with drips. The game model keeps its 64x64 UV layout, so these
drop in as replacements for the 64x64 textures.

Run from the repo root:
    python3 tools/gen_hd_skins.py src/main/resources/assets/zomboidcraft/textures/entity/walker_hd
"""
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import gen_zombie_skins as base  # noqa: E402

S = 4
N = 64 * S
CLOTH, SKIN, HAIR, METAL, BLOOD, EYE, TEETH, MOUTH = 1, 2, 3, 4, 5, 6, 7, 8
LIMBS = ("rarm", "larm", "rleg", "lleg")

# LabPBR per material: smoothness, F0 (230+ = metal), porosity (0-64) or SSS (65-255)
PBR = {
    0: (0, 0, 0), CLOTH: (34, 10, 48), SKIN: (92, 10, 150), HAIR: (60, 10, 30), METAL: (196, 231, 0),
    BLOOD: (222, 9, 0), EYE: (214, 12, 120), TEETH: (150, 14, 20), MOUTH: (205, 9, 90),
}


def face_rect(box, face):
    fx, fy, w, h = base.faces(box)[face]
    return fx * S, fy * S, w * S, h * S


def smooth_noise(rng, w, h, cell):
    """Value noise: random grid blown up with bilinear filtering, in [-1, 1]."""
    gw, gh = max(2, w // cell + 2), max(2, h // cell + 2)
    g = Image.fromarray((rng.random((gh, gw)) * 255).astype(np.uint8))
    return np.asarray(g.resize((w + cell, h + cell), Image.BILINEAR), dtype=np.float32)[:h, :w] / 127.5 - 1


class HD:
    def __init__(self, sk, outfit, seed):
        self.outfit = outfit
        self.sk = sk
        self.r = random.Random(seed)
        self.np = np.random.default_rng(seed)
        img = sk.img.resize((N, N), Image.NEAREST)
        self.c = np.asarray(img, dtype=np.float32).copy()  # RGBA 0-255
        self.mat = np.zeros((N, N), np.uint8)
        for (x, y), m in sk.mat.items():
            self.mat[y * S:(y + 1) * S, x * S:(x + 1) * S] = m
        self.height = np.full((N, N), 235.0, np.float32)
        self.ao = np.full((N, N), 255.0, np.float32)
        self.skin = np.array(sk.skin, np.float32)
        self.shadow = np.array(sk.shadow, np.float32)
        self.wound = np.array(sk.wound, np.float32)
        self._metal_from_colour()

    # --- helpers ---
    def px(self, x, y, col, m=None, a=1.0):
        if 0 <= x < N and 0 <= y < N and self.c[y, x, 3] > 0:
            self.c[y, x, :3] = self.c[y, x, :3] * (1 - a) + np.asarray(col[:3], np.float32) * a
            if m is not None:
                self.mat[y, x] = m

    def mul(self, x, y, f):
        if 0 <= x < N and 0 <= y < N:
            self.c[y, x, :3] *= f

    def faces_of(self, boxes, sides=("front", "back", "left", "right", "top", "bottom")):
        for b in boxes:
            for f in sides:
                yield b, f, face_rect(b, f)

    def _metal_from_colour(self):
        rgb = self.c[..., :3]
        gold = (rgb[..., 0] > 185) & (rgb[..., 1] > 145) & (rgb[..., 2] < 110) & (rgb[..., 0] - rgb[..., 2] > 100)
        silver = (np.abs(rgb[..., 0] - rgb[..., 1]) < 8) & (np.abs(rgb[..., 1] - rgb[..., 2]) < 10) & (rgb[..., 0] > 180)
        badge = gold.copy()
        if self.outfit in ("security", "police", "construction"):
            badge |= silver & (self.mat == CLOTH) & np.isin(np.arange(N)[:, None] // S, range(0, 32))
        self.mat[badge & (self.mat == CLOTH)] = METAL

    # --- passes ---
    def bevel_and_light(self):
        """Soft bevel at every face edge and a top-down light falloff, like the concept renders."""
        for box in base.BOXES:
            for face in ("front", "back", "left", "right", "top", "bottom"):
                x0, y0, w, h = face_rect(box, face)
                yy, xx = np.mgrid[0:h, 0:w]
                edge = np.minimum.reduce([xx, yy, w - 1 - xx, h - 1 - yy]).astype(np.float32)
                bevel = np.clip(edge / 3.0, 0, 1)
                f = 0.86 + 0.14 * bevel
                if face in ("front", "back", "left", "right"):
                    f *= 1.05 - 0.1 * yy / max(1, h - 1)
                elif face == "bottom":
                    f *= 0.82
                region = self.c[y0:y0 + h, x0:x0 + w]
                region[..., :3] *= f[..., None]
                self.height[y0:y0 + h, x0:x0 + w] = np.minimum(self.height[y0:y0 + h, x0:x0 + w], 200 + 55 * bevel)
                self.ao[y0:y0 + h, x0:x0 + w] = np.minimum(self.ao[y0:y0 + h, x0:x0 + w], 200 + 55 * bevel)

    def materials(self):
        """Weave and fibre noise on cloth, mottled corpse skin, streaky hair."""
        fine = self.np.normal(0, 1, (N, N)).astype(np.float32)
        yy, xx = np.mgrid[0:N, 0:N]
        cloth = self.mat == CLOTH
        weave = np.where((xx + yy) % 4 == 0, 0.95, np.where((xx - yy) % 4 == 0, 1.03, 1.0))
        self.c[..., :3] = np.where(cloth[..., None], self.c[..., :3] * weave[..., None] + fine[..., None] * 3.5, self.c[..., :3])
        skin = self.mat == SKIN
        blotch = smooth_noise(self.np, N, N, 10)
        tint = np.stack([blotch * 9 + 4 * np.maximum(blotch, 0), blotch * 4, -blotch * 2 + 5 * np.maximum(-blotch, 0)], -1)
        self.c[..., :3] = np.where(skin[..., None], self.c[..., :3] + tint + fine[..., None] * 2.5, self.c[..., :3])
        hair = self.mat == HAIR
        streak = smooth_noise(self.np, N, N, 3)
        strand = np.where(xx % 2 == 0, 0.92, 1.06) * (1 + 0.12 * streak)
        self.c[..., :3] = np.where(hair[..., None], self.c[..., :3] * strand[..., None], self.c[..., :3])
        self.height[hair] -= np.where(xx[hair] % 2 == 0, 12, 0)

    def veins(self):
        r = self.r
        purple = (78, 70, 96)
        for box, face in [("rarm", "front"), ("larm", "front"), ("rarm", "left"), ("larm", "right"), ("head", "left"), ("head", "right")]:
            x0, y0, w, h = face_rect(box, face)
            for _ in range(r.randint(1, 3)):
                x, y = x0 + r.randrange(w), y0 + r.randrange(h)
                for _ in range(r.randint(5, 14)):
                    if self.mat[y, x] == SKIN:
                        self.px(x, y, purple, a=0.45)
                    x = min(x0 + w - 1, max(x0, x + r.choice((-1, 0, 1))))
                    y = min(y0 + h - 1, max(y0, y + r.choice((0, 1, 1))))

    def frayed_hems(self):
        """Where a sleeve or trouser leg ends, make the edge ragged and dark."""
        r = self.r
        for box, face, (x0, y0, w, h) in self.faces_of(LIMBS, ("front", "back", "left", "right")):
            m = self.mat[y0:y0 + h, x0:x0 + w]
            cols = []
            for x in range(w):
                col = m[:, x]
                idx = np.where((col[:-1] == CLOTH) & (col[1:] == SKIN))[0]
                cols.append(int(idx[0]) if len(idx) else None)
            if all(c is None for c in cols):
                continue
            off = 0
            for x, edge in enumerate(cols):
                if edge is None:
                    continue
                off = max(-3, min(3, off + r.choice((-1, 0, 0, 1))))
                ys, yd = y0 + edge, y0 + edge + off
                if off > 0:  # cloth hangs lower here
                    colour = self.c[ys, x0 + x, :3].copy()
                    for y in range(ys + 1, yd + 1):
                        self.c[y, x0 + x, :3] = colour
                        self.mat[y, x0 + x] = CLOTH
                elif off < 0:  # cloth torn back
                    skin_c = self.c[ys + 1, x0 + x, :3].copy()
                    for y in range(yd + 1, ys + 1):
                        self.c[y, x0 + x, :3] = skin_c
                        self.mat[y, x0 + x] = SKIN
                self.mul(x0 + x, max(yd, ys) if off >= 0 else yd, 0.7)

    def folds(self):
        """Creases at elbows, knees and the waist, with a highlight above each."""
        r = self.r
        spots = [("rarm", 22), ("larm", 22), ("rleg", 24), ("lleg", 24), ("rleg", 40), ("lleg", 40), ("body", 40)]
        for box, ry in spots:
            for face in ("front", "back", "left", "right"):
                x0, y0, w, h = face_rect(box, face)
                for _ in range(r.randint(1, 3)):
                    x, y = x0 + r.randrange(w), y0 + ry + r.randint(-4, 4)
                    length = r.randint(3, 7)
                    for i in range(length):
                        xx, yy = x + i, y + (i // 3) * r.choice((-1, 0, 1))
                        if x0 <= xx < x0 + w and y0 < yy < y0 + h - 1 and self.mat[yy, xx] == CLOTH:
                            self.mul(xx, yy, 0.82)
                            self.mul(xx, yy - 1, 1.08)
                            self.height[yy, xx] -= 30

    def seams(self):
        """Stitch lines down the outside of trousers and sleeves, and hems on shirts."""
        for box, face, (x0, y0, w, h) in self.faces_of(("rleg", "lleg"), ("left", "right")):
            x = x0 + w // 2
            for y in range(y0, y0 + h):
                if self.mat[y, x] == CLOTH:
                    self.mul(x, y, 0.85)
                    self.height[y, x] -= 20
                    if self.outfit in ("civilian", "construction", "farmer", "hoodie") and y % 3 == 0:
                        self.px(x + 1, y, (176, 120, 60), a=0.5)  # denim stitching
        for face in ("front", "back"):
            x0, y0, w, h = face_rect("body", face)
            for x in range(x0, x0 + w):
                if self.mat[y0 + h - 3, x] == CLOTH:
                    self.mul(x, y0 + h - 3, 0.85)
            if self.outfit in ("civilian", "jogger", "nurse", "patient"):  # crew/v neck on the front
                if face == "front":
                    for x in range(x0 + 11, x0 + 21):
                        depth = 2 + (3 - abs(x - (x0 + 15.5)) // 2)
                        for y in range(y0, y0 + int(max(1, depth))):
                            self.px(x, y, self.skin * 0.95, SKIN)
                        self.mul(x, y0 + int(max(1, depth)), 0.8)

    def hands(self):
        """Fingers: dark gaps on the bottom of each arm, knuckle shading, a grey palm."""
        for arm in ("rarm", "larm"):
            for face in ("front", "back", "left", "right"):
                x0, y0, w, h = face_rect(arm, face)
                for y in range(y0 + h - 9, y0 + h):
                    for x in range(x0, x0 + w):
                        if self.mat[y, x] != SKIN:
                            continue
                        if face in ("front", "back") and (x - x0) % 4 == 3:
                            self.mul(x, y, 0.72)
                            self.height[y, x] -= 40
                        if y == y0 + h - 9:
                            self.mul(x, y, 0.9)
            x0, y0, w, h = face_rect(arm, "bottom")
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    if (x - x0) % 4 == 3:
                        self.mul(x, y, 0.75)

    def shoes(self):
        for box, face, (x0, y0, w, h) in self.faces_of(("rleg", "lleg"), ("front", "back", "left", "right")):
            # the last base row is the darker sole when the outfit has shoes
            if self.mat[y0 + h - 1, x0] != CLOTH:
                continue
            for x in range(x0, x0 + w):
                for y in (y0 + h - 2, y0 + h - 1):
                    self.c[y, x, :3] = self.c[y, x, :3] * 0.6 + 18
                self.mul(x, y0 + h - 3, 0.8)
            if face == "front":
                for y in range(y0 + h - 7, y0 + h - 3, 2):
                    for x in range(x0 + 5, x0 + 11):
                        self.px(x, y, (200, 196, 186), a=0.35)  # laces

    def face(self, rot=0.5):
        """A full 32x32 zombie face: hollow sockets, milky eyes, rotten nose, torn open mouth."""
        r = self.r
        x0, y0, w, h = face_rect("head", "front")
        sk, sh, wd = self.skin, self.shadow, self.wound
        hair_rows = 4 if self.sk.style in ("short", "long") else 0
        noise = smooth_noise(self.np, w, h, 4)
        for y in range(hair_rows, h):
            for x in range(w):
                if self.mat[y0 + y, x0 + x] == HAIR:
                    continue
                c = sk * (1 + 0.07 * noise[y, x])
                cheek = min(1.0, ((x - 6) ** 2 / 20 + (y - 20) ** 2 / 14) ** 0.5) * min(1.0, ((x - 25) ** 2 / 20 + (y - 20) ** 2 / 14) ** 0.5)
                c = c * (0.82 + 0.18 * cheek)
                self.c[y0 + y, x0 + x, :3] = c
                self.mat[y0 + y, x0 + x] = SKIN
        if hair_rows:  # ragged fringe
            for x in range(w):
                for y in range(hair_rows, hair_rows + r.choice((0, 0, 1, 2, 3)) * (1 if x < 8 or x > 23 or r.random() < 0.4 else 0)):
                    self.px(x0 + x, y0 + y, self.sk.hair, HAIR)

        def ellipse(cx, cy, rx, ry, fn):
            for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
                for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
                    if d <= 1 and 0 <= x < w and 0 <= y < h:
                        fn(x0 + x, y0 + y, d)

        # brow ridge and sunken sockets
        for y in range(9, 12):
            for x in list(range(3, 14)) + list(range(18, 29)):
                self.mul(x0 + x, y0 + y, 0.88 if y < 11 else 0.8)
        for cx in (8.5, 22.5):
            ellipse(cx, 14.5, 5.2, 3.9, lambda x, y, d: self.px(x, y, sh * (0.35 + 0.35 * d), SKIN))
        # milky eyes, each looking a slightly different way
        eye = np.array((226, 222, 200), np.float32)
        lost = r.choice((8.5, 22.5)) if rot > 0.7 else None
        for cx, look in ((8.5, -0.7), (22.5, 0.5)):
            if cx == lost:  # the eye is gone: a wet black hole
                ellipse(cx, 14.5, 3.4, 2.4, lambda x, y, d: self.px(x, y, np.array((24, 6, 6)) + 60 * d * np.array((1, 0.2, 0.2)), MOUTH))
                for y in range(17, 24):
                    self.px(x0 + int(cx) + (y % 2), y0 + y, (100, 12, 10), BLOOD)
                continue
            ellipse(cx, 14.5, 3.1, 1.9, lambda x, y, d: self.px(x, y, eye * (1 - 0.18 * d), EYE))
            ix, iy = int(cx + look), 14
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                self.px(x0 + ix + dx, y0 + iy + dy, (164, 170, 172), EYE)  # clouded iris
            self.px(x0 + ix, y0 + iy, (120, 126, 132), EYE)
            for dx in (-3, 3):
                self.px(x0 + int(cx) + dx, y0 + 15, (150, 40, 40), EYE, 0.7)  # bloodshot corners
            for x in range(int(cx) - 2, int(cx) + 3):
                self.px(x0 + x, y0 + 17, wd, SKIN, 0.6)  # raw lower lid
        # nose: a dark bridge and rotten nostrils
        for y in range(12, 20):
            self.mul(x0 + 15, y0 + y, 0.85)
            self.mul(x0 + 16, y0 + y, 1.05)
        for x in (13, 14, 17, 18):
            self.px(x0 + x, y0 + 20, sh * 0.35, SKIN)
        for x in range(14, 18):
            self.px(x0 + x, y0 + 19, sh * 0.7, SKIN)
        if rot > 0.45:  # the nose has rotted away to a hole
            ellipse(15.5, 19.0, 2.4, 2.0, lambda x, y, d: self.px(x, y, np.array((40, 12, 10)) + 40 * d, MOUTH))
        # torn open mouth with teeth
        ellipse(15.5, 25.0, 7.4, 3.6, lambda x, y, d: self.px(x, y, np.array((44, 6, 6)) + 50 * (1 - d) * np.array((1, 0.1, 0.1)), MOUTH))
        ellipse(15.5, 25.0, 8.4, 4.5, lambda x, y, d: self.px(x, y, wd * (0.8 + 0.2 * d), SKIN, 0.5) if d > 0.75 else None)
        tooth = np.array((214, 200, 152), np.float32)
        for x in range(9, 23):
            if (x - 9) % 3 != 2 and r.random() > 0.12:
                for y in (22, 23):
                    self.px(x0 + x, y0 + y, tooth * (0.9 if y == 23 else 1.0), TEETH)
            if (x - 10) % 3 != 2 and r.random() > 0.25 and 10 <= x <= 21:
                self.px(x0 + x, y0 + 27, tooth * 0.85, TEETH)
        side = r.choice((1, -1))  # one cheek torn open to the teeth
        for i in range(5):
            x = 15.5 + side * (8 + i)
            for y in (24, 25, 26):
                self.px(x0 + int(x), y0 + y, wd * 0.6 if y != 25 else (60, 10, 10), MOUTH)
            if i % 2 == 0:
                self.px(x0 + int(x), y0 + 23, tooth, TEETH)
        # blood down the chin
        for x in range(10, 22):
            if r.random() < 0.7:
                length = r.randint(1, 6)
                for y in range(29, min(h, 29 + length)):
                    self.px(x0 + x, y0 + y, base.BLOOD[r.randrange(4)], BLOOD)
        # jaw shadow
        for x in range(w):
            self.mul(x0 + x, y0 + h - 1, 0.85)

    def splat(self, box, face, cx, cy, radius, drips=True):
        """Fresh blood: a lumpy blob from a few circles, darker dried edges, runs going down."""
        r = self.r
        x0, y0, w, h = face_rect(box, face)
        blobs = [(cx + r.uniform(-radius, radius) * 0.6, cy + r.uniform(-radius, radius) * 0.6, radius * r.uniform(0.4, 0.9))
                 for _ in range(r.randint(2, 5))]
        cells = []
        for y in range(int(cy - radius * 1.6), int(cy + radius * 1.6) + 1):
            for x in range(int(cx - radius * 1.6), int(cx + radius * 1.6) + 1):
                v = sum(br * br / max(0.3, (x - bx) ** 2 + (y - by) ** 2) for bx, by, br in blobs)
                if v > 1 and 0 <= x < w and 0 <= y < h:
                    cells.append((x, y, v))
        for x, y, v in cells:
            wet = min(1.0, (v - 1) / 2.5)
            col = np.array((76, 16, 10)) * (1 - wet) + np.array((138, 14, 12)) * wet
            if r.random() < 0.06 * wet:
                col = np.array((172, 34, 28))  # wet highlight
            self.px(x0 + x, y0 + y, col, BLOOD, 0.95)
        if drips and cells:
            bottom = {}
            for x, y, _ in cells:
                bottom[x] = max(bottom.get(x, -1), y)
            for x, yb in bottom.items():
                if r.random() < 0.3:
                    end = min(h, yb + r.randint(3, 16))
                    for y in range(yb + 1, end):
                        self.px(x0 + x, y0 + y, (112, 12, 10), BLOOD, 0.9)
                    if end < h:
                        self.px(x0 + x, y0 + end, (90, 10, 8), BLOOD)  # the drop at the end

    def blood(self, amount=1.0):
        r = self.r
        # from the mouth down the chest
        x0, y0, w, h = face_rect("body", "front")
        for x in range(10, 22):
            if r.random() < 0.45 + 0.25 * amount:
                for y in range(0, int(r.randint(4, 26) * min(1.4, amount))):
                    self.px(x0 + x, y0 + y, base.BLOOD[r.randrange(4)], BLOOD, 0.9)
        for _ in range(1 + int(amount * 1.5)):  # a soaked patch on the chest, made of smaller lumps
            self.splat("body", "front", r.uniform(8, 24), r.uniform(6, 20), r.uniform(1.8, 2.2 + 2 * amount))
        # hands soaked to the wrist with a ragged line
        for arm in ("rarm", "larm"):
            for face in ("front", "back", "left", "right"):
                x0, y0, w, h = face_rect(arm, face)
                lo, hi = int(4 + 6 * amount), int(8 + 10 * amount)
                line = r.randint(lo, hi)
                for x in range(w):
                    line = max(lo - 2, min(hi + 4, line + r.choice((-2, -1, 0, 1, 2))))
                    for y in range(h - line, h):
                        dried = (y - (h - line)) < 2
                        self.px(x0 + x, y0 + y, (80, 18, 10) if dried else base.BLOOD[r.randrange(4)], BLOOD, 0.95)
            x0, y0, w, h = face_rect(arm, "bottom")
            self.c[y0:y0 + h, x0:x0 + w, :3] = np.array((96, 12, 10)) + self.np.normal(0, 6, (h, w, 1))
            self.mat[y0:y0 + h, x0:x0 + w] = BLOOD
        # splatters over the clothes
        for _ in range(int(r.randint(5, 9) * amount)):
            box = r.choice(["body", "body", "rarm", "larm", "rleg", "lleg", "head"])
            face = r.choice(["back", "left", "right"] if box == "head" else ["front", "front", "back", "left", "right"])
            _, _, w, h = face_rect(box, face)
            self.splat(box, face, r.uniform(0, w), r.uniform(0, h * 0.8), r.uniform(1.5, 4.5))

    def rot(self, amount):
        """Decay: discoloured patches, open sores, bite marks, bare bone, hair falling out."""
        r = self.r
        if amount <= 0:
            return
        skin = self.mat == SKIN
        patches = smooth_noise(self.np, N, N, 7)
        bruise = smooth_noise(self.np, N, N, 12)
        decay = np.clip((patches - (0.85 - 0.9 * amount)) * 2.5, 0, 0.75) * skin
        rotten = np.array((86, 96, 58), np.float32)  # green-grey
        dark = np.array((58, 44, 52), np.float32)  # purple-black
        target = np.where((bruise > 0.25)[..., None], dark, rotten)
        self.c[..., :3] = self.c[..., :3] * (1 - decay[..., None]) + target * decay[..., None]

        def blob(box, face, cx, cy, rx, ry, inner, rim, m):
            x0, y0, w, h = face_rect(box, face)
            for y in range(int(cy - ry - 2), int(cy + ry + 3)):
                for x in range(int(cx - rx - 2), int(cx + rx + 3)):
                    if not (0 <= x < w and 0 <= y < h):
                        continue
                    X, Y = x0 + x, y0 + y
                    if self.mat[Y, X] not in (SKIN, MOUTH, BLOOD):
                        continue
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + r.uniform(-0.25, 0.25)
                    if d < 0.55:
                        self.px(X, Y, inner * r.uniform(0.85, 1.1), m)
                        self.height[Y, X] -= 45
                    elif d < 1.0:
                        self.px(X, Y, rim, m, 0.85)
                    elif d < 1.6:
                        self.mul(X, Y, 0.78)

        flesh, pus, bone = np.array((150, 64, 58)), np.array((150, 138, 78)), np.array((214, 204, 174))
        skin_faces = [("rarm", f) for f in ("front", "back", "left", "right")] + [("larm", f) for f in ("front", "back", "left", "right")] \
            + [("head", f) for f in ("left", "right", "back", "front")] + [("rleg", "front"), ("lleg", "front")]
        for _ in range(int(2 + 7 * amount)):  # open sores
            box, face = r.choice(skin_faces)
            _, _, w, h = face_rect(box, face)
            rim = pus if r.random() < 0.35 else np.array((90, 24, 22))
            blob(box, face, r.uniform(3, w - 3), r.uniform(3, h - 3), r.uniform(1.2, 2.5 + 2 * amount), r.uniform(1.0, 2.0 + amount), flesh, rim, MOUTH)
        if amount > 0.3:  # bite marks: two arcs of tooth holes round a bloody middle
            for _ in range(r.randint(1, 2)):
                box, face = r.choice([("rarm", "front"), ("larm", "front"), ("rarm", "left"), ("larm", "right"), ("head", "left"), ("head", "right")])
                x0, y0, w, h = face_rect(box, face)
                cx, cy = r.uniform(5, w - 5), r.uniform(5, h - 10)
                blob(box, face, cx, cy, 2.2, 1.5, np.array((120, 20, 18)), np.array((90, 20, 16)), BLOOD)
                for i in range(7):
                    a = 3.14159 * i / 6
                    for sy in (-1, 1):
                        X, Y = x0 + int(cx + 4 * np.cos(a)), y0 + int(cy + sy * 3 * np.sin(a))
                        if self.mat[Y, X] in (SKIN, BLOOD, MOUTH):
                            self.px(X, Y, (60, 10, 8), BLOOD)
        if amount > 0.65:  # flesh gone down to the bone on a forearm
            box, face = r.choice([("rarm", "front"), ("larm", "front"), ("rarm", "back"), ("larm", "back")])
            x0, y0, w, h = face_rect(box, face)
            cy = r.uniform(h * 0.5, h * 0.7)
            blob(box, face, w / 2, cy, 5, 4, flesh * 0.8, np.array((100, 26, 22)), MOUTH)
            for y in range(int(cy - 3), int(cy + 3)):
                for x in range(w // 2 - 2, w // 2 + 2):
                    self.px(x0 + x, y0 + y, bone * r.uniform(0.85, 1.0), TEETH)
        if amount > 0.5:  # hair coming out in patches
            for box, face in (("head", "top"), ("head", "back"), ("head", "left"), ("head", "right")):
                x0, y0, w, h = face_rect(box, face)
                hole = smooth_noise(self.np, w, h, 6)
                for y in range(h):
                    for x in range(w):
                        if hole[y, x] > 1.1 - amount and self.mat[y0 + y, x0 + x] == HAIR:
                            self.px(x0 + x, y0 + y, self.shadow * 0.95, SKIN)

    def tears(self):
        """Holes in the clothes showing grey skin or a wound underneath."""
        r = self.r
        for _ in range(r.randint(3, 6)):
            box = r.choice(["body", "rarm", "larm", "rleg", "lleg"])
            face = r.choice(["front", "back", "left", "right"])
            x0, y0, w, h = face_rect(box, face)
            cx, cy = r.uniform(3, w - 3), r.uniform(3, h * 0.75)
            rx, ry = r.uniform(1.5, 3.5), r.uniform(1, 2.5)
            inside = self.wound if r.random() < 0.4 else self.skin * 0.9
            for y in range(int(cy - ry - 1), int(cy + ry + 2)):
                for x in range(int(cx - rx - 1), int(cx + rx + 2)):
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + r.uniform(-0.3, 0.3)
                    X, Y = x0 + x, y0 + y
                    if not (0 <= x < w and 0 <= y < h) or self.mat[Y, X] != CLOTH:
                        continue
                    if d < 1:
                        self.px(X, Y, inside, SKIN)
                        self.height[Y, X] -= 50
                    elif d < 1.7:
                        self.mul(X, Y, 0.7)  # frayed edge

    def dirt(self):
        grime = smooth_noise(self.np, N, N, 6)
        yy = np.mgrid[0:N, 0:N][0]
        legs = np.zeros((N, N), bool)
        for b in ("rleg", "lleg"):
            for f in ("front", "back", "left", "right"):
                x0, y0, w, h = face_rect(b, f)
                legs[y0 + h // 2:y0 + h, x0:x0 + w] = True
        amt = np.clip(grime * 0.5 + 0.1, 0, 0.45) * (self.mat == CLOTH)
        amt = np.where(legs, amt + 0.15, amt * 0.6)
        mud = np.array((74, 60, 42), np.float32)
        self.c[..., :3] = self.c[..., :3] * (1 - amt[..., None]) + mud * amt[..., None]

    # --- output ---
    def albedo(self):
        out = self.c.copy()
        out[..., :3] = np.clip(out[..., :3], 0, 255)
        return Image.fromarray(out.astype(np.uint8), "RGBA")

    def specular(self):
        lut = np.zeros((256, 3), np.float32)
        for m, v in PBR.items():
            lut[m] = v
        s = lut[self.mat]
        # blood is glossier where it's fresh (red), duller where it's dried (dark)
        fresh = np.clip((self.c[..., 0] - 70) / 70, 0, 1)
        s[..., 0] = np.where(self.mat == BLOOD, 130 + 95 * fresh, s[..., 0])
        a = np.full((N, N, 1), 255, np.float32)
        out = np.concatenate([s, a], -1)
        out[self.c[..., 3] == 0] = 0
        return Image.fromarray(out.astype(np.uint8), "RGBA")

    def normal(self, preview=False):
        """LabPBR normal map (DirectX, Y down) from the height field, face by face."""
        nx = np.zeros((N, N), np.float32)
        ny = np.zeros((N, N), np.float32)
        hgt = self.height
        for box in base.BOXES:
            for face in ("front", "back", "left", "right", "top", "bottom"):
                x0, y0, w, h = face_rect(box, face)
                p = np.pad(hgt[y0:y0 + h, x0:x0 + w], 1, mode="edge")
                nx[y0:y0 + h, x0:x0 + w] = (p[1:-1, :-2] - p[1:-1, 2:]) / 255 * 2.5
                ny[y0:y0 + h, x0:x0 + w] = (p[:-2, 1:-1] - p[2:, 1:-1]) / 255 * 2.5
        nz = 1 / np.sqrt(1 + nx ** 2 + ny ** 2)
        nx, ny = nx * nz, ny * nz
        if preview:  # OpenGL-style RGB for three.js
            rgb = np.stack([nx * 127.5 + 127.5, ny * 127.5 + 127.5, nz * 127.5 + 127.5], -1)
            return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB")
        out = np.stack([nx * 127.5 + 127.5, -ny * 127.5 + 127.5, self.ao, np.clip(hgt, 1, 255)], -1)
        return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


# Damage levels: (blood range, rot range). Each zombie picks one; the exact amounts are random.
DAMAGE = [((0.3, 0.7), (0.1, 0.35)), ((0.8, 1.2), (0.4, 0.6)), ((1.3, 1.8), (0.7, 1.0))]


def make_hd(outfit, skin_name, seed, level=1):
    sk = base.make(outfit, skin_name, seed, clean=True)
    hd = HD(sk, outfit, seed * 7 + level)
    dr = random.Random(seed * 13 + level)
    (b0, b1), (r0, r1) = DAMAGE[level]
    blood, rot = dr.uniform(b0, b1), dr.uniform(r0, r1)
    if outfit in ("patient", "nurse", "doctor"):
        blood *= 1.2
    hd.materials()
    hd.veins()
    hd.frayed_hems()
    hd.folds()
    hd.seams()
    hd.hands()
    hd.shoes()
    hd.tears()
    hd.dirt()
    hd.face(rot)
    hd.rot(rot)
    hd.blood(blood)
    hd.bevel_and_light()
    return hd


def main():
    """Args: OUT_DIR [--preview DIR] [outfit ...]. --preview also writes OpenGL normal maps for three.js."""
    args = sys.argv[1:]
    out = Path(args.pop(0) if args else "build/hd")
    preview = None
    if "--preview" in args:
        k = args.index("--preview")
        preview = Path(args[k + 1])
        del args[k:k + 2]
        preview.mkdir(parents=True, exist_ok=True)
    only = args
    out.mkdir(parents=True, exist_ok=True)
    n = 0
    for i, outfit in enumerate(base.OUTFITS):
        if only and outfit not in only:
            continue
        for j, skin_name in enumerate(base.SKINS):
            for level in range(len(DAMAGE)):
                hd = make_hd(outfit, skin_name, 1000 + i * 10 + j, level)
                name = f"{outfit}_{skin_name}_{level}"
                hd.albedo().save(out / f"{name}.png")
                hd.specular().save(out / f"{name}_s.png")
                hd.normal().save(out / f"{name}_n.png")
                if preview:
                    hd.normal(preview=True).save(preview / f"{name}_nrm_gl.png")
                n += 1
    print(f"wrote {n} HD skins to {out}")


if __name__ == "__main__":
    main()
