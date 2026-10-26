"""Modular guns: a bare body plus parts in slots, written as one item model that draws whatever is fitted.

A gun's item model is, for each pose (hip, aiming through each sight, the reload steps), a composite of
the body and one entry per slot. Each slot entry picks its part from the gun's custom model data strings,
one model key per slot in Attachments.Slot order: "none" for an empty slot, a part's key when one is
fitted. Anything else (an old stack with no model data at all) gets the slot's default part, so a rifle
from before attachments still shows its stock and magazine. A mounted optic folds the iron sights down.

Every part is its own block model (elements and the gun's texture, painted once for all of them); each pose
then has a tiny model per part that names it as parent and adds that pose's display transforms.

A gun can come in finishes (its own materials: blued or nickel, plain or figured walnut): each finish paints
the same texture layout, its part models name the first finish's as parent and swap the texture, and the
item model picks the finish by the model data's string after the slot keys.

For the weapon screen there's a ghost model per slot (the slot's stand-in part, drawn in a flat green
glass texture), and gun_slots/<gun>.json saying where each slot's mount point is and what fits it.
"""
import json
import math
import zlib

import numpy as np

import model as M
import paint as P
import pbr
import toon

NS = 'zomboidcraft'
SLOTS = ['optic', 'muzzle', 'underbarrel', 'side_rail', 'stock', 'magazine', 'bipod', 'right_rail']    # Attachments.Slot order
FINISH = len(SLOTS)     # the model data string after the slot keys: the gun's finish
PX = 16

# how a part drawn on a top rail turns to sit on each face (columns: where its x, y, z end up)
FACING = {
    'top': np.eye(3), 'axial': np.eye(3),
    'bottom': np.diag([-1.0, -1.0, 1.0]),
    'left': np.array([[0.0, -1.0, 0.0], [1.0, 0.0, 0.0], [0.0, 0.0, 1.0]]),
    'right': np.array([[0.0, 1.0, 0.0], [-1.0, 0.0, 0.0], [0.0, 0.0, 1.0]]),
}
AXES = 'xyz'


class Slot:
    def __init__(self, at, facing='top', fits=(), default=None, ghost=None):
        self.at = np.asarray(at, float)         # mount point, build frame
        self.facing = facing
        self.fits = list(fits)                  # part keys
        self.default = default                  # shown when the model data says nothing
        self.ghost = ghost or (fits[0] if fits else None)


class Part:
    """Something that goes in a slot: a model key, the item it comes from, how to draw it."""

    def __init__(self, key, item, title, slot, build, look, sight=None, note='', seed=None):
        self.key, self.item, self.title, self.slot = key, item, title, slot
        self.build, self.look, self.sight, self.note = build, look, sight, note
        self.seed = seed if seed is not None else zlib.crc32(key.encode()) % 900 + 100


class Gun:
    def __init__(self, name, title, body, look, hand, slots, parts, irons=None, aperture=None, flash=None,
                 seed=556, wear=0.4, handle=None, flash_size=1.0, finishes=None, opened=None):
        self.name, self.title, self.body, self.look = name, title, body, look
        self.hand = np.asarray(hand, float)     # centre of the shooting hand on the grip, build frame
        self.slots = slots                      # {slot name: Slot}
        self.parts = parts                      # {key: Part}, every part that fits somewhere
        self.irons = irons                      # fn(m, up) or None
        self.aperture = aperture                # rear sight's peep, build frame
        self.flash = flash                      # fn(m, muzzle part key or None)
        self.seed, self.wear = seed, wear
        self.handle = handle                    # fn(m): the charging handle or slide, drawn apart so it can be worked
        self.flash_size = flash_size
        self.gap_flash = None                   # a second flash's point, build frame (a revolver's cylinder gap)
        # {key: (title, look)}: the finishes it comes in, the first the plainest (look is then the first's)
        self.finishes = finishes
        # (axis, degrees, origin): how the handle (a double's barrels, a revolver's cylinder) is turned to show it
        # opened in third person while it's loaded; the steps the game takes (22.5 degrees) limit it
        self.opened = opened

    def mount(self, key):
        part = self.parts[key]
        slot = self.slots[part.slot]
        return FACING[slot.facing], slot.at

    def sight_point(self, key=None):
        """Where the eye lines up: the irons' peep, or a fitted optic's reticle (build frame)."""
        if key is None:
            return self.aperture
        R, t = self.mount(key)
        from inch import IN
        return R @ (IN * np.asarray(self.parts[key].sight, float)) + t

    def optics(self):
        s = self.slots.get('optic')
        return list(s.fits) if s else []


# ---------------------------------------------------------------- placing parts

def place(part, R, t):
    """A box turned by R (a quarter-turn rotation) and moved to t. Keeps what painting needs to see it in
    its own frame."""
    lo, hi = R @ part['lo'] + t, R @ part['hi'] + t
    q = dict(part, lo=np.minimum(lo, hi), hi=np.maximum(lo, hi))
    if part['rot']:
        a = AXES.index(part['rot']['axis'])
        v = R[:, a]
        b = int(np.argmax(np.abs(v)))
        q['rot'] = dict(axis=AXES[b], angle=part['rot']['angle'] * float(np.sign(v[b])),
                        origin=list(R @ np.asarray(part['rot']['origin'], float) + t))
    if 'axis' in part:
        q['axis'] = int(np.argmax(np.abs(R[:, part['axis']])))
    q['local'] = dict(part, lo=part['lo'].copy(), hi=part['hi'].copy())
    q['R'], q['t'] = R, np.asarray(t, float)
    return q


def drawn(name, build, R=np.eye(3), t=(0, 0, 0), look=None, seed=0, wear=0.4):
    """Draws a part in its own frame, places it, tidies it. Returns a Model in the build frame."""
    m = M.Model(name)
    build(m)
    out = M.Model(name)
    for i, p in enumerate(m.parts):
        q = place(p, R, t)
        q['paint'] = dict(look=look, seed=seed, idx=i, wear=wear)
        out.parts.append(q)
    out.tidy()
    return out


# ---------------------------------------------------------------- painting

TEXEL = 4.0             # screen pixels (at 1080p) a texel may cover when seen from an aiming eye
DENSITIES = (16, 24, 32, 48, 64, 96, 128)


def face_density(p, name, eyes, base):
    """Texture pixels per unit a face needs. Aiming puts the eye right behind the sight, so the faces it sees
    up close would show texels the size of fingernails at the bench's density: those get enough pixels that
    a texel covers about TEXEL screen pixels from the nearest eye that sees the face. eyes: build-frame points
    (each looking toward -z with the game's 70 degree hand view); everything else keeps `base`."""
    ax, sgn = M.FACES[name][:2]
    lo, hi = p['lo'], p['hi']
    n = np.zeros(3)
    n[ax] = sgn
    centre = (lo + hi) / 2
    centre[ax] = hi[ax] if sgn > 0 else lo[ax]
    want = base
    for eye in eyes:
        near = np.clip(eye, lo, hi)
        near[ax] = centre[ax]
        d_near, d_mid = eye - near, eye - centre
        if d_mid @ n <= 0 or d_near[2] <= 0.05:
            continue                                    # facing away, or behind the eye
        if abs(d_near[1]) > 0.9 * d_near[2] or abs(d_near[0]) > 1.5 * d_near[2]:
            continue                                    # outside the view
        dist = math.sqrt(np.linalg.norm(d_near) * np.linalg.norm(d_mid))
        need = 771.0 / max(dist, 0.3) / TEXEL         # 771 screen px per radian at 1080p, 70 degrees
        want = max(want, next((dd for dd in DENSITIES if dd >= need), DENSITIES[-1]))
    return want


def paint(models, px=PX, eyes=(), cache=None):
    """Paints every visible face of every model into one set of patches: colour, LabPBR specular and normal.
    Faces seen up close from an aiming eye get more pixels (see face_density). Each part is drawn in the
    character style (toon): an outline only round its outside, chipped edges. cache: shared between paintings
    of the same models (finishes, wear), so where each part's joints are is only worked out once."""
    old, M.PX = M.PX, px
    cache = {} if cache is None else cache
    patches = []
    try:
        for mi, m in enumerate(models):
            if ('pieces', mi) not in cache:
                cache[('pieces', mi)] = toon.pieces(m.parts)
            piece = cache[('pieces', mi)]
            for k, p in enumerate(m.parts):
                info = p['paint']
                R, t = p.get('R', np.eye(3)), p.get('t', np.zeros(3))
                local = p.get('local', p)
                look = info['look']
                mates = [q for j, q in enumerate(m.parts) if j != k and piece[j] == piece[k]]
                for name, (ax, sgn, ua, ud, va, vd) in M.FACES.items():
                    if name in p.get('skip', ()):
                        continue
                    M.PX = face_density(p, name, eyes, px) if eyes else px
                    M.PX = max(M.PX, look.get('_px', {}).get(p['mat'], 0))   # finishes with fine detail
                    pos, n, _edge, uv = M.face_grid(p, name)
                    key = (mi, k, name, M.PX)
                    if key not in cache:
                        covers = toon.cover(p, name, pos, n, mates, M.PX)
                        if toon.step_face(p, pos, n, mates):
                            covers = [np.ones(pos.shape[:2], bool)] * 4
                        cache[key] = covers, toon.outer_edge(p, name, covers, M.PX)
                    covers, edge = cache[key]
                    pos_l = (pos - t) @ R
                    n_l = R.T @ n
                    lname = next(fn for fn, (a2, s2, *_r) in M.FACES.items() if abs(n_l[a2] - s2) < 1e-6)
                    seed = info['seed'] + info['idx']
                    rng = np.random.default_rng(zlib.crc32(f'{seed}:{lname}'.encode()))
                    f = P.Face(pos_l, n_l, edge, local, rng, info['wear'], seed, uv)
                    mat = p['mat']
                    rgb = look[mat](f)
                    rgb = P.round_shade(f, rgb)
                    for fx in look.get('_after', []):
                        if mat not in ('dot', 'flash', 'lens', 'lens_green', 'lamp'):
                            rgb = fx(f, rgb)
                    lu, lv = p['hi'][ua] - p['lo'][ua], p['hi'][va] - p['lo'][va]
                    rgb, ink = toon.style(f, rgb, mat, M.PX, n[1], lu, lv)
                    rgb = np.clip(rgb, 0, 1)
                    seam = p.get('seam', ())                    # slices: no bevel where one meets the next
                    flat = (M.FACES[name][2] in seam, M.FACES[name][4] in seam)
                    s = pbr.spec(mat, f, rgb, look.get('_spec'))
                    s[..., 0] = np.where(ink, s[..., 0] * 0.5, s[..., 0])     # the outline is matt
                    patches.append(((mi, k), name, dict(rgb=rgb, s=s, n=pbr.normal(f, rgb, lu, lv, M.PX, bevel=0.12, tilt=0.6,
                                                                                   rel=True, flat=flat, cover=covers))))
    finally:
        M.PX = old
    return patches


def pack(patches):
    """Shelf-packs the patches (colour, specular, normal laid out the same) into the smallest square texture."""
    sizes = [(pt[2]['rgb'].shape[1] + 2, pt[2]['rgb'].shape[0] + 2) for pt in patches]
    order = sorted(range(len(patches)), key=lambda k: -sizes[k][1])
    for T in (32, 64, 128, 256, 512, 1024, 2048, 4096):
        x = y = shelf = 0
        spots, ok = {}, True
        for k in order:
            w, h = sizes[k]
            if x + w > T:
                x, y, shelf = 0, y + shelf, 0
            if w > T or y + h > T:
                ok = False
                break
            spots[k] = (x, y)
            x += w
            shelf = max(shelf, h)
        if ok:
            break
    img = {'rgb': np.zeros((T, T, 4)), 's': np.zeros((T, T, 4)), 'n': np.zeros((T, T, 4))}
    img['s'][..., 3] = 1
    img['n'][..., :] = (0.5, 0.5, 1, 1)
    uvs = {}
    for k, (x, y) in spots.items():
        key, name, layers = patches[k]
        h, w = layers['rgb'].shape[:2]
        for lname, arr in layers.items():
            padded = np.pad(arr, ((1, 1), (1, 1), (0, 0)), mode='edge')
            if lname == 'rgb':
                img['rgb'][y:y + h + 2, x:x + w + 2, :3] = padded
                img['rgb'][y:y + h + 2, x:x + w + 2, 3] = 1
            else:
                img[lname][y:y + h + 2, x:x + w + 2] = padded
        s = 16.0 / T
        uvs[(key, name)] = [round((x + 1) * s, 5), round((y + 1) * s, 5), round((x + 1 + w) * s, 5), round((y + 1 + h) * s, 5)]
    return img, uvs, T


def save_textures(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    M.save_png(img['rgb'], path)
    M.save_png(img['s'], path.with_name(path.stem + '_s.png'))
    M.save_png(img['n'], path.with_name(path.stem + '_n.png'))


def elements(m, mi, uvs, shift):
    """A model's elements in item space (moved by shift), faces mapped into the painted texture."""
    shifted = M.Model(m.name)
    sub = {}
    for k, p in enumerate(m.parts):
        q = dict(p, lo=p['lo'] + shift, hi=p['hi'] + shift)
        if p['rot']:
            q['rot'] = dict(p['rot'], origin=list(np.asarray(p['rot']['origin']) + shift))
        shifted.parts.append(q)
        for face in M.FACES:
            if ((mi, k), face) in uvs:
                sub[(k, face)] = uvs[((mi, k), face)]
    return M.elements_json(shifted, sub)


def shifted_model(models, shift):
    out = M.Model('fit')
    for m in models:
        for p in m.parts:
            q = dict(p, lo=p['lo'] + shift, hi=p['hi'] + shift)
            if p['rot']:
                q['rot'] = dict(p['rot'], origin=list(np.asarray(p['rot']['origin']) + shift))
            out.parts.append(q)
    return out


# ---------------------------------------------------------------- the item model

def ref(model):
    return {'type': 'minecraft:model', 'model': f'{NS}:item/{model}'}


EMPTY = {'type': 'minecraft:empty'}


def composite(models):
    models = [m for m in models if m is not EMPTY]
    if not models:
        return EMPTY
    return models[0] if len(models) == 1 else {'type': 'minecraft:composite', 'models': models}


def select(slot, cases, fallback):
    return {'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': SLOTS.index(slot),
            'cases': [{'when': k, 'model': v} for k, v in cases.items()], 'fallback': fallback}


def flash_entry(model):
    """The flash while the server has it lit, for everyone watching from outside. The shooter's own first person
    draws its flash itself, the instant the trigger is pulled, so the model leaves it out there."""
    seen = {'type': 'minecraft:select', 'property': 'minecraft:display_context',
            'cases': [{'when': ['firstperson_righthand', 'firstperson_lefthand'], 'model': EMPTY}], 'fallback': model}
    return {'type': 'minecraft:range_dispatch', 'property': 'minecraft:custom_model_data', 'index': 1,
            'entries': [{'threshold': 1, 'model': seen}], 'fallback': EMPTY}


FLASH_TEX = f'{NS}:item/weapon/muzzle_flash'


def flash_tip(gun):
    """Where the flash starts: the front of the bare muzzle on the bore line (item pixels, before the shift)."""
    lo, hi = drawn('flash', lambda m: gun.flash(m, None)).bounds()
    return np.array([(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, hi[2]])


def quiet_tip(gun):
    """Where a suppressed shot's smaller flash starts: the front of the suppressor's end cap, on the bore line (item
    pixels, before the shift). None for a gun that takes no suppressor."""
    key = next((k for k in gun.slots['muzzle'].fits if 'suppressor' in k), None) if 'muzzle' in gun.slots else None
    if key is None:
        return None
    R, t = gun.mount(key)
    lo, _hi = drawn(key, gun.parts[key].build, R, t).bounds()
    tip = flash_tip(gun)
    return np.array([tip[0], tip[1], lo[2]])


def star_flash(at, star):
    """A flat star of flame facing down the bore (a revolver's barrel-cylinder gap spits one sideways). Pixels."""
    x, y, z = (round(float(v), 4) for v in at)
    return {'shade': False, 'light_emission': 15,
            'from': [round(x - star, 4), round(y - star, 4), z], 'to': [round(x + star, 4), round(y + star, 4), z],
            'faces': {'north': {'uv': [0, 0, 8, 16], 'texture': '#0'}, 'south': {'uv': [0, 0, 8, 16], 'texture': '#0'}}}


def flash_model(tip, length=2.4, half=0.75, star=1.1):
    """A light muzzle flash: two crossed tongues of flame along the bore and a soft star facing down it, all
    see-through at the edges and lit by themselves. Pixels; the bore runs toward -z."""
    x, y, z = (round(float(v), 4) for v in tip)
    lit = {'shade': False, 'light_emission': 15}

    def tongue(angle):
        return dict(lit, **{'from': [x, round(y - half, 4), round(z - length, 4)], 'to': [x, round(y + half, 4), z],
                            'rotation': {'angle': angle, 'axis': 'z', 'origin': [x, y, z]},
                            'faces': {'east': {'uv': [8, 0, 16, 16], 'texture': '#0'},
                                      'west': {'uv': [16, 0, 8, 16], 'texture': '#0'}}})
    zs = round(z - 0.2, 4)
    face = dict(lit, **{'from': [round(x - star, 4), round(y - star, 4), zs], 'to': [round(x + star, 4), round(y + star, 4), zs],
                        'faces': {'north': {'uv': [0, 0, 8, 16], 'texture': '#0'},
                                  'south': {'uv': [0, 0, 8, 16], 'texture': '#0'}}})
    return {'gui_light': 'front', 'textures': {'0': FLASH_TEX, 'particle': FLASH_TEX},
            'elements': [tongue(45), tongue(-45), face]}


def write_glow_texture(assets):
    """The soft round glow the gun light and laser are drawn with (RailLights): white, bright in the middle and
    fading smoothly to nothing at the rim, the same in its colour and its alpha so it reads right added or blended.
    Its middle row is the profile across a beam."""
    N = 64
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    r = np.hypot(xx - N / 2, yy - N / 2) / (N / 2)
    g = np.clip(1 - r * r, 0, 1) ** 2.5
    img = np.repeat(g[..., None], 4, axis=2)
    path = assets / 'textures/effect/rail_glow.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    M.save_png(img, path)
    path.with_name('rail_glow.png.mcmeta').write_text(json.dumps({'texture': {'blur': True, 'clamp': True}}, indent=1) + '\n')


def write_flash_texture(assets):
    """The flash's picture: left half a soft five-pointed star (the flash seen down the bore), right half a short
    tongue of flame (seen from the side, the muzzle at its left). White-hot in the middle, through yellow to
    orange, fading to nothing at the edges; its LabPBR map makes it glow under a shader pack."""
    N = 64
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    rng = np.random.default_rng(7)
    img = np.zeros((N, 2 * N, 4))

    def colour(heat):
        orange, yellow, white = P.col('#ff7a1c'), P.col('#ffd36a'), P.col('#fff7dc')
        c = P.mix(orange, yellow, np.clip(heat / 0.5, 0, 1))
        return P.mix(c, white, np.clip((heat - 0.55) / 0.35, 0, 1))

    # the star
    dx, dy = (xx - N / 2) / (N / 2), (yy - N / 2) / (N / 2)
    r, a = np.hypot(dx, dy), np.arctan2(dy, dx)
    spikes = np.zeros((N, N))
    for k in range(5):
        at = k * 2 * np.pi / 5 + rng.uniform(-0.25, 0.25)
        reach = rng.uniform(0.7, 0.98)
        d = np.angle(np.exp(1j * (a - at)))
        spikes = np.maximum(spikes, np.exp(-(d / (0.16 * (1 - r) + 0.03)) ** 2) * np.clip(1 - r / reach, 0, 1))
    heat = np.clip(np.exp(-(r / 0.3) ** 2) + spikes * 0.85, 0, 1)
    img[:, :N, :3] = colour(heat)
    img[:, :N, 3] = np.clip(heat * 1.15, 0, 0.9) * (r < 1)

    # the tongue: u along the bore from the muzzle, v across it
    u, v = xx / N, (yy - N / 2) / (N / 2)
    width = (0.35 + 0.55 * np.exp(-((u - 0.3) / 0.28) ** 2)) * np.clip(1 - u, 0, 1) ** 0.5
    flick = 1 + 0.12 * np.sin(u * 23 + 1.3) * np.sin(u * 7)
    heat = np.clip(1 - np.abs(v) / np.maximum(width * flick, 1e-3), 0, 1) ** 1.3 * np.clip(1 - u, 0, 1) ** 0.8
    heat = np.clip(heat + np.exp(-((u / 0.12) ** 2 + (v / 0.3) ** 2)) * 0.6, 0, 1)
    img[:, N:, :3] = colour(heat)
    img[:, N:, 3] = np.clip(heat * 1.2, 0, 0.88)

    path = assets / 'textures/item/weapon/muzzle_flash.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    M.save_png(img, path)
    spec = np.zeros_like(img)
    spec[..., 1] = pbr.NONMETAL / 255.0
    spec[..., 3] = np.where(img[..., 3] > 0.02, 254 / 255.0, 1.0)     # emissive wherever it shows
    M.save_png(spec, path.with_name('muzzle_flash_s.png'))


def opened(m, axis, angle, origin):
    """A copy of a model with every element turned about origin, for a handle drawn open (only parts that aren't
    turned already can be: an element takes one turn). axis 'shift' moves it by origin instead (angle unused), for
    a handle whose elements are turned already (a revolver's cylinder, swung out)."""
    out = M.Model(m.name)
    if axis == 'shift':
        d = np.array(origin, float)
        for p in m.parts:
            q = dict(p, lo=p['lo'] + d, hi=p['hi'] + d)
            if p['rot']:
                q['rot'] = dict(p['rot'], origin=[float(v) for v in np.array(p['rot']['origin']) + d])
            out.parts.append(q)
        return out
    for p in m.parts:
        q = dict(p)
        if not p['rot']:
            q['rot'] = dict(axis=axis, angle=angle, origin=[float(v) for v in origin])
        out.parts.append(q)
    return out


# How worn a gun looks follows its condition (its damage): (skin, how worn it's painted, the damage it starts at).
# Clean through Pristine and Good, worn once it's Worn, battered from Battered down.
WEARS = [('', 0.12, 0.0), ('worn', 0.55, 0.4), ('battered', 1.0, 0.65)]


def write_gun(gun, assets, shift, displays, lang, eyes=()):
    """Writes everything for one gun. displays: fn(state, fit_model, sight_point) -> display dict.
    Every finish comes in each of the WEARS skins, laid out alike. Returns a summary line and the data the
    preview page reads."""
    seed = gun.seed
    fins = list(gun.finishes) if gun.finishes else [None]
    base_fin = fins[0]

    def skin(f, w):
        """The name a skin's texture and models go under: '' for the first finish, clean."""
        return '_'.join(x for x in ((f if f != base_fin else ''), w) if x)

    def finish_look(f):
        return gun.finishes[f][1] if f else gun.look

    # every finish shares one texture layout: each material gets the most texels any finish gives it
    px = {}
    for f in fins:
        for mat, d in finish_look(f).get('_px', {}).items():
            px[mat] = max(px.get(mat, 0), d)

    def build(look, wear):
        models = {'body': drawn('body', gun.body, look=look, seed=seed, wear=wear)}
        if gun.irons:
            models['irons_up'] = drawn('irons_up', lambda m: gun.irons(m, True), look=look, seed=seed + 1, wear=wear)
            models['irons_folded'] = drawn('irons_folded', lambda m: gun.irons(m, False), look=look, seed=seed + 1, wear=wear)
        for key, part in gun.parts.items():
            R, t = gun.mount(key)
            models[key] = drawn(key, part.build, R, t, part.look, part.seed, wear)
        if gun.handle:
            models['charging_handle'] = drawn('charging_handle', gun.handle, look=look, seed=seed + 3, wear=wear)
            if gun.opened:
                models['charging_handle_open'] = opened(models['charging_handle'], *gun.opened)
        return models

    base = f'weapon/{gun.name}'
    texs = {}
    cache = {}
    for f in fins:
        look = dict(finish_look(f), _px=px) if px else finish_look(f)
        for w, amount, _at in WEARS:
            fm = build(look, amount)
            painted = [n for n in fm if n != 'charging_handle_open']      # the open handle is the closed one, turned
            patches = paint([fm[n] for n in painted], eyes=eyes, cache=cache)
            img, fuvs, T = pack(patches)
            key = skin(f, w)
            if not key:
                models, uvs = fm, fuvs
            elif fuvs != uvs:
                raise ValueError(f'{gun.name}: skin {key} packs its texture differently')
            tex = f'item/weapon/{gun.name}' + (f'_{key}' if key else '')
            save_textures(img, assets / f'textures/{tex}.png')
            texs[key] = f'{NS}:{tex}'
    skins = [k for k in texs if k]
    names = list(models)
    for n in names:
        mi = painted.index('charging_handle' if n == 'charging_handle_open' else n)
        M.dump({'gui_light': 'front', 'textures': {'0': texs[''], 'particle': texs['']},
                'elements': elements(models[n], mi, uvs, shift)}, assets / f'models/item/{base}/{n}.json')
        for k in skins:
            M.dump({'parent': f'{NS}:item/{base}/{n}', 'textures': {'0': texs[k], 'particle': texs[k]}},
                   assets / f'models/item/{base}/{k}/{n}.json')
    if gun.flash:
        k = gun.flash_size
        flash = flash_model(flash_tip(gun) + shift, 2.4 * k, 0.75 * k, 1.1 * k)
        if gun.gap_flash is not None:
            flash['elements'].append(star_flash(gun.gap_flash + shift, 0.9 * k))
        M.dump(flash, assets / f'models/item/{base}/muzzle_flash.json')
        names_posed = names + ['muzzle_flash']
        quiet = quiet_tip(gun)
        if quiet is not None:
            # a suppressor still shows a small, short flash at the end of its can
            M.dump(flash_model(quiet + shift, 1.0 * k, 0.4 * k, 0.55 * k), assets / f'models/item/{base}/muzzle_flash_quiet.json')
            names_posed.append('muzzle_flash_quiet')
    else:
        names_posed = names

    # poses: the full gun (stock, magazine, the longest muzzle part) sets the size in the inventory
    fit_parts = [models['body']] + [models[k] for k in ('irons_up',) if k in models]
    for s in gun.slots.values():
        if s.default:
            fit_parts.append(models[s.default])
    if 'muzzle' in gun.slots and gun.slots['muzzle'].fits:
        fit_parts.append(models[gun.slots['muzzle'].fits[0]])
    fit = shifted_model(fit_parts, shift)
    states = ['hip', 'ads'] + [f'ads_{k}' for k in gun.optics()] + ['mag_out', 'mag_in', 'charge']
    shown = {}
    for st in states:
        optic = st[4:] if st.startswith('ads_') else None
        disp = displays(st, fit, gun.sight_point(optic) + shift)
        shown[st] = disp
        for n in names_posed:
            M.dump({'parent': f'{NS}:item/{base}/{n}', 'display': disp}, assets / f'models/item/{base}/{st}/{n}.json')
            for k in skins:
                if n in names:      # the flash has its own texture
                    M.dump({'parent': f'{NS}:item/{base}/{st}/{n}', 'textures': {'0': texs[k], 'particle': texs[k]}},
                           assets / f'models/item/{base}/{k}/{st}/{n}.json')

    def pose(st, key=''):
        r = lambda n: ref(f'{base}/{st}/{n}' if not key or n not in names else f'{base}/{key}/{st}/{n}')
        entries = [r('body')]
        if gun.handle:
            # first person draws the handle or slide itself: worked in a reload, run back by each shot, locked open;
            # others see a double's barrels or a revolver's cylinder open while it's loaded
            shown = r('charging_handle_open' if gun.opened and st in ('mag_out', 'mag_in') else 'charging_handle')
            entries.append({'type': 'minecraft:select', 'property': 'minecraft:display_context',
                            'cases': [{'when': 'firstperson_righthand', 'model': EMPTY}], 'fallback': shown})
        for slot_name in SLOTS:
            s = gun.slots.get(slot_name)
            if not s:
                continue
            if slot_name == 'optic':
                down = r('irons_folded') if gun.irons else EMPTY
                up = r('irons_up') if gun.irons else EMPTY
                if st.startswith('ads_'):
                    entries.append(composite([down, r(st[4:])]))
                else:
                    cases = {k: composite([down, r(k)]) for k in s.fits}
                    cases['none'] = up
                    entries.append(select('optic', cases, up))
            elif slot_name == 'muzzle':
                # a suppressor shrinks the flash to a small one at the end of its can
                cases = {k: composite([r(k), flash_entry(r('muzzle_flash_quiet' if 'suppressor' in k else 'muzzle_flash'))])
                         for k in s.fits}
                bare = flash_entry(r('muzzle_flash'))
                cases['none'] = bare
                entries.append(select('muzzle', cases, bare))
            elif slot_name == 'magazine':
                if st == 'mag_out':
                    continue    # first person draws the old magazine falling away and the new one coming up
                cases = {k: r(k) for k in s.fits}
                cases['none'] = EMPTY
                entries.append(select('magazine', cases, r(s.default) if s.default else EMPTY))
            else:
                cases = {k: r(k) for k in s.fits}
                cases['none'] = EMPTY
                entries.append(select(slot_name, cases, r(s.default) if s.default else EMPTY))
        return composite(entries)

    def tree(key):
        aim = select('optic', {k: pose(f'ads_{k}', key) for k in gun.optics()}, pose('ads', key)) if gun.optics() else pose('ads', key)
        return {'type': 'minecraft:range_dispatch', 'property': 'minecraft:custom_model_data', 'index': 0,
                'entries': [{'threshold': 1, 'model': pose('mag_out', key)}, {'threshold': 2, 'model': pose('mag_in', key)},
                            {'threshold': 3, 'model': pose('charge', key)}],
                'fallback': {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                             'on_true': aim, 'on_false': pose('hip', key)}}

    M.dump({'model': by_finish(fins, lambda f: by_wear(lambda w: tree(skin(f, w))))}, assets / f'items/{gun.name}.json')
    lang[f'item.{NS}.{gun.name}'] = gun.title
    for f in fins:
        if f:
            lang[f'finish.{NS}.{gun.name}.{f}'] = gun.finishes[f][0]
    # the loose pieces first person draws during a reload, in the gun's own model space (no display of their own)
    loose = {'mag': gun.slots['magazine'].default if 'magazine' in gun.slots else None,
             'charging_handle': 'charging_handle' if gun.handle else None,
             'flash': 'muzzle_flash' if gun.flash else None,
             'flash_quiet': 'muzzle_flash_quiet' if gun.flash and quiet_tip(gun) is not None else None}
    for name, key in loose.items():
        if not key:
            continue
        if name == 'charging_handle':
            # the slide, cylinder or barrels first person draws all the time: the gun's finish and wear (it's given
            # the gun's model data and damage)
            at = lambda k: ref(f'{base}/{k}/{key}' if k else f'{base}/{key}')
            model = by_finish(fins, lambda f: by_wear(lambda w: at(skin(f, w))))
        elif key in names:
            model = by_finish(fins, lambda f: ref(f'{base}/{key}' if f == base_fin else f'{base}/{f}/{key}'))
        else:
            model = ref(f'{base}/{key}')      # the flash has no finish
        M.dump({'model': model}, assets / f'items/{gun.name}_loose_{name}.json')

    # ghosts and the weapon screen's slot data
    slot_data = []
    for slot_name in SLOTS:
        s = gun.slots.get(slot_name)
        if not s:
            continue
        gname = f'{gun.name}_{slot_name}'
        gm = models[s.ghost]
        ghost_elements = []
        for p in gm.parts:
            q = dict(p, lo=p['lo'] + shift, hi=p['hi'] + shift)
            e = {'from': [round(v, 4) for v in q['lo']], 'to': [round(v, 4) for v in q['hi']],
                 'faces': {f: {'uv': [0, 0, 16, 16], 'texture': '#0'} for f in M.FACES if f not in p.get('skip', ())}}
            if p['rot']:
                e['rotation'] = {'angle': p['rot']['angle'], 'axis': p['rot']['axis'],
                                 'origin': [round(v, 4) for v in np.asarray(p['rot']['origin']) + shift]}
            if e['faces']:
                ghost_elements.append(e)
        M.dump({'gui_light': 'front', 'textures': {'0': f'{NS}:item/weapon/ghost', 'particle': f'{NS}:item/weapon/ghost'},
                'elements': ghost_elements, 'display': shown['hip']}, assets / f'models/item/weapon/ghost/{gname}.json')
        M.dump({'model': ref(f'weapon/ghost/{gname}')}, assets / f'items/weapon/ghost/{gname}.json')
        at = s.at + shift
        slot_data.append({'slot': slot_name, 'mount': [round(v, 3) for v in at],
                          'ghost': f'{NS}:weapon/ghost/{gname}',
                          'fits': [f'{NS}:{gun.parts[k].item}' for k in s.fits],
                          'default': f'{NS}:{gun.parts[s.default].item}' if s.default else None})
    M.dump({'gun': f'{NS}:{gun.name}', 'note': 'Generated by tools/weapons/weapons.py. Mount points are in item '
            'model pixels, before the display transform.', 'slots': slot_data}, assets / f'gun_slots/{gun.name}.json')
    return f'{gun.name}: {sum(len(m.parts) for m in models.values())} boxes in {len(models)} parts, {T}px', slot_data


def by_wear(model):
    """The model for how worn the gun is (see WEARS), picked by its damage."""
    return {'type': 'minecraft:range_dispatch', 'property': 'minecraft:damage',
            'entries': [{'threshold': at, 'model': model(w)} for w, _a, at in WEARS[1:]], 'fallback': model('')}


def by_finish(fins, model):
    """The model for each finish, picked by the model data's finish string (the first finish when there's none)."""
    if len(fins) == 1:
        return model(fins[0])
    return {'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': FINISH,
            'cases': [{'when': f, 'model': model(f)} for f in fins[1:]], 'fallback': model(fins[0])}


def write_part_items(parts, assets, display, lang, file=lambda part: part.key):
    """Each attachment's own item model (how it looks in the inventory and dropped), painted in its own frame
    with the same seeds as on the gun so it looks the same. file names its model and texture."""
    for part in parts:
        m = drawn(part.key, part.build, look=part.look, seed=part.seed)
        lo, hi = m.bounds()
        shift = np.array([8.0, 8.0, 8.0]) - (lo + hi) / 2
        patches = paint([m])
        img, uvs, T = pack(patches)
        tex = f'item/weapon/part/{file(part)}'
        save_textures(img, assets / f'textures/{tex}.png')
        fit = shifted_model([m], shift)
        M.dump({'gui_light': 'front', 'textures': {'0': f'{NS}:{tex}', 'particle': f'{NS}:{tex}'},
                'elements': elements(m, 0, uvs, shift), 'display': display(fit)},
               assets / f'models/item/weapon/part/{file(part)}.json')
        M.dump({'model': ref(f'weapon/part/{file(part)}')}, assets / f'items/{part.item}.json')
        lang[f'item.{NS}.{part.item}'] = part.title


def write_ghost_texture(assets):
    """Flat green glass with a brighter rim on every face: the weapon screen's empty-slot look."""
    T = 16
    img = np.zeros((T, T, 4))
    img[..., :3] = P.col('#57e389')
    img[..., 3] = 0.32
    rim = np.zeros((T, T), bool)
    rim[0, :] = rim[-1, :] = rim[:, 0] = rim[:, -1] = True
    img[rim, :3] = P.col('#b7f7c9')
    img[rim, 3] = 0.85
    path = assets / 'textures/item/weapon/ghost.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    M.save_png(img, path)
