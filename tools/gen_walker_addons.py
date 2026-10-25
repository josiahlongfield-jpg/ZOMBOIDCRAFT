#!/usr/bin/env python3
"""3D add-on pieces for the walker: hats, hoods, belts, pouches, vests, coat tails, pockets.

Each outfit gets real boxes on top of the body, placed to match Josiah's turnaround sheets.
The pieces are drawn by a second model layer with its own texture per skin (an atlas); every
face of a piece is sampled from the finished skin at the spot it covers, seen from the same
side, so a pouch shows the pouch the artist painted there, in that skin's tone and damage.

    python3 tools/gen_walker_addons.py [SKIN_DIR]

SKIN_DIR defaults to the mod's walker_hd textures. Writes the atlases (walker_addons_hd and the
64-style walker_addons), the generated Java parts class, and tools/mockup/walker_addons.json.

Coordinates are Minecraft model units for a standing walker: x toward its left hand, y down
(head top -8, feet 24), z toward its back (the face looks to -z).
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/zomboidcraft/textures/entity"
JAVA = ROOT / "src/client/java/com/zomboidcraft/client/zombie/WalkerAddonParts.java"
PX = 4            # atlas pixels per model unit (same as the HD skins)
ATLAS = 128       # atlas size in units
TONES = ["pale", "olive", "dark"]
LEVELS = 4

PIVOTS = {"head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
          "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0)}
BOUNDS = {"head": (-4, -8, -4, 4, 0, 4), "body": (-4, 0, -2, 4, 12, 2),
          "right_arm": (-8, 0, -2, -4, 12, 2), "left_arm": (4, 0, -2, 8, 12, 2),
          "right_leg": (-3.9, 12, -2, 0.1, 24, 2), "left_leg": (-0.1, 12, -2, 3.9, 24, 2)}
# Skin layout: texOffs and size of each bone's box (64x64 units)
SKIN_BOX = {"head": (0, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "right_arm": (40, 16, 4, 12, 4),
            "left_arm": (32, 48, 4, 12, 4), "right_leg": (0, 16, 4, 12, 4), "left_leg": (16, 48, 4, 12, 4)}


def q(v):
    """Snap to a quarter unit, one texel of the HD skins."""
    return round(v * 4) / 4


class Piece:
    def __init__(self, bone, x0, y0, z0, w, h, d, pivot=None, rot=(0, 0, 0), shift=(0, 0), mouth=False):
        self.bone = bone
        self.mouth = mouth  # top face shows the open mouth painted just above the piece
        self.box = tuple(q(v) for v in (x0, y0, z0, x0 + w, y0 + h, z0 + d))
        self.pivot = pivot
        self.rot = rot
        self.shift = shift  # where to sample from, relative to where the piece is (units, x and y)

    @property
    def size(self):
        x0, y0, z0, x1, y1, z1 = self.box
        return x1 - x0, y1 - y0, z1 - z0

    def mirrored(self):
        x0, y0, z0, x1, y1, z1 = self.box
        bone = self.bone.replace("right", "@").replace("left", "right").replace("@", "left")
        piv = None if self.pivot is None else (-self.pivot[0], self.pivot[1], self.pivot[2])
        p = Piece(bone, -x1, y0, z0, x1 - x0, y1 - y0, z1 - z0, piv, (self.rot[0], -self.rot[1], -self.rot[2]),
                  (-self.shift[0], self.shift[1]))
        return p


def both(*pieces):
    """The pieces and their mirror images (left and right side)."""
    out = []
    for p in pieces:
        out += [p, p.mirrored()]
    return out


def shell(bone, grow, y0, y1, dz=0.0):
    """A band around a bone (cuff, belt, hem), `grow` units proud of it all round."""
    x0, _, z0, x1, _, z1 = BOUNDS[bone]
    return Piece(bone, x0 - grow, y0, z0 - grow + dz, x1 - x0 + 2 * grow, y1 - y0, z1 - z0 + 2 * grow)


def boots(grow, height, toe):
    x0, _, z0, x1, y1, _ = BOUNDS["right_leg"]
    return both(Piece("right_leg", x0 - grow, y1 - height, z0 - grow - toe, x1 - x0 + 2 * grow, height, 4 + 2 * grow + toe))


def cuffs(y0, y1, grow=0.35):
    return both(shell("right_arm", grow, y0, y1))


def jaw():
    # the lower jaw hangs open, sampled from the chin
    return [Piece("head", -2.25, -1.25, -4.5, 4.5, 1.25, 1.25, pivot=(0, -1.25, -4), rot=(18, 0, 0), mouth=True)]


def collar(spread=12):
    return both(Piece("body", -2.75, -0.25, -2.5, 2.25, 1.5, 0.5, pivot=(-0.5, -0.25, -2.5), rot=(0, 0, -spread)))


def leg_pockets(y0=13, h=3.5, d=2.75):
    return both(Piece("right_leg", -4.6, y0, -d / 2, 0.75, h, d))


def hem_flaps(n, seed):
    """Torn strips hanging off an untucked shirt, sampled from the shirt above them."""
    rng = np.random.default_rng(seed)
    out = []
    for i in range(n):
        x = -3.75 + i * 7.5 / max(1, n - 1) * 0.9 + rng.uniform(-0.3, 0.3)
        w, h = q(rng.uniform(1.0, 1.75)), q(rng.uniform(1.75, 2.75))
        front = i % 3 != 2
        z = -2.5 if front else 2.0
        out.append(Piece("body", x, 11.5, z, w, h, 0.5, pivot=(x + w / 2, 11.5, z + 0.25),
                         rot=(8 if front else -8, 0, float(rng.uniform(-10, 10))), shift=(0, -2.5)))
    return out


def outfit_pieces():
    P = Piece
    o = {}
    o["police"] = [
        P("head", -4.75, -8.5, -4.75, 9.5, 2.25, 9.5),            # cap crown
        P("head", -4.5, -6.25, -4.5, 9, 1.5, 9),                  # cap band
        P("head", -1, -8, -5.0, 2, 1.75, 0.25),                   # cap badge
        *collar(),
        *both(P("body", -3.5, 3.5, -2.5, 2.25, 2.25, 0.5)),       # shirt pockets
        P("body", 1.75, 1.75, -2.75, 1.75, 2, 0.25),              # badge
        P("body", -3.25, 1.75, -2.75, 1.75, 0.75, 0.25),          # name plate
        P("body", -4.5, 6.75, -2.5, 9, 2, 5),                     # duty belt
        P("body", -0.75, 6.5, -2.75, 1.5, 2.25, 0.5),             # buckle
        *both(P("body", -4.25, 6.25, -3.25, 1.75, 3, 0.75)),      # pouches
        P("body", -2.25, 6.5, -3.0, 1.25, 2, 0.5),                # radio
        *both(P("body", -3.5, 6.5, 2.5, 2, 2.5, 0.75)),           # back pouches
        *cuffs(4.5, 5.5),
        *both(P("right_arm", -8.25, -0.25, -1.5, 4.5, 0.5, 3)),   # epaulettes
        *both(P("right_arm", -8.25, 1, -1.25, 0.25, 2.5, 2.5)),   # shoulder patches
        *leg_pockets(),
        *boots(0.25, 3, 0.75),
    ]
    o["soldier"] = [
        P("head", -4.75, -8.75, -4.75, 9.5, 3.5, 9.5),            # helmet
        P("head", -5, -5.5, -5, 10, 0.75, 10),                    # helmet rim
        P("head", -0.75, -8, -5.0, 1.5, 1.25, 0.25),              # night-vision mount
        P("body", -4.5, 0.5, -2.75, 9, 10.25, 5.5),               # plate carrier
        *both(P("body", -3.25, -0.25, -2.75, 1.5, 1, 5.5)),       # shoulder straps
        *[P("body", x, 5.75, -3.5, 1.5, 3, 0.75) for x in (-4.0, -1.75, 0.25, 2.5)],  # magazine pouches
        P("body", -1.25, 2.25, -3.25, 2.5, 2, 0.5),               # admin pouch
        *both(P("body", -3.75, 4.5, 2.75, 3.25, 4, 1)),           # back pouches
        *cuffs(4.75, 5.75),
        *both(P("right_arm", -8.25, 1.25, -1, 0.25, 2.25, 2)),    # sleeve pockets
        *leg_pockets(13, 3.5, 3),
        *boots(0.35, 3, 0.75),
    ]
    o["doctor"] = [
        P("body", -4.25, -0.25, -2.25, 8.5, 12.25, 4.5),          # lab coat
        *both(P("body", -4.25, 12, -2.25, 4.25, 3.5, 0.5, pivot=(-2, 12, -2), rot=(6, 0, 0))),  # front tails
        P("body", -4.25, 12, 1.75, 8.5, 3.5, 0.5, pivot=(0, 12, 2), rot=(-6, 0, 0)),          # back tail
        *both(P("body", -2.25, 0, -2.5, 1.25, 6.5, 0.25)),        # lapels
        P("body", -3.5, 1.25, -2.75, 0.5, 4, 0.5),                # stethoscope tubes
        P("body", -2.0, 1.25, -2.75, 0.5, 4, 0.5),
        P("body", -3.5, 5.0, -2.75, 2, 0.5, 0.5),
        P("body", -3.5, -0.25, -2.5, 7, 0.5, 5),                  # round the neck
        P("body", 2.25, 3.25, -3.0, 1.25, 1.25, 0.5),             # chest piece
        *both(P("body", -3.75, 8.75, -2.5, 2.25, 2, 0.25)),       # coat pockets
        *cuffs(4.5, 5.75),
        *boots(0.2, 2.5, 0.5),
    ]
    o["hoodie"] = [
        P("head", -5, -9, -5.25, 10, 1, 10.5),                    # hood top
        P("head", -5, -8, 4.25, 10, 9, 1),                        # hood back, onto the shoulders
        *both(P("head", -5, -8, -2.25, 1, 8.25, 6.5)),            # hood sides (the face shows in front)
        P("head", -4.25, -8, -4.75, 8.5, 1, 0.75),                # rim over the brow
        *both(P("head", -4.75, -8, -4.75, 0.75, 7.5, 0.75)),      # rim down the cheeks
        P("body", -3.5, -0.5, 1.75, 7, 1.5, 1),                   # hood bunched at the neck
        *both(P("body", -2.5, 0.5, -2.5, 0.5, 3.25, 0.5)),        # drawstrings
        *both(P("body", -2.75, 3.75, -2.75, 1, 1, 1)),            # aglets
        P("body", -4, 6.25, -2.75, 8, 3.5, 0.75),                 # front pocket
        shell("body", 0.25, 10.75, 12.25),                        # ribbed hem
        *cuffs(4.25, 5.75, 0.5),
        *leg_pockets(13.25, 3.25, 3),
        *boots(0.25, 2.75, 0.75),
    ]
    o["farmer"] = [
        P("head", -4.25, -8.75, -4.25, 8.5, 3.5, 8.5),            # straw hat crown
        P("head", -3.5, -10, -3.5, 7, 1.25, 7),                   # crown top
        P("body", -3, 1.75, -2.75, 6, 4.5, 0.5),                  # bib
        *both(P("body", -3.5, -0.25, -2.5, 1, 2, 5)),             # straps over the shoulders
        *both(P("body", -3.5, 1.75, -3.0, 1.25, 1.25, 0.5)),      # buckles
        P("body", -1.5, 3.25, -3.0, 3, 2.25, 0.25),               # bib pocket
        *cuffs(4.25, 5.5, 0.5),
        *leg_pockets(13, 3.25, 3),
        *boots(0.25, 2.75, 0.75),
    ]
    o["firefighter"] = [
        P("head", -4.5, -9, -4.5, 9, 3.75, 9),                    # helmet dome
        P("head", -0.75, -9.75, -4, 1.5, 0.75, 8.5),              # crest
        P("head", -1.25, -8.75, -5.0, 2.5, 2.5, 0.5),             # front shield
        P("head", -4.75, -5.25, 4.5, 9.5, 0.5, 1.75, pivot=(0, -5.25, 4.5), rot=(-12, 0, 0)),  # rear brim
        P("body", -4.5, -0.25, -2.5, 9, 12.5, 5),                 # turnout coat
        P("body", -4.75, 11.25, -2.75, 9.5, 1.5, 5.5),            # coat hem
        P("body", -3.25, -0.75, -2.75, 6.5, 1.25, 5.5),           # high collar
        P("body", -0.5, 0, -2.75, 1, 11, 0.25),                   # storm flap
        P("body", -4.75, 7.75, -2.75, 9.5, 1.25, 5.5),            # belt
        P("body", -1, 7.5, -3.0, 2, 1.75, 0.5),                   # buckle
        *both(shell("right_arm", 0.25, 5, 6.5)),                  # reflective bands
        *cuffs(10, 12),
        *leg_pockets(13, 3, 3),
        *both(shell("right_leg", 0.25, 17.25, 18.75)),
        *boots(0.5, 4, 0.75),
    ]
    o["construction"] = [
        P("head", -4.5, -9.25, -4.5, 9, 3.5, 9),                  # hard hat
        P("head", -0.75, -9.75, -4.25, 1.5, 0.5, 8.5),            # ridge
        P("body", -4.5, -0.25, -2.5, 9, 11, 5),                   # hi-vis vest
        *collar(),
        *cuffs(4.25, 5.25),
        *leg_pockets(),
        *boots(0.5, 3, 1.0),
    ]
    o["civilian"] = [
        *collar(18),
        shell("body", 0.25, 10.5, 12),                            # untucked shirt
        *hem_flaps(5, 11),
        *cuffs(4.25, 5.25),
        *leg_pockets(14, 3, 2.5),
        *boots(0.25, 2.75, 0.5),
    ]
    o["office"] = [
        *collar(8),
        P("body", -0.5, 0.5, -2.75, 1, 1, 0.5),                   # tie knot
        P("body", -0.5, 1.5, -2.5, 1, 7.5, 0.25, pivot=(0, 1.5, -2.5), rot=(4, 0, 3)),  # tie
        P("body", 1.5, 3.25, -2.5, 1.5, 1.75, 0.25),              # shirt pocket
        shell("body", 0.25, 10.75, 11.75),                        # belt
        P("body", -0.75, 10.5, -2.5, 1.5, 1.25, 0.25),            # buckle
        *cuffs(4.25, 5.25),
        *leg_pockets(13, 2.5, 2.5),
        *boots(0.2, 2.25, 0.5),
    ]
    o["prisoner"] = [
        *collar(14),
        P("body", 0.5, 3.5, -2.5, 2.75, 1, 0.25),                 # number patch
        shell("body", 0.25, 11.25, 12.25),
        *cuffs(4.5, 5.5),
        *both(shell("right_leg", 0.25, 20.5, 21.5)),              # trouser cuffs
        *boots(0.2, 2.5, 0.5),
    ]
    o["nurse"] = [
        P("head", -4.5, -8.75, -4.5, 9, 3.25, 9),                 # scrub cap
        P("head", -4.75, -6.0, -4.75, 9.5, 0.75, 9.5),            # cap band
        P("body", 1.0, 2.75, -2.5, 1.75, 1.75, 0.25),             # chest pocket
        shell("body", 0.25, 11.25, 12.25),                        # scrub top hem
        *cuffs(4.25, 5.25),
        *boots(0.15, 2.25, 0.5),
    ]
    for name in o:
        o[name] = jaw() + o[name]
    return o


# ---------------------------------------------------------------- sampling the skin

def face_rects(u, v, w, h, d):
    """Minecraft's cube net: where each face sits, in units: (x, y, width, height)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def fill_holes(img, have):
    """Spread painted pixels into the empty ones, so sampling past an edge repeats the edge."""
    img, have = img.copy(), have.copy()
    for _ in range(200):
        if have.all():
            break
        acc = np.zeros_like(img)
        cnt = np.zeros(have.shape, np.float32)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            acc += np.roll(np.roll(img * have[..., None], dy, 0), dx, 1)
            cnt += np.roll(np.roll(have, dy, 0), dx, 1)
        new = ~have & (cnt > 0)
        img[new] = acc[new] / cnt[new][:, None]
        have = have | new
    return img


def views(skin, alpha, group):
    """The walker seen from the front, back, left and right, built from one group of bones
    ('core': head, body, legs; 'arm': the arms), at PX pixels per unit."""
    bones = ["right_arm", "left_arm"] if group == "arm" else ["head", "body", "right_leg", "left_leg"]
    C = skin.shape[-1]
    out = {}
    for view, width in (("front", 16), ("back", 16), ("left", 8), ("right", 8)):
        img = np.zeros((32 * PX, width * PX, C), np.float32)
        have = np.zeros(img.shape[:2], bool)
        for b in bones:
            u, v, w, h, d = SKIN_BOX[b]
            x0, y0, z0, x1, y1, z1 = BOUNDS[b]
            fx, fy, fw, fh = face_rects(u, v, w, h, d)[view]
            if view == "front":
                vx = x0 + 8
            elif view == "back":
                vx = 8 - x1
            elif view == "left":
                vx = 4 + z0
            else:
                vx = 4 - z1
            vy = y0 + 8
            sx, sy = round(fx * PX), round(fy * PX)
            dx, dy = round(vx * PX), round(vy * PX)
            pw, ph = round(fw * PX), round(fh * PX)
            img[dy:dy + ph, dx:dx + pw] = skin[sy:sy + ph, sx:sx + pw]
            have[dy:dy + ph, dx:dx + pw] = alpha[sy:sy + ph, sx:sx + pw]
        out[view] = fill_holes(img, have)
    return out


def sample(view_img, vx0, vy0, w, h):
    """A w x h (units) patch of a view starting at (vx0, vy0) units, edges repeated."""
    H, W = view_img.shape[:2]
    xs = np.clip(np.arange(round(vx0 * PX), round(vx0 * PX) + round(w * PX)), 0, W - 1)
    ys = np.clip(np.arange(round(vy0 * PX), round(vy0 * PX) + round(h * PX)), 0, H - 1)
    return view_img[ys][:, xs]


def bone_face(skin, bone, face):
    u, v, w, h, d = SKIN_BOX[bone]
    fx, fy, fw, fh = face_rects(u, v, w, h, d)[face]
    return skin[round(fy * PX):round((fy + fh) * PX), round(fx * PX):round((fx + fw) * PX)]


def cap_face(skin, p, face):
    """Top or bottom of a piece from its bone's own top or bottom, edges repeated."""
    x0, _, z0, x1, _, z1 = p.box
    bx0, _, bz0, _, _, bz1 = BOUNDS[p.bone]
    src = bone_face(skin, p.bone, face)
    H, W = src.shape[:2]
    cols = np.clip(((np.arange(round((x1 - x0) * PX)) / PX + x0 - bx0) * PX).astype(int), 0, W - 1)
    # the net puts the front edge of the top face at the bottom row
    zs = z1 - (np.arange(round((z1 - z0) * PX)) + 0.5) / PX
    rows = np.clip(((bz1 - zs) * PX).astype(int), 0, H - 1)
    return src[rows][:, cols]


def piece_faces(p, V, skin=None):
    """Pixels for each face of a piece, sampled from the views it would be seen in."""
    x0, y0, z0, x1, y1, z1 = p.box
    w, h, d = p.size
    sx, sy = p.shift
    v = V["arm" if "arm" in p.bone else "core"]
    f = {
        "front": sample(v["front"], x0 + sx + 8, y0 + sy + 8, w, h),
        "back": sample(v["back"], 8 - x1 - sx, y0 + sy + 8, w, h),
        "left": sample(v["left"], 4 + z0, y0 + sy + 8, d, h),
        "right": sample(v["right"], 4 - z1, y0 + sy + 8, d, h),
    }
    f["top"] = sample(v["front"], x0 + sx + 8, y0 + sy + 8 - d, w, d) if p.mouth else cap_face(skin, p, "top")
    f["bottom"] = cap_face(skin, p, "bottom")
    return f


# ---------------------------------------------------------------- layout and output

def pack(pieces):
    """Shelf-pack each piece's cube net into the atlas; returns integer texOffs per piece."""
    x = y = row = 0
    offs = []
    for p in pieces:
        w, h, d = p.size
        nw, nh = math.ceil(2 * (w + d)), math.ceil(d + h)
        if x + nw > ATLAS:
            x, y, row = 0, y + row, 0
        if y + nh > ATLAS:
            raise SystemExit(f"atlas full for {p.bone} piece {p.box}")
        offs.append((x, y))
        x += nw
        row = max(row, nh)
    return offs


def atlas_for(pieces, offs, skin_maps):
    """Paint one atlas per map (albedo, _s, _n) from the skin's matching maps."""
    alb = skin_maps[0]
    alpha = alb[..., 3] > 0
    out = []
    for k, m in enumerate(skin_maps):
        V = {g: views(m, alpha, g) for g in ("core", "arm")}
        img = np.zeros((ATLAS * PX, ATLAS * PX, m.shape[-1]), np.float32)
        for p, (u, v) in zip(pieces, offs):
            faces = piece_faces(p, V, m)
            w, h, d = p.size
            for name, (fx, fy, fw, fh) in face_rects(u, v, w, h, d).items():
                px = faces[name]
                X, Y = round(fx * PX), round(fy * PX)
                img[Y:Y + px.shape[0], X:X + px.shape[1]] = px
                if k == 0:
                    img[Y:Y + px.shape[0], X:X + px.shape[1], 3] = 255
        out.append(img)
    return out


# The walker's arms and legs bend at the elbow and knee (WalkerModel.ELBOW, KNEE): pieces that sit
# wholly below the joint hang off the forearm or shin so they follow it. Sampling is unchanged.
JOINTS = {"right_arm": ("right_forearm", (-1, 3, 0)), "left_arm": ("left_forearm", (1, 3, 0)),
          "right_leg": ("right_shin", (0, 6, 0)), "left_leg": ("left_shin", (0, 6, 0))}
MODEL_PIVOTS = dict(PIVOTS)
for _bone, (_lower, _off) in JOINTS.items():
    MODEL_PIVOTS[_lower] = tuple(PIVOTS[_bone][k] + _off[k] for k in range(3))


def model_bone(p):
    """The bone a piece hangs off in the jointed walker."""
    if p.bone in JOINTS:
        lower, _ = JOINTS[p.bone]
        if p.box[1] >= MODEL_PIVOTS[lower][1] - 0.01:
            return lower
    return p.bone


def java(all_pieces, all_offs):
    lines = [
        "package com.zomboidcraft.client.zombie;",
        "",
        "import java.util.List;",
        "import java.util.Map;",
        "import net.minecraft.client.model.geom.PartPose;",
        "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
        "import net.minecraft.client.model.geom.builders.PartDefinition;",
        "",
        "/**",
        " * Every outfit's 3D add-on pieces, generated by tools/gen_walker_addons.py from the outfit",
        " * sheets. Do not edit by hand: change the generator and run it again.",
        " */",
        "public final class WalkerAddonParts {",
        f"    public static final int TEX_SIZE = {ATLAS};",
        "",
        "    private WalkerAddonParts() {",
        "    }",
        "",
        "    /** Piece names per outfit; each piece is named outfit_index and hangs off one bone. */",
        "    public static final Map<String, List<String>> PIECES = Map.ofEntries(",
    ]
    entries = []
    for o, pieces in all_pieces.items():
        names = ", ".join(f'"{o}_{i}"' for i in range(len(pieces)))
        entries.append(f'            Map.entry("{o}", List.of({names}))')
    lines.append(",\n".join(entries) + ");")
    lines += [
        "",
        "    /** Adds every outfit's pieces to the bones. */",
        "    public static void define(Map<String, PartDefinition> bones) {",
    ]
    for o, pieces in all_pieces.items():
        for i, (p, (u, v)) in enumerate(zip(pieces, all_offs[o])):
            bone = model_bone(p)
            bp = MODEL_PIVOTS[bone]
            piv = p.pivot if p.pivot is not None else bp
            x0, y0, z0, x1, y1, z1 = p.box
            w, h, d = p.size
            off = [piv[k] - bp[k] for k in range(3)]
            rx, ry, rz = (math.radians(a) for a in p.rot)
            pose = (f"PartPose.offsetAndRotation({off[0]:g}f, {off[1]:g}f, {off[2]:g}f, {rx:.4f}f, {ry:.4f}f, {rz:.4f}f)"
                    if any(p.rot) else f"PartPose.offset({off[0]:g}f, {off[1]:g}f, {off[2]:g}f)")
            lines.append(f'        bones.get("{bone}").addOrReplaceChild("{o}_{i}", CubeListBuilder.create().texOffs({u}, {v})'
                         f'.addBox({x0 - piv[0]:g}f, {y0 - piv[1]:g}f, {z0 - piv[2]:g}f, {w:g}f, {h:g}f, {d:g}f), {pose});')
    lines += ["    }", "", "    /** Which bone each piece hangs off, so pieces on a lost arm can be hidden. */",
              "    public static final Map<String, String> BONE = Map.ofEntries("]
    entries = []
    for o, pieces in all_pieces.items():
        for i, p in enumerate(pieces):
            entries.append(f'            Map.entry("{o}_{i}", "{model_bone(p)}")')
    lines.append(",\n".join(entries) + ");")
    lines.append("}")
    JAVA.write_text("\n".join(lines) + "\n")


def load(path, mode):
    return np.asarray(Image.open(path).convert(mode), np.float32)


def main():
    skin_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else TEX / "walker_hd"
    only = sys.argv[2].split(",") if len(sys.argv) > 2 else None
    all_pieces = outfit_pieces()
    all_offs = {o: pack(p) for o, p in all_pieces.items()}
    java(all_pieces, all_offs)
    hd_dir, lo_dir = TEX / "walker_addons_hd", TEX / "walker_addons"
    hd_dir.mkdir(exist_ok=True)
    lo_dir.mkdir(exist_ok=True)
    n = 0
    for o, pieces in all_pieces.items():
        if only and o not in only:
            continue
        for tone in TONES:
            for level in range(LEVELS):
                base = skin_dir / f"{o}_{tone}_{level}"
                if not Path(f"{base}.png").exists():
                    continue
                maps = [load(f"{base}.png", "RGBA"), load(f"{base}_s.png", "RGBA"), load(f"{base}_n.png", "RGBA")]
                alb, spec, nrm = atlas_for(pieces, all_offs[o], maps)
                name = f"{o}_{tone}_{level}"
                Image.fromarray(alb.clip(0, 255).astype(np.uint8), "RGBA").save(hd_dir / f"{name}.png")
                Image.fromarray(spec.clip(0, 255).astype(np.uint8), "RGBA").save(hd_dir / f"{name}_s.png")
                Image.fromarray(nrm.clip(0, 255).astype(np.uint8), "RGBA").save(hd_dir / f"{name}_n.png")
                if level == 0:
                    lo = Image.fromarray(alb.clip(0, 255).astype(np.uint8), "RGBA").resize((ATLAS, ATLAS), Image.BOX)
                    lo.save(lo_dir / f"{o}_{tone}.png")
                n += 1
    # for the three.js preview: pieces with their pose and texOffs
    preview = {}
    for o, pieces in all_pieces.items():
        preview[o] = [{"bone": p.bone, "box": p.box, "pivot": p.pivot or PIVOTS[p.bone], "rot": p.rot,
                       "uv": all_offs[o][i]} for i, p in enumerate(pieces)]
    (ROOT / "tools/mockup/walker_addons.json").write_text(json.dumps({"size": ATLAS, "pivots": PIVOTS, "outfits": preview}))
    print(f"{sum(len(p) for p in all_pieces.values())} pieces, {n} atlases")


if __name__ == "__main__":
    main()
