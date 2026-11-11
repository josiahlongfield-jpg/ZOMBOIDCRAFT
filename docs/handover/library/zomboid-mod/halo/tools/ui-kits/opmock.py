# Operator (Tarkov) side of the Armory, same layout as the Spartan one: Tarkov's own customisation (side, head, voice,
# upper/lower body) plus how the operator looks in kit, nameplate and pose. LOCAL ONLY art.
import sys, json
sys.path.insert(0, '/tmp/claude-0/armory')
import armory as A
from armory import S, F, WHITE, DIM, BLUE, LIST_DIM
from PIL import Image, ImageDraw
C = json.load(open('/mnt/project-files/zomboid-mod/tarkov/gear/clothes_raw.json'))
G = json.load(open('/mnt/project-files/zomboid-mod/tarkov/gear/tarkov_gear.json'))
OP = dict(side='USEC', head='ERIC', voice='USEC 2', top='USEC PCS MultiCam', pants='USEC Woodland Infiltrator',
          headwear='Team Wendy EXFIL Ballistic Helmet (Coyote Brown)', earpiece='Peltor ComTac V headset (OD Green)', face='NONE',
          eyewear='NONE', armband='NONE', armour='LBT-6094A Slick Plate Carrier (Coyote Tan)', rig='NONE', backpack='NONE',
          plate=283, pose='RIFLE READY', name='HOLLOW', tag='H117', emblem=(41, 0, 2, 0, 0, True))
CATS = [('SIDE', 'side'), ('HEAD', 'head'), ('VOICE', 'voice'), ('UPPER BODY', 'top'), ('LOWER BODY', 'pants'), ('HEADWEAR', 'headwear'),
        ('EARPIECE', 'earpiece'), ('FACE COVER', 'face'), ('EYEWEAR', 'eyewear'), ('ARMBAND', 'armband'), ('BODY ARMOUR', 'armour'),
        ('RIG', 'rig'), ('BACKPACK', 'backpack'), ('NAMEPLATE', None), ('POSE', 'pose')]
def short(s, n=26): return s if len(s) <= n else s[:n - 1].rstrip() + '…'
def frame(sel, cat):
    im = S.background(); S.header(im, 'MAIN MENU  /  ARMORY  /  OPERATOR', 'ARMORY')
    tx = 560
    for i, t in enumerate(['USEC', 'BEAR']):
        w = ImageDraw.Draw(im).textlength(t, font=F(24)) + 44
        if t == sel['side']: S.highlight(im, int(tx), 122, int(w), 40)
        S.text(im, (tx + 22, 128), t, F(24), WHITE if t == sel['side'] else (150, 160, 172), spacing=2); tx += w + 8
    S.text(im, (tx + 6, 132), 'Q / E', F(18), DIM, spacing=2)
    y = 214
    for c, key in CATS:
        on = c == cat
        if on: S.highlight(im, 112, y - 4, 520, 40)
        S.text(im, (132, y), c, F(23), WHITE if on else (180, 196, 212), spacing=2)
        v = sel[key] if key else dict(A.PLATES)[sel['plate']]
        S.text(im, (622, y + 3), short(v).upper(), F(19), (225, 232, 240) if on else (128, 142, 158), anchor='ra', spacing=1)
        y += 42
    S.put(im, A.plate(sel, 480), 118, 878)
    f = Image.open('/tmp/claude-0/op/menu_op.png').convert('RGBA'); S.shadow(im, 960, 985, 360, 34); S.put(im, f, 960, 995, h=780, anchor='mb')
    return im
def page_top():
    im = frame(OP, 'UPPER BODY')
    tops = [x['name'] for x in C if x['kind'] == 'top' and 'Usec' in x['side']]
    tops = sorted(set(tops), key=tops.index); i = tops.index(OP['top'])
    A.listing(im, 'UPPER BODY', tops, i, top=max(0, i - 3), desc='Tarkov\'s own USEC tops (BEAR has its own). The sleeves you see in first person change with it.')
    S.footer(im, [('ENTER', 'Wear'), ('ESC', 'Back'), ('W / S', 'Browse'), ('Q / E', 'USEC / BEAR'), ('DRAG', 'Turn')])
    im.convert('RGB').save(A.OUT + 'armory-v2-operator.jpg', quality=84, optimize=True, progressive=True); print('op top')
def page_headwear():
    im = frame(OP, 'HEADWEAR')
    hw = [x['name'] for x in G if x['cat'] == 'headwear']; hw = sorted(set(hw)); i = hw.index(OP['headwear'])
    A.listing(im, 'HEADWEAR', [short(h, 38) for h in hw], i, top=max(0, i - 3),
              desc='Every Tarkov helmet, cap and beanie (76). Helmets keep their Tarkov armour class.')
    S.footer(im, [('ENTER', 'Wear'), ('ESC', 'Back'), ('W / S', 'Browse'), ('Q / E', 'USEC / BEAR'), ('DRAG', 'Turn')])
    im.convert('RGB').save(A.OUT + 'armory-v2-operator-headwear.jpg', quality=84, optimize=True, progressive=True); print('op headwear')
if __name__ == '__main__':
    page_top(); page_headwear()
