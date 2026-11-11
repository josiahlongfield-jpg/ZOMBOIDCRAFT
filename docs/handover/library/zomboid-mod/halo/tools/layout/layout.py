"""Reach world layout between the missions: villages, roads, farms, woods (preview stage). Writes lay.npz + debug map."""
import json, subprocess, numpy as np
from PIL import Image
L = '/tmp/claude-0/halo/web/land/'
N, CELL = 1000, 16.0
SEA = -14.0
H4 = np.load(L + 'out/height.npy'); M4 = np.load(L + 'out/mask.npy')
H = H4.reshape(N, 4, N, 4).mean((1, 3)).astype(np.float32)
mask = M4.reshape(N, 4, N, 4).max((1, 3))
clim = np.load(L + 'out/clim.npy')
sea = (np.load(L + 'out/sea16.npy') & (mask == 0)) | (H < SEA + 0.5)
OFF = {"m10": (-5257, -4204), "m20": (-167, -5261), "m60": (4936, -5992), "m35": (-4299, 3102),
       "m52": (3983, 1544), "m50": (6225, -2533), "m70": (655, 5986)}
cs = json.load(open(L + 'cs.json'))
CEN = {m: (cs[m][0] + OFF[m][0], cs[m][2] + OFF[m][1]) for m in OFF}
NAMES = {"m10": "Winter Contingency", "m20": "Sword Base", "m60": "The Package", "m35": "Tip of the Spear", "m52": "New Alexandria",
         "m50": "Exodus", "m70": "Pillar of Autumn"}
def w2c(x, z): return int((z + 8000) / CELL), int((x + 8000) / CELL)
def c2w(r, c): return -8000 + (c + 0.5) * CELL, -8000 + (r + 0.5) * CELL
yy, xx = np.mgrid[0:N, 0:N]; WX = -8000 + (xx + 0.5) * CELL; WZ = -8000 + (yy + 0.5) * CELL

def dil(m, k=1):
    for _ in range(k):
        o = m.copy(); o[1:] |= m[:-1]; o[:-1] |= m[1:]; o[:, 1:] |= m[:, :-1]; o[:, :-1] |= m[:, 1:]; m = o
    return m

def dist(m, maxk):
    """steps (cells) from mask, chamfer"""
    D = np.where(m, 0.0, 1e9)
    for _ in range(2):
        for r in range(1, N): D[r] = np.minimum(D[r], D[r - 1] + 1)
        for r in range(N - 2, -1, -1): D[r] = np.minimum(D[r], D[r + 1] + 1)
        for c in range(1, N): D[:, c] = np.minimum(D[:, c], D[:, c - 1] + 1)
        for c in range(N - 2, -1, -1): D[:, c] = np.minimum(D[:, c], D[:, c + 1] + 1)
    return D

# which mission each piece of mission land belongs to
lab = np.full((N, N), -1, np.int8); keys = list(OFF)
dm = np.stack([np.hypot(WX - CEN[m][0], WZ - CEN[m][1]) for m in keys]); near = dm.argmin(0)
lab[mask > 0] = near[mask > 0]
foot = mask == 2
slope = np.hypot(*np.gradient(H, CELL))
dfoot = dist(foot, 0) * CELL; dmis = dist(mask > 0, 0) * CELL; dsea = dist(sea, 0) * CELL; dland = dist(~sea, 0) * CELL
np.savez('grids.npz', H=H, mask=mask, clim=clim, sea=sea, lab=lab, slope=slope, dfoot=dfoot, dmis=dmis, dsea=dsea)
print('grids ok', flush=True)
