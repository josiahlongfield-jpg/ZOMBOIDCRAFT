# Character-creation mock-ups in Halo: Reach's menu style, built from Reach's own UI bitmaps (pulled from Josiah's files),
# his Noble Team renders and Tarkov face renders. LOCAL ONLY (never in a published artifact).
import numpy as np, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageOps
SC = '/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/'
UI = SC + 'cs/ui/'; GL = SC + 'reach/hudpack2/glyphs/'; FD = SC + 'fonts/'
OUT = SC + 'cs/out/'
W, H = 1920, 1080
def F(sz, med=True): return ImageFont.truetype(FD + ('SairaSemiCondensed-Medium.ttfx.ttf' if med else 'Sairax.ttf'), sz)
WHITE = (236, 242, 248); DIM = (150, 168, 186); BLUE = (120, 180, 230); FAINT = (95, 112, 130)
def ui(n): return Image.open(UI + n).convert('RGBA')

# ---------------------------------------------------------------- art
noble = Image.open('/mnt/project-files/zomboid-mod/halo/previews/reach-noble-team-v2.png').convert('RGBA')
def key(im, bg, tol=10, soft=14):
    a = np.array(im).astype(float); d = np.abs(a[..., :3] - np.array(bg)).max(-1)
    al = np.clip((d - tol) / soft, 0, 1); a[..., 3] = a[..., 3] * al
    return Image.fromarray(a.astype('uint8'), 'RGBA')
NAMES = ['carter', 'kat', 'jun', 'emile', 'jorge', 'six', 'john']
from PIL import ImageEnhance
def figure(n, view=''):  # Noble posed in Reach's own combat idle (rifle/pistol held), rendered from his map files
    im = key(Image.open(SC + 'cs/posed3/' + view + n + '.png').convert('RGBA'), (40, 44, 52), tol=4, soft=8); im = im.crop(im.getbbox())
    a = im.getchannel('A'); im = ImageEnhance.Brightness(im.convert('RGB')).enhance(1.5 if n == 'six' else 1.2).convert('RGBA'); im.putalpha(a); return im
FIG = {n: figure(n) for n in NAMES}
FRONT = {n: figure(n, 'front_') for n in NAMES}
faces = Image.open('/mnt/project-files/zomboid-mod/tarkov/previews/tarkov-player-faces.jpg').convert('RGBA')
FK = faces.width / 1077
def face(idx, helmet=True):
    c, r = idx % 4, idx // 4; cx = [0, 272, 543, 814][c] + (128 if helmet else 0)
    return faces.crop((int(cx * FK), int((r * 150 + 16) * FK), int((cx + 134) * FK), int((r * 150 + 148) * FK)))
def glyph(kind, name, col=WHITE, h=None):
    im = Image.open(GL + kind + '/' + name + '.png').convert('RGBA'); a = np.array(im).astype(float)
    lum = a[..., :3].max(-1) / 255; a[..., :3] = np.array(col); a[..., 3] = a[..., 3] * np.clip(lum * 1.2, 0, 1)
    im = Image.fromarray(a.astype('uint8'), 'RGBA'); im = im.crop(im.getbbox())
    if h: im = im.resize((int(im.width * h / im.height), h), Image.LANCZOS)
    return im

# ---------------------------------------------------------------- shell
street = Image.open('/mnt/project-files/zomboid-mod/previews/city-street-latest.png').convert('RGB').crop((0, 135, 1440, 945)).resize((W, H))
def background():
    b = street.filter(ImageFilter.GaussianBlur(14)); a = np.array(b).astype(float)
    a = a * 0.32 + np.array([10, 16, 26]) * 0.68     # Reach's menus sit over the game, dimmed toward steel blue
    yy, xx = np.mgrid[0:H, 0:W]; v = 1 - 0.55 * (((xx - W / 2) / W) ** 2 + ((yy - H / 2) / H) ** 2) * 2.2
    a *= np.clip(v, 0.35, 1)[..., None]
    im = Image.fromarray(a.clip(0, 255).astype('uint8')).convert('RGBA')
    lat = ui('common_img_auntiedot_background_ondemand_00.png').resize((1100, 1100), Image.LANCZOS)   # Reach's diamond lattice
    la = np.array(lat).astype(float); la[..., 3] = la[..., :3].max(-1) * 0.10; lat = Image.fromarray(la.astype('uint8'), 'RGBA')
    im.alpha_composite(lat, (W - 900, -60))
    return im
def text(im, xy, s, f, fill=WHITE, anchor='la', spacing=0, alpha=255):
    d = ImageDraw.Draw(im)
    if spacing == 0: d.text(xy, s, font=f, fill=fill + (alpha,), anchor=anchor); return d.textlength(s, font=f)
    x, y = xy; w = sum(d.textlength(c, font=f) + spacing for c in s) - spacing
    if anchor[0] == 'r': x -= w
    elif anchor[0] == 'm': x -= w / 2
    for c in s: d.text((x, y), c, font=f, fill=fill + (alpha,), anchor='l' + anchor[1]); x += d.textlength(c, font=f) + spacing
    return w
def fadeline(im, x, y, w, alpha=200, h=2, col=WHITE):
    m = ui('common_menu_title_alpha_mask_00.png').resize((w, h)); a = np.array(m).astype(float)
    k = a[..., :3].max(-1) / 255 if a[..., 3].min() == 255 else a[..., 3] / 255
    L = np.zeros((h, w, 4)); L[..., :3] = col; L[..., 3] = k * alpha
    im.alpha_composite(Image.fromarray(L.astype('uint8'), 'RGBA'), (x, y))
def header(im, crumb, title):
    text(im, (120, 66), crumb, F(22), DIM, spacing=3)
    text(im, (116, 92), title, F(64, False), WHITE, spacing=2)
    fadeline(im, 120, 176, 760, 210)
def footer(im, items):
    x = 120; y = H - 66; d = ImageDraw.Draw(im)
    for k, label in items:
        kw = d.textlength(k, font=F(20)) + 20
        d.rounded_rectangle((x, y - 2, x + kw, y + 30), 5, outline=WHITE + (200,), width=2); d.text((x + 10, y + 1), k, font=F(20), fill=WHITE)
        x += kw + 12; x += text(im, (x, y + 1), label, F(22), DIM) + 40
def card(im, x, y, w, h, fill=(26, 40, 56, 120), line=(170, 200, 225, 110), lit=False):
    """Reach's loadout card: the 10px rounded corner pieces from loadout\\bitmaps, nine-sliced (top caps kept, middle stretched)."""
    if lit: fill, line = (52, 84, 116, 150), (215, 235, 250, 255)
    def slice9(n, col):
        p = ui(n); cap = 40
        top = p.crop((0, 0, p.width, cap)); mid = p.crop((0, cap, p.width, cap + 1)).resize((p.width, max(1, h - 2 * cap)))
        bot = ImageOps.flip(top); col_im = Image.new('RGBA', (p.width, h)); col_im.paste(top, (0, 0)); col_im.paste(mid, (0, cap)); col_im.paste(bot, (0, h - cap))
        a = np.array(col_im).astype(float); a[..., :3] = col[:3]; a[..., 3] = a[..., 3] * col[3] / 255
        return Image.fromarray(a.astype('uint8'), 'RGBA')
    for n, col in (('in_game_loadout_bitmaps_bitmap_corner_10pix_00.png', fill), ('in_game_loadout_bitmaps_bitmap_corner_10pix_line_00.png', line)):
        c = slice9(n, col); m = slice9(n.replace('corner', 'center'), col)
        im.alpha_composite(c, (x, y)); im.alpha_composite(m.resize((w - 2 * c.width, h)), (x + c.width, y)); im.alpha_composite(ImageOps.mirror(c), (x + w - c.width, y))
    if lit:   # Reach's selection glow
        g = Image.new('RGBA', im.size, (0, 0, 0, 0)); ImageDraw.Draw(g).rounded_rectangle((x - 2, y - 2, x + w + 2, y + h + 2), 14, outline=(150, 210, 255, 140), width=6)
        im.alpha_composite(g.filter(ImageFilter.GaussianBlur(7)))
def portrait(im, src, x, y, w, h, alpha=1.0):
    """A face render in a framed window (the renders sit on their own grey ground)."""
    k = max(w / src.width, h / src.height); t = src.resize((int(src.width * k) + 1, int(src.height * k) + 1), Image.LANCZOS)
    t = t.crop(((t.width - w) // 2, (t.height - h) // 2, (t.width - w) // 2 + w, (t.height - h) // 2 + h))
    if alpha < 1: a = np.array(t).astype(float); a[..., 3] *= alpha; t = Image.fromarray(a.astype('uint8'), 'RGBA')
    im.alpha_composite(t, (x, y)); ImageDraw.Draw(im).rectangle((x, y, x + w - 1, y + h - 1), outline=(170, 200, 225, 140), width=2)
def highlight(im, x, y, w, h):
    """The list highlight: a pale bar fading out to the right (Reach's roster gradient)."""
    g = ui('lobbies_roster_gradient_bitmap_00.png').resize((w, h)); a = np.array(g).astype(float)
    k = a[..., :3].max(-1) / 255; ramp = np.linspace(1, 0.05, w)[None, :]
    L = np.zeros((h, w, 4)); L[..., :3] = (175, 205, 230); L[..., 3] = 150 * ramp * (0.6 + 0.4 * k)
    im.alpha_composite(Image.fromarray(L.astype('uint8'), 'RGBA'), (x, y))
    ImageDraw.Draw(im).rectangle((x, y, x + 4, y + h), fill=(225, 240, 255, 255))
def put(im, src, x, y, h=None, w=None, alpha=1.0, anchor='lt'):
    if h: src = src.resize((max(1, int(src.width * h / src.height)), h), Image.LANCZOS)
    if w: src = src.resize((w, max(1, int(src.height * w / src.width))), Image.LANCZOS)
    if alpha < 1: a = np.array(src).astype(float); a[..., 3] *= alpha; src = Image.fromarray(a.astype('uint8'), 'RGBA')
    if anchor[0] == 'm': x -= src.width // 2
    if anchor[0] == 'r': x -= src.width
    if anchor[1] == 'b': y -= src.height
    L = Image.new('RGBA', im.size, (0, 0, 0, 0)); L.paste(src, (int(x), int(y))); im.alpha_composite(L); return src.size
def shadow(im, x, y, w, h=26):
    g = Image.new('RGBA', im.size, (0, 0, 0, 0)); ImageDraw.Draw(g).ellipse((x - w / 2, y - h / 2, x + w / 2, y + h / 2), fill=(0, 0, 0, 150))
    im.alpha_composite(g.filter(ImageFilter.GaussianBlur(10)))
def para(im, x, y, s, f, fill=DIM, width=600, lh=34):
    d = ImageDraw.Draw(im); line = ''
    for w_ in s.split():
        t = (line + ' ' + w_).strip()
        if d.textlength(t, font=f) > width: d.text((x, y), line, font=f, fill=fill); y += lh; line = w_
        else: line = t
    if line: d.text((x, y), line, font=f, fill=fill); y += lh
    return y
def save(im, n): im.convert('RGB').save(OUT + n, quality=84, optimize=True, progressive=True); print(n)

# ---------------------------------------------------------------- 1: side
def s1():
    im = background(); header(im, 'ENDLESS NIGHT  /  NEW SOLDIER', 'CHOOSE YOUR SIDE')
    card(im, 150, 230, 760, 680, lit=True); card(im, 1010, 230, 760, 680)
    shadow(im, 340, 868, 260); put(im, FRONT['six'], 340, 880, h=590, anchor='mb')
    text(im, (870, 262), 'SPARTAN', F(54, False), WHITE, 'ra', spacing=3)
    y = 360
    for s_ in ['Noble Team or your own Noble Six', 'MJOLNIR armour, energy shields', 'Armour abilities', 'Reach weapons, Reach HUD', 'Recruit the rest of Noble']:
        text(im, (870, y), s_, F(24), (210, 225, 240), 'ra'); y += 42
    portrait(im, face(13, True), 1050, 360, 330, 420)
    text(im, (1730, 262), 'OPERATOR', F(54, False), DIM, 'ra', spacing=3)
    y = 360
    for s_ in ['PMC for the UNSC (USEC or BEAR)', 'Tarkov weapons, mods and gear', 'Tarkov body: hits, bleeds, breaks', 'Near-empty Tarkov HUD', 'Cross-faction weapons, if allowed']:
        text(im, (1730, y), s_, F(24), (165, 180, 196), 'ra'); y += 42
    text(im, (W // 2, 935), 'Your side sets your HUD, your body and what you can carry.', F(24), DIM, 'ma')
    footer(im, [('ENTER', 'Select'), ('ESC', 'Back'), ('A / D', 'Switch')])
    save(im, 'cs1-choose-side.jpg')

# ---------------------------------------------------------------- 2: Noble
NOBLE = [('carter', 'CARTER-A259', 'NOBLE ONE', 'Commander. Leads the team.', 'dmr', 'jetpack'),
         ('kat', 'KAT-B320', 'NOBLE TWO', 'Lieutenant commander. Tech and intel.', 'magnum', 'sprint'),
         ('jun', 'JUN-A266', 'NOBLE THREE', 'Marksman and scout.', 'sniper_rifle', 'active_camo'),
         ('emile', 'EMILE-A239', 'NOBLE FOUR', 'Close-quarters assault.', 'shotgun', 'armor_lock'),
         ('jorge', 'JORGE-052', 'NOBLE FIVE', 'Heavy weapons. Carries the M247H machine gun.', 'machine_gun', 'drop_shield'),
         ('six', 'NOBLE SIX', 'YOU', 'Your own Spartan: your armour, colours and emblem.', 'dmr', 'evade'),
         ('john', 'JOHN-117', 'BONUS', 'Mark V. Unlocked as a bonus.', 'assault_rifle', 'sprint')]
GROW = 0.3   # the hovered card is this much wider, and a little taller, than the rest
def fitfig(fg, bw, bh):
    k = min(bw / fg.width, bh / fg.height); return fg.resize((max(1, int(fg.width * k)), max(1, int(fg.height * k))), Image.LANCZOS)
def s2frame(wts, sel):
    """wts: how 'hovered' each card is (sums to 1). Every figure is fitted INSIDE its own card border, never spilling out."""
    im = background(); header(im, 'NEW SOLDIER  /  SPARTAN', 'NOBLE TEAM')
    gap = 8; X0, X1 = 100, 1510; wb = (X1 - X0 - 6 * gap) / (7 + GROW)
    x = float(X0); boxes = []
    for i in range(7):
        w = wb * (1 + GROW * wts[i]); top = 250 - 22 * wts[i]; bot = 940 + 22 * wts[i]
        boxes.append((x, top, w, bot)); x += w + gap
    order = sorted(range(7), key=lambda i: wts[i])
    for i in order:
        n = NOBLE[i][0]; bx, top, w, bot = boxes[i]; a = wts[i]; lit = a > 0.5
        X, Y, Wd, Hd = int(round(bx)), int(round(top)), int(round(w)), int(round(bot - top))
        card(im, X, Y, Wd, Hd, fill=(30 + int(22 * a), 46 + int(38 * a), 64 + int(52 * a), 120 + int(30 * a)), lit=lit)
        inner = (X + 6, Y + 52, X + Wd - 6, Y + Hd - 16)
        fg = fitfig(FRONT[n], inner[2] - inner[0], min(inner[3] - inner[1], 470 + 120 * a))  # round-3 size (470 tall, 590 hovered), never past the border
        L = Image.new('RGBA', im.size, (0, 0, 0, 0)); cx = (inner[0] + inner[2]) // 2
        shadow(L, cx, inner[3] - 8, int(fg.width * 0.8)); put(L, fg, cx, inner[3], anchor='mb', alpha=0.62 + 0.38 * a)
        m = Image.new('L', im.size, 0); ImageDraw.Draw(m).rounded_rectangle((X + 3, Y + 3, X + Wd - 3, Y + Hd - 3), 12, fill=255)
        L.putalpha(Image.fromarray((np.array(L.getchannel('A')).astype(float) * np.array(m) / 255).astype('uint8'))); im.alpha_composite(L)
        text(im, (X + Wd // 2, Y + 30), NOBLE[i][2], F(18 + int(4 * a)), WHITE if lit else FAINT, 'ma', spacing=2)
    n, name, num, desc, wpn, ab = NOBLE[sel]
    x0 = 1545; text(im, (x0, 260), num, F(24), BLUE, spacing=3); text(im, (x0, 290), name, F(50, False), WHITE)
    fadeline(im, x0, 365, 330, 180)
    y = para(im, x0, 388, desc, F(25), (205, 220, 235), 330)
    text(im, (x0, y + 26), 'STARTS WITH', F(20), DIM, spacing=3)
    gw = glyph('weapons', wpn, h=60); gw = gw if gw.width <= 175 else gw.resize((175, int(gw.height * 175 / gw.width)), Image.LANCZOS)
    put(im, gw, x0, y + 64); text(im, (x0, y + 146), wpn.replace('_', ' ').upper(), F(21), WHITE)
    put(im, glyph('abilities', ab, h=58), x0 + 215, y + 66); text(im, (x0 + 215, y + 146), ab.replace('_', ' ').upper(), F(21), WHITE)
    para(im, x0, y + 210, 'The others join you as squad mates once you meet them in the world.', F(21), DIM, 330, 29)
    footer(im, [('ENTER', 'Play as ' + (NOBLE[sel][1].title() if sel != 5 else 'Noble Six')), ('ESC', 'Back'), ('A / D', 'Browse'), ('Q', 'Armory')])
    return im
def s2(sel=5):
    w = [0.0] * 7; w[sel] = 1.0
    save(s2frame(w, sel), 'cs2-noble-team.jpg' if sel == 5 else 'cs2b-noble-team-%s.jpg' % NOBLE[sel][0])

# ---------------------------------------------------------------- 3: Armory
CATS = ['HELMET', 'LEFT SHOULDER', 'RIGHT SHOULDER', 'CHEST', 'WRIST', 'UTILITY', 'KNEE GUARDS', 'VISOR COLOUR', 'ARMOUR EFFECT', 'PRIMARY COLOUR', 'SECONDARY COLOUR', 'EMBLEM']
HELMETS = [('MJOLNIR MKV(B)', 0), ('CQC', 0), ('MILITARY POLICE', 0), ('AIR ASSAULT', 0), ('GRENADIER', 0), ('SCOUT', 0), ('OPERATOR', 0), ('PILOT', 1), ('EOD', 1), ('JFO', 1), ('GUNGNIR', 1), ('HAUNTED', 1)]
COLS = [(110, 112, 110), (160, 162, 160), (220, 220, 214), (150, 40, 36), (120, 60, 90), (200, 120, 110), (200, 110, 40), (215, 110, 90), (225, 170, 130), (200, 150, 50), (210, 190, 60), (205, 200, 150),
        (130, 150, 110), (60, 110, 50), (95, 100, 60), (40, 120, 120), (80, 170, 170), (70, 160, 200), (40, 80, 160), (40, 60, 120), (30, 50, 100), (90, 60, 140), (150, 90, 160), (160, 140, 200), (120, 20, 30), (90, 30, 50), (220, 140, 170), (90, 60, 40), (150, 120, 85), (165, 150, 110)]
def s3():
    im = background(); header(im, 'NEW SOLDIER  /  NOBLE SIX', 'ARMORY')
    y = 230
    for i, c in enumerate(CATS):
        if i == 0: highlight(im, 112, y - 4, 420, 44)
        text(im, (132, y), c, F(26), WHITE if i == 0 else (180, 196, 212), spacing=2); y += 52
    shadow(im, 900, 975, 380, 34); put(im, FIG['six'], 900, 985, h=800, anchor='mb')
    lock = ui('in_game_loadout_bitmaps_equipment_scematics_00.png')
    x0 = 1330; text(im, (x0, 230), 'HELMET', F(22), BLUE, spacing=3)
    y = 268
    for i, (h, locked) in enumerate(HELMETS):
        if i == 1: highlight(im, x0 - 8, y - 4, 470, 40)
        text(im, (x0 + 12, y), h, F(24), (120, 132, 146) if locked else (WHITE if i == 1 else (200, 214, 228)), spacing=1)
        if locked: put(im, lock, x0 + 430, y + 2, h=30, alpha=0.8)
        y += 44
    fadeline(im, x0, y + 8, 460, 150)
    para(im, x0, y + 24, 'CQC: close-quarters helmet. Locked pieces open as you level up.', F(22), DIM, 460, 30)
    # colours row (Reach's 30)
    text(im, (x0, 905), 'PRIMARY', F(18), DIM, spacing=3)
    for i, c in enumerate(COLS):
        cx = x0 + (i % 15) * 31; cy = 932 + (i // 15) * 31
        ImageDraw.Draw(im).rectangle((cx, cy, cx + 26, cy + 26), fill=c + (255,), outline=(255, 255, 255, 255) if i == 0 else None, width=2)
    # rank
    med = ui('lobbies_exp_med_ui_ondemand_00.png'); r = med.crop((0, 0, med.width // 12, med.height // 6))
    put(im, r, 120, 880, h=54); text(im, (186, 884), 'NOBLE SIX', F(28, False), WHITE); text(im, (186, 918), 'RECRUIT', F(18), DIM, spacing=3)
    footer(im, [('ENTER', 'Equip'), ('ESC', 'Back'), ('W / S', 'Browse'), ('DRAG', 'Turn Spartan')])
    save(im, 'cs3-armory.jpg')

# ---------------------------------------------------------------- 4: loadout
LOADS = [('ASSAULT', 'assault_rifle', 'MA37 ASSAULT RIFLE', 'magnum', 'M6G MAGNUM', 2, 'sprint'),
         ('MARKSMAN', 'dmr', 'M392 DMR', 'magnum', 'M6G MAGNUM', 2, 'jetpack'),
         ('RECON', 'sniper_rifle', 'SRS99 SNIPER RIFLE', 'magnum', 'M6G MAGNUM', 1, 'active_camo'),
         ('BREACHER', 'shotgun', 'M45 SHOTGUN', 'assault_rifle', 'MA37 ASSAULT RIFLE', 2, 'armor_lock'),
         ('HEAVY', 'grenade_launcher', 'M319 GRENADE LAUNCHER', 'assault_rifle', 'MA37 ASSAULT RIFLE', 2, 'drop_shield')]
def s4(sel=1):
    im = background(); header(im, 'NEW SOLDIER  /  NOBLE SIX', 'CHOOSE A LOADOUT')
    y = 240
    for i, L in enumerate(LOADS):
        card(im, 120, y, 520, 96, lit=i == sel)
        put(im, glyph('weapons', L[1], col=WHITE if i == sel else DIM, h=46), 150, y + 25)
        text(im, (610, y + 30), L[0], F(30, False), WHITE if i == sel else (180, 196, 212), 'ra', spacing=2); y += 116
    n, p, pn, s, sn, g, ab = LOADS[sel]
    card(im, 720, 240, 1080, 676, fill=(22, 34, 48, 140))
    text(im, (770, 270), n, F(46, False), WHITE, spacing=3); fadeline(im, 770, 336, 700, 160)
    def slot(x, y, label, gimg, name):
        text(im, (x, y), label, F(20), BLUE, spacing=3); put(im, gimg, x, y + 40); text(im, (x, y + 40 + gimg.height + 16), name, F(24), WHITE, spacing=1)
    slot(770, 370, 'PRIMARY', glyph('weapons', p, h=130), pn)
    slot(1290, 370, 'SECONDARY', glyph('weapons', s, h=110), sn)
    text(im, (770, 640), 'GRENADES', F(20), BLUE, spacing=3)
    gr = Image.open(SC + 'reach/gamekit/reach/hud/grenades.png').convert('RGBA')
    put(im, gr, 770, 680, h=110); text(im, (880, 720), 'x%d  FRAG' % g, F(30, False), WHITE)
    text(im, (1290, 640), 'ARMOUR ABILITY', F(20), BLUE, spacing=3)
    put(im, glyph('abilities', ab, h=100), 1290, 680); text(im, (1410, 715), ab.replace('_', ' ').upper(), F(30, False), WHITE)
    para(im, 770, 830, 'Your loadout is what you spawn with. Ammo is finite: resupply comes from guarded pod drops.', F(22), DIM, 980, 30)
    footer(im, [('ENTER', 'Deploy'), ('ESC', 'Back'), ('W / S', 'Browse')])
    save(im, 'cs4-loadout.jpg')

# ---------------------------------------------------------------- 5: operator
def s5(sel=13):
    im = background(); header(im, 'NEW SOLDIER  /  OPERATOR', 'OPERATOR')
    for i, (lab, lit) in enumerate((('USEC', True), ('BEAR', False))):
        card(im, 120 + i * 270, 230, 250, 70, lit=lit); text(im, (245 + i * 270, 250), lab, F(30, False), WHITE if lit else DIM, 'ma', spacing=4)
    text(im, (120, 330), 'FACE', F(20), BLUE, spacing=3)
    usec = list(range(11, 23))
    for k, idx in enumerate(usec):
        x = 120 + (k % 4) * 132; y = 366 + (k // 4) * 132
        portrait(im, face(idx, False), x, y, 122, 122, 1.0 if idx == sel else 0.7)
        if idx == sel: ImageDraw.Draw(im).rectangle((x - 3, y - 3, x + 124, y + 124), outline=(225, 240, 255, 255), width=3)
    text(im, (120, 778), 'VOICE', F(20), BLUE, spacing=3)
    for k, v in enumerate(['USEC 1', 'USEC 2', 'USEC 3', 'USEC 4']):
        if k == 1: highlight(im, 112, 812 + k * 0 - 4, 380, 40)
    text(im, (132, 812), 'USEC 2', F(24), WHITE, spacing=1)
    put(im, ui('common_spin_arrow_lft_00.png'), 470, 812, h=28); put(im, ui('common_spin_arrow_rgt_00.png'), 510, 812, h=28)
    # big preview: bare and helmeted
    card(im, 760, 230, 1040, 690, fill=(18, 28, 40, 140))
    portrait(im, face(sel, False), 800, 270, 470, 590); portrait(im, face(sel, True), 1300, 270, 470, 590)
    text(im, (1035, 870), 'HEAD', F(20), DIM, 'ma', spacing=3); text(im, (1535, 870), 'IN KIT', F(20), DIM, 'ma', spacing=3)
    para(im, 120, 880, 'Your body is Tarkov\'s: hits, bleeds and breaks per part. Gear and weapons are picked on the next screen.', F(22), DIM, 600, 30)
    footer(im, [('ENTER', 'Next: gear'), ('ESC', 'Back'), ('A / D', 'USEC / BEAR')])
    save(im, 'cs5-operator.jpg')

for f in (s1, s2, s3, s4, s5): f()
s2(4)
