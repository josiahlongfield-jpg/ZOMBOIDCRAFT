# Endless Night ARMORY v2 mock-ups: the approved round-5 armory layout (categories left, Spartan centre, list right),
# now with every Reach piece by its real MCC name, Reach's 30 colours, MCC's emblems and nameplates, and MCC's 21 poses.
# Halo/MCC art: LOCAL ONLY (never in the repo, jar or a published artifact).
import sys, json, numpy as np
sys.path[:0] = ['/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/cs', '/tmp/claude-0/mcc/tools']
from PIL import Image, ImageDraw, ImageEnhance
import screens as S
import emblem as EM
F, WHITE, DIM, BLUE = S.F, S.WHITE, S.DIM, S.BLUE
N = json.load(open('/mnt/project-files/zomboid-mod/halo/armory/armory_names.json'))
R = '/tmp/claude-0/armory/r/'
OUT = '/mnt/project-files/zomboid-mod/halo/previews/armory/'
NP = '/mnt/user-data/uploads/data/ui/nameplates/images/'
LIST_DIM = (200, 214, 228)
CATS = [('HELMET', 'helmet'), ('LEFT SHOULDER', 'left_shoulder'), ('RIGHT SHOULDER', 'right_shoulder'), ('CHEST', 'chest'),
        ('WRIST', 'wrist'), ('UTILITY', 'utility'), ('KNEE GUARDS', 'knees'), ('VISOR COLOUR', 'visor'), ('ARMOUR EFFECT', 'armor_effect'),
        ('PRIMARY COLOUR', None), ('SECONDARY COLOUR', None), ('EMBLEM', None), ('NAMEPLATE', None), ('POSE', None), ('FIREFIGHT VOICE', None)]
VOICES = ['NOBLE SIX', 'GYSGT STACKER', 'JORGE-052', 'JUN-A266', 'EMILE-A239', 'KAT-B320', 'AUNTIE DOT', 'CARTER-A259', 'GYSGT BUCK',
          'SGTMAJ JOHNSON', 'CORTANA', 'JOHN-117']
SIX = dict(helmet='MARK V [B] UA/HUL', left_shoulder='UA/MULTI-THREAT', right_shoulder='FJ/PARA', chest='UA/MULTI-THREAT', wrist='TACTICAL/TACPAD',
           utility='TACTICAL/HARD CASE', knees='FJ/PARA', visor='GOLD', armor_effect='DEFAULT', prim='OLIVE', sec='TAN',
           emblem=(29, 0, 2, 22, 21, True), plate=292, pose='RIFLE READY', voice='NOBLE SIX', name='NOBLE SIX', tag='B312')
JOHN = dict(helmet='MJOLNIR Mk. V', left_shoulder='MJOLNIR Mk. V', right_shoulder='MJOLNIR Mk. V', chest='DEFAULT', wrist='DEFAULT',
            utility='DEFAULT', knees='DEFAULT', visor='GOLD', armor_effect='DEFAULT', prim='OLIVE', sec='TAN',
            emblem=(8, 0, 2, 22, 9, True), plate=284, pose='RIFLE BUFF', voice='JOHN-117', name='JOHN-117', tag='S117')
PLATES = [(281, 'BOAT COPS'), (283, 'DMR'), (284, 'ONI'), (285, 'JUN'), (286, 'GRENADE LAUNCHER'), (287, 'JORGE'), (288, 'FALCON'),
          (289, 'KAT'), (290, 'CARTER'), (291, 'EMILE'), (292, 'HALO: REACH'), (293, 'AUNTIE DOT'), (294, 'FIREFIGHT'), (295, 'HEADHUNTER'),
          (297, 'INVASION'), (298, 'FROM THE BEGINNING'), (299, 'SKIRMISHER'), (300, 'ASSASSIN'), (301, 'MOA'), (302, 'PLANET')]
POSES = ['AT EASE', 'CROSSED ARMS', 'DOUBLE FLEX', 'HANDS ON HIPS', 'KNEEL', 'LOOK BACK', 'MEDITATION', 'PEACE SIGN', 'POINT',
         'RIFLE AT SIDE', 'RIFLE BACK', 'RIFLE BUFF', 'RIFLE BUFF BACK', 'RIFLE HERO', 'RIFLE KNEEL', 'RIFLE ON SHOULDER', 'RIFLE READY',
         'RIFLE SALUTE', 'RIFLE SIT', 'SALUTE', 'YOGA']
POSE_FILES = ['AtEase', 'CrossedArms', 'DoubleFlex', 'HandsOnHips', 'Kneel', 'LookBack', 'Meditation', 'PeaceSign', 'Pose_Finger',
              'Rifle_At_side', 'Rifle_Back', 'Rifle_Buff', 'Rifle_Buff_Back', 'Rifle_Hero_Pose', 'Rifle_Kneel', 'Rifle_On_Shoulder',
              'Rifle_Ready', 'Rifle_Salute', 'Rifle_Sit', 'Salute', 'YogaPose']
def col255(i): return tuple(int(c * 255) for c in EM.COLS[i])
def ci(name): return EM.NAMES.index(name)

def fig(name):
    im = S.key(Image.open(R + name + '.png').convert('RGBA'), (40, 44, 52), tol=4, soft=8); im = im.crop(im.getbbox())
    a = im.getchannel('A'); im = ImageEnhance.Brightness(im.convert('RGB')).enhance(1.3).convert('RGBA'); im.putalpha(a); return im

def plate(sel, w=480, big=False):
    """The player's nameplate: MCC's plate art with the emblem in its square, name and service tag on it."""
    p = Image.open(NP + 'PlayerIDNamePlate_%03d.png' % sel['plate']).convert('RGBA')
    k = w / p.width; p = p.resize((w, int(p.height * k)), Image.LANCZOS); h = p.height
    sq = int(h * 0.80); em = EM.compose(*sel['emblem'][:5], secondary=sel['emblem'][5], size=sq)
    p.alpha_composite(em, (int(h * 0.10) + 2, int(h * 0.10)))
    fs = int(h * 0.42); S.text(p, (int(h * 1.18), int(h * 0.12)), sel['name'], F(fs), WHITE, spacing=1)
    S.text(p, (int(h * 1.20), int(h * 0.58)), sel['tag'], F(int(fs * 0.62)), (225, 230, 236), spacing=2)
    return p

def frame(sel, cat, who='NOBLE SIX', figname='six_ready', crumb=None):
    im = S.background()
    S.header(im, crumb or ('MAIN MENU  /  ARMORY  /  ' + who), 'ARMORY')
    # character tabs (Q / E), Reach's lobby tab style
    tx = 560; d = ImageDraw.Draw(im)
    for i, t in enumerate(['NOBLE SIX', 'JOHN-117']):
        w = d.textlength(t, font=F(24)) + 44
        if t == who: S.highlight(im, int(tx), 122, int(w), 40)
        S.text(im, (tx + 22, 128), t, F(24), WHITE if t == who else (150, 160, 172), spacing=2); tx += w + 8
    S.text(im, (tx + 6, 132), 'Q / E', F(18), DIM, spacing=2)
    # categories with what's equipped
    y = 214
    vals = {'helmet': sel['helmet'], 'left_shoulder': sel['left_shoulder'], 'right_shoulder': sel['right_shoulder'], 'chest': sel['chest'],
            'wrist': sel['wrist'], 'utility': sel['utility'], 'knees': sel['knees'], 'visor': sel['visor'], 'armor_effect': sel['armor_effect']}
    extra = {'PRIMARY COLOUR': sel['prim'], 'SECONDARY COLOUR': sel['sec'], 'EMBLEM': EM.E['foreground'][sel['emblem'][0]]['name'],
             'NAMEPLATE': dict(PLATES)[sel['plate']], 'POSE': sel['pose'], 'FIREFIGHT VOICE': sel['voice']}
    for i, (c, key) in enumerate(CATS):
        on = c == cat
        if on: S.highlight(im, 112, y - 4, 520, 40)
        S.text(im, (132, y), c, F(23), WHITE if on else (180, 196, 212), spacing=2)
        v = vals.get(key) if key else extra[c]
        S.text(im, (622, y + 3), v, F(19), (225, 232, 240) if on else (128, 142, 158), anchor='ra', spacing=1)
        y += 42
    # nameplate (bottom left)
    S.put(im, plate(sel, 480), 118, 878)
    if figname:
        f = fig(figname); S.shadow(im, 960, 985, 380, 34); S.put(im, f, 960, 995, h=790 if f.height > f.width * 1.6 else 640, anchor='mb')
    return im

def listing(im, title, items, sel_i, top=0, rows=12, x0=1330, desc=None, count=True):
    S.text(im, (x0, 214), title, F(22), BLUE, spacing=3)
    if count: S.text(im, (x0 + 460, 216), '%d / %d' % (sel_i + 1, len(items)), F(18), DIM, anchor='ra', spacing=2)
    y = 252
    for i in range(top, min(len(items), top + rows)):
        if i == sel_i: S.highlight(im, x0 - 8, y - 4, 470, 40)
        S.text(im, (x0 + 12, y), items[i], F(23), WHITE if i == sel_i else LIST_DIM, spacing=1); y += 44
    if len(items) > rows:   # scroll bar
        d = ImageDraw.Draw(im); h = rows * 44; d.rectangle((x0 + 468, 252, x0 + 471, 252 + h), fill=(90, 105, 120, 160))
        t = 252 + h * top / len(items); d.rectangle((x0 + 466, t, x0 + 473, t + h * rows / len(items)), fill=(220, 235, 250, 230))
    y = 252 + rows * 44
    S.fadeline(im, x0, y + 6, 460, 150)
    if desc: S.para(im, x0, y + 20, desc, F(21), DIM, 460, 29)
    return y

def swatches(im, x0, y, label, chosen):
    S.text(im, (x0, y), label, F(18), DIM, spacing=3); S.text(im, (x0 + 460, y), chosen, F(18), WHITE, anchor='ra', spacing=2)
    d = ImageDraw.Draw(im)
    for i in range(30):
        cx = x0 + (i % 15) * 31; cy = y + 27 + (i // 15) * 31
        d.rectangle((cx, cy, cx + 26, cy + 26), fill=col255(i) + (255,))
        if EM.NAMES[i] == chosen: d.rectangle((cx - 3, cy - 3, cx + 29, cy + 29), outline=(255, 255, 255, 255), width=3)

FOOT = [('ENTER', 'Equip'), ('ESC', 'Back'), ('W / S', 'Browse'), ('Q / E', 'Spartan'), ('DRAG', 'Turn')]

def page_helmet(sel=SIX, who='NOBLE SIX', fign='six_ready', out='armory-v2-helmet.jpg'):
    im = frame(sel, 'HELMET', who, fign)
    names = [h['name'] for h in N['helmet']]; i = names.index(sel['helmet'])
    item = N['helmet'][i]
    y = listing(im, 'HELMET', names, i, top=max(0, i - 3), desc=item['desc'])
    swatches(im, 1330, 905, 'PRIMARY', sel['prim'])
    S.footer(im, FOOT); im.convert('RGB').save(OUT + out, quality=84, optimize=True, progressive=True); print(out)

def page_colours(sel=SIX):
    im = frame(sel, 'PRIMARY COLOUR')
    x0 = 1330; S.text(im, (x0, 214), 'PRIMARY COLOUR', F(22), BLUE, spacing=3)
    d = ImageDraw.Draw(im)
    for i in range(30):   # Reach's 30 colours, named, in Reach's order
        c, r = i % 3, i // 3; cx = x0 + c * 156; cy = 252 + r * 52
        on = EM.NAMES[i] == sel['prim']
        if on: S.highlight(im, cx - 6, cy - 5, 152, 46)
        d.rectangle((cx, cy, cx + 34, cy + 34), fill=col255(i) + (255,), outline=(255, 255, 255, 255) if on else (0, 0, 0, 120), width=2)
        S.text(im, (cx + 44, cy + 5), EM.NAMES[i], F(19), WHITE if on else LIST_DIM, spacing=1)
    S.fadeline(im, x0, 780, 460, 150)
    S.para(im, x0, 796, 'Your armour\'s main colour. Secondary paints the trim; both show on the Spartan as you browse.', F(21), DIM, 460, 29)
    swatches(im, 1330, 905, 'SECONDARY', sel['sec'])
    S.footer(im, [('ENTER', 'Equip'), ('ESC', 'Back'), ('W A S D', 'Browse'), ('Q / E', 'Spartan'), ('DRAG', 'Turn')])
    im.convert('RGB').save(OUT + 'armory-v2-colours.jpg', quality=84, optimize=True, progressive=True); print('colours')

def page_emblem(sel=SIX):
    im = frame(sel, 'EMBLEM', figname=None)
    fg, bg, p, s, b, lay = sel['emblem']
    # big emblem where the Spartan stands, Reach's emblem editor preview
    S.put(im, EM.compose(fg, bg, p, s, b, lay, size=420), 960, 300, anchor='mt')
    S.text(im, (960, 740), EM.E['foreground'][fg]['name'] + '  ON  ' + EM.E['background'][bg]['name'], F(26), WHITE, anchor='ma', spacing=2)
    x0 = 1330; S.text(im, (x0, 214), 'EMBLEM', F(22), BLUE, spacing=3)
    rows = [('FOREGROUND', EM.E['foreground'][fg]['name']), ('BACKGROUND', EM.E['background'][bg]['name']), ('PRIMARY', EM.NAMES[p]),
            ('SECONDARY', EM.NAMES[s]), ('BACKGROUND COLOUR', EM.NAMES[b]), ('SECONDARY LAYER', 'ON' if lay else 'OFF')]
    y = 252; d = ImageDraw.Draw(im)
    for i, (k, v) in enumerate(rows):
        if i == 0: S.highlight(im, x0 - 8, y - 4, 470, 40)
        S.text(im, (x0 + 12, y), k, F(22), WHITE if i == 0 else LIST_DIM, spacing=1)
        if k in ('PRIMARY', 'SECONDARY', 'BACKGROUND COLOUR'):
            d.rectangle((x0 + 438, y + 4, x0 + 460, y + 26), fill=col255(EM.NAMES.index(v)) + (255,))
            S.text(im, (x0 + 428, y + 2), v, F(20), (225, 232, 240), anchor='ra')
        else: S.text(im, (x0 + 460, y + 2), v, F(20), (225, 232, 240), anchor='ra')
        y += 42
    S.fadeline(im, x0, y + 4, 460, 150); y += 20
    S.text(im, (x0, y), 'FOREGROUND', F(18), DIM, spacing=3); S.text(im, (x0 + 460, y), '%d / %d' % (fg + 1, len(EM.E['foreground'])), F(18), DIM, anchor='ra'); y += 30
    start = (fg // 6) * 6 - 6
    for k in range(30):   # thumbnails in the player's colours, 6 across
        j = start + k; cx = x0 + (k % 6) * 78; cy = y + (k // 6) * 78
        d.rectangle((cx, cy, cx + 70, cy + 70), fill=(20, 28, 38, 200))
        S.put(im, EM.compose(j, bg, p, s, b, lay, size=66), cx + 2, cy + 2)
        if j == fg: d.rectangle((cx - 3, cy - 3, cx + 73, cy + 73), outline=(255, 255, 255, 255), width=3)
    S.footer(im, [('ENTER', 'Pick'), ('ESC', 'Back'), ('W A S D', 'Browse'), ('Q / E', 'Spartan')])
    im.convert('RGB').save(OUT + 'armory-v2-emblem.jpg', quality=84, optimize=True, progressive=True); print('emblem')

def page_nameplate(sel=SIX):
    im = frame(sel, 'NAMEPLATE', figname=None)
    S.put(im, plate(sel, 620), 960, 430, anchor='mt')
    S.text(im, (960, 370), 'HOW YOUR SQUAD SEES YOU', F(20), DIM, anchor='ma', spacing=3)
    names = [n for _, n in PLATES]; i = [p for p, _ in PLATES].index(sel['plate'])
    listing(im, 'NAMEPLATE', names, i, top=max(0, i - 4), desc='Reach nameplates from MCC (about 600 in all, from every game). Your emblem sits in the square.')
    S.footer(im, [('ENTER', 'Equip'), ('ESC', 'Back'), ('W / S', 'Browse'), ('Q / E', 'Spartan')])
    im.convert('RGB').save(OUT + 'armory-v2-nameplate.jpg', quality=84, optimize=True, progressive=True); print('nameplate')

def page_pose(sel=SIX, pick='RIFLE ON SHOULDER'):
    s2 = dict(sel, pose=pick)
    im = frame(s2, 'POSE', figname='six_shoulder')
    x0 = 1300; S.text(im, (x0, 214), 'POSE', F(22), BLUE, spacing=3); S.text(im, (x0 + 500, 216), '%d / 21' % (POSES.index(pick) + 1), F(18), DIM, anchor='ra')
    d = ImageDraw.Draw(im)
    for k, (nm, fn) in enumerate(zip(POSES, POSE_FILES)):   # 4 across: little renders of you in each pose
        cx = x0 + (k % 4) * 126; cy = 250 + (k // 4) * 128
        on = nm == pick
        d.rectangle((cx, cy, cx + 118, cy + 120), fill=(52, 84, 116, 150) if on else (20, 28, 38, 170))
        t = fig('pose_' + fn); t.thumbnail((110, 96)); S.put(im, t, cx + 59, cy + 98, anchor='mb')
        S.text(im, (cx + 59, cy + 100), nm, F(13), WHITE if on else LIST_DIM, anchor='ma')
        if on: d.rectangle((cx - 2, cy - 2, cx + 120, cy + 122), outline=(255, 255, 255, 255), width=3)
    S.footer(im, [('ENTER', 'Use on menu'), ('ESC', 'Back'), ('W A S D', 'Browse'), ('Q / E', 'Spartan')])
    im.convert('RGB').save(OUT + 'armory-v2-pose.jpg', quality=84, optimize=True, progressive=True); print('pose')

if __name__ == '__main__':
    which = sys.argv[1:] or ['helmet', 'john', 'colours', 'emblem', 'nameplate', 'pose']
    if 'helmet' in which: page_helmet()
    if 'john' in which: page_helmet(JOHN, 'JOHN-117', 'john_atease', 'armory-v2-john.jpg')
    if 'colours' in which: page_colours()
    if 'emblem' in which: page_emblem()
    if 'nameplate' in which: page_nameplate()
    if 'pose' in which: page_pose()
