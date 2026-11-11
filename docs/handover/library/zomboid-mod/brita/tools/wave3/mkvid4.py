import json, os, re, subprocess, shutil, sys, textwrap
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, '/mnt/project-files/zomboid-mod/brita/tools/irons'); import irons3
D = '/mnt/project-files/zomboid-mod/brita/local-pc/guns/'
order = irons3.WAVE3
def desc(g):
    f = irons3.GUNS[g]; d = f.__doc__ or (irons3.ump.__doc__ if g.startswith('UMP') else '')
    d = ' '.join(d.split())
    if '): ' in d: t, rest = d.split('): ', 1); t += ')'
    else: t, _, rest = d.partition(': ')
    return t, rest
NAMES = {g: json.load(open(D + g + '.json')).get('name', g) for g in order}
B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'; R_ = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
F = ImageFont.truetype(B, 24); f = ImageFont.truetype(R_, 17)
out = 'vid4'; shutil.rmtree(out, ignore_errors=True); os.makedirs(out); n = 0
def card(im, g, label, k):
    im = im.convert('RGB').copy(); d = ImageDraw.Draw(im); t, rest = desc(g); L = textwrap.wrap(rest, 98)[:3]
    d.rectangle([0, 0, 960, 64 + 21 * len(L)], fill=(20, 22, 26))
    d.text((14, 6), f'{k}/{len(order)}  {NAMES[g]}', font=F, fill='white'); d.text((946 - d.textlength(label, font=f), 12), label, font=f, fill=(140, 200, 255))
    d.text((14, 38), t[:110], font=f, fill=(200, 205, 215))
    for i, l in enumerate(L): d.text((14, 60 + 21 * i), l, font=f, fill=(230, 200, 120))
    return im
def save(im, k=1):
    global n
    for _ in range(k): im.save(f'{out}/{n:05d}.png'); n += 1
op = Image.new('RGB', (960, 540), (20, 22, 26)); d = ImageDraw.Draw(op)
d.text((40, 150), 'Brita wave 3: real-life iron sights (88 guns)', font=ImageFont.truetype(B, 32), fill='white')
for i, t in enumerate(['Each gun has its own factory sights, taken from the reference photos.',
                       'Your pack\'s flip-ups only go on guns that ship with no irons (rail-only',
                       'or optic-only guns, the big sniper rifles). Shotguns with no rifle sights get a bead.',
                       'Per gun: a turn round it, the front sight, the rear sight, then the',
                       'sight picture from behind (front sight centred in the rear).',
                       'Preview only: nothing is in the game or on your PC yet.']):
    d.text((40, 210 + i * 28), t, font=f, fill=(230, 200, 120) if i < 3 else (200, 200, 200))
save(op, 150)
for k, g in enumerate(order, 1):
    V = f'v4/{g}'
    for i in range(12): save(card(Image.open(f'{V}/orb{i:02d}.png'), g, 'the gun', k), 1)
    save(card(Image.open(f'{V}/front.png'), g, 'front sight', k), 25)
    save(card(Image.open(f'{V}/rear.png'), g, 'rear sight', k), 25)
    save(card(Image.open(f'{V}/pic.png'), g, 'sight picture', k), 30)
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', '25', '-i', f'{out}/%05d.png', '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
                '-crf', '24', '/mnt/project-files/zomboid-mod/brita/previews/brita-wave3-iron-sights.mp4'], check=True)
print(n, 'frames')
