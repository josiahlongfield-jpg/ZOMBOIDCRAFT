"""Detailed 1911 extended magazine, replacing Brita's 12-triangle grey box.
Keeps Brita's envelope (same rake, same top, same bottom) so it mounts the same.
Writes review/out/p/Extended_Mag_1911.bin and review/out/pt/Extended_Mag_1911.png
Raw PZ mesh frame (before the page's z flip): +y muzzle, z up (negated later)."""
import sys, numpy as np
from PIL import Image, ImageDraw
S = '/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad/'
sys.path.insert(0, S + 'look'); from conv import toon
OUT = S + 'review/out/'

# Brita box corners in the y-z plane
TF, BF = np.array([0.019081, -0.064902]), np.array([-0.011816, -0.150447])   # top/bottom front
TB, BB = np.array([-0.006317, -0.055998]), np.array([-0.03733, -0.14169])    # top/bottom back
a = (BF - TF); L = np.linalg.norm(a); a /= L                                   # down the rake
f = np.array([-a[1], a[0]]);  f = f if f[0] > 0 else -f                         # toward the muzzle
D = abs(np.dot(TF - TB, f))                                                    # front-back depth
top = (TF + TB) / 2 + a * np.dot((TF - (TF + TB) / 2), a) * 0                  # top centre
top = top - a * np.dot(top - TF, a)                                            # on the top plane
W = 0.0092                                                                     # body width (x)
GRIP_Z = -0.0696                                                               # grip bottom

def profile(w, d, lip=0.0, n=10):
    """Cross-section (f, x) loop: rounded front, flat back with small chamfers."""
    r = w / 2; ch = 0.0007
    pts = [(-d / 2, -r + ch), (-d / 2 + ch, -r)]
    pts += [(d / 2 - r + lip * 0.4, -r)]
    for i in range(1, n):                                                       # front round
        t = -np.pi / 2 + np.pi * i / n
        pts.append((d / 2 - r + lip * 0.4 + (r + lip * 0.6) * np.cos(t), r * np.sin(t)))
    pts += [(d / 2 - r + lip * 0.4, r), (-d / 2 + ch, r), (-d / 2, r - ch)]
    return np.array(pts)

def P(u, fc, xc):                                                              # local -> raw xyz
    yz = top + a * u + f * fc
    return np.array([xc, yz[0], yz[1]], np.float32)

tris, uvs = [], []
def quad(p0, p1, p2, p3, t0, t1, t2, t3):
    tris.extend([p0, p1, p2, p0, p2, p3]); uvs.extend([t0, t1, t2, t0, t2, t3])

def ring_band(prof0, prof1, u0, u1, U0, U1):
    """Side wall between two profile rings; texture U along the rake, V around the loop."""
    n = len(prof0); per = np.r_[0, np.cumsum(np.linalg.norm(np.diff(np.r_[prof0, prof0[:1]], axis=0), axis=1))]
    V = per / per[-1]
    for i in range(n):
        j = (i + 1) % n
        quad(P(u0, *prof0[i]), P(u1, *prof1[i]), P(u1, *prof1[j]), P(u0, *prof0[j]),
             (U0, V[i]), (U1, V[i]), (U1, V[i + 1]), (U0, V[i + 1]))

def cap(prof, u, uv, flip):
    c = P(u, prof[:, 0].mean(), 0)
    for i in range(len(prof)):
        j = (i + 1) % len(prof); p, q = P(u, *prof[i]), P(u, *prof[j])
        if flip: tris.extend([c, q, p])
        else: tris.extend([c, p, q])
        uvs.extend([uv] * 3)

PAD = 0.0125                     # base pad height along the rake
body = profile(W, D)
pad = profile(W + 0.0018, D + 0.0016, lip=0.0022)
padlo = profile(W + 0.0006, D + 0.0004, lip=0.0016)
UB = L - PAD                     # body ends here
# body: split so the texture strip (U 0..0.7) covers u 0..UB
ring_band(body, body, 0.0, UB, 0.0, 0.70)
cap(body, 0.0, (0.72, 0.02), flip=False)
# body-to-pad step (floorplate seat) then pad wall, then bevel, then bottom
ring_band(body, pad, UB, UB + 0.0006, 0.74, 0.76)
ring_band(pad, pad, UB + 0.0006, L - 0.0022, 0.76, 0.96)
ring_band(pad, padlo, L - 0.0022, L, 0.96, 0.99)
cap(padlo, L, (0.995, 0.5), flip=True)

pos = np.array(tris, np.float32).reshape(-1, 3, 3)[:, [0, 2, 1]].reshape(-1, 3)   # PZ winding
uv = np.array(uvs, np.float32).reshape(-1, 3, 2)[:, [0, 2, 1]].reshape(-1, 2)
# triangles above must face out; check one wall normal points away from the axis
with open(OUT + 'p/Extended_Mag_1911.bin', 'wb') as fh:
    fh.write(np.array([len(pos)], np.uint32).tobytes()); fh.write(pos.tobytes()); fh.write(uv.tobytes())

# ---- texture: U along the rake (0 top .. 0.7 body end), V around the loop ----
rng0 = np.random.default_rng(3)
T = 512; img = Image.new('RGB', (T, T), (66, 68, 72)); dr = ImageDraw.Draw(img)
px = lambda U: int(U * T)
nb = len(body); per = np.r_[0, np.cumsum(np.linalg.norm(np.diff(np.r_[body, body[:1]], axis=0), axis=1))]; Vn = per / per[-1]
side_a = (Vn[2] + Vn[1]) / 2                        # the -x flat side starts after the back chamfer
side_a0, side_a1 = Vn[1], Vn[2]                     # -x side wall V range (back -> front)
side_b0, side_b1 = Vn[nb - 2 - 1], Vn[nb - 2]       # +x side wall
front0, front1 = Vn[2], Vn[nb - 3]
# blued steel body with a polished band down the front round and dark back spine
for y in range(T):
    v = y / T
    col = (92, 95, 100)
    if front0 + 0.02 < v < front1 - 0.02: col = (108, 111, 117)
    if abs(v - (front0 + front1) / 2) < 0.04: col = (150, 154, 160)        # front highlight
    if v < Vn[1] + 0.005 or v > Vn[nb - 1] - 0.005: col = (66, 68, 72)    # back spine
    dr.line([(0, y), (px(0.70), y)], fill=col)
# handling wear: fine scratches and rubbed edges, like the gun's own texture
for _ in range(260):
    x = int(rng0.integers(0, px(0.69))); y = int(rng0.integers(0, T)); l = int(rng0.integers(6, 30))
    dr.line([(x, y), (min(px(0.69), x + l), y + int(rng0.integers(-3, 4)))], fill=(120, 124, 130), width=1)
for v in (Vn[1], Vn[2], Vn[nb - 3], Vn[nb - 2]):
    dr.line([(0, int(v * T)), (px(0.69), int(v * T))], fill=(128, 132, 138), width=3)
# seam where the body meets the floorplate seat
dr.rectangle([px(0.70), 0, px(0.74), T], fill=(40, 40, 42))
# witness holes on both flat sides, visible below the grip only
u_grip = abs((GRIP_Z - top[1]) / a[1])
for s0, s1 in ((side_a0, side_a1), (side_b0, side_b1)):
    vc = s0 + (s1 - s0) * 0.38                                            # behind centre, like Colt mags
    u = u_grip + 0.006; k = 8
    while u < UB - 0.004:
        U = u / UB * 0.70; r = 0.0045
        dr.ellipse([px(U) - 10, int((vc - r) * T) - 3, px(U) + 10, int((vc + r) * T) + 3], fill=(150, 154, 160))
        dr.ellipse([px(U) - 7, int((vc - r) * T), px(U) + 7, int((vc + r) * T)], fill=(14, 14, 16))
        dr.text((px(U) - 4, int((vc + 0.012) * T)), str(k), fill=(150, 152, 156))
        u += 0.0092; k += 2
# polymer base pad: black, a grip-texture stipple band, lighter bevel
dr.rectangle([px(0.74), 0, px(0.96), T], fill=(46, 46, 49))
rng = np.random.default_rng(7)
for _ in range(1400):
    x = rng.integers(px(0.80), px(0.92)); y = rng.integers(0, T)
    dr.point((x, y), fill=(68, 68, 72))
dr.rectangle([px(0.96), 0, T, T], fill=(60, 60, 64))
dr.rectangle([px(0.70), 0, px(0.73), 12], fill=(64, 66, 70))
toon(img).save(OUT + 'pt/Extended_Mag_1911.png', optimize=True)
print('tris', len(pos) // 3, 'L', round(L, 4), 'D', round(D, 4), 'below grip', round(L - u_grip, 4))
