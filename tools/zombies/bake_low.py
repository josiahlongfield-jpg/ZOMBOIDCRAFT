# Bake each outfit's far-off zombie: the body boxes with the clothes painted on, plus only the clothing pieces big
# enough to change its outline. A zombie past the nearest few is drawn from this (ZDetail), a tenth of the full one.
#   python3 tools/zombies/bake_low.py [outfit ...]      (after pack.py and cull.py)
# Writes zbody/<outfit>_low.bin (same layout as <outfit>.bin) and textures/entity/zbody/low/<outfit>.png.
#
# The body skin shares texels between boxes (hips and spine both use the torso), so the low body gets an atlas of its
# own: every body face its own patch, filled from the skin and then, ray by ray from outside, with whatever clothing
# covers it on the same bone.
import struct, sys, numpy as np
from pathlib import Path
from PIL import Image
sys.path.insert(0, str(Path(__file__).parent))
import cull

A = cull.R.parent / 'textures/entity/zbody'
PX = 8          # atlas pixels per preview unit
SIZE, ROWS = 1024, 256   # atlas width and height (the 108 body faces fill about 240 rows)
KEEP_EDGE = 2.0  # clothing pieces at least this wide both ways stay 3D
TINT = np.array([0x9D, 0x97, 0x8C]) / 255.0   # ZBodyLayer.BODY_TINT
OVERHANG = 0.6  # clothing this far off its bone's body stays 3D
REACH = 2.5     # how far out from a body face to look for clothes over it (preview units)


def sample(img, u, v):
    h, w = img.shape[:2]
    x = np.clip((u * w).astype(int), 0, w - 1); y = np.clip((v * h).astype(int), 0, h - 1)
    return img[y, x]


def uvs(raw):
    return struct.unpack_from('<4f', raw, 2 + 60)


def corners(q):
    p = q[2]
    return p[0], p[1] - p[0], p[3] - p[0]   # origin (u0 v0), across (to u1), down (to v1)


def bake(outfit):
    head, quads = cull.read(cull.R / f'{outfit}.bin')
    skin = np.array(Image.open(A / 'skin' / f'{outfit}.png').convert('RGBA'))
    kit = np.array(Image.open(A / 'kit' / f'{outfit}.png').convert('RGBA'))
    body = [q for q in quads if q[1] & 8]
    clothes = [q for q in quads if not q[1] & 8]
    atlas = np.zeros((ROWS, SIZE, 4), np.uint8)
    x = y = row = 0
    out = []
    for q in body:
        o, ex, ey = corners(q)
        w = max(2, int(round(np.linalg.norm(ex) * PX))); h = max(2, int(round(np.linalg.norm(ey) * PX)))
        if x + w > SIZE:
            x, y, row = 0, y + row + 1, 0
        assert y + h <= ROWS, 'low atlas full'
        s = (np.arange(w) + 0.5) / w; t = (np.arange(h) + 0.5) / h
        S, T = np.meshgrid(s, t)
        u0, v0, u1, v1 = uvs(q[4])
        col = sample(skin, u0 + S * (u1 - u0), v0 + T * (v1 - v0)).astype(float)
        col[..., :3] *= TINT   # the game tints the body skin; the low atlas carries it, so clothes painted on stay true
        P = o + S[..., None] * ex + T[..., None] * ey
        n = q[3]
        best = np.full(S.shape, np.inf)
        for c in clothes:
            if c[0] != q[0] or np.dot(c[3], n) < 0.5:
                continue   # only clothing on this bone facing the same way can cover this face
            co, cx, cy = corners(c)
            # ray from outside the face inward: P + n * d, the hit where it crosses the clothing face's plane
            denom = np.dot(c[3], n)
            d = np.dot(co - P, c[3]) / denom
            ok = (d > -0.05) & (d < REACH) & (d < best)
            if not ok.any():
                continue
            H = P + d[..., None] * n - co
            cs = (H @ cx) / np.dot(cx, cx); ct = (H @ cy) / np.dot(cy, cy)
            ok &= (cs >= 0) & (cs <= 1) & (ct >= 0) & (ct <= 1)
            if not ok.any():
                continue
            cu0, cv0, cu1, cv1 = uvs(c[4])
            k = sample(kit, cu0 + cs * (cu1 - cu0), cv0 + ct * (cv1 - cv0))
            ok &= k[..., 3] > 127
            # the outermost cover wins: larger d is further out, so keep the hit closest to the outside
            outer = ok & (np.isinf(best) | (d > best))
            col[outer] = k[outer]
            best = np.where(outer, d, best)
        atlas[y:y + h, x:x + w] = np.clip(col, 0, 255).astype(np.uint8)
        nu0, nv0, nu1, nv1 = x / SIZE, y / ROWS, (x + w) / SIZE, (y + h) / ROWS
        raw = bytearray(q[4]); struct.pack_into('<4f', raw, 2 + 60, nu0, nv0, nu1, nv1)
        out.append((q[0], q[1], q[2], q[3], bytes(raw)))
        x += w + 1; row = max(row, h)
    boxes = {}
    for i in range(0, len(body), 6):
        P = np.concatenate([q[2] for q in body[i:i + 6]])
        boxes.setdefault(body[i][0], []).append((P.min(0), P.max(0)))

    def overhang(c):
        # hangs off the body (a coat's skirt, a hood, a cap's brim): nothing to paint it on, so it stays 3D
        m = c[2].mean(0)
        gap = min((np.linalg.norm(np.maximum(0, np.maximum(lo - m, m - hi))) for lo, hi in boxes.get(c[0], [])), default=9)
        return gap > OVERHANG and not c[1] & 1

    def edge(c):
        return min(np.linalg.norm(c[2][1] - c[2][0]), np.linalg.norm(c[2][2] - c[2][1]))
    kept = [c for c in clothes if c[1] & 14 or edge(c) >= KEEP_EDGE or (overhang(c) and edge(c) >= 0.5)]
    (A / 'low').mkdir(exist_ok=True)
    Image.fromarray(atlas).save(A / 'low' / f'{outfit}.png', optimize=True)
    cull.write(cull.R / f'{outfit}_low.bin', head, out + kept)
    return len(quads), len(out) + len(kept)


if __name__ == '__main__':
    names = sys.argv[1:] or sorted(p.stem for p in cull.R.glob('*.bin') if not p.stem.endswith('_low'))
    for o in names:
        full, low = bake(o)
        print(f'{o:12} {full:5} -> {low:4}')
