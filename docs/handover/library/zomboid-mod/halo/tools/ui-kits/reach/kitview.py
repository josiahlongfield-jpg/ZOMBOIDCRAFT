# Draws a frame from the game kit exactly the way the Java HUD (client/hud/halo/ReachHud) does: same layout maths,
# same frame picks, same compass slices, same tracker grid, same text placement. Used to check the kit and for previews.
import json, math, sys
from PIL import Image
import numpy as np

_RS = {}

class Kit:
    def __init__(s, root):
        s.root = root; s.k = json.load(open(root + '/kit.json')); s.cache = {}
    def img(s, rel):
        if rel not in s.cache: s.cache[rel] = Image.open(s.root + '/' + rel).convert('RGBA')
        return s.cache[rel]

class View:
    def __init__(s, kit, sw, sh):
        s.kit, s.sw, s.sh = kit, sw, sh
        s.s = min(sw / 1280, sh / 720); s.ox = (sw - 1280 * s.s) / 2; s.oy = (sh - 720 * s.s) / 2
        s.cv = Image.new('RGBA', (sw, sh), (0, 0, 0, 0))
    def X(s, x): return s.ox + x * s.s
    def Y(s, y): return s.oy + y * s.s
    def blit(s, im, x, y, w, h, alpha=1.0, crop=None, tint=None):
        W_, H_ = max(1, int(round(w * s.s))), max(1, int(round(h * s.s)))
        key = (id(im), crop, W_, H_)
        t = _RS.get(key)
        if t is None:
            src = im.crop(crop) if crop else im
            t = src.resize((W_, H_), Image.LANCZOS); _RS[key] = t
        if tint is not None or alpha < 1:
            a = np.array(t).astype(float)
            if tint is not None: a[..., :3] *= np.array(tint[:3]) / 255
            a[..., 3] *= alpha; t = Image.fromarray(a.clip(0, 255).astype('uint8'), 'RGBA')
        px, py = int(round(s.X(x))), int(round(s.Y(y)))
        if px >= 0 and py >= 0 and px + W_ <= s.cv.width and py + H_ <= s.cv.height: s.cv.alpha_composite(t, (px, py))
        else:
            L = Image.new('RGBA', s.cv.size, (0, 0, 0, 0)); L.paste(t, (px, py)); s.cv.alpha_composite(L)
    def entry(s, e, alpha=1.0): s.blit(s.kit.img(e['file']), e['x'], e['y'], e['w'], e['h'], alpha)
    def frames(s, el, f):
        fr = el['frames']; k = max(0, min(len(fr) - 1, int(round(f * (len(fr) - 1))))); s.entry(fr[k])
    def text(s, spec, txt):
        if not spec or txt == '': return
        F = s.kit.k['fonts'][spec['font']]; G = F['glyphs']; atlas = s.kit.img(F['file'])
        pen = 0.0; l = t = 1e9; r = b = -1e9; parts = []
        for ch in txt:
            g = G.get(ch)
            if g is None: continue
            l = min(l, pen + g['l']); r = max(r, pen + g['r']); t = min(t, g['t']); b = max(b, g['b'])
            parts.append((g, pen)); pen += g['adv']
        if not parts: return
        tw, th = r - l, b - t
        x0 = spec['x'] - spec['align_x'] * tw - l; y0 = spec['y'] - spec['align_y'] * th - t   # pen origin (baseline)
        for g, p in parts:
            s.blit(atlas, x0 + p + g['ox'], y0 + g['oy'], g['w'] / 2, g['h'] / 2, crop=(g['u'], g['v'], g['u'] + g['w'], g['v'] + g['h']))
    def compass(s, heading, slices=48):
        c = s.kit.k['elements']['compass']; strip = s.kit.img(c['file'])
        centre = c['east_texel'] + (heading - 90) / 90 * c['texels_per_90']
        win = c['window_texels']; bow = c['bow']
        for i in range(slices):
            t0 = i / slices; t1 = (i + 1) / slices; tm = (t0 + t1) / 2
            u0 = centre - win / 2 + t0 * win; u1 = centre - win / 2 + t1 * win
            xn = tm * 2 - 1; a = max(0.0, min(1.0, (1 - abs(xn)) / c['fade']))
            bi = tm * (len(bow) - 1); j = min(len(bow) - 2, int(bi)); f = bi - j
            dx = bow[j][0] * (1 - f) + bow[j + 1][0] * f; dy = bow[j][1] * (1 - f) + bow[j + 1][1] * f
            x = c['cx'] - c['w'] / 2 + t0 * c['w'] + dx; y = c['cy'] - c['h'] / 2 + dy
            uu0 = u0 % c['texels']; uu1 = uu0 + (u1 - u0)
            if uu1 <= c['texels']:
                s.blit(strip, x, y, c['w'] / slices, c['h'], a, crop=(int(round(uu0)), 0, max(int(round(uu1)), int(round(uu0)) + 1), c['texh']))
            else:  # wraps round the end of the strip
                k1 = (c['texels'] - uu0) / (u1 - u0)
                s.blit(strip, x, y, c['w'] / slices * k1, c['h'], a, crop=(int(round(uu0)), 0, c['texels'], c['texh']))
                s.blit(strip, x + c['w'] / slices * k1, y, c['w'] / slices * (1 - k1), c['h'], a, crop=(0, 0, max(1, int(round(uu1 - c['texels']))), c['texh']))
    def tracker_point(s, u, v):
        tr = s.kit.k['elements']['tracker']; G = tr['grid']; n = len(G); span = tr['grid_span']
        gi = (u + span) / (2 * span) * (n - 1); gj = (v + span) / (2 * span) * (n - 1)
        i = max(0, min(n - 2, int(gi))); j = max(0, min(n - 2, int(gj))); fi, fj = gi - i, gj - j
        def L(a, b, f): return [a[0] * (1 - f) + b[0] * f, a[1] * (1 - f) + b[1] * f]
        return L(L(G[j][i], G[j][i + 1], fi), L(G[j + 1][i], G[j + 1][i + 1], fi), fj)
    def tracker(s, blips):
        tr = s.kit.k['elements']['tracker']; s.entry(tr['image']); s.text(tr['text_25m'], '25m')
        bs = tr['blip_size'] * 2
        for (u, v, kind) in blips:
            if u * u + v * v > 1: continue
            x, y = s.tracker_point(u, v); s.blit(s.kit.img(tr['blips'][kind]), x - bs / 2, y - bs / 2, bs, bs)
    def weapon(s, wn, rounds, cap, reserve, back=None, back_reserve=None):
        W_ = s.kit.k['weapons'][wn]; fr = W_['panel']; n = len(fr) - 1
        k = 0 if rounds <= 0 or cap <= 0 else max(1, min(n, int(math.ceil(rounds / cap * n - 1e-6))))
        s.entry(fr[k]); s.text(W_['number'], str(reserve) if reserve is not None else '')
        if back and s.kit.k['weapons'][back]['backpack']:
            B = s.kit.k['weapons'][back]['backpack']; s.entry(B['image']); s.text(B['count'], str(back_reserve) if back_reserve is not None else '')
    def reticle(s, wn, spread):
        W_ = s.kit.k['weapons'][wn]; rel = W_['reticle']
        if not rel: return
        k = max(0, min(len(rel) - 1, int(round(spread * (len(rel) - 1)))))
        z = W_['reticle_size']; s.blit(s.kit.img(rel[k]), 640 - z / 2, 360 - z / 2, z, z)

def frame(kit, sw, sh, shield=1.0, health=1.0, heading=85, blips=(), weapon='assault_rifle', rounds=24, cap=32, reserve=96,
          back='magnum', back_reserve=24, grenades=2, energy=1.0, spread=0.0, reticle=True):
    v = View(kit, sw, sh); E = kit.k['elements']
    v.compass(heading); v.frames(E['shield'], shield); v.frames(E['health'], health)
    v.tracker(blips)
    v.entry(E['grenades']['image']); v.text(E['grenades']['count'], str(grenades))
    v.frames(E['ability'], energy)
    v.weapon(weapon, rounds, cap, reserve, back, back_reserve)
    if reticle: v.reticle(weapon, spread)
    return v.cv

if __name__ == '__main__':
    k = Kit(sys.argv[1]); bg = Image.open(sys.argv[2]).convert('RGBA').resize((1920, 1080))
    f = frame(k, 1920, 1080, shield=0.72, blips=[(-0.42, -0.5, 'enemy'), (-0.25, -0.65, 'enemy'), (0.37, -0.37, 'enemy'), (0.12, -0.25, 'friend')])
    bg.alpha_composite(f); bg.convert('RGB').save(sys.argv[3], quality=90)
