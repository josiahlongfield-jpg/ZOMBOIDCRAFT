# Endless Night main menu mock-up in Halo: Reach's lobby layout (preview box, wings title, vertical MAIN MENU, list with
# values, description, MY PARTY roster, button prompts). Reach UI bitmaps + renders are LOCAL ONLY.
import sys, numpy as np
sys.path.insert(0, '/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/cs')
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance
import screens as S
W, H = 1920, 1080; F = S.F; WHITE = (236, 240, 244); DIM = (176, 184, 192)
ICONS = Image.open(S.UI + 'lobbies_gameypes_large_ui_ondemand_00.png').convert('RGBA')
def icon(c, r, size, col=(255, 255, 255), alpha=255):
    t = ICONS.crop((c * 92, r * 92, c * 92 + 92, r * 92 + 92)); a = np.array(t).astype(float)
    k = a[..., 3] / 255 * (a[..., :3].max(-1) / max(1, a[..., :3].max()))
    L = np.zeros(a.shape); L[..., :3] = col; L[..., 3] = k * alpha
    return Image.fromarray(L.astype('uint8'), 'RGBA').resize((size, size), Image.LANCZOS)
ITEMS = [('CONTINUE', 'NOBLE SIX  ·  DAY 4', 'Pick up where you left off: Noble Six, day 4 in the county.', (4, 1)),
         ('NEW SOLDIER', '', 'Choose your side and make a new soldier: Noble Team or a Tarkov operator.', (0, 1)),
         ('ARMORY', 'NOBLE SIX', 'Change your Spartan\'s armour, colours and emblem.', (7, 2)),
         ('FIREFIGHT', 'SWORD BASE', 'Hold out against waves of the Covenant-led horde on a Reach map.', (6, 4)),
         ('MULTIPLAYER', 'FRIENDS SERVER', 'Join your friends\' server.', (1, 2)),
         ('INTEL', '', 'Field notes on the Covenant, your weapons and Noble Team.', (5, 3)),
         ('SETTINGS', '', 'Video, sound, controls and HUD.', (7, 0)),
         ('QUIT', '', 'Leave Endless Night.', (4, 0))]
def toggle(im, cx, y, anchor='m', on=0):
    d = ImageDraw.Draw(im); f = F(24); labels = ['SPARTAN', 'OPERATOR']; ws = [d.textlength(l, font=f) + 44 for l in labels]; tw = sum(ws) + 6
    x0 = cx - tw / 2 if anchor == 'm' else cx - tw
    d.rectangle((x0, y, x0 + tw, y + 44), fill=(20, 22, 26, 200), outline=(200, 205, 210, 160), width=2)
    x = x0 + 3
    for i, (l, w) in enumerate(zip(labels, ws)):
        if i == on: S.highlight(im, int(x), y + 3, int(w), 38)
        S.text(im, (x + w / 2, y + 9), l, f, WHITE if i == on else (150, 156, 164), anchor='ma', spacing=2); x += w
    kw = d.textlength('Q', font=F(18)) + 20; kx = x0 - kw - 12 if anchor == 'm' else x0 - kw - 12
    d.rounded_rectangle((kx, y + 7, kx + kw, y + 37), 15, fill=(200, 200, 200, 230)); d.text((kx + 10, y + 10), 'Q', font=F(18), fill=(30, 30, 30))

def build(sel, art, out, fig, tog=None, dx=0, figx=1300, px=1190, squad=None, figh=750, hover=-1, showfig=True, on=0):
    bg = art.copy().resize((W, H), Image.LANCZOS).convert('RGB')
    a = np.array(bg).astype(float) * 0.62
    xx = np.linspace(0, 1, W)[None, :, None]; a *= 0.45 + 0.55 * np.clip(xx * 1.6, 0, 1)   # darker toward the menu side
    yy = np.linspace(0, 1, H)[:, None, None]; a *= 1 - 0.35 * yy ** 3
    im = Image.fromarray(a.clip(0, 255).astype('uint8')).convert('RGBA')
    # rain
    rng = np.random.default_rng(3); r = Image.new('RGBA', (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(r)
    for _ in range(900):
        x, y, l = rng.uniform(0, W), rng.uniform(0, H), rng.uniform(18, 46)
        d.line((x, y, x - l * 0.18, y + l), fill=(200, 215, 230, int(rng.uniform(25, 70))), width=1)
    im.alpha_composite(r)
    # the player's Spartan stands centre-right, as the lobby art has its soldiers
    if showfig: S.shadow(im, figx, 985, 360, 34); S.put(im, fig, figx, 1000, h=figh, anchor='mb')
    im.alpha_composite(r.filter(ImageFilter.GaussianBlur(0.6)))
    # preview box with the item's art and icon
    bx, by, bw, bh = 250 + dx, 68, 700, 262
    pv = art.copy().convert('RGBA'); k = max(bw / pv.width, bh / pv.height)
    pv = pv.resize((int(pv.width * k) + 1, int(pv.height * k) + 1), Image.LANCZOS); pv = pv.crop(((pv.width - bw) // 2, (pv.height - bh) // 3, (pv.width - bw) // 2 + bw, (pv.height - bh) // 3 + bh))
    pv = ImageEnhance.Brightness(pv).enhance(0.85); im.alpha_composite(pv, (bx, by))
    ImageDraw.Draw(im).rectangle((bx - 2, by - 2, bx + bw + 1, by + bh + 1), outline=(10, 10, 12, 255), width=3)
    ImageDraw.Draw(im).rectangle((bx - 4, by - 4, bx + bw + 3, by + bh + 3), outline=(225, 225, 225, 220), width=2)
    c, rr = ITEMS[sel][3]; im.alpha_composite(icon(c, rr, 140), (bx + 14, by + 22))
    # wings + title + subline
    wsz = 112 if dx >= 0 else 100; im.alpha_composite(icon(4, 3, wsz, alpha=200), (max(4, 140 + dx), 336 + (112 - wsz) // 2))
    S.text(im, (262 + dx, 342), 'ENDLESS NIGHT', F(84, False), (225, 228, 232), spacing=1)
    S.text(im, (264 + dx, 455), 'Noble Six  ·  Spartan  ·  Personal game on this PC.', F(26), DIM)
    # vertical MAIN MENU
    t = Image.new('RGBA', (300, 60), (0, 0, 0, 0)); S.text(t, (0, 4), 'MAIN MENU', F(38), (120, 122, 126), spacing=4, alpha=150)
    im.alpha_composite(t.rotate(-90, expand=True), (172 + dx, 470))
    # list
    S.text(im, (264 + dx, 548), 'Ready', F(28), WHITE)
    S.fadeline(im, 250 + dx, 588, 720, 90, h=2)
    y = 604
    for i, (name, val, desc, ic) in enumerate(ITEMS):
        if i == sel:
            S.highlight(im, 252 + dx, y - 3, 700, 36)
        S.text(im, (266 + dx, y), name, F(25), WHITE if i == sel else (205, 210, 216), spacing=1)
        if val: S.text(im, (940 + dx, y), val, F(24), (212, 216, 220), anchor='ra', spacing=1)
        y += 38
    S.fadeline(im, 250 + dx, y + 6, 720, 90, h=2)
    S.text(im, (264 + dx, y + 20), ITEMS[sel][2], F(25), DIM)
    # MY PARTY roster (top right)
    d = ImageDraw.Draw(im)
    squad = squad or [('Noble Six', 'B312', True)]
    d.rectangle((px, 96, px + 400, 134), fill=(40, 40, 42, 235)); S.text(im, (px + 8, 101), 'MY SQUAD', F(26), WHITE)
    d.rectangle((px + 30, 138, px + 480, 180), fill=(70, 70, 72, 200)); S.text(im, (px + 60, 144), 'In Main Menu', F(26), WHITE); S.text(im, (px + 468, 146), '%d/16' % len(squad), F(24), DIM, anchor='ra')
    yy = 184
    for si, (nm, tag, me) in enumerate(squad):
        d.rectangle((px + 52, yy, px + 480, yy + 44), fill=(95, 100, 60, 240) if me else (52, 56, 60, 225))
        if si == hover: d.rectangle((px + 50, yy - 2, px + 482, yy + 46), outline=(240, 240, 240, 255), width=3)
        S.text(im, (px + 80, yy + 7), nm, F(28), WHITE); S.text(im, (px + 360, yy + 10), tag, F(24), (230, 232, 220))
        im.alpha_composite(icon(4, 3, 40), (px + 432, yy + 3))
        if me: d.arc((px + 4, yy + 2, px + 44, yy + 42), 40, 320, fill=(230, 230, 230, 230), width=4)
        yy += 48
    # button prompts
    x = 262 + dx; yb = 1012
    for col, key, label in [((90, 170, 70), 'ENTER', 'Select'), ((190, 60, 60), 'ESC', 'Back'), ((210, 180, 50), 'TAB', 'Change Squad'), ((200, 200, 200), 'F1', 'Menu')]:
        tw = ImageDraw.Draw(im).textlength(key, font=F(18)) + 22
        d.rounded_rectangle((x, yb, x + tw, yb + 32), 16, fill=col + (230,)); d.text((x + 11, yb + 5), key, font=F(18), fill=(255, 255, 255))
        x += tw + 8; x += S.text(im, (x, yb + 2), label, F(26), WHITE) + 34
    if tog == 'below': toggle(im, 1300, 1010)
    if tog == 'corner': toggle(im, px + 480, 1010, anchor='r', on=on)
    im.convert('RGB').save(out, quality=82, optimize=True, progressive=True); print(out)

def plate(im, cx, y, name, sub):
    S.text(im, (cx, y), name, F(30), WHITE, anchor='ma', spacing=1); S.text(im, (cx, y + 38), sub, F(22), DIM, anchor='ma', spacing=1)
if __name__ == '__main__':
    art = Image.open('/mnt/project-files/zomboid-mod/previews/city-street-latest.png').convert('RGB')
    fig = Image.open('/tmp/claude-0/arm/look_a.png').convert('RGBA'); fig = S.key(fig, (40, 44, 52), tol=4, soft=8); fig = fig.crop(fig.getbbox())
    a = fig.getchannel('A'); fig = ImageEnhance.Brightness(fig.convert('RGB')).enhance(1.25).convert('RGBA'); fig.putalpha(a)
    sq = [('Noble Six', 'B312', True), ('Hollow', 'H117', False), ('Rook', 'R204', False), ('Vesper', 'V091', False),
          ('Kestrel', 'K448', False), ('Mara', 'M330', False), ('Doyle', 'D007', False), ('Fenn', 'F521', False)]
    op = S.key(Image.open('/tmp/claude-0/op/op_idle.png').convert('RGBA'), (40, 44, 52), tol=4, soft=8); op = op.crop(op.getbbox())
    a = op.getchannel('A'); op = ImageEnhance.Brightness(op.convert('RGB')).enhance(1.3).convert('RGBA'); op.putalpha(a); op.save('/tmp/claude-0/op/menu_op.png')
    build(0, art, '/mnt/project-files/zomboid-mod/halo/previews/armory/main-menu-v3b-operator.jpg', op, 'corner', dx=-150, figx=1080, px=1340, squad=sq, figh=760, on=1)
