# Preview clip of both in-game HUDs, drawn with kitview/tarview (the same sums as the Java), over an in-game street.
import sys, math, subprocess
sys.path.insert(0, '.')
import kitview as kv, tarview as tv
from PIL import Image, ImageDraw, ImageFont
FPS = 30; W, H = 1920, 1080
bg0 = Image.open('/mnt/project-files/zomboid-mod/previews/city-street-latest.png').convert('RGBA').crop((0, 135, 1440, 945)).resize((W, H))
RK = kv.Kit('gamekit/reach'); TK = kv.Kit('gamekit/tarkov')
cap = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 26)
def ease(a, b, t0, t1, t):
    f = min(1, max(0, (t - t0) / (t1 - t0))); f = f * f * (3 - 2 * f); return a + (b - a) * f
def fade(t, on0, on1):  # in 0.22 s, out 1.1 s
    if t < on0: return 0
    if t < on1: return min(1, (t - on0) / 0.22)
    return max(0, 1 - (t - on1) / 1.1)
def caption(im, text):
    d = ImageDraw.Draw(im); w = d.textlength(text, font=cap)
    d.rounded_rectangle(((W - w) / 2 - 16, H - 164, (W + w) / 2 + 16, H - 124), 8, fill=(0, 0, 0, 150)); d.text(((W - w) / 2, H - 160), text, font=cap, fill=(235, 235, 235))
# magnum spread from its shots (kick 0.42, settle 1.4/s), full while reloading
mag_shots = [4.6, 5.0, 5.4, 5.8]
def mag_spread(t):
    s = 0.0; last = 4.4
    for sh in mag_shots:
        if sh > t: break
        s = max(0, s - 1.4 * (sh - last)) + 0.42; s = min(1, s); last = sh
    s = max(0, s - 1.4 * (t - last))
    return 1.0 if 6.3 <= t < 6.9 else s
N = int(13 * FPS)
for i in range(N):
    t = i / FPS; im = bg0.copy()
    if t < 7:
        heading = ease(70, 130, 0.3, 6.5, t)
        health = 1.0 if t < 2.0 else ease(1.0, 0.65, 2.0, 2.15, t)
        energy = ease(1.0, 0.45, 1.0, 3.0, t) if t < 3.2 else ease(0.45, 1.0, 3.2, 6.0, t)
        blips = [(-0.55 + 0.12 * t / 7, -0.75 + 0.35 * t / 7, 'enemy'), (0.45 - 0.1 * t / 7, -0.6 + 0.2 * t / 7, 'enemy'),
                 (0.05 + 0.2 * t / 7, -0.95 + 0.4 * t / 7, 'enemy'), (0.15, -0.2, 'friend')]
        if t < 4.0:
            rounds = 32 - int(max(0, min(2.0, t - 0.5)) * 10)
            f = kv.frame(RK, W, H, health=health, heading=heading, blips=blips, weapon='assault_rifle', rounds=rounds, cap=32, reserve=96,
                         back='magnum', back_reserve=8 + 24, grenades=2, energy=energy)
            caption(im, 'Spartan HUD: Reach art from your files, assault rifle firing, health hit, ability meter = stamina')
        else:
            fired = sum(1 for s in mag_shots if s <= t)
            rounds = 8 if t >= 6.9 else 8 - fired
            reserve = 24 - (8 - (8 - fired)) * (1 if t >= 6.9 else 0)
            f = kv.frame(RK, W, H, health=health, heading=heading, blips=blips, weapon='magnum', rounds=rounds, cap=8, reserve=reserve,
                         back='assault_rifle', back_reserve=12 + 96, grenades=2, energy=energy, spread=mag_spread(t))
            caption(im, 'Swapped to the pistol (magnum reticle): spread opens per shot, held open while reloading')
    else:
        u = t - 7
        stam = ease(1.0, 0.35, 0.6, 3.2, u) if u < 3.4 else ease(0.35, 1.0, 3.4, 7, u)
        effects = ['light_bleeding', 'fracture', 'pain'] if u >= 2.0 else []
        tab_a = fade(u, 3.6, 99)
        hp = [round(m * 0.8 * c) for m, c in zip(tv.MAX, [1, 1, 1, 1, 0.55, 0.3, 1])]
        f = tv.frame(TK, W, H, stamina=stam, stam_a=fade(u, 0.6, 99), effects=effects, gun=('M4A1', 'Full auto', 30, 120),
                     gun_a=fade(u, 0.0, 3.5), tab_a=tab_a, hp=hp, broken=('leftleg',))
        caption(im, 'Operator HUD: stamina while sprinting, effect icons when hurt' + (', body check (hold Left Alt)' if u >= 3.6 else ', gun line after drawing'))
    im.alpha_composite(f); im.convert('RGB').save('clipfr/%04d.jpg' % i, quality=92)
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-framerate', str(FPS), '-i', 'clipfr/%04d.jpg', '-c:v', 'libx264', '-crf', '20', '-pix_fmt', 'yuv420p', 'hud-ingame-preview.mp4'], check=True)
print('ok')
