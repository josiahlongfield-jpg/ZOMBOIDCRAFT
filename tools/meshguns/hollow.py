"""Opens up the view through a tube sight (Josiah 2026-10-30): from the eye on the sights, the lens rings and the inside
of the tube make a dark filler round a small far opening. Everything wholly inside a cone from the eye, out to a
given share of the screen's height, is left out, so the opening reaches that far and the sight's frame round it stays.

- 3x combat scope: out to the tips of its crosshairs (reticles.py draws them to the edge of the reticle, which
  CombatHud draws at the size the reticle had when this was set: half the screen's height, so a quarter from the
  middle). The crosshairs were shrunk afterwards; the opening stays where it was set.
- red dot: out to the inside of the black ring round its back end.

Works on the packed .zmesh in place (after pack.py), on its "body" part, about the sight line in bounds.json, with
the eye where tools/weapons/weapons.py puts it (1.6 inches behind the back end) and the first-person view's 70 degree
height. Running it again changes nothing; rerun it after repacking any of these.

    python3 tools/meshguns/hollow.py
"""
import io
import json
import math
import os
import struct
import zlib
import importlib.util

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
MESHES = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/meshes')
BOUNDS = os.path.join(os.path.dirname(__file__), 'bounds.json')
_spec = importlib.util.spec_from_file_location('cars_pack', os.path.join(ROOT, 'tools', 'cars', 'pack.py'))
_cars_pack = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_cars_pack)
scramble = _cars_pack.scramble
REC = np.dtype([('x', '>i2'), ('y', '>i2'), ('z', '>i2'), ('u', '>u2'), ('v', '>u2'), ('nx', 'i1'), ('ny', 'i1'), ('nz', 'i1')])
IN = 25.4 / 31          # item pixels an inch
RELIEF = 1.6            # inches from the back end to the eye
FOV = 70                # the hand's view, degrees top to bottom

# mesh: how far out from the middle the opening reaches, as a share of the screen's height
OPENINGS = {'own_combat_scope': 0.25, 'att_red_dot': 0.18}


def read(mid):
    raw = zlib.decompress(scramble(open(os.path.join(MESHES, mid + '.zmesh'), 'rb').read(), mid))
    b = io.BytesIO(raw)
    head = b.read(12)
    assert head[:4] == b'ZMSH'
    png = b.read(struct.unpack('>i', b.read(4))[0])
    parts = {}
    for _ in range(struct.unpack('>i', b.read(4))[0]):
        name = b.read(struct.unpack('>H', b.read(2))[0]).decode()
        tris = struct.unpack('>i', b.read(4))[0]
        parts[name] = np.frombuffer(b.read(tris * 3 * REC.itemsize), dtype=REC).reshape(-1, 3).copy()
    return head, png, parts


def write(mid, head, png, parts):
    out = io.BytesIO()
    out.write(head)
    out.write(struct.pack('>i', len(png))); out.write(png)
    out.write(struct.pack('>i', len(parts)))
    for name, rec in parts.items():
        b = name.encode()
        out.write(struct.pack('>H', len(b))); out.write(b)
        out.write(struct.pack('>i', len(rec))); out.write(rec.astype(REC).tobytes())
    open(os.path.join(MESHES, mid + '.zmesh'), 'wb').write(scramble(zlib.compress(out.getvalue(), 9), mid))


def cone(eye, reach):
    """Whether points (..., 3) are inside the cone from the eye down the sight line that shows `reach` of the screen's
    height out from the middle."""
    tan = reach * 2 * math.tan(math.radians(FOV / 2))
    return lambda P: (eye[2] - P[..., 2] > 0) & (np.hypot(P[..., 0] - eye[0], P[..., 1] - eye[1]) < tan * (eye[2] - P[..., 2]))


def cut(tri, ins, depth):
    """A face (3 corners of x, y, z, u, v, nx, ny, nz) that crosses the cone's edge, split in four again and again
    along it: the pieces wholly inside are left out, so the opening's edge is round rather than the face's own."""
    k = ins(tri[:, :3])
    if k.all() or depth == 0 and ins(tri[:, :3].mean(0)):
        return []
    if not k.any() and not ins(tri[:, :3].mean(0)) or depth == 0:
        return [tri]
    a, b, c = tri
    ab, bc, ca = (a + b) / 2, (b + c) / 2, (c + a) / 2
    out = []
    for t in ((a, ab, ca), (ab, b, bc), (ca, bc, c), (ab, bc, ca)):
        out += cut(np.array(t), ins, depth - 1)
    return out


def open_up(mid, reach, depth=3):
    head, png, parts = read(mid)
    lo, hi, sight = json.load(open(BOUNDS))[mid]
    body = parts['body']
    F = np.stack([body[k].astype(float) for k in ('x', 'y', 'z', 'u', 'v', 'nx', 'ny', 'nz')], -1)
    F[..., :3] /= 1000
    ins = cone(np.array([sight[0], sight[1], hi[2] + RELIEF * IN]), reach)     # the eye where weapons.py _eye puts it
    k = ins(F[..., :3])
    whole, gone = ~k.any(1) & ~ins(F[..., :3].mean(1)), k.all(1)
    if not gone.any():
        print(f'{mid}: already open')      # only the cut edge's pieces reach into the cone
        return
    pieces = [p for f in F[~whole & ~gone] for p in cut(f, ins, depth)]
    keep = np.concatenate([F[whole]] + ([np.array(pieces)] if pieces else []))
    rec = np.zeros(keep.shape[:2], REC)
    for i, key in enumerate(('x', 'y', 'z')):
        rec[key] = np.round(keep[..., i] * 1000)
    rec['u'], rec['v'] = np.round(keep[..., 3]), np.round(keep[..., 4])
    n = keep[..., 5:8] / (np.linalg.norm(keep[..., 5:8], axis=-1, keepdims=True) + 1e-9) * 127
    rec['nx'], rec['ny'], rec['nz'] = np.round(n[..., 0]), np.round(n[..., 1]), np.round(n[..., 2])
    parts['body'] = rec
    write(mid, head, png, parts)
    print(f'{mid}: {gone.sum()} faces in the opening left out, {(~whole & ~gone).sum()} on its edge cut into '
          f'{len(pieces)} pieces, {len(rec)} faces now')


if __name__ == '__main__':
    for mid, reach in OPENINGS.items():
        open_up(mid, reach)
