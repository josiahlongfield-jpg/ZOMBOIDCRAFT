# Builds the in-game character selection kit (LOCAL ONLY: Reach UI bitmaps, posed Noble renders, Tarkov faces) the way
# screens.py drew the approved round 5 mock-ups. Output goes to config/zomboidcraft/hud/charsel on Josiah's PC.
import os, sys, json, shutil
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageOps, ImageEnhance
SC = '/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/'
UI = SC + 'cs/ui/'; GL = SC + 'reach/hudpack2/glyphs/'; FD = SC + 'fonts/'; POSED = SC + 'cs/posed3/'
OUT = sys.argv[1] if len(sys.argv) > 1 else SC + 'charsel'
Z = 2
shutil.rmtree(OUT, ignore_errors=True)
for d in ('ui', 'fig', 'glyph', 'face', 'font'): os.makedirs(OUT + '/' + d, exist_ok=True)
kit = dict(version=1, layout=[1920, 1080], fonts={}, ui={}, fig={}, glyph={}, face={})
def ui(n): return Image.open(UI + n).convert('RGBA')
def save(im, rel, **extra):
    im.save(OUT + '/' + rel, optimize=True); return dict(file=rel, w=im.width, h=im.height, **extra)

# ---- card nine-slice templates (Reach's 10px corner pieces), white, fill and line apart
def slice_card(w, h, which):
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    def slice9(n):
        p = ui(n); cap = 40
        top = p.crop((0, 0, p.width, cap)); mid = p.crop((0, cap, p.width, cap + 1)).resize((p.width, h - 2 * cap))
        col = Image.new('RGBA', (p.width, h)); col.paste(top, (0, 0)); col.paste(mid, (0, cap)); col.paste(ImageOps.flip(top), (0, h - cap))
        a = np.array(col).astype(float); a[..., :3] = 255; return Image.fromarray(a.astype('uint8'), 'RGBA')
    n = 'in_game_loadout_bitmaps_bitmap_corner_10pix%s_00.png' % ('_line' if which == 'line' else '')
    c = slice9(n); m = slice9(n.replace('corner', 'center'))
    out.alpha_composite(c, (0, 0)); out.alpha_composite(m.resize((w - 2 * c.width, h)), (c.width, 0)); out.alpha_composite(ImageOps.mirror(c), (w - c.width, 0))
    return out
kit['ui']['card_fill'] = save(slice_card(200, 200, 'fill'), 'ui/card_fill.png', inset=[36, 40])
kit['ui']['card_line'] = save(slice_card(200, 200, 'line'), 'ui/card_line.png', inset=[36, 40])
g = Image.new('RGBA', (240, 240), (0, 0, 0, 0)); ImageDraw.Draw(g).rounded_rectangle((18, 18, 222, 222), 14, outline=(255, 255, 255, 255), width=6)
kit['ui']['glow'] = save(g.filter(ImageFilter.GaussianBlur(7)), 'ui/glow.png', inset=[56, 56], pad=20)
# title fade line, list highlight, lattice, lock, rank, spin arrows, grenades
m = ui('common_menu_title_alpha_mask_00.png').resize((760, 4)); a = np.array(m).astype(float)
k = a[..., :3].max(-1) / 255 if a[..., 3].min() == 255 else a[..., 3] / 255
L = np.zeros((4, 760, 4)); L[..., :3] = 255; L[..., 3] = k * 255
kit['ui']['fadeline'] = save(Image.fromarray(L.astype('uint8'), 'RGBA'), 'ui/fadeline.png')
gr = ui('lobbies_roster_gradient_bitmap_00.png').resize((470, 44)); a = np.array(gr).astype(float)
kk = a[..., :3].max(-1) / 255; ramp = np.linspace(1, 0.05, 470)[None, :]
L = np.zeros((44, 470, 4)); L[..., :3] = 255; L[..., 3] = 255 * ramp * (0.6 + 0.4 * kk)
kit['ui']['highlight'] = save(Image.fromarray(L.astype('uint8'), 'RGBA'), 'ui/highlight.png')
lat = ui('common_img_auntiedot_background_ondemand_00.png').resize((1100, 1100), Image.LANCZOS)
la = np.array(lat).astype(float); la[..., 3] = la[..., :3].max(-1) * 0.10; la[..., :3] = 255
kit['ui']['lattice'] = save(Image.fromarray(la.astype('uint8'), 'RGBA'), 'ui/lattice.png')
# vignette: the menus' darkening toward the edges (black, alpha)
yy, xx = np.mgrid[0:270, 0:480]; v = 1 - 0.55 * (((xx - 240) / 480) ** 2 + ((yy - 135) / 270) ** 2) * 2.2
L = np.zeros((270, 480, 4)); L[..., 3] = (1 - np.clip(v, 0.35, 1)) * 255
kit['ui']['vignette'] = save(Image.fromarray(L.astype('uint8'), 'RGBA'), 'ui/vignette.png')
sh = Image.new('RGBA', (340, 80), (0, 0, 0, 0)); ImageDraw.Draw(sh).ellipse((20, 20, 320, 60), fill=(0, 0, 0, 150))
kit['ui']['shadow'] = save(sh.filter(ImageFilter.GaussianBlur(10)), 'ui/shadow.png')
kit['ui']['lock'] = save(ui('in_game_loadout_bitmaps_equipment_scematics_00.png'), 'ui/lock.png')
med = ui('lobbies_exp_med_ui_ondemand_00.png'); kit['ui']['rank'] = save(med.crop((0, 0, med.width // 12, med.height // 6)), 'ui/rank.png')
kit['ui']['arrow_l'] = save(ui('common_spin_arrow_lft_00.png'), 'ui/arrow_l.png')
kit['ui']['arrow_r'] = save(ui('common_spin_arrow_rgt_00.png'), 'ui/arrow_r.png')
kit['ui']['grenades'] = save(Image.open(SC + 'reach/gamekit/reach/hud/grenades.png').convert('RGBA'), 'ui/grenades.png')

# ---- glyphs (white, alpha from luminance, cropped) as screens.py's glyph()
for kind in ('weapons', 'abilities'):
    for f in sorted(os.listdir(GL + kind)):
        im = Image.open(GL + kind + '/' + f).convert('RGBA'); a = np.array(im).astype(float)
        lum = a[..., :3].max(-1) / 255; a[..., :3] = 255; a[..., 3] = a[..., 3] * np.clip(lum * 1.2, 0, 1)
        im = Image.fromarray(a.astype('uint8'), 'RGBA'); im = im.crop(im.getbbox())
        kit['glyph'][kind + '/' + f[:-4]] = save(im, 'glyph/%s_%s' % (kind, f))

# ---- the Noble figures: front view for the cards and the side pick, three-quarter for the Armory
def key(im, bg=(40, 44, 52), tol=4, soft=8):
    a = np.array(im).astype(float); d = np.abs(a[..., :3] - np.array(bg)).max(-1)
    a[..., 3] = a[..., 3] * np.clip((d - tol) / soft, 0, 1); return Image.fromarray(a.astype('uint8'), 'RGBA')
for n in ['carter', 'kat', 'jun', 'emile', 'jorge', 'six', 'john']:
    for view, pre in (('front', 'front_'), ('quarter', '')):
        im = key(Image.open(POSED + pre + n + '.png').convert('RGBA')); im = im.crop(im.getbbox())
        a = im.getchannel('A'); im = ImageEnhance.Brightness(im.convert('RGB')).enhance(1.5 if n == 'six' else 1.2).convert('RGBA'); im.putalpha(a)
        kit['fig'][view + '_' + n] = save(im, 'fig/%s_%s.png' % (view, n))

# ---- operator faces (Tarkov renders): bare head and in kit
faces = Image.open('/mnt/project-files/zomboid-mod/tarkov/previews/tarkov-player-faces.jpg').convert('RGBA'); FK = faces.width / 1077
def face(idx, helmet):
    c, r = idx % 4, idx // 4; cx = [0, 272, 543, 814][c] + (128 if helmet else 0)
    return faces.crop((int(cx * FK), int((r * 150 + 16) * FK), int((cx + 134) * FK), int((r * 150 + 148) * FK)))
rows = faces.height / FK
for idx in range(int((rows - 16) // 150 + 1) * 4):
    try:
        b, h = face(idx, False), face(idx, True)
    except Exception:
        continue
    if b.getbbox() is None or np.array(b)[..., :3].std() < 4: continue
    kit['face']['%d' % idx] = dict(bare=save(b.convert('RGB'), 'face/%d_bare.jpg' % idx)['file'], kit=save(h.convert('RGB'), 'face/%d_kit.jpg' % idx)['file'])

# ---- fonts: white atlases (the game tints them), at twice the layout size
CH = ''.join(chr(c) for c in range(32, 127)) + '·'
def atlas(key_, ttf, size):
    f = ImageFont.truetype(FD + ttf, int(size * Z)); pad = int(3 * Z); cells = {}; ims = []
    for ch in CH:
        l, t, r, b = f.getbbox(ch, anchor='ls'); adv = f.getlength(ch)
        if r <= l: l, r = 0, 1
        if b <= t: t, b = -1, 0
        im = Image.new('RGBA', (r - l + 2 * pad, b - t + 2 * pad), (0, 0, 0, 0))
        ImageDraw.Draw(im).text((pad - l, pad - t), ch, font=f, fill=(255, 255, 255, 255), anchor='ls')
        ims.append((ch, im, l - pad, t - pad, adv, l, r, t, b))
    AW = 1024; x = y = rh = 0; pl = []
    for it in ims:
        im = it[1]
        if x + im.width > AW: x = 0; y += rh + 2; rh = 0
        pl.append((it, x, y)); x += im.width + 2; rh = max(rh, im.height)
    A = Image.new('RGBA', (AW, y + rh + 2), (0, 0, 0, 0))
    for (ch, im, ox, oy, adv, l, r, t, b), ax, ay in pl:
        A.alpha_composite(im, (ax, ay))
        cells[ch] = dict(u=ax, v=ay, w=im.width, h=im.height, ox=ox / Z, oy=oy / Z, adv=adv / Z, l=l / Z, r=r / Z, t=t / Z, b=b / Z)
    a_, d_ = f.getmetrics()
    A.save(OUT + '/font/%s.png' % key_, optimize=True)
    kit['fonts'][key_] = dict(file='font/%s.png' % key_, aw=A.width, ah=A.height, ascent=a_ / Z, descent=d_ / Z, glyphs=cells)
MED, LIGHT = 'SairaSemiCondensed-Medium.ttfx.ttf', 'Sairax.ttf'
for sz in (18, 20, 21, 22, 24, 25, 26): atlas('med_%d' % sz, MED, sz)
for sz in (28, 30, 46, 50, 54, 64): atlas('light_%d' % sz, LIGHT, sz)
json.dump(kit, open(OUT + '/kit.json', 'w'), separators=(',', ':'))
tot = sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(OUT) for f in fs)
print('kit', OUT, round(tot / 1e6, 2), 'MB', len(kit['face']), 'faces')
