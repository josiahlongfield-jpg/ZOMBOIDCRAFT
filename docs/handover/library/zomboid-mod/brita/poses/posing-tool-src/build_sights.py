"""Josiah's sight pack (red-dot-rifle-scope-optics-sights-pack, staged from his PC, LOCAL ONLY) -> posing-tool parts.
Each sight: OBJ in metres (+z = front, y up) turned to the gun frame (x right, y up, z back toward the stock), origin
at its base's middle under its own sight line; one texture atlas (body + mount + lens/reticle colours) as JPEG;
per-triangle glass flags (1 lens, 2 reticle) so the eye view can hide the glass. The sight line is the reticle's middle
(the red dot itself, or the scope's crosshair), which the lenses agree with to within half a millimetre."""
import sys, json, gzip, base64, io, glob, os, re, numpy as np
from PIL import Image
from collections import deque
sys.path.insert(0, os.path.dirname(__file__))
import objread as O
def smooth(T):
    """Per-corner smooth normals (area-weighted over every face meeting at that point), int8, for the ink outline."""
    n = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0]); P = T.reshape(-1, 3)
    key = np.unique(np.round(P * 20000).astype(np.int64), axis=0, return_inverse=True)[1].reshape(-1)
    acc = np.zeros((key.max() + 1, 3)); np.add.at(acc, key, np.repeat(n, 3, 0))
    v = acc[key]; v /= np.linalg.norm(v, axis=1, keepdims=True) + 1e-12
    return np.round(v * 127).astype('i1')
OUT = '/tmp/claude-0/-home-user-zomboidcraft/659694dd-f437-5058-9359-dae443b02255/scratchpad/tool/web/parts/'
ROOT = O.ROOT
RIFLES = ['AAC_Honey', 'AssaultRifle2', 'BOAR', 'L86', 'Winchester1866', 'TenPointTurbo_XLT']
SMGS = ['AR57_PDW', 'M2Carbine', 'American180']   # American-180: red dots and irons only (Josiah 2026-11-03 08:53)
ASSAULT = ['AAC_Honey', 'AssaultRifle2', 'L86']   # assault rifles top out at 8x (Josiah 2026-11-03 07:49)
PISTOLS = ['Colt1911', 'SW_M500']
SHOTGUNS = ['M870_CQB']          # 870 CQB is a close-range pump, not a slug gun
SLUG = []                         # slug shotguns may also take sights up to 4x (Josiah 2026-11-03 07:04)
# id, folder, display name, magnification, needs the Burris 30 mm cantilever (tube size m) or None, main reticle material
SIGHTS = [
    ('sp_fastfire', 'burris-fastfire-3-red-dot-reflex-sight', 'Burris FastFire 3 red dot', 1, None, None),
    ('sp_eflx', 'eotech-eflx-red-dot-sight', 'EOTech EFLX red dot', 1, None, None),
    ('sp_micro_h2', 'aimpoint-micro-h-2-red-dot-reflex-sight', 'Aimpoint Micro H-2 red dot', 1, None, None),
    ('sp_acro', 'red-dot-reflex-sight-aimpoint-acro-p-2', 'Aimpoint ACRO P-2 red dot', 1, None, None),
    ('sp_aems', 'red-dot-sight-holosun-open-reflex-aems-core', 'Holosun AEMS Core red dot', 1, None, None),
    ('sp_uh1', 'vortex-razor-amg-uh-1-gen-2-red-dot-holographic-sight', 'Vortex Razor AMG UH-1 Gen II holographic', 1, None, None),
    ('sp_trihawk', 'swampfox-trihawk-tactical-prism-scope', 'Swampfox Trihawk 3x prism', 3, None, None),
    ('sp_hamr', 'leupold-mark-4-hamr-scope-with-deltapoint-red-dot-sight', 'Leupold Mark 4 HAMR 4x + DeltaPoint', 4, None, 'rifle_scope_HAMR_reticle'),
    ('sp_rt6', 'burris-rt6-1-6x24-riflescope', 'Burris RT-6 1-6x24', 6, 0.030, None),
    ('sp_vudu', 'eotech-vudu-1-6x-variable-optic', 'EOTech Vudu 1-6x24', 6, 0.034, None),
    ('sp_razor', 'rifle-scope-vortex-razor-hd-gen-ii-e', 'Vortex Razor HD Gen II-E 1-6x24', 6, None, None),
    ('sp_pm2_18', 'rifle-scope-schmidt-bender-1-8-pm2', 'Schmidt & Bender PM II 1-8x24', 8, None, None),
    ('sp_hensoldt', 'riflescope-hensoldt-ff-4-16x56', 'Hensoldt FF 4-16x56', 16, None, None),
    ('sp_pm2_320', 'ridlescope-schmidt-bender-3-20pmii-with-red-dot-aimpoint-h-2', 'Schmidt & Bender PM II 3-20x50 + Aimpoint H-2', 20, None, 'riflescope_schmidtbender_PM2_3-20_50_reticle'),
    ('sp_goldeneagle', 'variable-optic-rifle-scope-vortex-golden-eagle', 'Vortex Golden Eagle 15-60x52', 60, None, None),
]
# wave 2 (2026-11-03 12:0x; AUG9 takes no optics per Arsenal): battle rifles and machine guns follow the assault-rifle 8x cap
PISTOLS += ['Automag', 'G17', 'HK_USP', 'Colt1903', 'SW_327', 'Colt_Service38']
SHOTGUNS += ['UTS15', 'M1014_Wick', 'SPAS12_Fixed']
SMGS += ['CAR15SMG', 'MP7', 'UZI']
W2_AR = ['Bush_AR15_MOE', 'AK47', 'F2000', 'G36C', 'SCARL', 'M60', 'PKM', 'FAL', 'SCARH', 'M1']
ASSAULT += W2_AR
RIFLES += W2_AR + ['M82', 'M40A3', 'SV_98', 'Ruger_No1_308']
# wave 3 (88 guns, 2026-11-06): same class rules; the stand-alone M203 and the M2A1 flamethrower take no optics (Arsenal)
PISTOLS += ['G21', 'AMT1911', 'B93R', 'M9', 'G34', 'VP70', 'MP45', 'XD4', 'Makarov', 'FN_57', 'MPA57DMG', 'DEagle', 'Judge513_45LC', 'GP100_6']
SHOTGUNS += ['M870_Wingmaster', 'VIPER_G2', 'M1887', 'ROYAL_SXS', 'Coachgun', 'A400', 'SPAS12', 'TXS_804_45LC', 'Colt1855', 'KS23_S', 'M870_MCS', 'M1208', 'SPAS_15', 'USAS_12', 'CAWS', 'TOZ106', 'NEF_Handi_410']
SMGS += ['M1911_Carbine', 'Model55', 'Bizon2_01', 'EVO', 'KRISS', 'MAC10', 'UMP9', 'UMP45', 'UZI_Micro', 'Carbon15_97', 'M635S', 'KG9']
W3_AR = ['AK74', 'AK74_Custom', 'AKM_Custom', 'M723', 'CAR15_Survival', 'AssaultRifle', 'AK12_New', 'AN94', 'AR18', 'FAMAS', 'SKS', 'Vz58', 'AUG_A3', 'L85A2', 'MK47', 'ADAR', 'Bush_XM15', 'Bush_XM15_Custom', 'M4', 'G33', 'XM8', 'AK74_Alpha', 'Ots14_4A_03', 'RPK16', 'G21LMG', 'M249E3', 'RPD', 'AK308', 'Galil', 'M14EBR', 'MAS49', 'OTS_03_SVU']
ASSAULT += W3_AR
RIFLES += W3_AR + ['TAC50', 'CheyTac50', 'Sako85', 'L96', 'M40', 'WA2000', 'M1903', 'Viper', 'Thompson_Center_3006', 'AR7', 'M29_44Carbine']   # M29 carbine: full Arsenal optic rail, like the lever guns (2026-11-06)
def guns_for(pid, mag):
    g = []
    if pid in ('sp_fastfire', 'sp_eflx'): g += PISTOLS + SHOTGUNS
    if mag <= 4: g += SLUG
    g += [r for r in RIFLES if not (mag > 8 and r in ASSAULT) and not (mag > 4 and r == 'TenPointTurbo_XLT')]   # crossbow max 4x (Josiah 10:39)
    if mag <= 1: g += SMGS            # SMGs get no rifle scopes (Josiah 2026-11-03 07:02)
    return g

def kind(m):
    m = m.lower()
    if 'reticle' in m: return 2
    if 'lens' in m or 'lems' in m: return 1
    return 0
def tex_for(folder, mat):
    """The pack's base colour map for a material, or None (lenses usually have none)."""
    pngs = glob.glob(ROOT + folder + '/**/PBR/**/*.png', recursive=True) + glob.glob(ROOT + folder + '/*/PBR/*.png')
    key = re.sub(r'\.\d+$', '', mat).lower().replace('lems', 'lens')
    cands = [p for p in set(pngs) if os.path.basename(p).lower().startswith(key + '_base')]
    if not cands and kind(mat) == 2:     # the piggyback Aimpoint's reticle is named without its frame
        cands = [p for p in set(pngs) if 'reticle_base' in os.path.basename(p).lower() and key.split('_with')[0] in os.path.basename(p).lower()]
    if not cands and kind(mat) == 1:
        cands = [p for p in set(pngs) if os.path.basename(p).lower().startswith(key.replace('_low_poly', '') + '_low_poly_base')]
    return sorted(cands, key=len)[0] if cands else None

def holec2(T, seed):
    """Middle of the enclosed see-through hole around seed (x, y), seen along z (for the mount's ring)."""
    from PIL import ImageDraw
    pix = 0.0001; P = T.reshape(-1, 3); x0, y1 = P[:, 0].min() - 0.002, P[:, 1].max() + 0.002
    W = int((np.ptp(P[:, 0]) + 0.004) / pix) + 1; H = int((np.ptp(P[:, 1]) + 0.004) / pix) + 1
    img = Image.new('L', (W, H), 0); d = ImageDraw.Draw(img)
    for t in T: d.polygon([((x - x0) / pix, (y1 - y) / pix) for x, y, _ in t], fill=255)
    solid = np.array(img) > 0; iy, ix = int((y1 - seed[1]) / pix), int((seed[0] - x0) / pix)
    seen = np.zeros_like(solid); seen[iy, ix] = True; q = deque([(iy, ix)])
    while q:
        y, x = q.popleft()
        for yy, xx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
            if 0 <= yy < H and 0 <= xx < W and not solid[yy, xx] and not seen[yy, xx]: seen[yy, xx] = True; q.append((yy, xx))
    ys, xs = np.nonzero(seen)
    return np.array([x0 + ((xs.min() + xs.max()) / 2 + 0.5) * pix, y1 - ((ys.min() + ys.max()) / 2 + 0.5) * pix]), (np.ptp(xs) + 1) * pix / 2

def burris_mount(T, M, tube, axis):
    """Josiah's pack's Burris 30 mm cantilever, sized to the tube, rings round the scope's axis, saddling its turret."""
    m = O.read(glob.glob(ROOT + 'burris-ar-pepr-scope-mount-30mm/OBJ/*.obj')[0])
    MT, MU, MM, _ = m
    c, r = holec2(MT, (0.0, MT[:, :, 1].max() - 0.022))     # the ring (the opening under it is the rail clamp)
    MT = (MT - np.array([c[0], c[1], 0])) * (tube / 2 / r)
    # ring middles along the mount: the rings' tops
    P = MT.reshape(-1, 3); top = P[P[:, 1] > 0.8 * tube / 2]; zs = np.sort(top[:, 2]); gap = np.argmax(np.diff(zs))
    mid = (zs[:gap + 1].mean() + zs[gap + 1:].mean()) / 2
    S = T[M != ''].reshape(-1, 3); turret_z = S[S[:, 1] > S[:, 1].max() - 0.004][:, 2].mean()
    MT = MT + np.array([axis[0], axis[1], turret_z - mid])
    return MT, MU, MM

def atlas(folder, mats):
    """Body 1024 left, mount/extra 512 tiles right, small 64 px tiles for untextured glass/reticle colours."""
    W, H = 2048, 1024; A = Image.new('RGB', (W, H), (40, 40, 40)); place = {}
    big = [m for m in mats if kind(m) == 0]
    big.sort(key=lambda m: -(Image.open(tex_for(folder, m)).size[0] if tex_for(folder, m) else 0))
    slots = [(0, 0, 1024)] + [(1024 + 512 * (i % 2), 512 * (i // 2), 512) for i in range(4)]
    for m, (x, y, s) in zip(big, slots):
        p = tex_for(folder, m)
        im = Image.open(p).convert('RGB').resize((s, s), Image.LANCZOS) if p else Image.new('RGB', (s, s), (60, 60, 60))
        A.paste(im, (x, y)); place[m] = (x, y, s)
    small = [m for m in mats if kind(m) != 0]
    sx, sy = 1536, 512   # last 512 slot holds the small tiles (never more than 3 big extras)
    for i, m in enumerate(small):
        x, y = sx + 64 * (i % 8), sy + 64 * (i // 8); p = tex_for(folder, m)
        if kind(m) == 1 and not p: im = Image.new('RGB', (64, 64), (52, 74, 86))       # lens glass
        elif not p: im = Image.new('RGB', (64, 64), (230, 30, 30))
        else: im = Image.open(p).convert('RGB').resize((64, 64), Image.LANCZOS)
        A.paste(im, (x, y)); place[m] = (x, y, 64)
    assert len(big) <= 3, big
    return A, place

def ret_png(folder, mat):
    """The pack's reticle mask (white on black) for a reticle material, or None."""
    pngs = [p for p in set(glob.glob(ROOT + folder + '/**/*.png', recursive=True)) if 'reticle' in os.path.basename(p).lower()]
    norm = lambda t: re.sub(r'\.\d+$', '', t).lower().replace(' ', '_').replace('_low_poly', '')
    key = norm(mat)
    c = [p for p in pngs if norm(os.path.basename(p)).startswith(key + '_base')]
    if not c: c = [p for p in pngs if norm(os.path.basename(p)).startswith(key.split('_with')[0]) and 'reticle_base' in norm(os.path.basename(p))]
    return sorted(c, key=len)[0] if c else None

def vudu_reticle(S=1024):
    """EOTech Vudu has no reticle map in the pack: its SR-1 style drawn (thin cross, heavy outer posts, lit centre ring and dot)."""
    from PIL import ImageDraw
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(im); c = S // 2; k = (10, 10, 10, 255); red = (225, 30, 20, 255)
    d.line([(0, c), (S, c)], fill=k, width=3); d.line([(c, 0), (c, S)], fill=k, width=3)
    for a, b in ((0, int(S * 0.3)), (int(S * 0.7), S)):
        d.rectangle([a, c - 9, b, c + 9], fill=k); d.rectangle([c - 9, a, c + 9, b], fill=k)
    for i in range(1, 5):                                     # drop marks below the centre
        y = c + int(S * 0.04 * i); w = int(S * (0.025 - 0.003 * i)); d.line([(c - w, y), (c + w, y)], fill=k, width=3)
    d.ellipse([c - 60, c - 60, c + 60, c + 60], outline=red, width=6); d.ellipse([c - 8, c - 8, c + 8, c + 8], fill=red)
    return im.transpose(Image.FLIP_TOP_BOTTOM)                # its uv runs v down the sight

def ret_rgba(path, main, size):
    """Mask -> RGBA: a red dot's reticle glows red; a scope's main reticle is etched black, its lit (red) parts stay red."""
    a = np.asarray(Image.open(path).convert('RGB').resize((size, size), Image.LANCZOS)).astype(np.float32)
    m = a.max(-1); red = (a[..., 0] - np.maximum(a[..., 1], a[..., 2])) > 60
    out = np.zeros(a.shape[:2] + (4,), np.uint8); out[..., 3] = np.clip(m, 0, 255)
    if main: out[..., :3] = 10; out[red, 0], out[red, 1], out[red, 2] = 225, 30, 20
    else: out[..., 0], out[..., 1], out[..., 2] = 255, 42, 26
    return Image.fromarray(out, 'RGBA')

def png_url(im):
    b = io.BytesIO(); im.save(b, 'PNG', optimize=True); return 'data:image/png;base64,' + base64.b64encode(b.getvalue()).decode()

def build(pid, folder, name, mag, tube, retmat):
    T, U, M, G = O.read(glob.glob(ROOT + folder + '/OBJ/*.obj')[0])
    K = np.array([kind(m) for m in M])
    rets = sorted(set(M[K == 2]))
    rm = retmat or rets[0]
    R = T[M == rm].reshape(-1, 3); axis = (R.min(0) + R.max(0)) / 2
    if tube:
        MT, MU, MM = burris_mount(T, M, tube, axis)
        T = np.concatenate([T, MT]); U = np.concatenate([U, MU]); M = np.concatenate([M, MM]); K = np.r_[K, np.zeros(len(MT), int)]
    # pack frame (+z front) -> gun frame (+z back): turn half round about y
    T = T * np.array([-1, 1, -1]); axis = axis * np.array([-1, 1, -1])
    P = T.reshape(-1, 3); base = P[:, 1].min(); band = P[P[:, 1] < base + 0.004]
    o = np.array([axis[0], base, (band[:, 2].min() + band[:, 2].max()) / 2]); T = T - o; P = T.reshape(-1, 3)
    line = axis[1] - base
    band = P[P[:, 1] < 0.004]
    mats = sorted(set(M), key=lambda m: (kind(m), m))
    A, place = atlas(folder, mats)
    # uv into the atlas: wrap each face into its tile (stacked shells sit one tile over), then v top-down
    UU = U - np.floor(U.min(1, keepdims=True) + 1e-6)
    UU = np.clip(UU, 0, 1)
    out = np.zeros_like(UU)
    for m in mats:
        x, y, s = place[m]; sel = M == m
        out[sel, :, 0] = (x + UU[sel, :, 0] * s) / A.size[0]
        out[sel, :, 1] = (y + (1 - UU[sel, :, 1]) * s) / A.size[1]
    buf = io.BytesIO(); A.save(buf, 'JPEG', quality=88)
    q = np.clip(np.round(T.reshape(-1) * 10000), -32767, 32767).astype('<i2')
    uq = np.clip(np.round(out.reshape(-1) * 65535), 0, 65535).astype('<u2')
    main = (M == rm) & (mag > 1)
    glass = np.where(main, 3, K).astype('u1')                # 1 lens, 2 lit reticle (red dots, piggyback dots), 3 a scope's own reticle
    rets_out = []
    for m in rets:
        sel = M == m; is_main = bool(mag > 1 and m == rm)
        pth = ret_png(folder, m)
        im = ret_rgba(pth, is_main, 1024 if is_main else 128) if pth else (vudu_reticle() if is_main else None)
        if im is None: print('  no reticle map for', m); continue
        R2 = T[sel].reshape(-1, 3)
        rets_out.append({'main': is_main, 'tex': png_url(im), 'tri': np.nonzero(sel)[0].tolist(),
                         'uv': np.round(U[sel].reshape(-1), 5).tolist(), 'r': round(float(np.ptp(R2[:, 0]) / 2), 5), 'z': round(float(R2[:, 2].mean()), 5)})
    js = {'id': pid, 'tris': base64.b64encode(gzip.compress(q.tobytes(), 9)).decode(),
          'uv': base64.b64encode(gzip.compress(uq.tobytes(), 9)).decode(), 'tex': 'data:image/jpeg;base64,' + base64.b64encode(buf.getvalue()).decode(),
          'sn': base64.b64encode(gzip.compress(smooth(T).tobytes(), 9)).decode(), 'outline': 0.001,
          'glass': base64.b64encode(gzip.compress(glass.tobytes(), 9)).decode(), 'reticles': rets_out}
    json.dump(js, open(OUT + pid + '.json', 'w'))
    rec = {'id': pid, 'name': name, 'kind': 'optic', 'src': 'pack2', 'mount': guns_for(pid, mag), 'size': np.ptp(P, 0).round(4).tolist(),
           'c': ((P.min(0) + P.max(0)) / 2).round(5).tolist(), 'line': round(float(line), 5), 'front': round(float(P[:, 2].min()), 5),
           'back': round(float(P[:, 2].max()), 5), 'how': 'reticle middle', 'mag': mag, 'glassFlags': True,
           'foot': [round(float(band[:, 2].min()), 5), round(float(band[:, 2].max()), 5)]}
    if mag > 1:      # the eyepiece: rearmost lens on the sight line (the game's zoomed picture fills it)
        best = None
        for m in sorted(set(M[K == 1])):
            L = T[M == m].reshape(-1, 3); cx, cy = (L[:, 0].min() + L[:, 0].max()) / 2, (L[:, 1].min() + L[:, 1].max()) / 2
            if abs(cx) > 0.003 or abs(cy - line) > 0.003: continue
            z = L[:, 2].max(); r = float(np.hypot(L[:, 0] - cx, L[:, 1] - cy).max())
            if best is None or z > best[0]: best = (z, r)
        rec['eyepiece'] = [round(float(best[0]), 5), round(best[1], 5)]
    if mag <= 1: rec['dot'] = [0.0, rec['line'], round(float(axis[2] - o[2]), 5)]     # where the red dot shows (on its window)
    print(pid, len(T), 'tris', rec['size'], 'line', rec['line'], 'jpeg', len(buf.getvalue()) // 1024, 'KB', len(rec['mount']), 'guns', flush=True)
    return rec

if __name__ == '__main__':
    idx = [r for r in json.load(open(OUT + 'index.json')) if r.get('src') != 'pack2']
    # his first pack's sights follow the same rules: pistols/shotguns only the FastFire and EFLX now
    for r in idx:
        if r['kind'] == 'optic': r['mount'] = [g for g in RIFLES + (SMGS if r['mag'] <= 1 else [])]
    for s in SIGHTS: idx.append(build(*s))
    json.dump(idx, open(OUT + 'index.json', 'w'), indent=0)
