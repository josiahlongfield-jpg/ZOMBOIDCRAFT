#!/usr/bin/env python3
"""Generates ZomboidCraft walker skins (64x64, Minecraft humanoid layout) and a flat preview sheet.

Every skin is a zombified body (skin tone, hair) wearing one outfit, with dirt, tears and blood
painted on. The look follows Josiah's 3D concept sheet (reference/zombie-outfits-3d-concepts.png):
white eyes, an open bloody mouth, heavy blood on the chest and hands, and outfits that read from
a distance. Hat brims, cap visors and backpacks are extra model parts (see WalkerModel), painted
into the unused overlay rows of the texture.

Run from the repo root:  python3 tools/gen_zombie_skins.py [preview.png]
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path("src/main/resources/assets/zomboidcraft/textures/entity/walker")
PARTS_JSON = Path("tools/mockup/walker_parts.json")  # for the 3D preview; WalkerModel mirrors it

# Zombified skin tones: pale, olive, dark. Each is (base, shadow, wound).
SKINS = {
    "pale": ((158, 168, 140), (118, 128, 104), (120, 40, 38)),
    "olive": ((140, 132, 100), (104, 96, 72), (112, 34, 30)),
    "dark": ((96, 84, 70), (68, 58, 48), (92, 26, 24)),
}
HAIR = [(28, 24, 22), (70, 48, 30), (96, 66, 38), (150, 120, 76), (130, 126, 120), (110, 50, 26)]
TEES = [(214, 212, 204), (150, 40, 40), (44, 70, 130), (52, 100, 60), (110, 110, 112), (180, 150, 60)]
BLOOD = [(128, 14, 12), (104, 12, 10), (82, 10, 8), (140, 24, 18)]

BOXES = {  # name: (u, v, w, h, d)
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "rarm": (40, 16, 4, 12, 4), "larm": (32, 48, 4, 12, 4),
    "rleg": (0, 16, 4, 12, 4), "lleg": (16, 48, 4, 12, 4),
    # Extra parts (WalkerModel), in the overlay rows the zombie model leaves empty.
    # Only one hat part shows per outfit, so the visor and brim share their corner.
    "visor": (0, 32, 8, 1, 4),
    "brim": (0, 32, 10, 1, 10),
    "pack": (42, 37, 8, 8, 3),
    # Bone sticking out of an arm stump, for zombies missing a forearm.
    "rbone": (56, 16, 2, 6, 2), "lbone": (56, 24, 2, 6, 2),
}


def faces(box):
    u, v, w, h, d = BOXES[box]
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def jitter(c, rng, amount=4):
    return tuple(max(0, min(255, ch + rng.randint(-amount, amount))) for ch in c[:3]) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(ch * f))) for ch in c[:3]) + (255,)


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)


# ---------- materials: f(x, y, rng) -> colour, x/y are face-local ----------

def solid(c):
    return lambda x, y, rng: c


def denim(c):
    return lambda x, y, rng: shade(c, 1.08 if (x + y // 3) % 3 == 0 else 0.96)


def plaid(a, b):
    return lambda x, y, rng: a if (x % 4 < 2) ^ (y % 4 < 2) else (b if (x + y) % 2 else mix(a, b, 0.5))


def woven(c):
    return lambda x, y, rng: shade(c, 1.1 if (x + y) % 2 else 0.88)


def camo(seed):
    """Woodland camo: big green and brown blobs with black and khaki streaks."""
    cols = [(86, 104, 52), (112, 86, 52), (40, 40, 30), (150, 140, 96)]
    r = random.Random(seed)
    grid = {}
    for _ in range(90):
        cx, cy, c = r.randint(0, 20), r.randint(0, 20), r.choice(cols[1:])
        for dx in range(-1, 2):
            for dy in range(-1, 1 + (c == cols[1])):
                if r.random() < 0.8:
                    grid[(cx + dx, cy + dy)] = c
    return lambda x, y, rng, _g=grid: _g.get((x % 20, y % 20), cols[0])


def pattern_dots(a, b):
    return lambda x, y, rng: b if (x * 3 + y * 5) % 7 == 0 else a


# ---------- the painter ----------

class Skin:
    def __init__(self, skin_name, seed):
        self.rng = random.Random(seed)
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.skin, self.shadow, self.wound = SKINS[skin_name]
        self.parts = {}  # extra model parts this outfit shows, e.g. {"brim": 1.3}
        self.mat = {}  # (x, y) -> 1 cloth, 2 skin, 3 hair; read by gen_hd_skins.py
        self.hair_c = None

    def put(self, x, y, c, amount=4):
        self.img.putpixel((x, y), jitter(c, self.rng, amount))

    def paint_box(self, box, fn):
        """fn(face, x, y, w, h) -> colour or None for each pixel of each face of the box."""
        for face, (fx, fy, w, h) in faces(box).items():
            for y in range(h):
                for x in range(w):
                    c = fn(face, x, y, w, h)
                    if c is not None:
                        is_skin = box in ("head", "rarm", "larm", "rleg", "lleg") and (c is self.skin or c is self.shadow)
                        self.mat[(fx + x, fy + y)] = 3 if c is self.hair_c else 2 if is_skin else 1
                        side = face in ("right", "left", "back")
                        self.put(fx + x, fy + y, shade(c, 0.9 if side else 1.0))

    # --- body parts ---
    def head(self, hair_style, hair):
        sk, sh, wd = self.skin, self.shadow, self.wound
        self.hair_c = hair
        r = self.rng

        def fn(face, x, y, w, h):
            if face == "top":
                return hair if hair_style != "bald" else sh
            if face == "bottom":
                return sh
            if hair_style in ("short", "long") and y == 0:
                return hair
            if hair_style == "short" and face == "front" and y == 1 and x in (0, 1, 6, 7):
                return hair
            if hair_style == "long" and face in ("back", "left", "right") and y < (7 if face == "back" else 5):
                if face == "back" or (face == "right" and x <= 4) or (face == "left" and x >= 3):
                    return hair
            if hair_style == "short" and face == "back" and y < 3:
                return hair
            if hair_style == "short" and face in ("left", "right") and y < 2:
                return hair
            if hair_style == "long" and face == "front" and (x in (0, 7) and y < 5 or y == 1 and x in (1, 6)):
                return hair
            if face == "front":
                return self.face_pixel(x, y)
            if face in ("left", "right") and y in (3, 4) and x in (3, 4):
                return sh  # ear
            return sk
        self.paint_box("head", fn)
        # a bald patch, a gash or a missing scalp on some heads
        if r.random() < 0.35:
            fx, fy, _, _ = faces("head")["top"]
            for _ in range(r.randint(2, 5)):
                self.put(fx + r.randint(0, 7), fy + r.randint(0, 7), wd)

    def face_pixel(self, x, y):
        """White staring eyes in dark sockets, and a gaping mouth with blood down the chin."""
        sk, sh = self.skin, self.shadow
        socket = shade(sh, 0.5)
        eye = (236, 232, 214)
        if y == 2 and x in (1, 2, 5, 6):
            return shade(sh, 0.75)  # brow
        if y == 3 and x in (1, 6):
            return socket
        if y == 3 and x in (2, 5):
            return eye
        if y == 4 and x in (1, 2, 5, 6):
            return shade(sh, 0.8)
        if y == 4 and x in (3, 4):
            return shade(sh, 0.85)  # nose
        if y == 5 and 2 <= x <= 5:
            return (214, 204, 168) if x in (2, 4) else (40, 8, 8)  # top teeth
        if y == 6 and 2 <= x <= 5:
            return (70, 6, 6) if x in (2, 5) else (110, 10, 10)  # mouth
        if y == 7 and 2 <= x <= 5:
            return (126, 14, 12) if x in (3, 4) else ((96, 12, 10) if x == 2 else sk)
        if y == 6 and x in (1, 6):
            return shade(sh, 0.8)
        return sk

    def hat(self, kind, c=None):
        """Hats live on the outer head layer; brims and visors are separate model parts."""
        if not kind:
            return
        gold = (214, 176, 70)

        def fn(face, x, y, w, h):
            if face == "bottom":
                return None
            if kind == "hood":
                if face == "top" or face == "back":
                    return c
                if face in ("left", "right"):
                    return c if y < 8 else None
                if face == "front":
                    if y == 0 or x in (0, 7) and y < 7:
                        return shade(c, 0.8 if y else 1.0)
                return None
            if kind in ("cap", "security_cap"):  # police / security peaked cap
                if face == "top":
                    return shade(c, 1.1)
                if face == "front" and y == 1 and x in (3, 4):
                    return gold if kind == "cap" else (196, 196, 200)
                if y < 2:
                    return c
                if y == 2 and face != "front":
                    return (20, 20, 22)  # hat band
                return None
            if kind == "ballcap":
                if face == "top":
                    return c
                return c if y < 2 else None
            if kind == "hardhat":
                if face == "top":
                    return shade(c, 1.12) if x in (3, 4) else c  # ridge
                return c if y < 2 else (shade(c, 0.8) if y == 2 else None)
            if kind == "firehelmet":
                if face == "top":
                    return shade(c, 1.3) if x in (3, 4) else c
                if face == "front" and y < 3 and 2 <= x <= 5:
                    return gold if (x, y) != (3, 1) and (x, y) != (4, 1) else (150, 30, 24)  # shield
                return c if y < 3 else None
            if kind == "helmet":  # camo-covered combat helmet down over the ears
                cam = camo(7)
                if face == "top":
                    return cam(x, y, None)
                if face == "front":
                    return cam(x, y, None) if y < 2 else None
                return cam(x + 3, y, None) if y < (4 if face in ("left", "right") else 3) else None
            if kind == "boonie":  # floppy camo bush hat
                cam = camo(11)
                if face == "top":
                    return cam(x, y, None)
                return cam(x + 5, y, None) if y < 2 else None
            if kind == "straw":
                if face == "top":
                    return woven(c)(x, y, None)
                if y < 2:
                    return woven(c)(x, y, None)
                if y == 2:
                    return (96, 54, 30)  # band
                return None
            return None
        self.paint_box("hat", fn)

    def visor(self, c):
        self.parts["visor"] = 1.0
        self.paint_box("visor", lambda face, x, y, w, h: shade(c, 0.85) if face == "bottom" else c)

    def brim(self, c, scale=1.0, mat=None):
        self.parts["brim"] = scale
        paint = mat or solid(c)
        self.paint_box("brim", lambda face, x, y, w, h: shade(paint(x, y, None), 0.8) if face == "bottom" else paint(x, y, None))

    def bones(self):
        """The bone poking out of an arm stump: off-white, knobbly end, raw flesh where it joins."""
        def fn(face, x, y, w, h):
            if face == "bottom":
                return (196, 186, 160)
            if face == "top":
                return (120, 30, 26)
            return (96, 22, 20) if y == 0 else (206, 196, 168) if y < h - 1 else (226, 218, 196)
        self.paint_box("rbone", fn)
        self.paint_box("lbone", fn)

    def pack(self, c):
        self.parts["pack"] = 1.0
        strap = shade(c, 0.7)

        def fn(face, x, y, w, h):
            if face == "back" and 1 <= x <= 6 and 4 <= y <= 6:
                return shade(c, 0.8) if y == 4 else shade(c, 1.1)  # pocket
            if face == "back" and x in (1, 6) and y == 7:
                return (40, 36, 30)  # buckles
            if face == "top" and x in (1, 6):
                return strap
            return c
        self.paint_box("pack", fn)

    def torso(self, mat, extra=None):
        def fn(face, x, y, w, h):
            if extra:
                c = extra(face, x, y, w, h)
                if c is not None:
                    return c
            return mat(x, y, self.rng)
        self.paint_box("body", fn)

    def arms(self, sleeve_rows, mat, extra=None):
        sk = self.skin

        def fn(face, x, y, w, h):
            if extra:
                c = extra(face, x, y, w, h)
                if c is not None:
                    return c
            if face == "top":
                return mat(x, y, self.rng) if sleeve_rows > 0 else sk
            if face == "bottom":
                return sk
            return mat(x, y, self.rng) if y < sleeve_rows else sk
        self.paint_box("rarm", fn)
        self.paint_box("larm", fn)

    def legs(self, pant_rows, mat, shoe_rows, shoe, extra=None):
        sk = self.skin

        def fn(face, x, y, w, h):
            if extra:
                c = extra(face, x, y, w, h)
                if c is not None:
                    return c
            if face == "top":
                return mat(x, y, self.rng) if pant_rows > 0 else sk
            if face == "bottom":
                return shade(shoe, 0.7) if shoe_rows else sk
            if y >= h - shoe_rows:
                return shoe if y < h - 1 else shade(shoe, 0.75)
            return mat(x, y, self.rng) if y < pant_rows else sk
        self.paint_box("rleg", fn)
        self.paint_box("lleg", fn)

    # --- damage ---
    def splat(self, box, face, x, y, size):
        """An irregular blood blob that grows outward from (x, y) on one face."""
        r = self.rng
        fx, fy, w, h = faces(box)[face]
        cells = {(x, y)}
        while len(cells) < size:
            cx, cy = r.choice(sorted(cells))
            nx, ny = cx + r.choice((-1, 0, 1)), cy + r.choice((0, 1, 1))  # blood runs down
            if 0 <= nx < w and 0 <= ny < h:
                cells.add((nx, ny))
            if r.random() < 0.05:
                break
        for cx, cy in cells:
            if self.img.getpixel((fx + cx, fy + cy))[3]:
                self.put(fx + cx, fy + cy, r.choice(BLOOD), 6)

    def weather(self, blood=1.0, clean=False):
        """Dirt, tears and blood. clean=True stops after the dirt (the HD painter adds the rest)."""
        r = self.rng
        img = self.img
        clothing_boxes = ["body", "rarm", "rleg", "larm", "lleg"]
        # dirt and grime
        for box in clothing_boxes:
            for face, (fx, fy, w, h) in faces(box).items():
                for _ in range(int(w * h * 0.05)):
                    x, y = fx + r.randrange(w), fy + r.randrange(h)
                    if img.getpixel((x, y))[3]:
                        self.put(x, y, mix(img.getpixel((x, y)), (70, 58, 42), 0.25))
        if clean:
            return
        # tears showing skin or wounds
        for _ in range(r.randint(3, 7)):
            box = r.choice(clothing_boxes)
            fx, fy, w, h = faces(box)[r.choice(["front", "back", "right", "left"])]
            x, y = fx + r.randrange(w), fy + r.randrange(h)
            self.put(x, y, self.wound if r.random() < 0.5 else self.shadow)
        # blood running from the mouth down the chest
        fx, fy, w, h = faces("body")["front"]
        drip = r.randint(3, 6)
        for y in range(drip):
            for x in (3, 4):
                if y < drip - 1 or x == 3:
                    self.put(fx + x, fy + y, r.choice(BLOOD), 6)
        self.splat("body", "front", r.randint(2, 5), r.randint(1, 4), int(r.randint(8, 16) * blood))
        # hands soaked to the wrist, fading up the forearm
        for arm in ("rarm", "larm"):
            for face in ("front", "back", "right", "left", "bottom"):
                ax, ay, aw, ah = faces(arm)[face]
                for x in range(aw):
                    for y in range(ah):
                        up = ah - 1 - y
                        chance = 1.0 if face == "bottom" or up < 2 else (0.75, 0.4, 0.15)[up - 2] if up < 5 else 0
                        if r.random() < chance * min(1.0, blood):
                            self.put(ax + x, ay + y, r.choice(BLOOD), 6)
        # splatters over the clothes
        for _ in range(int(r.randint(4, 8) * blood)):
            box = r.choice(clothing_boxes)
            face = r.choice(["front", "front", "back", "right", "left"])
            _, _, w, h = faces(box)[face]
            self.splat(box, face, r.randrange(w), r.randrange(h), r.randint(2, 6))


# ---------- outfits ----------

def outfit_civilian(s, r):
    tee = r.choice(TEES)
    s.torso(solid(tee))
    s.arms(r.choice([3, 4]), solid(tee))
    s.legs(12, denim(r.choice([(52, 70, 110), (40, 48, 70), (70, 84, 110)])), 1, r.choice([(110, 72, 42), (40, 40, 44)]))


def outfit_hoodie(s, r):
    c = r.choice([(64, 66, 70), (52, 54, 58), (40, 44, 60)])
    def pocket(face, x, y, w, h):
        if face == "front" and 7 <= y <= 9 and 1 <= x <= 6:
            return shade(c, 0.82)
        if face == "front" and 1 <= y <= 4 and x in (3, 4):
            return (200, 200, 196) if y in (2, 4) and x == (3 if y == 2 else 4) else None  # drawstrings
        if y == 11:
            return shade(c, 0.85)  # waistband
        return None
    s.torso(solid(c), pocket)
    s.arms(12, solid(c), lambda face, x, y, w, h: shade(c, 0.85) if y == 10 and face not in ("top", "bottom") else None)
    s.legs(12, denim((40, 46, 62)), 1, (40, 40, 44))
    s.hat("hood", c)


def outfit_office(s, r):
    shirt = r.choice([(222, 222, 214), (186, 206, 224)])
    tie = r.choice([(140, 20, 28), (30, 40, 90)])
    def tie_fn(face, x, y, w, h):
        if face == "front" and x in (3, 4) and 1 <= y <= 8:
            return tie if y < 8 or x == 3 else None
        if face == "front" and y == 0 and x in (2, 5):
            return shade(shirt, 0.8)  # collar
        if y == 11:
            return (24, 22, 22)  # belt
        return None
    s.torso(solid(shirt), tie_fn)
    s.arms(11, solid(shirt))
    s.legs(12, solid((50, 50, 56)), 1, (26, 22, 20))


def outfit_police(s, r):
    navy = (30, 40, 76)
    gold = (214, 180, 70)
    def details(face, x, y, w, h):
        if face == "front" and y == 3 and x == 5:
            return gold  # badge
        if face == "front" and y == 2 and x == 2:
            return (210, 210, 214)  # name tag
        if face == "front" and x in (3, 4) and y < 10 and y % 3 == 1:
            return shade(navy, 1.5)  # buttons
        if y == 11:
            return (20, 20, 22) if not (face == "front" and x in (3, 4)) else (190, 176, 130)
        return None
    s.torso(solid(navy), details)
    def patch(face, x, y, w, h):
        return gold if face in ("right", "left") and y in (1, 2) and x in (1, 2) else None
    s.arms(4, solid(navy), patch)
    s.legs(12, solid((26, 32, 52)), 1, (18, 18, 20))
    s.hat("cap", (26, 34, 64))
    s.visor((16, 16, 18))


def outfit_nurse(s, r):
    c = r.choice([(128, 172, 214), (110, 170, 168)])
    def vneck(face, x, y, w, h):
        if face == "front" and ((y == 0 and x in (2, 3, 4, 5)) or (y == 1 and x in (3, 4))):
            return s.skin
        if face == "front" and y == 4 and x == 1:
            return (220, 220, 214)  # pen
        if face == "front" and y in (4, 5) and x in (1, 2):
            return shade(c, 0.85)  # chest pocket
        return None
    s.torso(solid(c), vneck)
    s.arms(3, solid(c))
    s.legs(12, solid(shade(c, 0.95)), 1, (226, 226, 220))


def outfit_doctor(s, r):
    coat = (226, 226, 220)
    shirt = (120, 150, 190)
    def coat_fn(face, x, y, w, h):
        if face == "front" and x in (3, 4) and y < 8:
            return (40, 40, 48) if x == 3 and 1 <= y <= 6 else shirt  # tie
        if face == "front" and ((y == 1 and x in (2, 5)) or (x == 2 and 2 <= y <= 4)):
            return (34, 34, 38)  # stethoscope
        if face == "front" and y == 4 and x == 2:
            return (170, 170, 176)
        if face == "front" and y == 6 and x == 6:
            return (60, 60, 120)  # pen
        return None
    s.torso(solid(coat), coat_fn)
    s.arms(11, solid(coat))
    s.legs(12, solid((50, 52, 60)), 1, (30, 26, 24),
           lambda face, x, y, w, h: coat if y < 3 and face not in ("top", "bottom") else None)


def outfit_construction(s, r):
    tee = r.choice([(110, 110, 112), (70, 70, 74), (200, 200, 196)])
    vest = (238, 118, 26)
    stripe = (218, 218, 210)
    def vest_fn(face, x, y, w, h):
        if face == "bottom":
            return None
        if face == "front" and x in (3, 4) and y > 0:
            return None  # open front shows the shirt
        if face in ("front", "back") and x in (1, 6) and y < 6:
            return stripe  # shoulder stripes
        if y in (6, 9):
            return stripe
        return vest
    s.torso(solid(tee), vest_fn)
    s.arms(3, solid(tee))
    s.legs(12, denim((52, 70, 110)), 2, (110, 74, 40))
    s.hat("hardhat", (238, 200, 36))
    s.brim((228, 190, 32), 1.0)


def outfit_firefighter(s, r):
    tan = (176, 146, 84)
    yellow, silver = (226, 214, 70), (206, 206, 200)
    def bands(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        if y in (6, 10):
            return yellow
        if y in (5, 9):
            return silver
        if face == "front" and x in (3, 4):
            return shade(tan, 0.8) if y % 2 else (40, 36, 30)  # clasps
        return None
    s.torso(solid(tan), bands)
    s.arms(12, solid(tan), lambda face, x, y, w, h: (yellow if y == 8 else silver if y == 9 else None) if face not in ("top", "bottom") else None)
    s.legs(12, solid(tan), 3, (24, 22, 20), lambda face, x, y, w, h: yellow if y == 6 and face not in ("top", "bottom") else None)
    s.hat("firehelmet", (30, 30, 30))
    s.brim((26, 26, 26), 1.15)


def outfit_farmer(s, r):
    shirt = plaid((158, 36, 32), (40, 28, 28))
    over = (60, 86, 136)
    def overalls(face, x, y, w, h):
        if face == "front" and y == 4 and x in (1, 6):
            return (200, 170, 80)  # brass buttons
        if face == "front" and (y >= 4 or (x in (1, 6) and y < 4)):
            return shade(over, 1.05 if (x + y) % 3 else 0.95)
        if face == "back" and (y >= 6 or (y < 6 and x in (2 + y // 2 % 2, 5 - y // 2 % 2))):
            return over
        if face in ("left", "right") and y >= 6:
            return over
        return None
    s.torso(shirt, overalls)
    s.arms(11, shirt)
    s.legs(12, denim(over), 2, (90, 60, 36))
    straw = (206, 170, 100)
    s.hat("straw", straw)
    s.brim(straw, 1.35, woven(straw))


def outfit_prisoner(s, r):
    orange = (226, 102, 28)
    def number(face, x, y, w, h):
        if face == "front" and 2 <= y <= 3 and 4 <= x <= 6:
            return (30, 30, 30) if (x + y) % 2 else (230, 224, 210)  # number patch
        if face == "back" and 2 <= y <= 4 and 1 <= x <= 6:
            return (30, 30, 30) if (x + y) % 2 else (230, 224, 210)
        if face == "front" and x == 3 and y < 9:
            return shade(orange, 0.85)  # zip
        return None
    s.torso(solid(orange), number)
    s.arms(4, solid(orange))
    s.legs(12, solid(orange), 1, (226, 226, 220))


def outfit_soldier(s, r):
    cam = camo(r.randint(0, 9999))
    webbing = (52, 50, 36)
    def gear(face, x, y, w, h):
        if y == 11 or y == 10 and face == "front" and x in (1, 2, 5, 6):
            return webbing  # belt and pouches
        if face in ("front", "back") and x in (1, 6) and y < 10:
            return webbing  # harness straps
        return None
    s.torso(cam, gear)
    s.arms(12, cam)
    s.legs(12, cam, 3, (40, 34, 28))
    s.hat("helmet")


def outfit_survivor(s, r):
    jacket = camo(r.randint(0, 9999))
    pack = (92, 74, 48)
    def straps(face, x, y, w, h):
        if face == "front" and x in (1, 6) and y < 9:
            return shade(pack, 0.7)
        if face == "front" and x == 3 and y < 11:
            return (40, 40, 36)  # zip
        return None
    s.torso(jacket, straps)
    s.arms(12, jacket)
    s.legs(12, solid((58, 56, 50)), 2, (50, 40, 30))
    s.pack(pack)
    s.hat("boonie")
    s.brim(None, 1.15, camo(11))


def outfit_security(s, r):
    shirt = (156, 186, 220)
    def details(face, x, y, w, h):
        if face == "front" and y == 2 and x == 5:
            return (196, 196, 200)  # badge
        if face == "front" and x == 3 and 1 <= y <= 7:
            return (30, 34, 50)  # tie
        if face == "front" and y == 3 and x == 1:
            return (26, 26, 26)  # radio
        if y == 11:
            return (20, 20, 22)
        return None
    s.torso(solid(shirt), details)
    s.arms(4, solid(shirt), lambda face, x, y, w, h: (30, 30, 30) if face in ("right", "left") and y == 1 and x in (1, 2) else None)
    s.legs(12, solid((26, 26, 30)), 1, (18, 18, 20))
    s.hat("security_cap", (24, 24, 28))
    s.visor((16, 16, 18))


def outfit_jogger(s, r):
    top = r.choice([(36, 36, 40), (28, 30, 44)])
    white = (230, 230, 230)
    def jacket(face, x, y, w, h):
        if face == "front" and x == 3 and y < 11:
            return (120, 120, 124)  # zip
        if face == "front" and y == 0 and x in (2, 5):
            return white  # collar trim
        return None
    s.torso(solid(top), jacket)
    s.arms(11, solid(top), lambda face, x, y, w, h: white if face in ("left", "right") and x in (1, 2) and y < 11 else None)
    s.legs(6, solid((30, 30, 34)), 2, (236, 236, 236),
           lambda face, x, y, w, h: white if face in ("left", "right") and x == 1 and y < 6 else None)


def outfit_patient(s, r):
    gown = pattern_dots((218, 228, 236), (96, 130, 186))
    s.torso(gown, lambda face, x, y, w, h: s.skin if face == "front" and y == 0 and x in (3, 4) else None)
    s.arms(4, gown)
    s.legs(12, gown, 1, (224, 224, 220))


OUTFITS = {
    "civilian": outfit_civilian, "hoodie": outfit_hoodie, "office": outfit_office,
    "police": outfit_police, "nurse": outfit_nurse, "doctor": outfit_doctor,
    "construction": outfit_construction, "firefighter": outfit_firefighter, "farmer": outfit_farmer,
    "prisoner": outfit_prisoner, "soldier": outfit_soldier, "survivor": outfit_survivor,
    "security": outfit_security, "jogger": outfit_jogger, "patient": outfit_patient,
}
HAIR_STYLES = ["short", "short", "long", "bald"]


def make(outfit, skin_name, seed, clean=False):
    s = Skin(skin_name, seed)
    r = random.Random(seed * 31 + 7)
    style = r.choice(HAIR_STYLES)
    hair = r.choice(HAIR)
    s.head(style, hair)
    OUTFITS[outfit](s, r)
    s.bones()
    s.weather(blood=1.3 if outfit in ("patient", "nurse", "doctor") else 1.0, clean=clean)
    s.style, s.hair = style, hair
    return s


# ---------- preview ----------

def face_img(img, box, face):
    fx, fy, w, h = faces(box)[face]
    return img.crop((fx, fy, fx + w, fy + h))


def figure(img, back=False):
    """A flat front (or back) view of the model: head, body, arms, legs, with the hat layer on top."""
    f = "back" if back else "front"
    fig = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    fig.alpha_composite(face_img(img, "head", f), (4, 0))
    fig.alpha_composite(face_img(img, "hat", f), (4, 0))
    fig.alpha_composite(face_img(img, "body", f), (4, 8))
    rarm, larm = face_img(img, "rarm", f), face_img(img, "larm", f)
    rleg, lleg = face_img(img, "rleg", f), face_img(img, "lleg", f)
    fig.alpha_composite(larm if back else rarm, (0, 8))
    fig.alpha_composite(rarm if back else larm, (12, 8))
    fig.alpha_composite(lleg if back else rleg, (4, 20))
    fig.alpha_composite(rleg if back else lleg, (8, 20))
    return fig


def preview(skins, path):
    scale = 5
    cell_w, cell_h = 16 * scale * 2 + 3 * scale, 32 * scale + 22
    cols = len(SKINS)
    names = list(OUTFITS)
    label_w = 120
    sheet = Image.new("RGB", (label_w + cols * (cell_w + 16) + 16, 40 + len(names) * (cell_h + 10)), (34, 36, 38))
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 14)
        small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
    except OSError:
        font = small = ImageFont.load_default()
    for c, skin_name in enumerate(SKINS):
        d.text((label_w + c * (cell_w + 16) + 16, 12), f"{skin_name} skin (front, back)", fill=(220, 220, 220), font=font)
    for row, outfit in enumerate(names):
        y = 40 + row * (cell_h + 10)
        d.text((12, y + cell_h // 2 - 8), outfit, fill=(230, 230, 230), font=font)
        for c, skin_name in enumerate(SKINS):
            img = skins[(outfit, skin_name)]
            x = label_w + c * (cell_w + 16) + 16
            d.rectangle((x - 4, y - 2, x + cell_w + 4, y + cell_h - 4), fill=(52, 56, 58))
            for i, back in enumerate((False, True)):
                fig = figure(img, back).resize((16 * scale, 32 * scale), Image.NEAREST)
                sheet.paste(fig, (x + i * (16 * scale + 3 * scale), y + 2), fig)
            d.text((x, y + 32 * scale + 4), f"{outfit}_{skin_name}", fill=(150, 150, 150), font=small)
    sheet.save(path)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    skins = {}
    parts = {}
    for i, outfit in enumerate(OUTFITS):
        for j, skin_name in enumerate(SKINS):
            s = make(outfit, skin_name, seed=1000 + i * 10 + j)
            s.img.save(OUT / f"{outfit}_{skin_name}.png")
            skins[(outfit, skin_name)] = s.img
            parts[outfit] = s.parts
    # Which extra parts each outfit shows (and the brim size), for the 3D preview.
    PARTS_JSON.write_text(json.dumps(parts, indent=2) + "\n")
    if len(sys.argv) > 1:
        preview(skins, sys.argv[1])
    print(f"wrote {len(skins)} skins to {OUT}")


if __name__ == "__main__":
    main()
