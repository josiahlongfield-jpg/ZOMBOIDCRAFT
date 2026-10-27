"""Box modelling, texture packing and item-model export for the weapon generator.

Models are written as Minecraft item models made of elements (boxes). Each face gets its own
patch of a per-weapon texture, painted by paint.py, at PX pixels per model unit.
"""
import json
import math
import numpy as np
from PIL import Image

import paint as P

PX = 8          # texture pixels per model unit (1/16 block); matches a 128x resource pack
LIMIT = (-16, 32)

FACES = {  # name: (normal axis, sign, u axis, u dir, v axis, v dir)
    'north': (2, -1, 0, -1, 1, -1),
    'south': (2, 1, 0, 1, 1, -1),
    'east': (0, 1, 2, -1, 1, -1),
    'west': (0, -1, 2, 1, 1, -1),
    'up': (1, 1, 0, 1, 2, 1),
    'down': (1, -1, 0, 1, 2, -1),
}


class Model:
    def __init__(self, name):
        self.name = name
        self.parts = []
        self.offset = np.zeros(3)     # how far shift() has moved it from the frame it was built in

    # ------------------------------------------------------------ primitives

    def box(self, lo, hi, mat, rot=None, **kw):
        lo, hi = np.array(lo, float), np.array(hi, float)
        a, b = np.minimum(lo, hi), np.maximum(lo, hi)
        part = dict(lo=a, hi=b, mat=mat, rot=rot, **kw)
        self.parts.append(part)
        return part

    def rod(self, y0, y1, r, mat, cx=0.0, cz=0.0, axis=1, **kw):
        """A round bar: three boxes in a stepped circle, shaded so it reads as round."""
        y0, y1 = min(y0, y1), max(y0, y1)
        others = [i for i in range(3) if i != axis]
        centre = {others[0]: cx, others[1]: cz}

        def mk(ra, rb, inset=0.0):
            lo, hi = [0, 0, 0], [0, 0, 0]
            lo[axis], hi[axis] = y0 + inset, y1 - inset
            lo[others[0]], hi[others[0]] = centre[others[0]] - ra, centre[others[0]] + ra
            lo[others[1]], hi[others[1]] = centre[others[1]] - rb, centre[others[1]] + rb
            return self.box(lo, hi, mat, round=r, axis=axis, **kw)

        if r < 0.3:
            return [mk(r, r)]
        return [mk(r, r * 0.45), mk(r * 0.45, r), mk(r * 0.74, r * 0.74)]

    def seg(self, p0, p1, w, t, mat, z=0.0, plane='xy', over=None, **kw):
        """A straight bar from p0 to p1 in the x-y plane (or z-y with plane='zy'), w wide, t thick.

        The direction snaps to 22.5 degree steps, the finest element rotation the game takes.
        """
        (x0, y0), (x1, y1) = p0, p1
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy)
        phi = math.degrees(math.atan2(dy, dx))
        snapped = round(phi / 22.5) * 22.5
        base = round(snapped / 90.0) * 90.0
        ang = snapped - base
        if ang > 45:
            ang -= 90
            base += 90
        mx, my = (x0 + x1) / 2, (y0 + y1) / 2
        ext = over if over is not None else w * 0.25
        half_l, half_w = L / 2 + ext, w / 2
        along_x = round(base / 90.0) % 2 == 0
        hx, hy = (half_l, half_w) if along_x else (half_w, half_l)
        a = 0 if plane == 'xy' else 2      # which model axis is the plane's horizontal
        lo, hi = [0, 0, 0], [0, 0, 0]
        lo[a], hi[a] = mx - hx, mx + hx
        lo[1], hi[1] = my - hy, my + hy
        d = 2 if plane == 'xy' else 0
        lo[d], hi[d] = z - t / 2, z + t / 2
        rot = None
        if abs(ang) > 1e-6:
            origin = [0, 0, 0]
            origin[a], origin[1], origin[d] = mx, my, z
            # positive angle about +z turns +x toward +y; about +x it turns +y toward +z
            rot = dict(axis='z' if plane == 'xy' else 'x', angle=ang if plane == 'xy' else -ang, origin=origin)
        return self.box(lo, hi, mat, rot=rot, axis=a if along_x else 1, **kw)

    def arc(self, centre, radius, a0, a1, w, t, mat, steps=None, z=0.0, **kw):
        """Bars round an arc from angle a0 to a1 (degrees) in the x-y plane."""
        steps = steps or max(2, int(abs(a1 - a0) / 22.5))
        pts = [(centre[0] + radius * math.cos(math.radians(a0 + (a1 - a0) * i / steps)),
                centre[1] + radius * math.sin(math.radians(a0 + (a1 - a0) * i / steps))) for i in range(steps + 1)]
        return [self.seg(pts[i], pts[i + 1], w, t, mat, z=z, **kw) for i in range(steps)]

    def shift(self, d):
        d = np.array(d, float)
        self.offset = self.offset + d
        for p in self.parts:
            p['lo'] = p['lo'] + d
            p['hi'] = p['hi'] + d
            if p['rot']:
                p['rot'] = dict(p['rot'], origin=list(np.array(p['rot']['origin']) + d))
        return self

    def bounds(self):
        pts = []
        for p in self.parts:
            for c in corners(p):
                pts.append(c)
        pts = np.array(pts)
        return pts.min(0), pts.max(0)

    # ------------------------------------------------------------ clean-up

    def tidy(self):
        """Pull back coplanar overlapping faces (no z-fighting) and drop faces buried in other boxes."""
        plain = [p for p in self.parts if not p['rot']]
        for i, p in enumerate(plain):
            for q in plain[i + 1:]:
                for ax in range(3):
                    for side in (0, 1):
                        key = 'lo' if side == 0 else 'hi'
                        if abs(p[key][ax] - q[key][ax]) > 1e-6:
                            continue
                        o = [k for k in range(3) if k != ax]
                        ov = all(min(p['hi'][k], q['hi'][k]) - max(p['lo'][k], q['lo'][k]) > 1e-6 for k in o)
                        if not ov:
                            continue
                        small = p if area(p, ax) < area(q, ax) else q
                        small[key] = small[key].copy()
                        small[key][ax] += 0.012 if side == 0 else -0.012
        for p in self.parts:
            p['skip'] = set()
            if p['rot']:
                continue
            for name, (ax, sgn, *_r) in FACES.items():
                plane = p['hi'][ax] if sgn > 0 else p['lo'][ax]
                o = [k for k in range(3) if k != ax]
                for q in plain:
                    if q is p:
                        continue
                    if not (q['lo'][ax] + 1e-4 < plane < q['hi'][ax] - 1e-4):
                        continue
                    if all(q['lo'][k] <= p['lo'][k] + 1e-6 and q['hi'][k] >= p['hi'][k] - 1e-6 for k in o):
                        p['skip'].add(name)
                        break
        return self


def area(p, ax):
    o = [k for k in range(3) if k != ax]
    return (p['hi'][o[0]] - p['lo'][o[0]]) * (p['hi'][o[1]] - p['lo'][o[1]])


def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def corners(p):
    lo, hi = p['lo'], p['hi']
    cs = [np.array([x, y, z]) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
    if p['rot']:
        R = rot_matrix(p['rot']['axis'], p['rot']['angle'])
        o = np.array(p['rot']['origin'])
        cs = [R @ (c - o) + o for c in cs]
    return cs


# ---------------------------------------------------------------- painting + packing


def face_grid(p, name):
    ax, sgn, ua, ud, va, vd = FACES[name]
    lo, hi = p['lo'], p['hi']
    lu, lv = hi[ua] - lo[ua], hi[va] - lo[va]
    W, H = max(1, int(round(lu * PX))), max(1, int(round(lv * PX)))
    u = (np.arange(W) + 0.5) / W
    v = (np.arange(H) + 0.5) / H
    uu, vv = np.meshgrid(u, v)
    pos = np.zeros((H, W, 3))
    pos[..., ax] = hi[ax] if sgn > 0 else lo[ax]
    pos[..., ua] = lo[ua] + (uu if ud > 0 else 1 - uu) * lu
    pos[..., va] = lo[va] + (vv if vd > 0 else 1 - vv) * lv
    edge = np.minimum(np.minimum(uu, 1 - uu) * lu, np.minimum(vv, 1 - vv) * lv)
    uv = np.stack([uu * lu, vv * lv], -1)
    n = np.zeros(3)
    n[ax] = sgn
    return pos, n, edge, uv


def paint_model(model, look, wear, seed):
    """Paints every visible face. look: {material name: fn(face) -> rgb}. Returns patches.

    Materials see positions in the frame the model was built in, before any shift()."""
    rng = np.random.default_rng(seed)
    patches = []
    off = getattr(model, 'offset', np.zeros(3))
    for i, p in enumerate(model.parts):
        local = dict(p, lo=p['lo'] - off, hi=p['hi'] - off)
        for name in FACES:
            if name in p.get('skip', ()):
                continue
            pos, n, edge, uv = face_grid(local, name)
            f = P.Face(pos, n, edge, local, rng, wear, seed + i, uv)
            rgb = look[p['mat']](f)
            rgb = P.round_shade(f, rgb)
            for fx in look.get('_after', []):
                rgb = fx(f, rgb)
            patches.append((i, name, np.clip(rgb, 0, 1)))
    return patches


def pack(patches):
    """Shelf-packs the face patches into the smallest square power-of-two texture."""
    sizes = [(pt[2].shape[1] + 2, pt[2].shape[0] + 2) for pt in patches]
    order = sorted(range(len(patches)), key=lambda k: -sizes[k][1])
    for T in (32, 64, 128, 256, 512, 1024, 2048):
        x = y = shelf = 0
        spots = {}
        ok = True
        for k in order:
            w, h = sizes[k]
            if w > T:
                ok = False
                break
            if x + w > T:
                x, y, shelf = 0, y + shelf, 0
            if y + h > T:
                ok = False
                break
            spots[k] = (x, y)
            x += w
            shelf = max(shelf, h)
        if ok:
            break
    img = np.zeros((T, T, 4))
    uvs = {}
    for k, (x, y) in spots.items():
        i, name, rgb = patches[k]
        h, w = rgb.shape[:2]
        padded = np.pad(rgb, ((1, 1), (1, 1), (0, 0)), mode='edge')
        img[y:y + h + 2, x:x + w + 2, :3] = padded
        img[y:y + h + 2, x:x + w + 2, 3] = 1
        s = 16.0 / T
        uvs[(i, name)] = [round((x + 1) * s, 5), round((y + 1) * s, 5), round((x + 1 + w) * s, 5), round((y + 1 + h) * s, 5)]
    return img, uvs, T


def save_png(img, path):
    Image.fromarray((np.clip(img, 0, 1) * 255).round().astype(np.uint8), 'RGBA').save(path)


# ---------------------------------------------------------------- display transforms


def euler_xyz(M):
    """Angles (degrees) so that Rx(a) @ Ry(b) @ Rz(c) == M, the order the game applies them."""
    b = math.asin(max(-1, min(1, M[0, 2])))
    if abs(M[0, 2]) < 0.99999:
        a = math.atan2(-M[1, 2], M[2, 2])
        c = math.atan2(-M[0, 1], M[0, 0])
    else:
        a = math.atan2(M[2, 1], M[1, 1])
        c = 0
    return [round(math.degrees(v), 3) for v in (a, b, c)]


def R_xyz(a, b, c):
    return rot_matrix('x', a) @ rot_matrix('y', b) @ rot_matrix('z', c)


def fit(model, M, size, centre_at=(0, 0, 0), depth=False):
    """Scale and translation that fit the model, turned by M, into a size-unit square at centre_at (a cube with
    depth: for the ground and item frames, where a long thin part seen end on would otherwise blow up huge)."""
    lo, hi = model.bounds()
    pts = np.array([c for p in model.parts for c in corners(p)]) - 8
    q = pts @ M.T
    ext = q.max(0) - q.min(0)
    s = size / (max(ext) if depth else max(ext[0], ext[1]))
    mid = (q.max(0) + q.min(0)) / 2
    t = np.array(centre_at) - mid * s
    return round(s, 4), [round(v, 3) for v in t]


def transform(rot, trans, scale):
    sc = scale if isinstance(scale, (list, tuple)) else [scale] * 3
    return {'rotation': [round(v, 3) for v in rot], 'translation': [round(max(-80, min(80, v)), 3) for v in trans],
            'scale': [round(min(4, v), 4) for v in sc]}


# ---------------------------------------------------------------- export


def elements_json(model, uvs, light=None):
    out = []
    for i, p in enumerate(model.parts):
        faces = {}
        for name in FACES:
            if (i, name) in uvs:
                faces[name] = {'uv': uvs[(i, name)], 'texture': '#0'}
        if not faces:
            continue
        e = {'from': [round(v, 4) for v in p['lo']], 'to': [round(v, 4) for v in p['hi']], 'faces': faces}
        if p['rot']:
            e['rotation'] = {'angle': p['rot']['angle'], 'axis': p['rot']['axis'],
                             'origin': [round(v, 4) for v in p['rot']['origin']]}
        if p.get('light'):
            e['light_emission'] = p['light']
        if p.get('noshade'):
            e['shade'] = False
        check = [v for v in e['from'] + e['to']]
        if min(check) < LIMIT[0] or max(check) > LIMIT[1]:
            raise ValueError(f'{model.name}: element {i} outside the model limits: {e["from"]} {e["to"]}')
        out.append(e)
    return out


def model_json(model, uvs, texture, display, gui_light='front'):
    return {'gui_light': gui_light, 'textures': {'0': texture, 'particle': texture},
            'elements': elements_json(model, uvs), 'display': display}


# How far an item model's elements may reach (pixels): the game refuses to load a model past these.
LIMIT = (-16.0, 32.0)


def dump(obj, path):
    for e in obj.get('elements', ()) if isinstance(obj, dict) else ():
        for corner in ('from', 'to'):
            if not all(LIMIT[0] <= v <= LIMIT[1] for v in e[corner]):
                raise ValueError(f'{path}: an element\'s {corner!r} {e[corner]} is outside the model limits {LIMIT}')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=1) + '\n')
