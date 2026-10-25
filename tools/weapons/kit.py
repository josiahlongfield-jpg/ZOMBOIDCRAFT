"""Modular guns: a bare body plus parts in slots, written as one item model that draws whatever is fitted.

A gun's item model is, for each pose (hip, aiming through each sight, the reload steps), a composite of
the body and one entry per slot. Each slot entry picks its part from the gun's custom model data strings,
one model key per slot in Attachments.Slot order: "none" for an empty slot, a part's key when one is
fitted. Anything else (an old stack with no model data at all) gets the slot's default part, so a rifle
from before attachments still shows its stock and magazine. A mounted optic folds the iron sights down.

Every part is its own block model (elements and the gun's texture, painted once for all of them); each pose
then has a tiny model per part that names it as parent and adds that pose's display transforms.

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

NS = 'zomboidcraft'
SLOTS = ['optic', 'muzzle', 'underbarrel', 'side_rail', 'stock', 'magazine', 'bipod']    # Attachments.Slot order
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
                 seed=556, wear=0.4, handle=None):
        self.name, self.title, self.body, self.look = name, title, body, look
        self.hand = np.asarray(hand, float)     # centre of the shooting hand on the grip, build frame
        self.slots = slots                      # {slot name: Slot}
        self.parts = parts                      # {key: Part}, every part that fits somewhere
        self.irons = irons                      # fn(m, up) or None
        self.aperture = aperture                # rear sight's peep, build frame
        self.flash = flash                      # fn(m, muzzle part key or None)
        self.seed, self.wear = seed, wear
        self.handle = handle                    # fn(m): the charging handle, drawn apart so it can be worked

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


def paint(models, px=PX, eyes=()):
    """Paints every visible face of every model into one set of patches: colour, LabPBR specular and normal.
    Faces seen up close from an aiming eye get more pixels (see face_density)."""
    old, M.PX = M.PX, px
    patches = []
    try:
        for mi, m in enumerate(models):
            for k, p in enumerate(m.parts):
                info = p['paint']
                R, t = p.get('R', np.eye(3)), p.get('t', np.zeros(3))
                local = p.get('local', p)
                look = info['look']
                for name, (ax, sgn, ua, ud, va, vd) in M.FACES.items():
                    if name in p.get('skip', ()):
                        continue
                    M.PX = face_density(p, name, eyes, px) if eyes else px
                    pos, n, edge, uv = M.face_grid(p, name)
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
                    rgb = np.clip(rgb, 0, 1)
                    lu, lv = p['hi'][ua] - p['lo'][ua], p['hi'][va] - p['lo'][va]
                    patches.append(((mi, k), name, dict(rgb=rgb, s=pbr.spec(mat, f, rgb), n=pbr.normal(f, rgb, lu, lv, M.PX, bevel=0.12, tilt=0.6, rel=True))))
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
    return {'type': 'minecraft:range_dispatch', 'property': 'minecraft:custom_model_data', 'index': 1,
            'entries': [{'threshold': 1, 'model': model}], 'fallback': EMPTY}


def write_gun(gun, assets, shift, displays, lang, eyes=()):
    """Writes everything for one gun. displays: fn(state, fit_model, sight_point) -> display dict.
    Returns a summary line and the data the preview page reads."""
    look, seed, wear = gun.look, gun.seed, gun.wear
    models = {'body': drawn('body', gun.body, look=look, seed=seed, wear=wear)}
    if gun.irons:
        models['irons_up'] = drawn('irons_up', lambda m: gun.irons(m, True), look=look, seed=seed + 1, wear=wear)
        models['irons_folded'] = drawn('irons_folded', lambda m: gun.irons(m, False), look=look, seed=seed + 1, wear=wear)
    for key, part in gun.parts.items():
        R, t = gun.mount(key)
        models[key] = drawn(key, part.build, R, t, part.look, part.seed, wear)
    if gun.handle:
        models['charging_handle'] = drawn('charging_handle', gun.handle, look=look, seed=seed + 3, wear=wear)
    muzzles = [None] + (gun.slots['muzzle'].fits if 'muzzle' in gun.slots else [])
    for mk in muzzles:
        name = 'flash' if mk is None else f'flash_{mk}'
        models[name] = drawn(name, lambda m, mk=mk: gun.flash(m, mk), look=look, seed=seed + 2, wear=wear)

    names = list(models)
    patches = paint([models[n] for n in names], eyes=eyes)
    img, uvs, T = pack(patches)
    tex = f'item/weapon/{gun.name}'
    save_textures(img, assets / f'textures/{tex}.png')
    base = f'weapon/{gun.name}'
    for mi, n in enumerate(names):
        M.dump({'gui_light': 'front', 'textures': {'0': f'{NS}:{tex}', 'particle': f'{NS}:{tex}'},
                'elements': elements(models[n], mi, uvs, shift)}, assets / f'models/item/{base}/{n}.json')

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
        for n in names:
            M.dump({'parent': f'{NS}:item/{base}/{n}', 'display': disp}, assets / f'models/item/{base}/{st}/{n}.json')

    def pose(st):
        r = lambda n: ref(f'{base}/{st}/{n}')
        entries = [r('body')]
        if gun.handle:
            # while it's being worked, first person draws the handle itself (sliding back and snapping home)
            entries.append({'type': 'minecraft:select', 'property': 'minecraft:display_context',
                            'cases': [{'when': 'firstperson_righthand', 'model': EMPTY}], 'fallback': r('charging_handle')}
                           if st == 'charge' else r('charging_handle'))
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
                cases = {k: composite([r(k), flash_entry(r(f'flash_{k}'))]) for k in s.fits}
                bare = flash_entry(r('flash'))
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

    aim = select('optic', {k: pose(f'ads_{k}') for k in gun.optics()}, pose('ads')) if gun.optics() else pose('ads')
    M.dump({'model': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:custom_model_data', 'index': 0,
                      'entries': [{'threshold': 1, 'model': pose('mag_out')}, {'threshold': 2, 'model': pose('mag_in')},
                                  {'threshold': 3, 'model': pose('charge')}],
                      'fallback': {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                                   'on_true': aim, 'on_false': pose('hip')}}},
           assets / f'items/{gun.name}.json')
    lang[f'item.{NS}.{gun.name}'] = gun.title
    # the loose pieces first person draws during a reload, in the gun's own model space (no display of their own)
    loose = {'mag': gun.slots['magazine'].default if 'magazine' in gun.slots else None,
             'charging_handle': 'charging_handle' if gun.handle else None}
    for name, key in loose.items():
        if key:
            M.dump({'model': ref(f'{base}/{key}')}, assets / f'items/{gun.name}_loose_{name}.json')

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


def write_part_items(parts, assets, display, lang):
    """Each attachment's own item model (how it looks in the inventory and dropped), painted in its own frame
    with the same seeds as on the gun so it looks the same."""
    for part in parts:
        m = drawn(part.key, part.build, look=part.look, seed=part.seed)
        lo, hi = m.bounds()
        shift = np.array([8.0, 8.0, 8.0]) - (lo + hi) / 2
        patches = paint([m])
        img, uvs, T = pack(patches)
        tex = f'item/weapon/part/{part.key}'
        save_textures(img, assets / f'textures/{tex}.png')
        fit = shifted_model([m], shift)
        M.dump({'gui_light': 'front', 'textures': {'0': f'{NS}:{tex}', 'particle': f'{NS}:{tex}'},
                'elements': elements(m, 0, uvs, shift), 'display': display(fit)},
               assets / f'models/item/weapon/part/{part.key}.json')
        M.dump({'model': ref(f'weapon/part/{part.key}')}, assets / f'items/{part.item}.json')
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
