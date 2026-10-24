#!/usr/bin/env python3
"""Generates ZomboidCraft walker skins (64x64, Minecraft humanoid layout) and a preview sheet.

Every skin is a zombified body (skin tone, hair) wearing one outfit, with dirt, tears and blood
painted on. Run from the repo root:  python3 tools/gen_zombie_skins.py [preview.png]
"""
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path("src/main/resources/assets/zomboidcraft/textures/entity/walker")

# Zombified skin tones: pale, olive, dark. Each is (base, shadow, wound).
SKINS = {
    "pale": ((168, 172, 150), (128, 132, 112), (120, 40, 38)),
    "olive": ((146, 132, 104), (108, 96, 74), (112, 34, 30)),
    "dark": ((98, 84, 72), (70, 58, 50), (92, 26, 24)),
}
HAIR = [(28, 24, 22), (70, 48, 30), (120, 92, 52), (176, 150, 96), (120, 116, 110), (110, 50, 26)]
TEES = [(150, 40, 40), (44, 70, 130), (52, 100, 60), (110, 110, 112), (180, 150, 60), (90, 60, 110), (200, 200, 196)]

BOXES = {  # name: (u, v, w, h, d)
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "rarm": (40, 16, 4, 12, 4), "larm": (32, 48, 4, 12, 4),
    "rleg": (0, 16, 4, 12, 4), "lleg": (16, 48, 4, 12, 4),
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


def camo(seed):
    cols = [(78, 88, 52), (104, 96, 62), (56, 60, 40), (120, 112, 80)]
    r = random.Random(seed)
    blobs = [(r.randint(0, 40), r.randint(0, 40), r.randint(1, 3), r.choice(cols)) for _ in range(140)]
    def f(x, y, rng, _b=blobs):
        for bx, by, br, bc in _b:
            if abs(x - bx % 9) + abs(y - by % 13) <= br - 1 and (bx + by) % 3 == (x * 7 + y * 3) % 3:
                return bc
        return cols[0]
    return f


def pattern_dots(a, b):
    return lambda x, y, rng: b if (x * 3 + y * 5) % 7 == 0 else a


# ---------- the painter ----------

class Skin:
    def __init__(self, skin_name, seed):
        self.rng = random.Random(seed)
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.skin, self.shadow, self.wound = SKINS[skin_name]

    def put(self, x, y, c):
        self.img.putpixel((x, y), jitter(c, self.rng))

    def paint_box(self, box, fn):
        """fn(face, x, y, w, h) -> colour or None for each pixel of each face of the box."""
        for face, (fx, fy, w, h) in faces(box).items():
            for y in range(h):
                for x in range(w):
                    c = fn(face, x, y, w, h)
                    if c is not None:
                        side = face in ("right", "left", "back")
                        self.put(fx + x, fy + y, shade(c, 0.9 if side else 1.0))

    # --- body parts ---
    def head(self, hair_style, hair):
        sk, sh, wd = self.skin, self.shadow, self.wound
        r = self.rng

        def fn(face, x, y, w, h):
            if face == "top":
                return hair if hair_style != "bald" else sh
            if face == "bottom":
                return sh
            if hair_style in ("short", "long") and y == 0:
                return hair
            if hair_style == "long" and face in ("back", "left", "right") and y < (7 if face == "back" else 5):
                if face == "back" or (face == "right" and x >= 3) or (face == "left" and x <= 4):
                    return hair
            if hair_style == "short" and face == "back" and y < 3:
                return hair
            if hair_style == "short" and face in ("left", "right") and y < 2:
                return hair
            if hair_style == "long" and face == "front" and (x in (0, 7) and y < 4):
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
        """Sunken milky eyes, a grey slack face and a torn, bloody mouth."""
        sk, sh, wd = self.skin, self.shadow, self.wound
        socket = shade(sh, 0.55)
        eye = (188, 186, 152)
        dark = (40, 14, 12)
        if y == 2:
            return shade(sk, 0.9)
        if y == 3 and x in (1, 6):
            return socket
        if y == 3 and x in (2, 5):
            return eye
        if y == 4 and x in (1, 2, 5, 6):
            return shade(sh, 0.8)
        if y == 4 and x in (3, 4):
            return sh
        if y == 5 and x == 3:
            return shade(sh, 0.7)
        if y == 6 and x in (3, 5):
            return dark
        if y == 6 and x == 4:
            return (164, 152, 116)
        if y == 6 and x == 2:
            return sh
        if y == 7 and x in (3, 4):
            return (84, 20, 18)
        return sk

    def hat(self, kind, c=None):
        """Hats live on the outer head layer."""
        if not kind:
            return
        def fn(face, x, y, w, h):
            if face == "bottom":
                return None
            if kind == "cap":  # police / ball cap
                if face == "top":
                    return c
                if y < 2 or (face == "front" and y == 2):
                    return shade(c, 1.0 if y < 2 else 0.7)
            if kind in ("hardhat", "helmet", "firehelmet"):
                if face == "top":
                    return c if kind != "firehelmet" or x not in (3, 4) else (210, 170, 40)
                if y < 3:
                    return c if y < 2 else shade(c, 0.75)
            if kind == "boonie":
                if face == "top":
                    return c
                if y < 3:
                    return shade(c, 1.0 if y < 2 else 0.8)
            return None
        self.paint_box("hat", fn)

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
    def weather(self, blood=1.0):
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
        # tears showing skin or wounds
        for _ in range(r.randint(3, 7)):
            box = r.choice(clothing_boxes)
            fx, fy, w, h = faces(box)[r.choice(["front", "back", "right", "left"])]
            x, y = fx + r.randrange(w), fy + r.randrange(h)
            self.put(x, y, self.wound if r.random() < 0.5 else self.shadow)
        # blood: chin and chest, hands, a few splatters
        fx, fy, w, h = faces("body")["front"]
        for _ in range(int(r.randint(4, 10) * blood)):
            self.put(fx + r.randint(2, 5), fy + r.randint(0, 5), r.choice([(110, 16, 14), (84, 14, 12), (70, 24, 18)]))
        for arm in ("rarm", "larm"):
            for face in ("front", "back", "right", "left", "bottom"):
                ax, ay, aw, ah = faces(arm)[face]
                for x in range(aw):
                    if r.random() < 0.6 * blood:
                        self.put(ax + x, ay + ah - 1 if face != "bottom" else ay + r.randrange(ah), (96, 16, 14))
        for _ in range(int(r.randint(3, 8) * blood)):
            box = r.choice(clothing_boxes)
            fx, fy, w, h = faces(box)[r.choice(["front", "back"])]
            x, y = fx + r.randrange(w), fy + r.randrange(h)
            for dx, dy in ((0, 0), (1, 0), (0, 1)):
                if r.random() < 0.7 and dx < w and dy < h:
                    self.put(min(x + dx, fx + w - 1), min(y + dy, fy + h - 1), (92, 18, 14))


# ---------- outfits ----------

def outfit_civilian(s, r):
    tee = r.choice(TEES)
    s.torso(solid(tee))
    s.arms(r.choice([3, 4]), solid(tee))
    s.legs(12, denim(r.choice([(52, 70, 110), (40, 48, 70), (70, 84, 110)])), 1, r.choice([(220, 220, 214), (40, 40, 44)]))


def outfit_hoodie(s, r):
    c = r.choice([(60, 62, 66), (90, 30, 30), (40, 54, 90), (40, 70, 50)])
    def pocket(face, x, y, w, h):
        if face == "front" and 7 <= y <= 9 and 1 <= x <= 6:
            return shade(c, 0.85)
        if face == "front" and y < 4 and x in (3, 4):
            return (200, 200, 196) if y % 2 else None  # drawstrings
        return None
    s.torso(solid(c), pocket)
    s.arms(11, solid(c))
    s.legs(12, solid(r.choice([(40, 40, 46), (80, 80, 84)])), 1, (40, 40, 44))


def outfit_office(s, r):
    shirt = r.choice([(214, 214, 206), (170, 196, 220)])
    tie = r.choice([(120, 20, 30), (30, 40, 90), (50, 50, 54)])
    def tie_fn(face, x, y, w, h):
        if face == "front" and x in (3, 4) and 1 <= y <= 8:
            return tie if y < 8 or x == 3 else None
        if face == "front" and y == 0 and x in (2, 5):
            return shade(shirt, 0.85)
        return None
    s.torso(solid(shirt), tie_fn)
    s.arms(11, solid(shirt))
    s.legs(12, solid((46, 46, 52)), 1, (26, 22, 20))


def outfit_police(s, r):
    navy = (34, 42, 70)
    def details(face, x, y, w, h):
        if face == "front" and y == 3 and x == 5:
            return (214, 180, 70)  # badge
        if face == "front" and y == 3 and x in (1, 2):
            return shade(navy, 1.4)  # pocket flap
        if y == 11:
            return (20, 20, 22) if not (face == "front" and x in (3, 4)) else (180, 170, 140)
        return None
    s.torso(solid(navy), details)
    def patch(face, x, y, w, h):
        return (170, 140, 60) if face in ("right", "left") and y in (1, 2) and x in (1, 2) else None
    s.arms(4, solid(navy), patch)
    s.legs(12, solid((28, 32, 48)), 1, (18, 18, 20))
    s.hat("cap", (24, 30, 52))


def outfit_nurse(s, r):
    c = r.choice([(110, 170, 168), (96, 140, 190), (170, 120, 160)])
    def vneck(face, x, y, w, h):
        if face == "front" and ((y == 0 and x in (2, 3, 4, 5)) or (y == 1 and x in (3, 4))):
            return s.skin
        if face == "front" and y == 5 and x == 2:
            return (200, 200, 196)  # pen
        return None
    s.torso(solid(c), vneck)
    s.arms(3, solid(c))
    s.legs(12, solid(shade(c, 0.95)), 1, (226, 226, 220))


def outfit_doctor(s, r):
    coat = (224, 224, 218)
    shirt = (120, 150, 190)
    def coat_fn(face, x, y, w, h):
        if face == "front" and x in (3, 4):
            return shirt if y < 9 else coat
        if face == "front" and y in (2, 3) and x in (1, 2, 5) and y == 2:
            return (40, 40, 44) if x == 2 else None  # stethoscope tube
        if face == "front" and y == 6 and x == 1:
            return (60, 60, 70)  # pocket pen
        return None
    s.torso(solid(coat), coat_fn)
    s.arms(11, solid(coat))
    def coat_legs(face, x, y, w, h):
        if y < 4 and face in ("front", "back", "right", "left") and not (face == "front" and x in (0,) and False):
            return coat
        return None
    s.legs(12, solid((50, 52, 60)), 1, (30, 26, 24), coat_legs)


def outfit_construction(s, r):
    tee = r.choice([(110, 110, 112), (70, 70, 74), (200, 200, 196)])
    vest = (230, 120, 30)
    stripe = (210, 210, 200)
    def vest_fn(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        if face == "front" and x in (3, 4):
            return None
        if y in (6, 9):
            return stripe
        return vest
    s.torso(solid(tee), vest_fn)
    s.arms(3, solid(tee))
    s.legs(12, denim((52, 70, 110)), 2, (96, 66, 40))
    s.hat("hardhat", (226, 196, 40))


def outfit_firefighter(s, r):
    tan = (168, 140, 82)
    band = lambda face, x, y, w, h: ((210, 200, 70) if y in (6, 10) else None) if face not in ("top", "bottom") else None
    s.torso(solid(tan), band)
    s.arms(12, solid(tan), lambda face, x, y, w, h: (210, 200, 70) if y == 8 and face not in ("top", "bottom") else None)
    s.legs(12, solid(tan), 2, (24, 22, 20), lambda face, x, y, w, h: (210, 200, 70) if y == 7 and face not in ("top", "bottom") else None)
    s.hat("firehelmet", (30, 30, 30))


def outfit_farmer(s, r):
    shirt = plaid((150, 40, 36), (40, 30, 30)) if r.random() < 0.6 else plaid((50, 80, 140), (220, 220, 210))
    over = (60, 82, 126)
    def overalls(face, x, y, w, h):
        if face == "front" and (y >= 4 or (x in (1, 6) and y < 4)):
            return shade(over, 1.05 if (x + y) % 3 else 0.95)
        if face == "back" and (y >= 6 or (y < 6 and x in (2 + y // 2 % 2, 5 - y // 2 % 2))):
            return over
        if face in ("left", "right") and y >= 6:
            return over
        return None
    s.torso(shirt, overalls)
    s.arms(4, shirt)
    s.legs(12, denim(over), 2, (90, 60, 36))
    if r.random() < 0.5:
        s.hat("cap", r.choice([(40, 80, 40), (150, 40, 36), (200, 200, 190)]))


def outfit_prisoner(s, r):
    orange = (214, 104, 32)
    def number(face, x, y, w, h):
        if face == "back" and 2 <= y <= 4 and 1 <= x <= 6:
            return (30, 30, 30) if (x + y) % 2 else (220, 210, 190)
        return None
    s.torso(solid(orange), number)
    s.arms(4, solid(orange))
    s.legs(12, solid(orange), 1, (220, 220, 214))


def outfit_soldier(s, r):
    cam = camo(r.randint(0, 9999))
    s.torso(cam, lambda face, x, y, w, h: (40, 40, 30) if y == 11 else None)
    s.arms(12, cam)
    s.legs(12, cam, 2, (50, 40, 30))
    s.hat("helmet", (74, 84, 50))


def outfit_survivor(s, r):
    hood = (54, 58, 50)
    pack = (100, 70, 40)
    def straps(face, x, y, w, h):
        if face == "front" and x in (1, 6) and y < 9:
            return shade(pack, 0.8)
        if face == "back" and 1 <= x <= 6 and 1 <= y <= 10:
            return shade(pack, 1.0 if y not in (5,) else 0.75)
        return None
    s.torso(solid(hood), straps)
    s.arms(12, solid(hood))
    s.legs(12, camo(r.randint(0, 9999)), 2, (50, 40, 30))
    s.hat("boonie", (110, 104, 70))


def outfit_security(s, r):
    shirt = (150, 176, 206)
    def details(face, x, y, w, h):
        if face == "front" and y == 2 and x == 5:
            return (190, 190, 190)
        if face == "front" and y == 2 and x == 2:
            return (30, 30, 30)  # radio
        if y == 11:
            return (20, 20, 22)
        return None
    s.torso(solid(shirt), details)
    s.arms(4, solid(shirt), lambda face, x, y, w, h: (30, 30, 30) if face in ("right", "left") and y == 1 and x in (1, 2) else None)
    s.legs(12, solid((26, 26, 30)), 1, (18, 18, 20))


def outfit_jogger(s, r):
    top = r.choice([(220, 60, 90), (60, 160, 200), (230, 230, 230), (40, 40, 44)])
    s.torso(solid(top), lambda face, x, y, w, h: s.skin if face in ("front", "back") and y < 2 and x in (0, 1, 6, 7) else None)
    s.arms(0, solid(top))
    s.legs(4, solid((30, 30, 34)), 1, r.choice([(230, 230, 230), (230, 100, 40)]))


def outfit_patient(s, r):
    gown = pattern_dots((170, 196, 210), (110, 140, 170))
    s.torso(gown)
    s.arms(2, gown)
    s.legs(4, gown, 0, s.skin)


OUTFITS = {
    "civilian": outfit_civilian, "hoodie": outfit_hoodie, "office": outfit_office,
    "police": outfit_police, "nurse": outfit_nurse, "doctor": outfit_doctor,
    "construction": outfit_construction, "firefighter": outfit_firefighter, "farmer": outfit_farmer,
    "prisoner": outfit_prisoner, "soldier": outfit_soldier, "survivor": outfit_survivor,
    "security": outfit_security, "jogger": outfit_jogger, "patient": outfit_patient,
}
HAIR_STYLES = ["short", "short", "long", "bald"]


def make(outfit, skin_name, seed):
    s = Skin(skin_name, seed)
    r = random.Random(seed * 31 + 7)
    style = r.choice(HAIR_STYLES)
    hair = r.choice(HAIR)
    s.head(style, hair)
    OUTFITS[outfit](s, r)
    s.weather(blood=1.3 if outfit in ("patient", "nurse", "doctor") else 1.0)
    return s.img


# ---------- preview ----------

def face_img(img, box, face):
    fx, fy, w, h = faces(box)[face]
    return img.crop((fx, fy, fx + w, fy + h))


def figure(img, back=False):
    """A flat front (or back) view of the model: head, body, arms, legs, with the hat layer on top."""
    f = "back" if back else "front"
    fig = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    head = face_img(img, "head", f)
    hat = face_img(img, "hat", f)
    fig.alpha_composite(head, (4, 0))
    fig.alpha_composite(hat, (4, 0))
    fig.alpha_composite(face_img(img, "body", f), (4, 8))
    arm = face_img(img, "rarm", f)  # zombies mirror the right arm and leg for the left
    leg = face_img(img, "rleg", f)
    left, right = (arm.transpose(Image.FLIP_LEFT_RIGHT), arm) if back else (arm, arm.transpose(Image.FLIP_LEFT_RIGHT))
    fig.alpha_composite(left, (0, 8))
    fig.alpha_composite(right, (12, 8))
    fig.alpha_composite(leg, (4, 20))
    fig.alpha_composite(leg.transpose(Image.FLIP_LEFT_RIGHT), (8, 20))
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
    for i, outfit in enumerate(OUTFITS):
        for j, skin_name in enumerate(SKINS):
            img = make(outfit, skin_name, seed=1000 + i * 10 + j)
            img.save(OUT / f"{outfit}_{skin_name}.png")
            skins[(outfit, skin_name)] = img
    if len(sys.argv) > 1:
        preview(skins, sys.argv[1])
    print(f"wrote {len(skins)} skins to {OUT}")


if __name__ == "__main__":
    main()
