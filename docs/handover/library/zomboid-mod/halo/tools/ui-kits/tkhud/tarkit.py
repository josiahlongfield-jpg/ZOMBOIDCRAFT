# Builds the in-game Tarkov operator HUD kit (LOCAL ONLY, from Josiah's Tarkov files) the way tarkov_hud.py drew the preview.
import os, sys, json, glob, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)) + '/../reach')
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import body as B
P = '/mnt/user-data/uploads/additions/tarkov-ui/png/'
FD = '/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/fonts/'
OUT = sys.argv[1] if len(sys.argv) > 1 else 'gamekit/tarkov'
Z = 2
shutil.rmtree(OUT, ignore_errors=True)
for d in ('icons', 'body', 'font', 'ui'): os.makedirs(OUT + '/' + d, exist_ok=True)
kit = dict(version=1, layout=[1280, 720], scale_png=2, fonts={}, icons={}, body={}, ui={})
def sp(n): return Image.open(glob.glob(P + 'S_%s__*' % n)[0]).convert('RGBA')
EFFECTS = ['light_bleeding', 'heavy_bleeding', 'fracture', 'pain', 'painkiller', 'contusion', 'tremor', 'tunnelvision', 'exhausted', 'fatigue',
           'dehydrated', 'encumbered', 'overencumbered', 'stun', 'frozen', 'sunheat', 'intoxication', 'lethal_intoxication', 'zombie_infection',
           'stim_buff', 'stim_debuff', 'berserk', 'fear', 'Panic', 'wound', 'misfire', 'immunity']
for e in EFFECTS:
    try: im = sp('effect_' + e)
    except IndexError: continue
    im.save(OUT + '/icons/%s.png' % e.lower()); kit['icons'][e.lower()] = dict(file='icons/%s.png' % e.lower(), w=im.width * 1.25, h=im.height * 1.25)
# body parts: luminance x 1.25 in white, so the game multiplies in each part's colour (body.py's tint)
for n, (x, y) in B.LAY.items():
    a = np.array(B.part(n)).astype(float); lum = a[..., :3].mean(-1, keepdims=True) / 255
    a[..., :3] = np.clip(lum * 1.25, 0, 1) * 255; Image.fromarray(a.astype('uint8'), 'RGBA').save(OUT + '/body/%s.png' % n)
    kit['body'][n] = dict(file='body/%s.png' % n, x=x, y=y, w=a.shape[1], h=a.shape[0])
for n in ('health_bar_border', 'health_bar_fill', 'body_part_broken_cross', 'body_part_background'):
    im = sp(n); im.save(OUT + '/ui/%s.png' % n); kit['ui'][n] = dict(file='ui/%s.png' % n, w=im.width, h=im.height)
CH = ''.join(chr(c) for c in range(32, 127))
def atlas(key, ttf, size, colour, shadow=True):
    f = ImageFont.truetype(FD + ttf, int(size * Z)); pad = int(4 * Z); cells = {}; ims = []
    for ch in CH:
        l, t, r, b = f.getbbox(ch, anchor='ls'); adv = f.getlength(ch)
        if r <= l: l, r = 0, 1
        if b <= t: t, b = -1, 0
        cw, chh = r - l + 2 * pad, b - t + 2 * pad
        im = Image.new('RGBA', (cw, chh), (0, 0, 0, 0))
        if shadow:
            sh = Image.new('RGBA', (cw, chh), (0, 0, 0, 0)); ImageDraw.Draw(sh).text((pad - l + Z, pad - t + Z), ch, font=f, fill=(0, 0, 0, 170), anchor='ls')
            im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(1.2 * Z)))
        ImageDraw.Draw(im).text((pad - l, pad - t), ch, font=f, fill=colour, anchor='ls')
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
    A.save(OUT + '/font/%s.png' % key); kit['fonts'][key] = dict(file='font/%s.png' % key, aw=A.width, ah=A.height, ascent=a_ / Z, descent=d_ / Z, glyphs=cells)
atlas('gun_name', 'Rajdhani-SemiBold.ttf', 20, (222, 222, 212, 235))
atlas('gun_mode', 'Rajdhani-Medium.ttf', 15, (170, 170, 160, 225))
atlas('gun_ammo', 'Rajdhani-SemiBold.ttf', 26, (222, 222, 212, 235))
atlas('tab_label', 'Rajdhani-SemiBold.ttf', 11, (200, 200, 190, 230), shadow=False)
atlas('tab_value', 'Rajdhani-Medium.ttf', 12, (200, 200, 190, 230), shadow=False)
atlas('tab_total', 'Rajdhani-SemiBold.ttf', 22, (222, 222, 212, 240), shadow=False)
# layout (1280x720), as in the approved preview (tarkov_hud.py)
kit['layout_spec'] = dict(
    stamina=dict(cx=640, bars=[dict(y=676, w=250, h=4), dict(y=685, w=170, h=3)], back=[0, 0, 0, 110], fill=[205, 205, 195, 215]),
    status=dict(x=18, y=678, gap=5, scale=1.25),
    gunline=dict(right=1258, y=632, mode_dy=22, ammo_dy=40),
    tab=dict(x0=110, y0=110, k=0.62, shade=[8, 10, 10, 170], pad=[60, 50, 60, 40], body_w=400, body_h=720,
             parts=[['head', 'HEAD'], ['chest', 'THORAX'], ['stomach', 'STOMACH'], ['leftarm', 'LEFT ARM'], ['rightarm', 'RIGHT ARM'], ['leftleg', 'LEFT LEG'], ['rightleg', 'RIGHT LEG']],
             bar_scale=0.6, colours=dict(good=[150, 190, 150], hurt=[205, 175, 80], bad=[200, 80, 60], dead=[40, 40, 40])))
json.dump(kit, open(OUT + '/kit.json', 'w'), indent=1)
print('tarkov kit', OUT, len(kit['icons']), 'icons')
