"""Wave 4 item models: the medical items added with the medicine system (survival/MedSpecs.java).
Same conventions as gen_loot.py (inches, standing on y = 0, front toward +z); table(it) returns the entries.
"""
import art as A
import paint as P
import shapes as sh
import wave3


def blob(m, c, r, mat, axis=1):
    sh.blob(m, c, (r, r, r) if isinstance(r, (int, float)) else r, mat, axis=axis)


def S(fn, **kw):
    return lambda m: fn(m, **kw)


RED, CREAM, BLUE, WHITE = '#a8281f', '#efe6d0', '#2a5aa8', '#e8e4da'
OLIVE, ORANGE = '#5a5a3a', '#e0661a'


# ================================================================== small packs and bottles


def swabs(m):
    """A clear tub of cotton buds, a few loose in front."""
    m.rod(0, 3.2, 1.3, 'glass')
    m.rod(3.2, 3.6, 1.35, 'cap')
    for i, x in enumerate((-0.6, 0.0, 0.6)):
        m.rod(0.3, 3.0, 0.08, 'stick', cx=x, cz=0.2 * (i - 1))
        blob(m, (x, 3.0, 0.2 * (i - 1)), 0.18, 'cloth')
    for z in (1.8, 2.2):
        m.rod(-1.3, 1.3, 0.07, 'stick', cx=0.1, cz=z, axis=0)
        blob(m, (-1.4, 0.1, z), 0.16, 'cloth', axis=0)
        blob(m, (1.4, 0.1, z), 0.16, 'cloth', axis=0)


def dropper(h=3.0, d=1.1):
    """A small squeeze bottle with a pointed nozzle: eye drops, nasal spray."""
    def build(m):
        m.rod(0, h, d / 2, 'body')
        m.box([-d / 2 - 0.02, h * 0.25, -0.2], [d / 2 + 0.02, h * 0.7, d / 2 + 0.02], 'label')
        m.rod(h, h + 0.35, d * 0.32, 'cap')
        m.rod(h + 0.35, h + 1.0, d * 0.16, 'cap')
    return build


def blister(w=3.4, l=2.2):
    """A pill box with one blister strip half out of it."""
    def build(m):
        m.box([-w / 2, 0, -l / 2], [w / 2, 0.9, l / 2], 'label')
        m.box([w / 2 - 0.3, 0.2, -l / 2 + 0.2], [w / 2 + 1.8, 0.3, l / 2 - 0.2], 'alu')
        for i in range(2):
            for j in range(2):
                m.rod(0.3, 0.45, 0.2, 'glass', cx=w / 2 + 0.5 + i * 0.7, cz=-0.35 + j * 0.7)
    return build


def sachet(w=3.0, l=4.5):
    """Two foil sachets, one lying across the other."""
    def build(m):
        m.box([-w / 2, 0, -l / 2], [w / 2, 0.15, l / 2], 'label')
        m.box([-w / 2 + 0.1, 0.15, -l / 2 + 0.3], [w / 2 - 0.1, 0.3, l / 2 - 0.3], 'seal')
        m.box([-l / 2, 0.3, -w / 2 + 0.4], [l / 2, 0.45, w / 2 + 0.4], 'label',
              rot=dict(axis='y', angle=22.5, origin=[0, 0.3, 0]))
    return build


def gel_tube(m):
    """A glucose gel tube: a flat squeeze tube with a twist-off tip."""
    m.box([-0.8, 0, -0.25], [0.8, 0.5, 2.6], 'label')
    m.box([-0.85, 0, 2.6], [0.85, 0.55, 2.9], 'seal')
    m.box([-0.45, 0.05, -0.8], [0.45, 0.45, 0], 'body')
    m.rod(-1.3, -0.8, 0.18, 'cap', cz=0.25, axis=2)


def tincture(m):
    """A brown glass dropper bottle."""
    m.rod(0, 2.6, 0.8, 'glass')
    m.box([-0.82, 0.5, -0.4], [0.82, 1.9, 0.82], 'label')
    m.rod(2.6, 3.0, 0.4, 'glass')
    m.rod(3.0, 3.5, 0.5, 'cap')
    blob(m, (0, 3.9, 0), 0.4, 'rubber')


def vial(m):
    """A small glass vial with a crimped cap, the anaesthetic box behind."""
    m.box([-1.6, 0, -1.4], [0.6, 3.8, -0.2], 'label')
    m.rod(0, 2.0, 0.5, 'glass', cx=1.0, cz=0.6)
    m.rod(2.0, 2.2, 0.32, 'glass', cx=1.0, cz=0.6)
    m.rod(2.2, 2.6, 0.45, 'alu', cx=1.0, cz=0.6)
    m.rod(2.6, 2.75, 0.3, 'cap', cx=1.0, cz=0.6)


def closures(m):
    """A card of butterfly closures."""
    m.box([-1.8, 0, -2.6], [1.8, 0.08, 2.6], 'label')
    for z in (-1.4, -0.2, 1.0):
        m.box([-1.3, 0.08, z], [1.3, 0.14, z + 0.8], 'white')
        m.box([-0.25, 0.14, z + 0.15], [0.25, 0.18, z + 0.65], 'cloth')


def organiser(m):
    """A seven-day pill organiser: a row of lidded boxes with day letters."""
    w = 0.9
    for i in range(7):
        x = -3.15 + i * w
        m.box([x + 0.04, 0, -0.6], [x + w - 0.04, 0.8, 0.6], 'body')
        m.box([x + 0.02, 0.8, -0.62], [x + w - 0.02, 0.95, 0.62], 'lid')
        m.box([x + 0.2, 0.95, 0.0], [x + w - 0.2, 0.98, 0.4], 'mark')
    m.box([-3.15, 0.3, -0.7], [3.15, 0.45, -0.6], 'body')


def scissors(m):
    """Trauma shears: angled blades, big plastic loops."""
    m.box([-0.1, 0, -0.5], [3.4, 0.12, 0.0], 'metal')
    m.box([-0.1, 0.12, 0.0], [3.0, 0.24, 0.5], 'metal', rot=dict(axis='y', angle=-22.5, origin=[0, 0.12, 0]))
    m.box([3.0, 0, -0.5], [3.6, 0.12, -0.3], 'metal')
    for y, z in ((0.0, -0.75), (0.12, 0.75)):
        m.box([-2.4, y, z - 0.6], [-0.2, y + 0.2, z - 0.3], 'handle')
        m.box([-2.4, y, z + 0.3], [-0.2, y + 0.2, z + 0.6], 'handle')
        m.box([-2.4, y, z - 0.6], [-2.1, y + 0.2, z + 0.6], 'handle')
        m.box([-0.5, y, z - 0.6], [-0.2, y + 0.2, z + 0.6], 'handle')
    m.rod(0.0, 0.3, 0.15, 'alu', cx=0.0, cz=0.0)


def oximeter(m):
    """A fingertip pulse oximeter: a clam with a little screen."""
    m.box([-1.0, 0, -0.75], [1.2, 0.6, 0.75], 'body')
    m.box([-1.0, 0.6, -0.75], [1.2, 1.2, 0.75], 'lid')
    m.box([-0.6, 1.2, -0.45], [0.8, 1.24, 0.45], 'dark')
    m.box([-0.4, 1.24, -0.3], [0.2, 1.26, 0.0], 'lens')
    m.box([0.3, 1.24, -0.3], [0.6, 1.26, 0.3], 'red')
    m.box([-1.3, 0.25, -0.5], [-1.0, 0.95, 0.5], 'rubber')


def dental(m):
    """An emergency dental kit: a blister card with a tub of filling, a mirror and a pick."""
    m.box([-2.5, 0, -3.5], [2.5, 0.1, 3.5], 'label')
    m.box([-2.2, 0.1, -3.2], [2.2, 0.8, 0.5], 'glass')
    m.rod(0.1, 0.7, 0.7, 'white', cx=-1.0, cz=-1.4)
    m.rod(0.1, 0.25, 0.4, 'metal', cx=1.0, cz=-2.2)
    m.box([0.9, 0.1, -1.8], [1.1, 0.2, 0.2], 'body')
    m.box([0.4, 0.1, -0.6], [0.55, 0.18, 0.3], 'metal')


def hot_water_bottle(m):
    """A ribbed rubber bottle with its neck and stopper."""
    m.box([-3.0, 0, -4.0], [3.0, 1.2, 3.0], 'rubber')
    m.box([-2.6, 1.2, -3.6], [2.6, 1.6, 2.6], 'rubber')
    for z in range(-3, 3):
        m.box([-2.4, 1.6, z + 0.1], [2.4, 1.7, z + 0.4], 'body')
    m.box([-1.0, 0.3, 3.0], [1.0, 1.1, 4.4], 'rubber')
    m.rod(4.4, 5.0, 0.55, 'cap', cx=0, cz=0.7, axis=2)


def splint_roll(m):
    """A moldable splint rolled up, orange foam over aluminium, with a band round it."""
    sh.roll(m, d=3.0, h=4.2, core=0.4)
    m.rod(1.7, 2.5, 1.55, 'strap')


def finger_splint(m):
    """An aluminium finger splint, foam-lined, bent at the tip."""
    m.box([-2.0, 0, -0.35], [1.5, 0.08, 0.35], 'alu')
    m.box([-2.0, 0.08, -0.32], [1.5, 0.18, 0.32], 'cloth')
    m.seg((1.5, 0.1), (2.3, 0.6), 0.2, 0.7, 'alu')


def knee_brace(m):
    """A neoprene sleeve with a hole at the kneecap and side straps, lying on its side."""
    m.rod(-3.0, 3.0, 2.0, 'body', axis=2)
    m.rod(-0.9, 0.9, 2.05, 'strap', axis=2)
    m.box([-0.6, 1.4, -0.6], [0.6, 2.1, 0.6], 'dark')
    for z in (-2.2, 2.2):
        m.rod(z - 0.4, z + 0.4, 2.1, 'strap', axis=2)
    sh.shift_up(m, 2.0)


def collar(m):
    """A cervical collar: a stiff two-tone band in a C, chin rest at the front."""
    sh.torus(m, (0, 0), 2.6, 1.4, 2.4, 'body', steps=14)
    m.box([-1.4, 0.0, 2.2], [1.4, 3.2, 3.0], 'lid')
    m.box([-0.8, 1.0, 2.9], [0.8, 2.0, 3.05], 'dark')
    sh.shift_up(m, 1.2)


def cane(m):
    """A walking cane lying down: crook handle, rubber foot."""
    m.rod(-14.0, 10.0, 0.4, 'wood', axis=0)
    m.arc((10.0, 1.6), 1.6, -90, 90, 0.8, 0.8, 'wood')
    m.seg((10.0, 3.2), (8.0, 3.2), 0.8, 0.8, 'wood')
    m.rod(-15.0, -14.0, 0.5, 'rubber', axis=0)
    sh.shift_up(m, 0.5)


def crutches(m):
    """One aluminium underarm crutch, lying down: twin rails, pad, grip, rubber foot."""
    for z in (-1.0, 1.0):
        m.rod(-20.0, 18.0, 0.3, 'alu', cz=z, axis=0)
    m.rod(-24.0, -20.0, 0.35, 'alu', axis=0)
    m.rod(-25.0, -24.0, 0.55, 'rubber', axis=0)
    m.box([18.0, -0.5, -1.4], [21.0, 0.6, 1.4], 'rubber')
    m.rod(-1.3, 1.3, 0.45, 'rubber', cx=2.0, axis=2)
    for x in range(-18, 18, 4):
        m.rod(-1.0, 1.0, 0.08, 'metal', cx=x, axis=2)
    sh.shift_up(m, 0.6)


def wheelchair(m):
    """A folding wheelchair, folded upright: two big wheels, the seat sling between, push handles."""
    for z in (-2.5, 2.5):
        sh.torus(m, (0, 12.0), 11.5, 0.9, 0.8, 'rubber', z=z, steps=20)
        sh.torus(m, (0, 12.0), 10.4, 0.5, 0.3, 'alu', z=z + (0.6 if z > 0 else -0.6), steps=20)
        m.box([-0.4, 11.6, z - 0.4], [0.4, 12.4, z + 0.4], 'metal')
        m.seg((-6.0, 2.0), (-6.0, 22.0), 0.5, 0.5, 'alu', z=z * 0.7)
        m.seg((-6.0, 22.0), (-9.0, 22.5), 0.5, 0.5, 'alu', z=z * 0.7)
        m.seg((-9.0, 22.5), (-10.0, 22.5), 0.8, 0.8, 'rubber', z=z * 0.7)
        m.seg((-6.0, 2.0), (5.0, 1.5), 0.5, 0.5, 'alu', z=z * 0.7)
        m.rod(1.0, 2.5, 1.2, 'rubber', cx=5.0, cz=z * 0.7)
    m.box([-6.2, 10.0, -1.6], [-5.6, 20.0, 1.6], 'cloth')
    m.box([-5.6, 9.0, -1.6], [2.0, 9.5, 1.6], 'cloth')
    m.seg((-6.0, 14.0), (2.0, 4.0), 0.4, 3.4, 'metal')


def stretcher(m):
    """A folding pole stretcher, rolled: two poles, canvas wrapped round them, straps."""
    for z in (-0.8, 0.8):
        m.rod(-20.0, 20.0, 0.45, 'alu', cz=z, axis=0)
        m.rod(20.0, 22.0, 0.55, 'rubber', cz=z, axis=0)
        m.rod(-22.0, -20.0, 0.55, 'rubber', cz=z, axis=0)
    m.rod(-17.0, 17.0, 1.6, 'cloth', axis=0)
    for x in (-10.0, 0.0, 10.0):
        m.rod(x - 0.6, x + 0.6, 1.7, 'strap', axis=0)
    sh.shift_up(m, 1.7)


def cylinder(m):
    """A portable oxygen cylinder lying on its side: green-shouldered, a regulator and gauge on the valve."""
    m.rod(-10.0, 6.0, 2.1, 'body', axis=0)
    m.rod(6.0, 8.0, 1.6, 'lid', axis=0)
    m.rod(8.0, 9.0, 0.6, 'metal', axis=0)
    m.box([9.0, -0.8, -0.8], [10.6, 0.8, 0.8], 'metal')
    m.rod(10.6, 11.2, 0.25, 'metal', axis=0)
    m.rod(0.8, 1.6, 0.7, 'metal', cx=9.8, cz=0, axis=1)
    m.rod(1.6, 1.7, 0.55, 'dial', cx=9.8, cz=0, axis=1)
    m.box([-6.0, -0.6, 2.05], [2.0, 0.9, 2.15], 'label')
    sh.shift_up(m, 2.1)


def stapler(m):
    """A skin stapler: a pistol-grip with a clear cartridge nose."""
    m.box([-1.0, 0, -0.6], [1.0, 3.0, 0.6], 'body')
    m.box([-1.0, 3.0, -0.6], [2.6, 4.2, 0.6], 'body')
    m.box([2.6, 3.0, -0.45], [3.4, 4.0, 0.45], 'glass')
    m.box([0.6, 1.0, -0.4], [1.8, 2.8, 0.4], 'lid')
    m.box([-0.6, 3.6, 0.6], [1.8, 4.0, 0.65], 'label')


def bag_valve(m):
    """A bag-valve mask: the squeeze bag, the valve, the clear mask."""
    blob(m, (-2.0, 1.6, 0), 1.6, 'body', axis=0)
    m.box([-5.5, 1.0, -1.5], [-1.0, 2.2, 1.5], 'body')
    m.rod(-6.6, -5.5, 0.7, 'cap', cx=1.6, cz=0, axis=0)
    m.rod(-1.0, 1.0, 0.6, 'cap', cx=1.6, cz=0, axis=0)
    m.box([1.0, 0.4, -1.4], [2.4, 2.8, 1.4], 'glass')
    m.box([2.4, 0.8, -1.0], [2.6, 2.4, 1.0], 'rubber')
    m.box([-8.0, 1.2, -1.2], [-6.6, 2.0, 1.2], 'glass')


def airway(m):
    """A roll-up pouch of airways in different sizes."""
    m.box([-4.0, 0, -2.8], [4.0, 0.3, 2.8], 'cloth')
    for i, (x, c) in enumerate(((-3.0, 'red'), (-1.6, 'mark'), (-0.2, 'lens'), (1.2, 'lid'), (2.6, 'body'))):
        s = 0.7 + i * 0.12
        m.seg((x - 0.2, 0.45), (x + 0.2, 0.45), 0.4, 3.0 + i * 0.3, c, plane='xy')
        m.box([x - 0.45, 0.3, 1.6 + i * 0.15], [x + 0.45, 0.7, 1.9 + i * 0.15], c)
    m.box([-4.0, 0.3, -2.8], [4.0, 0.5, -2.3], 'strap')


def gauze_pack(w=3.5, l=5.0, t=0.4):
    """A flat sealed pack: hemostatic gauze, a chest seal."""
    def build(m):
        m.box([-w / 2, 0, -l / 2], [w / 2, t, l / 2], 'label')
        m.box([-w / 2, 0, l / 2], [w / 2, t * 0.5, l / 2 + 0.4], 'seal')
        m.box([-w / 2, 0, -l / 2 - 0.4], [w / 2, t * 0.5, -l / 2], 'seal')
    return build


def tray_kit(w, h, d, colour_mat='body'):
    """A lidded plastic tray kit: IV start kit, blood typing kit."""
    def build(m):
        m.box([-w / 2, 0, -d / 2], [w / 2, h, d / 2], 'glass')
        m.box([-w / 2 - 0.1, h, -d / 2 - 0.1], [w / 2 + 0.1, h + 0.12, d / 2 + 0.1], 'label')
        m.box([-w / 2 + 0.3, 0.1, -d / 2 + 0.3], [-w / 2 + 1.6, h - 0.2, d / 2 - 0.3], colour_mat)
        m.rod(0.1, h - 0.2, 0.25, 'cap', cx=w / 2 - 1.0, cz=0)
        m.box([0, 0.1, -d / 2 + 0.4], [w / 2 - 1.6, 0.5, d / 2 - 0.4], 'white')
    return build


def manual(w=5.5, l=8.5, t=0.8):
    """A spiral-bound field manual lying flat."""
    def build(m):
        m.box([-w / 2, 0, -l / 2], [w / 2, 0.08, l / 2], 'side')
        m.box([-w / 2 + 0.1, 0.08, -l / 2 + 0.1], [w / 2 - 0.1, t - 0.08, l / 2 - 0.1], 'white')
        m.box([-w / 2, t - 0.08, -l / 2], [w / 2, t, l / 2], 'label')
        for z in range(-int(l / 2) + 1, int(l / 2)):
            m.rod(-0.1, t + 0.1, 0.1, 'metal', cx=-w / 2 + 0.15, cz=z + 0.5)
    return build


def drug_book(m):
    """A thick handbook with a tabbed edge."""
    m.box([-3.0, 0, -4.5], [3.0, 0.15, 4.5], 'side')
    m.box([-2.9, 0.15, -4.4], [2.88, 2.2, 4.4], 'white')
    m.box([-3.0, 2.2, -4.5], [3.0, 2.35, 4.5], 'label')
    m.box([-3.05, 0, -4.5], [-2.85, 2.35, 4.5], 'side')
    for i, c in enumerate(('red', 'lid', 'mark', 'body')):
        m.box([2.88, 0.4 + i * 0.45, -3.0 + i * 1.6], [3.1, 0.7 + i * 0.45, -2.2 + i * 1.6], c)


def backpack(m):
    """A big medic's backpack: main body, front pockets, top handle, cross on the flap, straps behind."""
    m.box([-6.0, 0, -4.0], [6.0, 17.0, 4.0], 'body')
    m.box([-5.5, 1.0, 4.0], [5.5, 9.0, 5.6], 'body')
    m.box([-5.0, 10.0, 4.0], [5.0, 15.0, 5.0], 'lid')
    m.box([-1.6, 11.5, 5.0], [1.6, 13.5, 5.1], 'mark')
    m.box([-0.6, 10.5, 5.0], [0.6, 14.5, 5.1], 'mark')
    m.box([-6.1, 4.0, -3.6], [-6.0, 4.4, 4.0], 'zip')
    m.box([-5.6, 9.0, 4.0], [5.6, 9.4, 5.7], 'zip')
    m.box([-1.6, 17.0, -0.4], [1.6, 17.8, 0.4], 'strap')
    for x in (-3.5, 3.5):
        m.box([x - 0.8, 2.0, -4.6], [x + 0.8, 16.0, -4.0], 'strap')
    for x in (-6.3, 6.0):
        m.box([x, 2.0, -2.5], [x + 0.3, 8.0, 2.5], 'cloth')


def ultrasound(m):
    """A laptop-style portable ultrasound, half open, the probe on its cable beside it."""
    m.box([-5.5, 0, -4.0], [5.5, 1.2, 4.0], 'body')
    m.box([-4.8, 1.2, -3.2], [4.8, 1.25, 1.0], 'keys')
    m.box([-1.2, 1.2, 1.6], [1.2, 1.25, 3.2], 'dark')
    m.box([-5.5, 1.0, -4.6], [5.5, 9.0, -4.0], 'body', rot=dict(axis='x', angle=-22.5, origin=[0, 1.0, -4.0]))
    m.box([-4.8, 1.8, -4.05], [4.8, 8.2, -3.95], 'dark', rot=dict(axis='x', angle=-22.5, origin=[0, 1.0, -4.0]))
    m.box([-3.6, 3.0, -3.95], [1.4, 7.0, -3.9], 'lens', rot=dict(axis='x', angle=-22.5, origin=[0, 1.0, -4.0]))
    m.box([6.5, 0, -0.7], [8.5, 1.1, 0.7], 'cap')
    m.box([8.5, 0, -0.5], [9.5, 0.9, 0.5], 'glass')
    m.seg((5.5, 0.4), (6.5, 0.4), 0.3, 0.3, 'grip')


def hard_case(w, h, d, latch=True):
    """A military hard case: ribbed lid, latches, a stencilled cross."""
    def build(m):
        m.box([-w / 2, 0, -d / 2], [w / 2, h * 0.6, d / 2], 'body')
        m.box([-w / 2 - 0.1, h * 0.6, -d / 2 - 0.1], [w / 2 + 0.1, h, d / 2 + 0.1], 'lid')
        for x in (-w * 0.3, 0.0, w * 0.3):
            m.box([x - 0.25, h, -d / 2], [x + 0.25, h + 0.2, d / 2], 'lid')
        if latch:
            for x in (-w * 0.32, w * 0.32):
                m.box([x - 0.6, h * 0.45, d / 2], [x + 0.6, h * 0.75, d / 2 + 0.3], 'metal')
        m.box([-w * 0.12, h + 0.2, -d * 0.06], [w * 0.12, h + 0.24, d * 0.06], 'mark')
        m.box([-w * 0.04, h + 0.2, -d * 0.18], [w * 0.04, h + 0.24, d * 0.18], 'mark')
        m.box([-1.6, h * 0.3, -d / 2 - 0.5], [1.6, h * 0.5, -d / 2], 'handle')
    return build


def antique_case(m):
    """A brass-cornered wooden surgeon's case, lid open on velvet with the instruments laid in."""
    w, d = 14.0, 6.0
    m.box([-w / 2, 0, -d / 2], [w / 2, 1.6, d / 2], 'wood')
    m.box([-w / 2 + 0.3, 1.2, -d / 2 + 0.3], [w / 2 - 0.3, 1.62, d / 2 - 0.3], 'felt')
    m.box([-w / 2, 1.6, -d / 2 - 0.3], [w / 2, 1.6 + d, -d / 2], 'wood')
    m.box([-w / 2 + 0.3, 1.9, -d / 2 - 0.02], [w / 2 - 0.3, 1.3 + d, -d / 2], 'felt')
    for x in (-w / 2, w / 2 - 0.5):
        for z in (-d / 2, d / 2 - 0.5):
            m.box([x, 0, z], [x + 0.5, 1.65, z + 0.5], 'brass')
    for i, x in enumerate((-5.0, -2.5, 0.0, 2.5)):
        m.box([x, 1.62, -1.8], [x + 0.3, 1.75, 1.8], 'leather' if i % 2 else 'wood')
        m.box([x - 0.05, 1.62, 1.8], [x + 0.35, 1.72, 2.6], 'blade')
    m.box([4.0, 1.62, -1.5], [6.0, 1.75, 1.5], 'blade')
    m.box([-w / 2 + 6.0, 0.6, d / 2], [-w / 2 + 8.0, 1.2, d / 2 + 0.15], 'brass')


def vials(m):
    """Clinical trial vials in a little rack, coloured caps, a biohazard-yellow box behind."""
    m.box([-3.2, 0, -1.2], [3.2, 0.6, 1.2], 'body')
    m.box([-3.4, 0, -2.6], [3.4, 2.4, -1.4], 'label')
    for i in range(5):
        x = -2.5 + i * 1.25
        m.rod(0.3, 3.2, 0.42, 'glass', cx=x)
        m.rod(3.2, 3.7, 0.46, 'cap' if i % 2 else 'red', cx=x)
    m.box([-3.2, 1.6, -1.2], [3.2, 1.9, 1.2], 'body')


def burn_kit(m):
    """A flat wallet of burn gel dressings with an orange flash."""
    m.box([-4.0, 0, -3.0], [4.0, 1.2, 3.0], 'body')
    m.box([-4.05, 0.3, -3.05], [4.05, 0.9, 3.05], 'zip')
    m.box([-3.6, 1.2, -2.6], [3.6, 1.25, 2.6], 'label')
    m.box([-1.0, 1.2, -3.2], [1.0, 1.4, -3.0], 'strap')


def cough_bottle(m):
    """A cough syrup bottle with its dosing cup on top."""
    sh.bottle(m, kind='flask_bottle')
    lo, hi = m.bounds()
    m.rod(hi[1], hi[1] + 0.9, 0.75, 'cap')


def table(it):
    return {
        'cotton_swabs': it(swabs, tint='#e8f0f0', cap=BLUE, cloth=A.fabric('#f4f2ec'), stick=A.flat('#f0f0f0')),
        'cough_syrup': it(cough_bottle, 'COUGH', '#7a2a5a', band=CREAM, tint='#5a1a10', liquid='#6a1a1a',
                          cap='#e8e4da'),
        'eye_drops': it(dropper(2.6, 1.0), 'EYE', WHITE, band='#3a8ac8', word_ink=WHITE, body=WHITE, cap='#3a8ac8'),
        'nasal_spray': it(dropper(3.2, 1.2), 'NASAL', WHITE, band='#3a9a6a', word_ink=WHITE, body=WHITE, cap='#3a9a6a'),
        'hydrocortisone': it(S(sh.tube, length=4.5, w=1.2), 'HC 1%', WHITE, band='#6a3a8a', word_ink=WHITE, cap=WHITE),
        'butterfly_closures': it(closures, 'CLOSURES', WHITE, band=BLUE, word_ink=WHITE, cloth=A.flat('#efe8dc')),
        'finger_splint': it(finger_splint, cloth=A.fabric('#4a6aa0')),
        'rehydration_salts': it(sachet(), 'ORS', '#3a8ac8', 'drop', '#e8e4da', band=CREAM, seal=A.foil('#c8ccd0')),
        'glucose_gel': it(gel_tube, 'GLUCOSE', ORANGE, band=CREAM, body=ORANGE, cap=ORANGE, seal=A.foil('#c8ccd0')),
        'iodine': it(tincture, 'IODINE', CREAM, band='#5a2a10', word_ink=CREAM, tint='#5a2a10', liquid='#3a1a08',
                     cap='#1e1f20'),
        'medical_scissors': it(scissors, handle=A.plastic('#3a6ab0'), alu=A.chrome),
        'pill_organiser': it(organiser, body='#e8e4da', lid=A.plastic('#6ab0d0'), mark=A.flat('#2a4a7a')),
        'crutches': it(crutches, alu=A.alu),
        'walking_cane': it(cane),
        'knee_brace': it(knee_brace, body='#1e1f20', strap=A.fabric('#3a3a3a'), dark=A.fabric('#2a2a2a')),
        'neck_collar': it(collar, body='#e8e4da', lid=A.plastic('#3a6ab0'), dark=A.plastic('#1e1f20')),
        'moldable_splint': it(splint_roll, label=A.fabric(ORANGE), core=A.alu, strap=A.fabric('#2a5aa8')),
        'pulse_oximeter': it(oximeter, body='#e8e4da', lid=A.plastic('#3a6ab0'), lens=A.flat('#60e0f0')),
        'dental_kit': it(dental, 'DENTAL', WHITE, 'cross', '#3a8ac8', band='#3a8ac8', body='#3a8ac8', tint='#e8f0f0'),
        'anti_nausea': it(blister(), 'NAUSEA', WHITE, band='#3a9a8a', word_ink=WHITE, tint='#e8f0f0'),
        'local_anaesthetic': it(vial, 'LIDOCAINE', WHITE, band=BLUE, word_ink=WHITE, tint='#e8f0f0', cap=BLUE),
        'wheelchair': it(wheelchair, alu=A.chrome, cloth=A.fabric('#1e1f20')),
        'hot_water_bottle': it(hot_water_bottle, body='#a8281f', cap='#e8e4da',
                               rubber=lambda f: P.paint(f, '#b8302a', steel_base=0.0, chips=0.0)),
        'hemostatic_gauze': it(gauze_pack(3.5, 5.0), 'HEMOSTATIC', OLIVE, band='#c8b070', word_ink='#1e1f20',
                               seal=A.foil('#6a6a4a')),
        'chest_seal': it(gauze_pack(5.5, 6.5, 0.3), 'CHEST SEAL', '#1e1f20', band=RED, word_ink=WHITE,
                         seal=A.foil('#3a3a3a')),
        'airway_kit': it(airway, cloth=A.fabric('#b02a22'), body='#e8c040', lid=A.plastic('#3a8a3a'),
                         lens=A.plastic('#e8e4da'), mark=A.plastic('#3a6ab0')),
        'iv_kit': it(tray_kit(6.0, 1.4, 4.0), 'IV START', WHITE, band=BLUE, word_ink=WHITE, tint='#e8f0f0', body=BLUE,
                     cap=BLUE),
        'bag_valve_mask': it(bag_valve, body='#e8a020', cap=BLUE, tint='#e0eef0'),
        'stretcher': it(stretcher, cloth=A.fabric('#4a5a3a'), alu=A.alu, strap=A.fabric('#1e1f20')),
        'oxygen_cylinder': it(cylinder, 'OXYGEN', WHITE, band='#2a7a3a', word_ink='#1e1f20', body='#d8d8d0',
                              lid=lambda f: P.paint(f, '#e8e4da', steel_base=0.5, chips=0.3), metal=A.chrome),
        'antivirals': it(blister(3.0, 2.0), 'ANTIVIRAL', WHITE, band='#c84a2a', word_ink=WHITE, tint='#e8f0f0'),
        'wound_stapler': it(stapler, 'STAPLER', WHITE, band=BLUE, word_ink=WHITE, body='#e8e4da',
                            lid=A.plastic('#3a6ab0'), tint='#e0eef0'),
        'field_medic_manual': it(manual(), 'FIELD MEDIC', OLIVE, 'cross', RED, band=OLIVE, word_ink='#1e1f20',
                                 white=A.flat('#efe8d6')),
        'burn_kit': it(burn_kit, 'BURN KIT', ORANGE, 'cross', WHITE, band=ORANGE, word_ink=WHITE, body='#b02a22',
                       strap=A.fabric('#1e1f20')),
        'drug_guide': it(drug_book, 'DRUG GUIDE', '#2a3a5a', 'rx', '#e8e4da', band='#2a3a5a', word_ink='#e8e4da',
                         white=A.flat('#efe8d6'), body='#e8c040', lid=A.flat('#3a8a3a')),
        'trauma_bag': it(backpack, body='#b02a22', lid=A.plastic('#9a2a20'), mark=A.flat('#efeae0'),
                         strap=A.fabric('#1e1f20'), cloth=A.fabric('#1e1f20')),
        'ultrasound': it(ultrasound, body='#d8d6d0', cap='#e8e4da', keys=A.plastic('#9a9a9a'),
                         lens=A.flat('#4a5a6a'), tint='#e0eef0'),
        'blood_typing_kit': it(tray_kit(5.0, 1.0, 3.5, 'red'), 'TYPING', WHITE, band=RED, word_ink=WHITE,
                               tint='#e8f0f0', cap=RED),
        'forward_surgical_case': it(hard_case(16.0, 7.0, 10.0), body='#4a4a30',
                                    lid=lambda f: P.paint(f, '#55553a', steel_base=0.4, chips=0.35),
                                    mark=A.flat('#d8d0b8')),
        'antique_surgeon_set': it(antique_case, felt=A.fabric('#5a1a2a'), brass=wave3.BRASS),
        'trial_vials': it(vials, 'TRIAL', '#e8c020', 'star', '#1e1f20', band='#1e1f20', word_ink='#e8c020',
                          body='#e8e4da', tint='#e8f0f0', liquid='#c8e0f0', level=0.7, cap=BLUE),
    }
