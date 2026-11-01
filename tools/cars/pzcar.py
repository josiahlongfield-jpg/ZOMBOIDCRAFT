#!/usr/bin/env python3
"""Turns a Project Zomboid vehicle (a Workshop mod's scripts, FBX models and textures) into a ZomboidCraft vehicle.

    python3 tools/cars/pzcar.py VEHICLE OUT_DIR --scripts DIR [DIR ...] --assets DIR [DIR ...]
        [--id ID] [--skin N] [--kind car] [--skip PART ...] [--toon] [--preview DIR]

VEHICLE is the script's vehicle name (e.g. 89defender110). --scripts are the mod's script folders (the B42 version
folder first, then common/, then any library it leans on, such as damnlib); --assets are folders searched, by file
name, for the FBX models and PNG textures the scripts name. This writes OUT_DIR/ID.zcar and ID.json, which go in
config/zomboidcraft/vehicles/ on each computer that's to have it (CarModels.LOCAL). The mod's files are other
people's work kept for Josiah's own copy: neither they nor what's made from them go in this repository or the jar.

How Zomboid builds a vehicle, and so how this does:
  - the vehicle's own `model` (its body, scaled and offset), then every part's first model (doors, hood, glass,
    seats, bumpers...), parts pulled in through `template = ...` lines as Zomboid does; armour and the other
    add-ons the mod only fits now and then are left off (--skip adds to them)
  - a model's mesh is `file|node` in an FBX; with no texture of its own it wears the vehicle's skin (--skin picks
    which of the vehicle's paints), and invertX mirrors it (the right-hand seats)
  - meshes are modelled nose toward -z with the left at -x: turned half round they face our way (nose +z, +x left)
  - the four wheels are the tyre part's first model, set at the script's wheel offsets (right side mirrored)
  - window and windshield parts are glass

Looks: usable (the skin as Zomboid shows it), abandoned (the mod's own rust texture worked in, dust and streaks),
wrecked (the mod's damage texture, scorching, half the glass gone) and burnt (charred, no glass). --toon paints the
same car in the mod's character style instead: flat bands, lit tops, shaded undersides and inked part edges.
"""
import argparse
import io
import json
import os
import struct
import sys
import zlib

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import fbx  # noqa: E402
from pzscript import Scripts, floats  # noqa: E402
from pack import CELL, VARIANTS, heights, java_hash, scramble  # noqa: E402
from aircraft import texels, weather  # noqa: E402
from wreck import fbm, sstep  # noqa: E402

# parts the mods fit only some of the time (random spawns, player builds): left off the stock car
SKIP = ('Armor', 'Snorkel', 'Roofrack', 'Cage', 'Tarp', 'HardCover', 'Mag', 'Gun', 'Turret', 'Trailer')
GLASS = ('Window', 'Windshield')
UP = 2  # the 512px Zomboid textures are doubled so the weathering has room to sit


class Finder:
    """Files by name, any case, under the asset folders."""

    def __init__(self, dirs):
        self.by = {}
        for d in dirs:
            for root, _, files in os.walk(d):
                for f in files:
                    self.by.setdefault(f.lower(), os.path.join(root, f))

    def mesh(self, path):
        base = path.replace('\\', '/').split('/')[-1].lower()
        for ext in ('.fbx', '.x'):
            if base + ext in self.by:
                return self.by[base + ext]
        return self.by.get(base)

    def texture(self, path):
        base = path.replace('\\', '/').split('/')[-1].lower()
        return self.by.get(base + '.png') or self.by.get(base)


def skips(name, extra):
    return any(k.lower() in name.lower() for k in SKIP + tuple(extra))


def collect(s, v, extra):
    """(part, model definition, the part's model block or None) for everything on the stock vehicle."""
    out = [('Body', s.models[v.block('model').kv['file']], None)]
    for pname, p in v.parts.items():
        if skips(pname, extra) or pname.startswith('Tire'):
            continue
        ms = p.blocks('model')
        if not ms:
            continue
        # most parts list their variants and fit one; a few (the body's interior pieces on the glove box) list
        # pieces that all show at once
        chosen = ms if pname == 'GloveBox' else ms[:1]
        for m in chosen:
            f = m.kv.get('file')
            if f in s.models:
                out.append((pname, s.models[f], m))
    return out


def rot_xyz(r):
    rx, ry, rz = np.radians(r)
    X = np.array([[1, 0, 0], [0, np.cos(rx), -np.sin(rx)], [0, np.sin(rx), np.cos(rx)]])
    Y = np.array([[np.cos(ry), 0, np.sin(ry)], [0, 1, 0], [-np.sin(ry), 0, np.cos(ry)]])
    Z = np.array([[np.cos(rz), -np.sin(rz), 0], [np.sin(rz), np.cos(rz), 0], [0, 0, 1]])
    return Z @ Y @ X


class Atlas:
    """Packs the textures the car wears into one image; each gets a rectangle, UVs are moved into it."""

    def __init__(self):
        self.images, self.slot = [], {}

    def add(self, key, img):
        if key not in self.slot:
            self.slot[key] = len(self.images)
            self.images.append(img.convert('RGB'))
        return self.slot[key]

    def pack(self):
        ims = [im.resize((im.width * UP, im.height * UP), Image.BICUBIC) for im in self.images]
        side = max(im.width for im in ims)
        # shelves of the widest texture's width
        cols = 2 if len(ims) > 1 else 1
        W = side * cols
        x = y = row = 0
        self.rect = []
        for im in ims:
            if x + im.width > W:
                x, y, row = 0, y + row, 0
            self.rect.append((x, y, im.width, im.height))
            x += im.width
            row = max(row, im.height)
        H = y + row
        H = 1 << int(np.ceil(np.log2(max(H, 1))))
        W = 1 << int(np.ceil(np.log2(W)))
        self.image = Image.new('RGB', (W, H), (40, 40, 40))
        for im, (x, y, _, _) in zip(ims, self.rect):
            self.image.paste(im, (x, y))
        self.W, self.H = W, H

    def uv(self, slot, UV):
        x, y, w, h = self.rect[slot]
        u = np.clip(UV[..., 0], 0, 1)
        v = np.clip(1 - UV[..., 1], 0, 1)
        # half a texel in from the rectangle's edge, so the neighbour never bleeds in
        u = (x + 0.5 + u * (w - 1)) / self.W
        v = (y + 0.5 + v * (h - 1)) / self.H
        return np.stack([u, v], -1)


def build(a):
    s = Scripts(a.scripts)
    v = s.vehicle(a.vehicle)
    find = Finder(a.assets)
    vm = v.block('model').kv
    vscale = float(vm.get('scale', '1').rstrip('fF'))
    voff = np.array(floats(vm.get('offset', '0 0 0'), 3))
    skins = [b.kv['texture'] for b in v.blocks('skin') if 'texture' in b.kv]
    skin = skins[a.skin % len(skins)] if skins else v.kv.get('texture')
    atlas = Atlas()
    cache = {}

    def mesh_of(spec):
        fname, _, node = spec.partition('|')
        path = find.mesh(fname)
        if path is None:
            raise FileNotFoundError(f'no model file for {fname}')
        if path not in cache:
            cache[path] = {k.lower(): m for k, m in fbx.load(path).items()}
        ms = cache[path]
        if node:
            m = ms.get(node.lower())
            if m is None:
                raise KeyError(f'{node} not in {os.path.basename(path)}')
            return m
        return {'P': np.concatenate([m['P'] for m in ms.values()]), 'UV': np.concatenate([m['UV'] for m in ms.values()])}

    def texture_slot(name):
        path = find.texture(name)
        if path is None:
            raise FileNotFoundError(f'no texture {name}')
        return atlas.add(path, Image.open(path))

    P_all, UV_all, slot_all, part_all = [], [], [], []
    missing = []
    for pname, mdef, pm in collect(s, v, a.skip):
        spec = mdef.kv.get('mesh')
        if not spec:
            continue
        try:
            m = mesh_of(spec)
            tex = mdef.kv.get('texture') or skin
            slot = texture_slot(tex)
        except (FileNotFoundError, KeyError) as e:
            missing.append(f'{pname}: {e}')
            continue
        P = m['P'] * float(mdef.kv.get('scale', '1').rstrip('fF'))
        UV = m['UV']
        if mdef.kv.get('invertX', '').upper() == 'TRUE':
            P = P * np.array([-1, 1, 1])
            P, UV = P[:, ::-1], UV[:, ::-1]
        if pm is not None:
            ps = float(pm.kv.get('scale', '1').rstrip('fF'))
            P = P * ps
        # half round: nose to +z, left to +x
        # (--world keeps the body in the script's own units: Zomboid scales the whole vehicle by the model scale)
        P = P * np.array([-1, 1, -1]) * (1.0 if a.world else vscale)
        if pm is not None and ('offset' in pm.kv or 'rotate' in pm.kv):
            R = rot_xyz(floats(pm.kv.get('rotate', '0 0 0'), 3))
            P = P @ R.T + np.array(floats(pm.kv.get('offset', '0 0 0'), 3))
        P = P + (voff / vscale if a.world else voff)   # the model offset is in world units, after the model scale
        P_all.append(P)
        UV_all.append(UV)
        slot_all.append(np.full(len(P), slot))
        part_all.append(np.full(len(P), pname, object))
    # the wheels
    tyre = v.parts.get('TireFrontLeft')
    wdef = s.models[tyre.blocks('model')[0].kv['file']] if tyre is not None else None
    wm = mesh_of(wdef.kv['mesh'])
    wslot = texture_slot(wdef.kv.get('texture'))
    WP = wm['P'] * float(wdef.kv.get('scale', '1').rstrip('fF'))
    WP = WP - (WP.reshape(-1, 3).max(0) + WP.reshape(-1, 3).min(0)) / 2
    WP = WP * np.array([-1, 1, -1])
    wheels = []
    # wheels hang off the physics chassis, which sits at the centre-of-mass offset; the body model doesn't
    com = np.array(floats(v.kv.get('centerOfMassOffset', '0 0 0'), 3))
    widths = []
    for k, wb in enumerate(sorted(v.blocks('wheel'), key=lambda b: ['FrontLeft', 'FrontRight', 'RearLeft', 'RearRight'].index(b.name))):
        off = np.array(floats(wb.kv['offset'], 3)) + com
        r = float(wb.kv.get('radius', '0.35').rstrip('fF')) * a.tyre
        q, u = WP, wm['UV']
        if off[0] < 0:
            q = q * np.array([-1, 1, 1])
            q, u = q[:, ::-1], u[:, ::-1]
        # the mesh's own radius against the script's: the script's wins, it's what Zomboid rolls on
        mr = (WP[..., 1].max() - WP[..., 1].min()) / 2
        q = q * (r / mr) if mr > 0 else q
        P_all.append(q + off)
        UV_all.append(u)
        slot_all.append(np.full(len(q), wslot))
        part_all.append(np.full(len(q), f'wheel{k}', object))
        wheels.append((off, r, wb.kv.get('front', 'false').lower() == 'true'))
        widths.append(num(wb.kv.get('width')) or 0.25)
    if missing:
        print('left out (missing):', *missing, sep='\n  ')
    atlas.pack()
    P = np.concatenate(P_all)
    part = np.concatenate(part_all)
    slots = np.concatenate(slot_all)
    UV = np.zeros(P.shape[:2] + (2,))
    rawUV = np.concatenate(UV_all)
    for sl in np.unique(slots):
        m = slots == sl
        UV[m] = atlas.uv(sl, rawUV[m])
    # the ground: the tyres' bottoms
    # each wheel hangs its suspension's rest length below where it's fixed on (as Zomboid's physics has it), and
    # never higher than its arch allows: tucked up inside the wheel well with a hand's breadth of air over the tread
    # the arches are in the painted shell (the chassis, axles and floor in the interior pieces sit inside the wells)
    skin_slot = atlas.slot.get(find.texture(skin)) if skin else None
    body = np.array([not str(p).startswith('wheel') for p in part]) & (slots == skin_slot) & ~np.array(
        [any(g in str(p) for g in GLASS) for p in part])
    rest = num(v.kv.get('suspensionRestLength')) or 0.2
    travel = (num(v.kv.get('maxSuspensionTravelCm')) or 10) / 100
    # the body is laid over the wheels where its own arches are: Zomboid's model scale and offset don't always put
    # them over the script's wheels (the Defender's arches came out 0.9 of its wheelbase), so the body is scaled and
    # slid until the arch centres sit over the wheel centres
    fr = [w for w in wheels if w[2]]
    rr = [w for w in wheels if not w[2]]
    if fr and rr:
        x = abs(fr[0][0][0])
        runs = arches(P[body], x)

        def near(z):
            return min(runs, key=lambda rn: abs((rn[0] + rn[1]) / 2 - z)) if runs else None
        af, ar = near(fr[0][0][2]), near(rr[0][0][2])
        if af and ar and af != ar:
            cf, cr = (af[0] + af[1]) / 2, (ar[0] + ar[1]) / 2
            k = (fr[0][0][2] - rr[0][0][2]) / (cf - cr)
            if a.body_only and a.body:
                # the body at the size asked, its arches' middle over the wheels' middle
                k = a.body / vscale
                mid_a, mid_w = (cf + cr) / 2, (fr[0][0][2] + rr[0][0][2]) / 2
                cr, rz = mid_a, mid_w
            else:
                rz = rr[0][0][2]
            if 0.7 < k < 1.6:
                bodyall = np.array([not str(p).startswith('wheel') for p in part])
                Pb = P[bodyall]
                Pb[..., 2] = (Pb[..., 2] - cr) * k + rz
                Pb[..., 0] *= k
                ybase = Pb[..., 1].min()
                Pb[..., 1] = (Pb[..., 1] - ybase) * k + ybase
                P[bodyall] = Pb
                _FIT[v.name] = (k, cr, rz, ybase)
                # the tyres grow with the body, so they keep filling their arches (and match the spare)
                for j, (off, r, f) in enumerate(wheels if not a.body_only else []):
                    sel = part == f'wheel{j}'
                    P[sel] = (P[sel] - off) * k + off
                    wheels[j] = (off, r * k, f)
                print(f'body fitted to the wheels: arches at {cf:+.2f}/{cr:+.2f}, scaled {k:.3f}')
    fitted = []
    ys, tops = [], []
    for k, (off, r, f) in enumerate(wheels):
        y = off[1] - rest
        top = arch_top(P[body], np.array([off[0], y, off[2]]), r, widths[k])
        tops.append(top)
        y = top - 0.06 if top is not None else y
        if a.world:
            # never below the suspension's full travel: a car modelled with its tyres tucked into the
            # wings (most of them) keeps them there
            y = max(y, off[1] - travel / vscale)
        ys.append(y)
    # left and right of an axle hang alike: a stray low piece over one tyre (a mudflap, a lip) must not drop it alone
    for k, (off, r, f) in enumerate(wheels):
        for j, (o2, _, _) in enumerate(wheels):
            if j != k and abs(o2[2] - off[2]) < 0.05 and abs(o2[0] + off[0]) < 0.05:
                ys[k] = max(ys[k], ys[j])
    for k, (off, r, f) in enumerate(wheels):
        y, top = ys[k], tops[k]
        drop = off[1] - y
        sel = part == f'wheel{k}'
        P[sel] = P[sel] - np.array([0, drop, 0])
        fitted.append((np.array([off[0], y, off[2]]), r, f))
        if a.debug:
            print(f'wheel {k}: hung {drop:.3f} m (arch allows centre {top})')
    wheels = fitted
    ground = min(off[1] - r for off, r, _ in wheels)
    _GROUND[v.name] = ground
    P[..., 1] -= ground
    wheels = [(off - np.array([0, ground, 0]), r, f) for off, r, f in wheels]
    # --body: the body's size against the model's own (Zomboid draws it at its model scale, the fit above moves it
    # on); the whole vehicle grows with it, so the wheels stay in their arches
    if (a.body or a.world) and not a.body_only:
        k = _FIT[v.name][0] if v.name in _FIT else 1.0
        # --world: the body at F times the size Zomboid draws it (script units times the model scale)
        g = a.world * vscale / k if a.world else a.body / (vscale * k)
        zc = float(np.mean([off[2] for off, _, _ in wheels]))
        if a.body_only:
            # the frame and tyres stay as the script has them: only the body (and its seats) grows, about the
            # wheels' middle, standing on the same ground
            bodyall = np.array([not str(p).startswith('wheel') for p in part])
            Pb = P[bodyall]
            yb = Pb[..., 1].min()
            Pb[..., 0] *= g
            Pb[..., 1] = (Pb[..., 1] - yb) * g + yb
            Pb[..., 2] = (Pb[..., 2] - zc) * g + zc
            P[bodyall] = Pb
            _GROW[v.name] = (g, zc, yb)
        else:
            P[..., 0] *= g
            P[..., 1] *= g
            P[..., 2] = (P[..., 2] - zc) * g + zc
            wheels = [(np.array([off[0] * g, off[1] * g, (off[2] - zc) * g + zc]), r * g, f) for off, r, f in wheels]
            _GROW[v.name] = (g, zc, 0.0)
        print(f'body at {(a.world * vscale if a.world else a.body) * 100:.0f}% of its model: everything grown {g:.3f}')
    glass = np.array([any(g in str(p) for g in GLASS) for p in part])
    wheel_of = np.array([int(str(p)[5:]) if str(p).startswith('wheel') else -1 for p in part])
    interior = np.array([('Seat' in str(p)) or ('GloveBox' in str(p)) for p in part])
    write(a, v, P, UV, glass, wheel_of, interior, atlas, wheels, part)


def column_hits(T, x, z):
    """Heights where a vertical line through (x, z) crosses the triangles."""
    a, b, c = T[:, 0], T[:, 1], T[:, 2]
    d = (b[:, 2] - c[:, 2]) * (a[:, 0] - c[:, 0]) + (c[:, 0] - b[:, 0]) * (a[:, 2] - c[:, 2])
    ok = np.abs(d) > 1e-12
    d = np.where(ok, d, 1)
    l0 = ((b[:, 2] - c[:, 2]) * (x - c[:, 0]) + (c[:, 0] - b[:, 0]) * (z - c[:, 2])) / d
    l1 = ((c[:, 2] - a[:, 2]) * (x - c[:, 0]) + (a[:, 0] - c[:, 0]) * (z - c[:, 2])) / d
    l2 = 1 - l0 - l1
    ins = ok & (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
    return (l0 * a[:, 1] + l1 * b[:, 1] + l2 * c[:, 1])[ins]


def arches(T, x, step=0.025):
    """The wheel openings along one side: runs where the body's underside rises well above its sills."""
    lo, hi = T[..., 2].min(), T[..., 2].max()
    zs = np.arange(lo, hi, step)
    under = np.array([(lambda h: h.min() if len(h) else np.nan)(column_hits(T, x, z)) for z in zs])
    sill = np.nanpercentile(under, 25)
    high = under >= sill + 0.12            # risen (open air past the ends doesn't count)
    runs, start = [], None
    for k, hgt in enumerate(high):
        if hgt and start is None:
            start = k
        if (not hgt or k == len(high) - 1) and start is not None:
            end = k if not hgt else k + 1
            seg = under[start:end]
            if np.isfinite(seg).any():       # a gap with some body over it: an arch, not air past the bumper
                runs.append((zs[start], zs[end - 1]))
            start = None
    return runs


def arch_top(T, c, r, width):
    """The lowest bodywork over a wheel's tread: up columns across the tyre's top half, the lowest hit above its middle."""
    tops = []
    for fx in (-0.4, 0.0, 0.4):
        for fz in (-0.55, -0.3, 0.0, 0.3, 0.55):
            x, z = c[0] + fx * width, c[2] + fz * r
            h = column_hits(T, x, z)
            h = h[h > c[1]]
            if len(h):
                # the arch line above this spot, less the tyre's own curve there
                tops.append(h.min() - np.sqrt(max(r * r - (fz * r) ** 2, 0)))
    return min(tops) if tops else None


def toon(img, p, n, ok):
    """Flat painted bands, lit tops and shaded undersides, inked along the edges of each UV island."""
    a = np.asarray(img, float)
    # bands: a light blur then each channel stepped
    soft = np.asarray(img.filter(ImageFilter.MedianFilter(5)), float)
    lum = soft @ np.array([0.3, 0.59, 0.11])
    band = np.round(lum / 255 * 6) / 6 * 255
    col = soft * (band / np.maximum(lum, 1))[..., None]
    out = col.copy()
    nn = np.zeros(a.shape)
    nn[ok] = n
    out = out * (1 + 0.08 * (nn[..., 1] > 0.6))[..., None] * (1 - 0.15 * (nn[..., 1] < -0.4))[..., None]
    # ink where the island ends
    edge = ~ok
    grow = np.zeros_like(edge)
    for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        grow |= np.roll(edge, (dy * 2, dx * 2), (0, 1))
    inked = ok & grow
    out[inked] = (11, 11, 13)
    out[~ok] = a[~ok]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def write(a, v, P, UV, glass, wheel_of, interior, atlas, wheels, part=None):
    W, H = atlas.W, atlas.H
    base = np.asarray(atlas.image, float)
    body = wheel_of < 0
    tri, bary = texels(P, UV * np.array([W, H]), W, H)
    ok = tri >= 0
    t, w = tri[ok], bary[ok]
    pts = (P[t] * w[:, :, None]).sum(1)
    nrm = np.cross(P[t, 1] - P[t, 0], P[t, 2] - P[t, 0])
    nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-12)
    cls = np.where(interior[t], 'interior', np.where(glass[t], 'glass', 'body'))
    # the mod's rust texture, laid where the skin is (same UVs, same rectangle)
    rust = None
    rk = v.kv.get('textureRust')
    dmg = v.kv.get('textureDamage2Shell') or v.kv.get('textureDamage1Shell')
    find = Finder(a.assets)

    def over(name):
        path = find.texture(name) if name else None
        if not path:
            return None
        im = Image.open(path).convert('RGBA')
        im = im.resize((im.width * UP, im.height * UP), Image.BICUBIC)
        layer = np.zeros((H, W, 4))
        skin_slot = atlas.slot.get(find.texture([b.kv['texture'] for b in v.blocks('skin')][a.skin % max(1, len(v.blocks('skin')))]))
        if skin_slot is None:
            return None
        x, y, w_, h_ = atlas.rect[skin_slot]
        layer[y:y + h_, x:x + w_] = np.asarray(im.resize((w_, h_)), float)
        return layer

    rust, damage = over(rk), over(dmg)
    centre = np.array([(P[..., 0].min() + P[..., 0].max()) / 2, 0, (P[..., 2].min() + P[..., 2].max()) / 2])
    lids = lid_parts(P, part, body)
    on_lid = np.zeros(len(P), bool)
    for _, sel, _, _, _ in lids:
        on_lid |= sel

    buf = io.BytesIO()
    buf.write(b'ZCAR')
    buf.write(struct.pack('>ii', 3, len(VARIANTS)))
    for state in VARIANTS:
        rng = np.random.default_rng(java_hash(a.id + state) & 0xFFFFF)
        img = base.copy()
        if a.toon:
            img = np.asarray(toon(Image.fromarray(base.astype(np.uint8)), pts, nrm, ok), float)
        col = img[ok]
        if state == 'burnt':
            lum = col @ np.array([0.3, 0.59, 0.11])
            shade = np.clip(lum / max(np.median(lum), 1), 0.35, 1.8)
            col = weather(pts, nrm, col, cls, state, rng, ()) * shade[:, None] ** 0.7
        elif state != 'usable':
            if rust is not None:
                r = rust[ok]
                amt = {'abandoned': 0.75, 'wrecked': 0.55}[state]
                al = (r[:, 3:4] / 255) * amt * (cls != 'glass')[:, None]
                col = col * (1 - al) + r[:, :3] * al
            if state == 'wrecked' and damage is not None:
                d = damage[ok]
                al = (d[:, 3:4] / 255) * (cls == 'body')[:, None]
                col = col * (1 - al) + d[:, :3] * al
            col = weather(pts, nrm, col, cls, state, rng, ())
        img[ok] = col
        im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
        keep = np.ones(len(P), bool)
        if state == 'wrecked':
            keep &= ~(glass & (rng.random(len(P)) < 0.5))
        if state == 'burnt':
            keep &= ~glass
        jpg = io.BytesIO()
        im.save(jpg, 'JPEG', quality=88, subsampling=0)
        jpg = jpg.getvalue()
        nb = state.encode()
        buf.write(struct.pack('>H', len(nb)))
        buf.write(nb)
        buf.write(struct.pack('>i', len(jpg)))
        buf.write(jpg)
        m = keep & body & ~on_lid
        mesh(buf, P[m], UV[m], glass[m], centre)
        buf.write(struct.pack('>i', len(lids)))
        for kind, sel, pivot, y_axis, angle in lids:
            buf.write(struct.pack('>b4f', 1 if y_axis else 0, *(pivot - centre), angle))
            mm = keep & sel
            mesh(buf, P[mm], UV[mm], glass[mm], centre)
        buf.write(struct.pack('>i', 4))
        for k, (c, r, steers) in enumerate(wheels):
            buf.write(struct.pack('>4fb', *(np.asarray(c) - centre), r, 1 if steers else 0))
            mm = keep & (wheel_of == k)
            mesh(buf, P[mm], UV[mm], glass[mm], centre)
        if a.preview:
            os.makedirs(a.preview, exist_ok=True)
            im.save(os.path.join(a.preview, f'{a.id}_{state}.jpg'), quality=90)
        print(f'  {state}: {keep.sum()} triangles, texture {len(jpg) // 1024} KB', flush=True)
    blob = scramble(zlib.compress(buf.getvalue(), 9), a.id)
    os.makedirs(a.out, exist_ok=True)
    with open(os.path.join(a.out, a.id + '.zcar'), 'wb') as f:
        f.write(blob)
    Q = P[body] - centre
    glo, nx, nz, grid = heights(Q)
    lo, hi = P.reshape(-1, 3).min(0), P.reshape(-1, 3).max(0)
    size = hi - lo
    roof = hi[1]
    seats, handles = [], []
    # a seat named again later (the vehicle's own after its template's) is the one Zomboid keeps
    last = {}
    for b in v.blocks('passenger'):
        if '*' not in b.name and b.block('position', 'inside') is not None:
            last[b.name] = b
    com = np.array(floats(v.kv.get('centerOfMassOffset', '0 0 0'), 3))
    for b in last.values():
        seat = None if part is None else (part == 'Seat' + b.name)
        if seat is not None and seat.any():
            # sat where the mod's own seat model is: eyes a little over its backrest, the door handle on the
            # bodywork beside it at hand height (the mirrors left out of the width)
            q = P[seat].reshape(-1, 3)
            lo_s, hi_s = q.min(0), q.max(0)
            cushion = lo_s[1] + 0.35 * (hi_s[1] - lo_s[1])
            o = np.array([(lo_s[0] + hi_s[0]) / 2, cushion, (lo_s[2] + hi_s[2]) / 2])
            eye = [o[0], min(roof - 0.18, hi_s[1] + 0.12), o[2] + 0.10]
            # whoever sits up front looks out through the middle of the windscreen, not along the roof's edge
            ws = part == 'Windshield'
            if ws.any() and 'Front' in b.name:
                wq = P[ws].reshape(-1, 3)
                eye[1] = min(eye[1], wq[:, 1].min() + 0.45 * (wq[:, 1].max() - wq[:, 1].min()))
                front_eye = eye[1]
            shell = P[(wheel_of < 0) & ~interior & ~glass].reshape(-1, 3)
            near = shell[(np.abs(shell[:, 2] - o[2]) < 0.35) & (shell[:, 1] > cushion) & (shell[:, 1] < cushion + 0.6)]
            half = np.abs(near[:, 0]).max() if len(near) else size[0] / 2
            hy = cushion
            # the seat's own door (the middle one in the back gets in by the door on its right): its handle sits
            # toward the door's back edge, a little under halfway up
            name = b.name.replace('Middle', 'Right')
            door = part == 'Door' + name
            if door.any():
                dq = P[door].reshape(-1, 3)
                dlo, dhi = dq.min(0), dq.max(0)
                half = max(abs(dlo[0]), abs(dhi[0]))
                hy = dlo[1] + 0.45 * (dhi[1] - dlo[1])
                o = np.array([o[0], o[1], dlo[2] + 0.2])
        else:
            pos = b.block('position', 'inside')
            o = fit_point(v, np.array(floats(pos.kv.get('offset', '0 0 0'), 3))) - np.array([0, ground_of(v), 0])
            if v.name in _GROW:
                g, zc, yb = _GROW[v.name]
                o = np.array([o[0] * g, (o[1] - yb) * g + yb, (o[2] - zc) * g + zc])
            eye = [o[0], min(roof - 0.22, o[1] + 0.78), o[2]]
            half = size[0] / 2
            hy = o[1] + 0.45
        seats.append([round(float(eye[0] - centre[0]), 3), round(float(eye[1]), 3), round(float(eye[2] - centre[2]), 3)])
        if seats and len(seats) > 2 and seats[0][1] < seats[-1][1]:
            seats[-1][1] = seats[0][1]
        side = 1 if o[0] > 0.05 else -1
        if 'Right' in b.name:
            side = -1
        elif 'Left' in b.name:
            side = 1
        if 'Middle' in b.name:
            # the middle of the back bench has no door of its own: climbed into from behind, by the tailgate
            # the middle of the back bench has no door of its own: in by the right-hand back door, aimed a little
            # further forward than that door's own seat (the back of the car is the boot's)
            handles.append(None)    # filled in below, once the right-hand back door's handle is known
            continue
        handles.append([round(float(side * (half + 0.05)), 3), round(float(hy), 3), round(float(o[2] - centre[2]), 3)])
    right = [h for h in handles if h is not None and h[0] < -0.05]
    back_right = min(right, key=lambda h: h[2]) if right else [round(float(-size[0] / 2 - 0.05), 3), 1.0, 0.0]
    handles = [h if h is not None else [back_right[0], back_right[1], round(back_right[2] + 0.35, 3)] for h in handles]
    glovebox = None
    ws = part == 'Windshield' if part is not None else None
    if ws is not None and ws.any() and len(seats) > 1:
        # in front of the front passenger, under the dash top, a little back from the windscreen's foot
        wq = P[ws].reshape(-1, 3)
        glovebox = [seats[1][0], round(float(wq[:, 1].min() - 0.25), 3), round(float(wq[:, 2].max() - 0.35 - centre[2]), 3)]
    fronts = [c for c, _, f in wheels if f]
    rears = [c for c, _, f in wheels if not f]
    wb = float(np.mean([c[2] for c in fronts]) - np.mean([c[2] for c in rears])) if fronts and rears else 2.5
    row = {'id': a.id, 'kind': a.kind, 'weight': a.weight, 'size': [round(float(x), 3) for x in size],
           'cell': CELL, 'x0': round(float(glo[0]), 3), 'z0': round(float(glo[2]), 3), 'nx': nx, 'nz': nz,
           'heights': [int(round(h * 100)) for h in grid.ravel()],
           'seats': seats, 'handles': handles, 'wheelbase': round(wb, 3),
           'glovebox': glovebox, 'lidKinds': [k for k, _, _, _, _ in lids] or None,
           'wheels': [[round(float(c[0] - centre[0]), 3), round(float(c[2] - centre[2]), 3), round(float(r), 3)]
                      for c, r, _ in wheels],
           'zomboid': {'vehicle': a.vehicle, 'mass': num(v.kv.get('mass')), 'engineForce': num(v.kv.get('engineForce')),
                       'maxSpeed': num(v.kv.get('maxSpeed')), 'seats': num(v.kv.get('seats')),
                       'engine': (v.block('sound').kv.get('engine') if v.block('sound') else None),
                       'horn': (v.block('sound').kv.get('horn') if v.block('sound') else None)}}
    with open(os.path.join(a.out, a.id + '.json'), 'w') as f:
        json.dump(row, f, separators=(',', ':'))
    print(f'{a.id:20s} {len(blob) // 1024:5d} KB  {size[0]:.2f} x {size[1]:.2f} x {size[2]:.2f} m, {len(seats)} seats', flush=True)


_GROUND = {}
_FIT = {}
_GROW = {}


def fit_point(v, p):
    """A point given in Zomboid's vehicle space, moved as the body was when it was fitted over the wheels."""
    if v.name not in _FIT:
        return p
    k, cr, wz, ybase = _FIT[v.name]
    return np.array([p[0] * k, (p[1] - ybase) * k + ybase, (p[2] - cr) * k + wz])


def ground_of(v):
    return _GROUND.get(v.name, 0.0)


LID_PARTS = {'bonnet': ('EngineDoor',), 'boot': ('TrunkDoor',)}
# what rides on a boot door or bonnet and swings with it: a back door's window, a spare wheel hung on it
LID_RIDERS = ('WindshieldRear', 'DAMNSpareTire', 'WindowBackLeft', 'WindowBackRight')


def lid_parts(P, part, body):
    """The bonnet and the boot lid (or back door) as lids: (kind, triangles, hinge, turns about y, full swing)."""
    out = []
    if part is None:
        return out
    pn = np.array([str(p) for p in part])
    for kind, names in LID_PARTS.items():
        sel = np.isin(pn, names) & body
        if not sel.any():
            continue
        q = P[sel].reshape(-1, 3)
        lo, hi = q.min(0), q.max(0)
        pad = np.array([0.05, 0.05, 0.3])
        # a rider (rear window, spare tyre on its carrier) goes whole when its own centre is by the lid,
        # so a tyre that sticks out past the door never leaves half of itself behind
        for rn in LID_RIDERS:
            rm = (pn == rn) & body
            if rm.any():
                r = P[rm].reshape(-1, 3)
                rc = (r.min(0) + r.max(0)) / 2
                if np.all((rc > lo - pad) & (rc < hi + pad)):
                    sel = sel | rm
        w, h, d = hi - lo
        if kind == 'bonnet':
            # hinged at its back edge by the windscreen, lifting from the front
            out.append((kind, sel, np.array([0.0, hi[1], lo[2]]), False, -1.1))
        elif h > 0.6 * w and d < 0.5 * h:
            # an upright back door: hinged down its right-hand edge, swinging out and round
            out.append((kind, sel, np.array([lo[0], 0.0, lo[2]]), True, 1.6))
        else:
            # a boot lid: hinged at its front edge under the back window, lifting from the back
            out.append((kind, sel, np.array([0.0, hi[1], hi[2]]), False, 1.2))
    return out


def num(s):
    if s is None:
        return None
    try:
        return float(str(s).rstrip('fF'))
    except ValueError:
        return None


def mesh(buf, T, UV, glass, centre):
    order = np.concatenate([np.where(~glass)[0], np.where(glass)[0]])
    Q = np.round((T[order] - centre) * 1000).astype('>i2').reshape(-1, 3)
    U = np.round(np.clip(UV[order], 0, 1) * 65535).astype('>u2').reshape(-1, 2)
    buf.write(struct.pack('>ii', int((~glass).sum()), int(glass.sum())))
    buf.write(np.concatenate([Q.view('>u2'), U], 1).astype('>u2').tobytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('vehicle')
    ap.add_argument('out')
    ap.add_argument('--scripts', nargs='+', required=True)
    ap.add_argument('--assets', nargs='+', required=True)
    ap.add_argument('--id')
    ap.add_argument('--skin', type=int, default=0)
    ap.add_argument('--kind', default='car')
    ap.add_argument('--weight', type=int, default=2)
    ap.add_argument('--body-only', action='store_true', help='--body grows the body alone, not the frame and tyres')
    ap.add_argument('--body', type=float, help="the body's size against its own model (1.1 = 110%%)")
    ap.add_argument('--world', type=float, help="the body at this many times the size Zomboid draws it (the Defender's 130%% is 1.444)")
    ap.add_argument('--tyre', type=float, default=1.0, help='tyres at this share of the size the script gives them')
    ap.add_argument('--skip', nargs='*', default=[])
    ap.add_argument('--toon', action='store_true')
    ap.add_argument('--preview')
    ap.add_argument('--debug', action='store_true')
    a = ap.parse_args()
    a.id = a.id or ('pz_' + a.vehicle.lower())
    build(a)


if __name__ == '__main__':
    main()
