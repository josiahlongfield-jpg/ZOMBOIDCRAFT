# Draws the Tarkov operator HUD from its game kit the way the Java (client/hud/TarkovHud) does, for previews.
import json
from PIL import Image, ImageDraw
import kitview as kv

PARTS = ['head', 'chest', 'stomach', 'leftarm', 'rightarm', 'leftleg', 'rightleg']
MAX = [35, 85, 70, 60, 60, 65, 65]

class TView(kv.View):
    def fill(s, x0, y0, x1, y1, rgba):
        L = Image.new('RGBA', s.cv.size, (0, 0, 0, 0))
        ImageDraw.Draw(L).rectangle((round(s.X(x0)), round(s.Y(y0)), round(s.X(x1)) - 1, round(s.Y(y1)) - 1), fill=tuple(int(v) for v in rgba))
        s.cv.alpha_composite(L)
    def mtext(s, font, txt, x, y, ax, ay, alpha=1.0):
        F = s.kit.k['fonts'][font]; G = F['glyphs']; atlas = s.kit.img(F['file'])
        pen = 0.0; parts = []
        for ch in txt:
            g = G.get(ch)
            if g is None: continue
            parts.append((g, pen)); pen += g['adv']
        asc, desc = F['ascent'], F['descent']
        x0 = x - ax * pen; y0 = y + asc - ay * (asc + desc)
        for g, p in parts:
            s.blit(atlas, x0 + p + g['ox'], y0 + g['oy'], g['w'] / 2, g['h'] / 2, alpha, crop=(g['u'], g['v'], g['u'] + g['w'], g['v'] + g['h']))

def col(cols, v, m, a=1.0):
    k = 'dead' if v <= 0 else 'good' if v / m > 0.75 else 'hurt' if v / m > 0.4 else 'bad'
    c = cols[k]; return (c[0], c[1], c[2], 255 * a)

def frame(kit, sw, sh, stamina=1.0, stam_a=1.0, effects=(), gun=None, gun_a=1.0, tab_a=0.0, hp=None, broken=()):
    v = TView(kit, sw, sh); L = kit.k['layout_spec']
    st = L['stamina']
    if stam_a > 0:
        b = st['bars'][0]; cx = st['cx']; w = b['w']; y = b['y']; h = b['h']
        bk = st['back']; fl = st['fill']
        v.fill(cx - w / 2, y, cx + w / 2, y + h, (bk[0], bk[1], bk[2], bk[3] * stam_a))
        v.fill(cx - w / 2, y, cx - w / 2 + w * stamina, y + h, (fl[0], fl[1], fl[2], fl[3] * stam_a))
    S = L['status']; x = S['x']
    for e in effects:
        ic = kit.k['icons'][e]; v.blit(kit.img(ic['file']), x, S['y'], ic['w'], ic['h']); x += ic['w'] + S['gap']
    if gun and gun_a > 0:
        g = L['gunline']; name, mode, rounds, carried = gun
        v.mtext('gun_name', name, g['right'], g['y'], 1, 0, gun_a)
        v.mtext('gun_mode', mode, g['right'], g['y'] + g['mode_dy'], 1, 0, gun_a)
        v.mtext('gun_ammo', '%d / %d' % (rounds, carried), g['right'], g['y'] + g['ammo_dy'], 1, 0, gun_a)
    if tab_a > 0:
        t = L['tab']; x0, y0, k = t['x0'], t['y0'], t['k']; bw, bh = t['body_w'] * k, t['body_h'] * k; p = t['pad']; sh_ = t['shade']
        v.fill(x0 - p[0], y0 - p[1], x0 + bw + p[2], y0 + bh + p[3], (sh_[0], sh_[1], sh_[2], sh_[3] * tab_a))
        hp = hp or MAX; cols = t['colours']
        for i, n in enumerate(PARTS):
            e = kit.k['body'][n]; v.blit(kit.img(e['file']), x0 + e['x'] * k, y0 + e['y'] * k, e['w'] * k, e['h'] * k, tab_a, tint=col(cols, hp[i], MAX[i]))
        bd = kit.k['ui']['health_bar_border']; cr = kit.k['ui']['body_part_broken_cross']; bs = t['bar_scale']
        for i, n in enumerate(PARTS):
            e = kit.k['body'][n]; cx = x0 + (e['x'] + e['w'] / 2) * k; cy = max(y0 + (e['y'] + e['h'] / 2) * k, y0 + 12)
            v.mtext('tab_label', t['parts'][i][1], cx, cy - 9, 0.5, 0.5, tab_a)
            barW, barH = bd['w'] * bs, bd['h'] * bs
            v.blit(kit.img(bd['file']), cx - barW / 2, cy, barW, barH, tab_a)
            fw = (barW - 4) * max(0, hp[i]) / MAX[i]
            if fw > 0: c = col(cols, hp[i], MAX[i], tab_a * 0.9); v.fill(cx - barW / 2 + 2, cy + 2, cx - barW / 2 + 2 + fw, cy + barH - 2, c)
            v.mtext('tab_value', '%d/%d' % (hp[i], MAX[i]), cx, cy + barH + 6, 0.5, 0.5, tab_a)
            if n in broken: v.blit(kit.img(cr['file']), cx + barW / 2 + 3, cy, cr['w'] * 1.6, cr['h'] * 1.6, tab_a)
        v.mtext('tab_total', '%d/%d' % (sum(hp), sum(MAX)), x0 + bw / 2, y0 + bh + 18, 0.5, 0.5, tab_a)
    return v.cv
