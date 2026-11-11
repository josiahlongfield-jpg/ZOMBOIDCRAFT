# Builds the in-game Reach HUD kit (LOCAL ONLY: it is made from Josiah's MCC files and goes to his PC, never the repo).
# Every piece is rendered exactly as the previews were (hudrender.py), with the bow warp baked in, cropped, and placed in
# 1280x720 layout units. Images are 2x. The game (client/hud/halo) reads kit.json and draws them.
import os, sys, json, math, shutil
os.environ['HUDZ'] = '2'
sys.argv = ['x']; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import hudrender as h, sheet as S

Z = h.Z; W, H = h.W, h.H
OUT = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] != 'x' else 'gamekit/reach'
shutil.rmtree(OUT, ignore_errors=True)
for d in ('hud', 'panel', 'backpack', 'reticle', 'font'): os.makedirs(OUT + '/' + d, exist_ok=True)
kit = dict(version=1, layout=[1280, 720], scale_png=2, elements={}, weapons={}, fonts={})

# ---------------------------------------------------------------- warp a region of a full-canvas layer
def _inv(X, Y):
    u, v = X, Y
    for _ in range(20):
        fx, fy = h.bow_fwd(u, v); u += X - fx; v += Y - fy
    return u, v
def fwd_px(x, y):
    """A layout point (Z units) to where the bow warp puts it."""
    u, v = h.bow_fwd(x / W * 2 - 1, y / H * 2 - 1); return ((u + 1) / 2 * W, (v + 1) / 2 * H)
def region(L, flat=False, box=None, pad=None):
    """Warp (unless flat) and crop: returns (image, (x, y) of its top-left in Z px). box fixes the output box."""
    if box is None:
        bb = L.getbbox()
        if bb is None: return None, (0, 0)
        if flat: box = bb
        else:
            xs, ys = [], []
            for (px, py) in ((bb[0], bb[1]), (bb[2], bb[1]), (bb[0], bb[3]), (bb[2], bb[3]), ((bb[0]+bb[2])/2, bb[1]), ((bb[0]+bb[2])/2, bb[3])):
                fx, fy = fwd_px(px, py); xs.append(fx); ys.append(fy)
            m = int(8 * Z) if pad is None else pad
            box = (max(0, int(min(xs)) - m), max(0, int(min(ys)) - m), min(W, int(max(xs)) + m), min(H, int(max(ys)) + m))
    if flat: return L.crop(box), box[:2]
    x0, y0, x1, y1 = box
    # source area: inverse-map the box's corners and edge midpoints, padded
    pts = [(x, y) for x in np.linspace(x0, x1, 5) for y in np.linspace(y0, y1, 5)]
    sp = [(((_inv(px / W * 2 - 1, py / H * 2 - 1)[0] + 1) / 2 * W), ((_inv(px / W * 2 - 1, py / H * 2 - 1)[1] + 1) / 2 * H)) for px, py in pts]
    sx0 = max(0, int(min(p[0] for p in sp)) - 8); sy0 = max(0, int(min(p[1] for p in sp)) - 8)
    sx1 = min(W, int(max(p[0] for p in sp)) + 8); sy1 = min(H, int(max(p[1] for p in sp)) + 8)
    src = L.crop((sx0, sy0, sx1, sy1))
    nx, ny = max(2, (x1 - x0) // int(16 * Z) + 1), max(2, (y1 - y0) // int(16 * Z) + 1)
    def s(px, py):
        u, v = _inv(px / W * 2 - 1, py / H * 2 - 1); return ((u + 1) / 2 * W - sx0, (v + 1) / 2 * H - sy0)
    mesh = []
    for gy in range(ny):
        for gx in range(nx):
            a0, a1 = x0 + gx * (x1 - x0) / nx, x0 + (gx + 1) * (x1 - x0) / nx
            b0, b1 = y0 + gy * (y1 - y0) / ny, y0 + (gy + 1) * (y1 - y0) / ny
            q = s(a0, b0) + s(a0, b1) + s(a1, b1) + s(a1, b0)
            mesh.append(((int(round(a0 - x0)), int(round(b0 - y0)), int(round(a1 - x0)), int(round(b1 - y0))), q))
    return src.transform((x1 - x0, y1 - y0), Image.MESH, mesh, Image.BICUBIC), (x0, y0)

def save(img, rel, pos):
    img.save(OUT + '/' + rel)
    return dict(file=rel, x=round(pos[0] / Z, 3), y=round(pos[1] / Z, 3), w=round(img.width / Z, 3), h=round(img.height / Z, 3))

def canvas(): return Image.new('RGBA', (W, H), (0, 0, 0, 0))

# ---------------------------------------------------------------- text specs (where each Reach text sits, how big)
FONTFILE = h.FONT
def text_spec(w, name):
    p = w['place'][0] if w['place'] else dict(anchor=0, offset=(0, 0), mirror=(0, 0), scale=(1, 1))
    ax, ay = h.ANCH.get(p['anchor'], (640 * Z, 360 * Z))
    P = (ax + p['offset'][0] * h.HS[0] * Z, ay + p['offset'][1] * h.HS[1] * Z); ps = p['scale']
    OX, OY = h.HS[0] * ps[0] * Z, h.HS[1] * ps[1] * Z
    for tx in w['texts']:
        if tx['name'] != name: continue
        bp = tx['place'][0]; sc = bp['scale']
        bx, by = (P[0] + bp['offset'][0] * OX, P[1] + bp['offset'][1] * OY) if bp['anchor'] == 0 else \
                 (h.ANCH[bp['anchor']][0] + bp['offset'][0] * OX, h.ANCH[bp['anchor']][1] + bp['offset'][1] * OY)
        size = {8: 21, 6: 17, 5: 15, 1: 21}.get(tx['font'], 18) * sc[1] * ps[1] * Z
        rd = tx['render'][0] if tx['render'] else None
        col = tuple(int(v * 255) for v in h.colour(rd, 0))
        fx, fy = fwd_px(bx, by)
        return dict(x=round(fx / Z, 3), y=round(fy / Z, 3), align_x=bp['mirror'][0], align_y=bp['mirror'][1], size=size / Z, colour=col)
    return None

def font_atlas(key, size_layout, colour, chars, shadow=None, ttf=FONTFILE):
    """Glyph cells for a font at one size and colour. Metrics in layout px; pen at the baseline."""
    if key in kit['fonts']: return key
    f = ImageFont.truetype(ttf, max(1, int(round(size_layout * Z))))
    asc, desc = f.getmetrics(); pad = int(3 * Z)
    cells = {}; imgs = []
    for ch in chars:
        l, t, r, b = f.getbbox(ch, anchor='ls')
        adv = f.getlength(ch)
        cw, chh = max(1, r - l) + 2 * pad, max(1, b - t) + 2 * pad
        im = Image.new('RGBA', (cw, chh), (0, 0, 0, 0))
        if shadow:
            sh = Image.new('RGBA', (cw, chh), (0, 0, 0, 0))
            ImageDraw.Draw(sh).text((pad - l + shadow[0] * Z, pad - t + shadow[1] * Z), ch, font=f, fill=shadow[2], anchor='ls')
            im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(shadow[3] * Z)))
        ImageDraw.Draw(im).text((pad - l, pad - t), ch, font=f, fill=tuple(colour), anchor='ls')
        imgs.append((ch, im, l - pad, t - pad, adv, l, r, t, b))
    # pack in one row-wrapped atlas
    AW = 1024; x = y = rowh = 0; placed = []
    for ch, im, ox, oy, adv, l, r, t, b in imgs:
        if x + im.width > AW: x = 0; y += rowh + 2; rowh = 0
        placed.append((ch, im, x, y, ox, oy, adv, l, r, t, b)); x += im.width + 2; rowh = max(rowh, im.height)
    atlas = Image.new('RGBA', (AW, y + rowh + 2), (0, 0, 0, 0))
    for ch, im, ax_, ay_, ox, oy, adv, l, r, t, b in placed:
        atlas.alpha_composite(im, (ax_, ay_))
        cells[ch] = dict(u=ax_, v=ay_, w=im.width, h=im.height, ox=round(ox / Z, 3), oy=round(oy / Z, 3), adv=round(adv / Z, 3),
                         l=round(l / Z, 3), r=round(r / Z, 3), t=round(t / Z, 3), b=round(b / Z, 3))
    atlas.save(OUT + '/font/' + key + '.png')
    kit['fonts'][key] = dict(file='font/' + key + '.png', aw=atlas.width, ah=atlas.height, glyphs=cells)
    return key

DIGITS = '0123456789/ m:-x'
def text_entry(w, name, chars=DIGITS):
    ts = text_spec(w, name)
    if ts is None: return None
    key = 'saira_%d_%02x%02x%02x' % (round(ts['size'] * 10), *ts['colour'][:3])
    font_atlas(key, ts['size'], ts['colour'] + (255,) if len(ts['colour']) == 3 else ts['colour'], chars)
    return dict(font=key, x=ts['x'], y=ts['y'], align_x=ts['align_x'], align_y=ts['align_y'])

sp = {w['name']: w for w in h.c.parse('ui\\chud\\spartan')}
E = kit['elements']

# ---------------------------------------------------------------- shield and health (frames by fill)
def frames(fn, n, rel, flat=False):
    full = fn(1.0); im, pos = region(full, flat)
    box = (pos[0], pos[1], pos[0] + im.width, pos[1] + im.height)
    out = []
    for k in range(n + 1):
        img, p = region(fn(k / n), flat, box=box)
        out.append(save(img, rel % k, p))
    return out
def shield(f):
    L = canvas(); h.draw_widget(L, sp['?80632'], dict(skip=('flahsy', 'meter_sup2', 'meter_sup3', 'meter_sup4', 'meter_border_flash'), fill={'meter': f})); return L
def health(f):
    L = canvas(); h.draw_widget(L, sp['health_meter'], dict(skip=('flahsy', 'shield_boot_blob', 'compass_additive', 'compass'), fill={'health_meter': f})); return L
E['shield'] = dict(kind='frames', frames=frames(shield, 40, 'hud/shield_%02d.png'))
E['health'] = dict(kind='frames', frames=frames(health, 20, 'hud/health_%02d.png'))
print('shield/health done', flush=True)

# ---------------------------------------------------------------- compass strip
im = h.img('ui\\chud\\bitmaps\\compass_alpha').convert('L'); a = np.array(im).astype(float) / 255
rgb = np.clip(np.array(h.G[2][1:]) * 1.15, 0, 1)
o = np.zeros(a.shape + (4,)); o[..., :3] = rgb; o[..., 3] = a
Image.fromarray((o * 255).astype('uint8'), 'RGBA').save(OUT + '/hud/compass_strip.png')
cx, cy = h.ANCH[2][0], h.ANCH[2][1] - 20 * h.HS[1] * Z
win = int(0.4 * a.shape[1])
wpx = win * 0.5 * h.HS[0]; hpx = a.shape[0] * 0.5 * h.HS[1]
xs = np.linspace(-0.5, 0.5, 33)
bow = []
for t in xs:
    px = cx + t * wpx * Z; fx, fy = fwd_px(px, cy); bow.append([round((fx - px) / Z, 3), round((fy - cy) / Z, 3)])
E['compass'] = dict(kind='compass', file='hud/compass_strip.png', texels=a.shape[1], texh=a.shape[0], east_texel=595, texels_per_90=768,
                    window_texels=win, cx=round(cx / Z, 3), cy=round(cy / Z, 3), w=round(wpx, 3), h=round(hpx, 3), fade=0.14, bow=bow)

# ---------------------------------------------------------------- motion tracker: background + where a flat point lands
T = canvas(); h.draw_widget(T, sp['?80630'], dict(skip=('voice_broadcast1', 'voice_broadcast2', 'ping_boot', '?8003e'), alpha={'motion_tracker_background': 0.72}))
tb = np.array(T)[..., 3]; ys_, xs_ = np.nonzero(tb > 40)
tcx, tcy = h.ANCH[8]; R = max(xs_.max() - tcx, tcx - xs_.min(), ys_.max() - tcy, tcy - ys_.min())
Lt = canvas(); Lt.alpha_composite(h.tilt(T, tcx, tcy, 80 * Z))
tx25 = text_entry(sp['?80630'], '25m')
img, pos = region(Lt); trk = save(img, 'hud/tracker.png', pos)
# forward homography of tilt (src flat -> dst tilted)
t = dict(s=0.95, top=0.86, bot=1.04, up=0.62, down=0.72); r = 80 * Z; R_ = r * t['s']
src = [(tcx - r, tcy - r), (tcx + r, tcy - r), (tcx + r, tcy + r), (tcx - r, tcy + r)]
dst = [(tcx - R_ * t['top'], tcy - R_ * t['up']), (tcx + R_ * t['top'], tcy - R_ * t['up']), (tcx + R_ * t['bot'], tcy + R_ * t['down']), (tcx - R_ * t['bot'], tcy + R_ * t['down'])]
cf = h.persp_coeffs(src, dst)  # maps a flat point to the tilted one
def tilt_fwd(x, y):
    a_, b_, c_, d_, e_, f_, g_, h_ = cf; den = g_ * x + h_ * y + 1; return ((a_ * x + b_ * y + c_) / den, (d_ * x + e_ * y + f_) / den)
N = 17; grid = []
for j in range(N):
    row = []
    for i in range(N):
        u, v = -1.2 + 2.4 * i / (N - 1), -1.2 + 2.4 * j / (N - 1)
        x, y = tilt_fwd(tcx + u * R, tcy + v * R); fx, fy = fwd_px(x, y); row.append([round(fx / Z, 3), round(fy / Z, 3)])
    grid.append(row)
# blips: Reach-style glowing dots (enemy red, friend yellow)
for nm, col in (('enemy', (240, 40, 40)), ('friend', (245, 215, 40))):
    s_ = int(9 * Z) * 2; b = Image.new('RGBA', (s_, s_), (0, 0, 0, 0)); dd = ImageDraw.Draw(b)
    q = s_ // 4; dd.ellipse((q, q, s_ - q - 1, s_ - q - 1), fill=col + (235,)); g = b.filter(ImageFilter.GaussianBlur(2 * Z)); g.alpha_composite(b); g.save(OUT + '/hud/blip_%s.png' % nm)
E['tracker'] = dict(kind='tracker', image=trk, range_m=25, grid=grid, grid_span=1.2, blip_size=9 * 2 / 2 * 2 / 2 * 2, blips=dict(enemy='hud/blip_enemy.png', friend='hud/blip_friend.png'), text_25m=tx25)
E['tracker']['blip_size'] = 7  # drawn size in layout px of the blip image (it has glow padding)
print('tracker done', flush=True)

# ---------------------------------------------------------------- grenades
L = canvas(); h.draw_widget(L, sp['?80631'], dict(text={'grenade_count_hilite': '', 'grenade_count_backpack': ''}, sprite={'unhilight': 1}))
img, pos = region(L); E['grenades'] = dict(kind='image', image=save(img, 'hud/grenades.png', pos), count=text_entry(sp['?80631'], 'grenade_count_hilite'))

# ---------------------------------------------------------------- ability (sprint) with energy frames
eq = h.c.parse('ui\\chud\\personal_equipment\\e_speed_burst')[0]
def ability(f):
    L = canvas(); h.draw_widget(L, eq, dict(fill={'energy_meter': f}, fit={'?80635': 30})); return L
E['ability'] = dict(kind='frames', frames=frames(ability, 20, 'hud/ability_%02d.png'))
print('ability done', flush=True)

# ---------------------------------------------------------------- weapons: panel frames by rounds, backpack glyph, reticles
for wn in S.NAMES:
    ws = h.c.parse('ui\\chud\\' + wn); n, _ = h.AMMO.get(wn, (1, ''))
    main = [w for w in ws if w['special'] == 1 and w['name'] not in ('backpack_ammo_area', 'warning_flashes')]
    gl = h.glyph(h.GLYPH.get(wn, 0xe112))
    def panel(f):
        L = canvas()
        for w in main: h.draw_widget(L, w, dict(skip=('red_flash',), fill={'meter': f}, text={'shematic': gl, 'reserve_count': '', 'ammo_count': '', 'reserve_count_kudos': ''}))
        return L
    os.makedirs(OUT + '/panel/' + wn, exist_ok=True)
    fr = frames(panel, max(1, n), 'panel/' + wn + '/%02d.png')
    num = None
    for w in main:
        for nm in ('reserve_count', 'ammo_count'):
            num = num or text_entry(w, nm)
    bp = [w for w in ws if w['name'] == 'backpack_ammo_area']
    back = None
    if bp:
        L = canvas(); h.draw_widget(L, bp[0], dict(text={'shematic_backpack': gl, 'ammo_count': ''}))
        img, pos = region(L)
        if img is not None: back = dict(image=save(img, 'backpack/%s.png' % wn, pos), count=text_entry(bp[0], 'ammo_count'))
    # reticle: rest, plus spread frames for the weapons whose spread animations we have
    rel = []
    steps = 11 if wn in h.SPREAD else 1
    Xs = [S.hip(wn, spread=k / (steps - 1)) if wn in h.SPREAD else S.hip(wn) for k in range(steps)]
    bb = [X.getbbox() for X in Xs if X.getbbox()]
    if bb:
        rr = max(max(abs(b[0] - W / 2), abs(b[2] - W / 2), abs(b[1] - H / 2), abs(b[3] - H / 2)) for b in bb) + 4
        rr = int(math.ceil(rr))
        for k, X in enumerate(Xs):
            c_ = X.crop((W // 2 - rr, H // 2 - rr, W // 2 + rr, H // 2 + rr)); c_.save(OUT + '/reticle/%s_%02d.png' % (wn, k)); rel.append('reticle/%s_%02d.png' % (wn, k))
        rsize = 2 * rr / Z
    else: rsize = 0
    kit['weapons'][wn] = dict(name=S.NAMES[wn], rounds=n, panel=fr, number=num, backpack=back, reticle=rel, reticle_size=round(rsize, 3),
                              spread=dict(h.SPREAD_TIME.get(wn, {})), calibrated=wn in h.SPREAD)
    print('weapon', wn, len(fr), len(rel), flush=True)

json.dump(kit, open(OUT + '/kit.json', 'w'), indent=1)
print('kit done', OUT)
