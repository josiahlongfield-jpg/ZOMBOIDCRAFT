"""Hollows a packed tube sight (Josiah 2026-10-30: "you can't have a blacked out sight, make sure everything that
stretches out to where the crosshair ends can be seen through"). On the sights the eye is close behind the tube's
back end, so its insides (lenses, their rings, the inside of the tube) fill the view round a small far opening.

Works on the packed .zmesh in place (after pack.py, and after any triangle cutting), on its "body" part, about the
sight line in bounds.json:
- left out: everything wholly inside the back opening (r_open, item pixels from the sight line), walls facing the
  sight line, and faces across the tube ahead of its back end;
- moved to a part of its own, "tube": the tube's outside walls, which MeshGuns draws without the ink rim (from the
  eye the rim of the tube's far side would show black through it).

    python3 tools/meshguns/hollow.py [MESH_ID R_OPEN R_WALL]

Without arguments it does every sight in HOLLOW and WINDOWS. Running it again on a hollowed mesh changes nothing (the tube goes
back in the body and is split off the same way), so rerun it after repacking any of these.
"""
import io
import json
import os
import struct
import sys
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


# mesh: (r_open, r_wall), item pixels from the sight line, fitted to each tube's back opening and outside wall
HOLLOW = {'att_red_dot': (0.5, 1.0), 'own_combat_scope': (0.55, 1.0), 'own_rifle_optic': (0.45, 0.9)}
# the holo (Josiah 2026-10-30: "the holo needs a much bigger sight picture"): its window sits in a thick dark frame
# well inside the hood, so everything in the hood's opening ahead of its back end is left out ({x0, x1, y0, y1}
# item pixels about the sight line, z up to which): the hood and the thin front window frame are what's left round
# the sight picture
WINDOWS = {'att_holo': (-0.7, 0.72, -0.55, 0.6, 0.6)}
# inches from the back end to the eye on the sights (tools/weapons/weapons.py EYE_RELIEF, 1.6 for the rest)
RELIEF = {'att_holo': 1.0}
IN = 25.4 / 31


def away(T, eye):
    """Faces turned away from the eye: culled, but their ink rim would show from there as black shards through the
    sight, so these go in the tube part too (drawn without it)."""
    n = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0])
    return (n * (eye - T.mean(1))).sum(1) < 0


def eye_of(mid, T, sight):
    return np.array([sight[0], sight[1], T[:, :, 2].max() + RELIEF.get(mid, 1.6) * IN])


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
        out.write(struct.pack('>i', len(rec))); out.write(rec.astype(REC).tobytes())    # concatenate makes it native-endian
    open(os.path.join(MESHES, mid + '.zmesh'), 'wb').write(scramble(zlib.compress(out.getvalue(), 9), mid))


def split(T, sight, r_open, r_wall, rear_band=0.3):
    """Which faces to leave out, and which are the tube's outside walls."""
    c = T.mean(1)
    n = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0])
    n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-12
    rad = c[:, :2] - sight[:2]
    rc = np.linalg.norm(rad, axis=1)
    radial = (n[:, :2] * rad / (rc[:, None] + 1e-9)).sum(1)
    rmax = np.hypot(T[:, :, 0] - sight[0], T[:, :, 1] - sight[1]).max(1)
    inside = rmax < r_open
    walls = (radial < -0.3) & (rc < r_wall)
    across = (np.abs(n[:, 2]) > 0.5) & (rmax < r_wall) & (c[:, 2] < T[:, :, 2].max() - rear_band)
    drop = inside | walls | across
    return drop, ~drop & (radial > 0.3) & (rc < r_wall)


def main(mid, r_open, r_wall):
    head, png, parts = read(mid)
    sight = np.array(json.load(open(BOUNDS))[mid][2])
    body = np.concatenate([parts['body'], parts.pop('tube')]) if 'tube' in parts else parts['body']
    T = np.stack([body['x'], body['y'], body['z']], -1).astype(float) / 1000
    drop, tube = split(T, sight, r_open, r_wall)
    rmax = np.hypot(T[:, :, 0] - sight[0], T[:, :, 1] - sight[1]).max(1)
    tube |= ~drop & (rmax < 1.3 * r_wall) & away(T, eye_of(mid, T, sight))
    parts['body'] = body[~drop & ~tube]
    parts['tube'] = body[tube]
    write(mid, head, png, parts)
    print(f'{mid}: {drop.sum()} faces left out, {tube.sum()} in the tube, {len(parts["body"])} in the body')


def window(mid, box):
    head, png, parts = read(mid)
    sight = np.array(json.load(open(BOUNDS))[mid][2])
    body = np.concatenate([parts['body'], parts.pop('tube')]) if 'tube' in parts else parts['body']
    T = np.stack([body['x'], body['y'], body['z']], -1).astype(float) / 1000
    c = T.mean(1)
    x0, x1, y0, y1, z1 = box
    opening = (c[:, 0] > x0) & (c[:, 0] < x1) & (c[:, 1] - sight[1] > y0) & (c[:, 1] - sight[1] < y1)
    drop = opening & (c[:, 2] < z1)
    # round the opening, out to the hood, the faces turned away from the eye lose their rim too
    hood = (np.abs(c[:, 0]) < 1.1) & (c[:, 1] - sight[1] > y0 - 0.1) & (c[:, 1] - sight[1] < y1 + 0.4)
    tube = ~drop & hood & away(T, eye_of(mid, T, sight))
    parts['body'] = body[~drop & ~tube]
    parts['tube'] = body[tube]
    write(mid, head, png, parts)
    print(f'{mid}: {drop.sum()} faces in its window left out, {tube.sum()} in the tube')


if __name__ == '__main__':
    if len(sys.argv) > 1:
        main(sys.argv[1], float(sys.argv[2]), float(sys.argv[3]))
    else:
        for mid, (r_open, r_wall) in HOLLOW.items():
            main(mid, r_open, r_wall)
        for mid, box in WINDOWS.items():
            window(mid, box)
