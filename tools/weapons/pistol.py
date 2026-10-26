"""The pistol: a Beretta M9A3-style 9mm (open-top slide, 3-slot accessory rail, threaded barrel under a
thread protector, 3-dot sights, slide-mounted safety-decocker), drawn from the real thing's measurements:
220 mm long, 137 mm tall, 38 mm across the safety levers, a 130 mm barrel and a 160 mm sight radius.
Measurements are inches from the bore's centre line (y) and the slide's front face (z, positive back
toward the shooter).

Modular like the rifle (kit.py): the body is always there, and the magazine, a suppressor on the threads
and a light on the rail are parts in slots. The sights are fixed, so they're part of the body.
"""
import numpy as np

import attachments as AT
import paint as P
from inch import IN, Inch, picatinny

BORE_Y = 2.2            # where the bore sits in the build frame
Z0 = -3.4               # where the slide's front face sits

SLIDE_LEN = 7.85
SLIDE_TOP = 0.55        # top of the slide behind the open section
SLIDE_BOT = -0.48
SLIDE_W = 0.46          # half its width
BREECH = 4.90           # breech face: the 5.12" barrel runs from here to the muzzle
MUZZLE = -0.22          # where the barrel's threads end, ahead of the slide
CAP_TIP = -0.46         # front of the thread protector
FRONT_SIGHT = 0.42
REAR_SIGHT = FRONT_SIGHT + 6.30
SIGHT = 0.70            # the sights' line above the bore: the rear notch's floor and the front dot's top
RAIL_Z = (0.72, 2.00)   # the three-slot rail under the dust cover
RAIL_BASE = 0.98
GUARD_FRONT = 2.05
GRIP_TOP = (5.62, -1.12)    # the grip's centre line, top and bottom (z, y)
GRIP_BOT = (6.52, -4.38)
GRIP_W = 2.08           # front strap to back strap
MAG_BASE = -4.40        # the bottom of the grip, where the magazine's base plate starts


def frame_point(x, y, z):
    """A measured point (inches) in the build frame."""
    return np.array([IN * x, BORE_Y + IN * y, Z0 + IN * z])


HAND = frame_point(0.0, -2.55, 6.05)        # middle of the shooting hand on the grip


def slide(m):
    """The slide, drawn apart from the frame so first person can work it: it runs back with every shot, locks
    open on the last one and is racked to load. The sights ride on it."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    slide, steel = 'slide', 'steel'
    w, t = SLIDE_W, SLIDE_TOP
    # ---- the slide: a nose round the barrel, the front sight's bridge, the open top, the breech block
    b.box((-w, SLIDE_BOT, 0.0), (w, 0.48, 0.62), slide)                     # nose and bridge
    b.box((-w, SLIDE_BOT, 0.62), (-0.30, 0.28, BREECH), slide)              # open top: the two walls
    b.box((0.30, SLIDE_BOT, 0.62), (w, 0.28, BREECH), slide)
    b.box((-w, 0.28, BREECH - 0.28), (w, t, BREECH), slide)                 # the port's back edge
    b.box((-w, SLIDE_BOT, BREECH), (w, t, SLIDE_LEN), 'serr')               # breech block, serrated at the back
    b.box((-w + 0.06, t, 5.15), (w - 0.06, t + 0.03, 6.55), slide)          # a raised flat along the top
    b.box((0.44, 0.10, BREECH + 0.05), (w + 0.025, 0.24, BREECH + 0.62), steel)    # extractor, right
    b.rod(BREECH + 0.62, BREECH + 0.70, 0.04, steel, axis=2, at=(w + 0.01, 0.17))
    # safety-decocker: a drum through the slide and a lever each side
    b.rod(-w - 0.02, w + 0.02, 0.15, steel, axis=0, at=(0.28, 7.32))
    for s in (-1, 1):
        x0, x1 = (w, w + 0.20) if s > 0 else (-w - 0.20, -w)
        b.box((x0, 0.16, 7.05), (x1, 0.40, 7.60), steel)
        b.seg((7.05, 0.32), (6.62, 0.46), 0.18, 0.18, steel, x=s * (w + 0.10), over=0.04)
    # sights: a dovetailed rear with its notch, the front blade on the bridge
    b.box((-0.32, t, REAR_SIGHT - 0.22), (0.32, t + 0.08, REAR_SIGHT + 0.22), 'sight')   # dovetail base
    for x0, x1 in ((-0.30, -0.06), (0.06, 0.30)):
        b.box((x0, t + 0.08, REAR_SIGHT - 0.16), (x1, SIGHT + 0.10, REAR_SIGHT + 0.16), 'sight')
    b.box((-0.06, t + 0.08, REAR_SIGHT - 0.16), (0.06, SIGHT, REAR_SIGHT + 0.16), 'sight')
    b.box((-0.055, 0.48, FRONT_SIGHT - 0.16), (0.055, SIGHT + 0.06, FRONT_SIGHT + 0.12), 'sight')


def body(m):
    """The frame and everything fixed to it: barrel, rail, trigger guard, grip, controls, hammer."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    frame, steel, bar = 'frame', 'steel', 'barrel'
    # ---- the barrel: threads and their protector ahead of the slide, the chamber hood in the port
    b.rod(MUZZLE, BREECH, 0.275, bar, axis=2)
    b.rod(MUZZLE, 0.0, 0.25, 'thread', axis=2)
    b.rod(CAP_TIP, MUZZLE + 0.06, 0.31, 'cap', axis=2)
    b.box((-0.29, -0.12, BREECH - 0.95), (0.29, 0.29, BREECH), bar)          # chamber hood
    b.rod(0.0, 0.08, 0.09, steel, axis=2, at=(0.0, -0.66))                   # recoil spring guide's end
    # ---- the frame: dust cover with its rail, trigger guard, grip, controls
    fw = 0.44
    b.box((-fw, -RAIL_BASE, 0.62), (fw, SLIDE_BOT, 3.40), frame)
    picatinny(b, RAIL_Z[0], RAIL_Z[1], 'bottom', base=RAIL_BASE, mat='frame')
    b.box((-fw, -1.06, 3.40), (fw, SLIDE_BOT, 7.42), frame)                  # over the trigger and grip
    b.box((-0.40, -1.00, 7.42), (0.40, -0.68, 8.08), frame)                  # tang under the hammer
    b.box((-0.20, -2.16, GUARD_FRONT), (0.20, -RAIL_BASE, GUARD_FRONT + 0.20), frame)   # guard: squared front
    b.box((-0.20, -2.20, GUARD_FRONT - 0.10), (0.20, -1.98, GUARD_FRONT + 0.20), frame)  # its hooked toe
    b.box((-0.20, -2.30, GUARD_FRONT), (0.20, -2.13, 4.45), frame)           # and the bow under the trigger
    b.seg((3.42, -1.04), (3.52, -1.52), 0.22, 0.24, steel, over=0.03)         # trigger
    b.seg((3.52, -1.52), (3.78, -1.90), 0.20, 0.24, steel, over=0.03)
    b.box((fw, -0.74, 3.62), (fw + 0.025, -0.62, 6.30), steel)               # trigger bar, right
    # the grip: aluminium straps, wrap-round panels a touch narrower front to back
    b.seg(GRIP_TOP, GRIP_BOT, GRIP_W, 0.92, frame, over=0.05)
    b.seg((GRIP_TOP[0] + 0.05, GRIP_TOP[1] - 0.35), (GRIP_BOT[0] - 0.02, GRIP_BOT[1] + 0.12), GRIP_W - 0.42, 1.30,
          'grips', over=0.0)
    b.box((-0.47, MAG_BASE - 0.04, 5.30), (0.47, MAG_BASE + 0.10, 7.65), frame)   # the well's bevelled lip
    for s in (-1, 1):                                                         # panel screws
        for z, y in ((5.95, -1.75), (6.75, -3.95)):
            b.rod(s * 0.62, s * 0.68, 0.08, 'screw', axis=0, at=(y, z))
    b.box((-0.68, -1.92, 4.62), (-0.58, -1.58, 4.98), steel)                 # magazine release, left
    b.box((-0.50, -0.82, 3.74), (-fw, -0.50, 5.15), steel)                   # slide stop, left
    b.box((-0.53, -0.92, 3.74), (-fw, -0.62, 4.12), steel)                   # and its thumb pad
    b.box((-0.50, -0.82, 2.58), (-fw, -0.62, 3.02), steel)                   # takedown lever
    b.rod(fw, fw + 0.05, 0.10, steel, axis=0, at=(-0.72, 2.80))              # its button, right
    for z, y in ((3.55, -0.86), (7.20, -0.82), (6.50, -0.78)):              # trigger, hammer, sear pins
        b.rod(-fw - 0.02, fw + 0.02, 0.05, steel, axis=0, at=(y, z))
    b.box((-0.62, -4.32, 7.22), (-0.52, -4.08, 7.42), steel)                 # lanyard loop
    # ---- the hammer, down, its spur behind the slide
    b.box((-0.20, -0.70, SLIDE_LEN), (0.20, 0.20, SLIDE_LEN + 0.16), steel)
    b.seg((SLIDE_LEN + 0.10, 0.12), (SLIDE_LEN + 0.32, 0.42), 0.22, 0.34, 'spur', over=0.03)


def magazine(m):
    """A 15-round steel magazine with its polymer base plate, up the grip at the grip's angle. Origin: the
    middle of the bottom of the magazine well."""
    b = Inch(m)
    dz = (GRIP_BOT[0] - GRIP_TOP[0]) / (GRIP_BOT[1] - GRIP_TOP[1])           # how far back per inch down
    top = 3.05
    b.seg((-0.18 + dz * top, top), (-0.18, 0.0), 1.22, 0.56, 'magsteel', over=0.0)
    b.seg((-0.04, 0.0), (-0.04 - dz * 0.20, -0.20), 1.62, 0.80, 'baseplate', over=0.02)
    b.box((-0.18, 0.0, -0.95), (0.18, 0.08, -0.80), 'baseplate')            # the toe the fingers pull


HANDLE_TRAVEL = 1.45    # inches the slide runs back: on each shot, locked open, and when it's racked


# ---------------------------------------------------------------- parts made for the pistol

def suppressor(m):
    """A compact 9mm suppressor screwed on in place of the thread protector: its mount collar, a slim can (slim
    enough that the line of sight still clears it over the standard sights), the end cap."""
    b = Inch(m)
    b.rod(-0.05, 0.30, 0.47, 'park', axis=2)
    b.rod(-0.10, -0.02, 0.52, 'steel', axis=2)
    b.rod(-6.80, -0.05, 0.60, 'can', axis=2)
    b.rod(-7.00, -6.80, 0.55, 'cap', axis=2)


def light(m):
    """The weapon light as it sits on a pistol: a compact light (TLR-1 style) clamped to the dust cover's rail."""
    b = Inch(m)
    b.box((-0.42, 0.0, -0.55), (0.42, 0.28, 0.55), 'anod')
    AT.jaws(b, -0.55, 0.55, half=0.42)
    b.box((-0.55, 0.28, -1.95), (0.55, 1.05, 1.15), 'anod')                  # body
    b.box((-0.50, 0.33, 1.15), (0.50, 0.60, 1.32), 'rubber')                  # the switch paddles at the back
    b.rod(-2.30, -1.95, 0.50, 'bezel', axis=2, at=(0.0, 0.68))               # head
    b.box((-0.36, 0.32, -2.32), (0.36, 1.04, -2.30), 'lamp')
    b.rod(0.42, 0.58, 0.12, 'steel', axis=0, at=(0.14, 0.0))                 # clamp screw


# where each beam leaves its part, in the part's own inches (drawn on a top rail, pointing to -z)
LIGHT_LENS = (0.0, 0.68, -2.32)
LASER_LENS = (0.0, 0.54, -1.03)


def laser(m):
    """The laser sight as it sits on a pistol: a small green laser module clamped to the right of the frame,
    over the trigger guard's front, so it leaves the rail underneath for the light."""
    b = Inch(m)
    b.box((-0.42, 0.0, -0.45), (0.42, 0.26, 0.45), 'anod')
    AT.jaws(b, -0.45, 0.45, half=0.42)
    b.box((-0.46, 0.26, -1.00), (0.46, 0.82, 0.70), 'housing')
    b.box((-0.20, 0.40, -1.03), (0.20, 0.68, -1.00), 'lens_green')
    b.box((-0.26, 0.32, 0.70), (0.26, 0.52, 0.80), 'rubber')                  # its switch


def look(wear=0.4):
    """The pistol's materials: a Bruniton-black slide, black anodised frame, moulded grips, blacked steel."""
    def slide(f):
        return P.anodised(f, '#1b1b1d')

    def serr(f):
        c = slide(f)
        if abs(f.n[0]) > 0.5:                   # the grip serrations at the back of the slide's sides
            z = (f.Z - Z0) / IN
            groove = (z > 5.95) & (z < 7.0) & (((z - 5.95) % 0.11) < 0.045)
            c = np.where(groove[..., None], c * 0.45, c)
        return c

    def frame(f):
        c = P.anodised(f, '#17181a')
        if abs(f.n[2]) > 0.5 or abs(f.n[0]) < 0.5:     # checkered front and back straps
            y = (f.Y - BORE_Y) / IN
            chk = (y < -1.6) & (y > -4.2) & ((np.sin(f.Y * 24) * np.sin((f.X + f.Z) * 24)) > 0.35)
            c = np.where(chk[..., None], c * 0.72, c)
        return c

    def grips(f):
        c = P.polymer(f, '#161617', 0.12)
        if abs(f.n[0]) > 0.5:
            y, z = (f.Y - BORE_Y) / IN, (f.Z - Z0) / IN
            chk = (np.abs(np.sin((f.Y + f.Z) * 20)) < 0.32) | (np.abs(np.sin((f.Y - f.Z) * 20)) < 0.32)
            c = np.where(chk[..., None], c * 0.78, c)
            cy, cz = -2.85, 6.3 + (2.85 - 1.12) * 0.28 - 0.4         # a plain round medallion mid-panel
            r = np.hypot(y - cy, z - cz)
            c = np.where((r < 0.30)[..., None], P.col('#2a2a2c') * (1 + (f.noise(5, 3, 2) - 0.5) * 0.2)[..., None], c)
            c = np.where(((r > 0.30) & (r < 0.34))[..., None], c * 0.6, c)
        return c

    def barrel(f):
        return P.anodised(f, '#1b1b1d', wear_to='#5d6166')

    def thread(f):
        c = P.steel(f, 0.24, spread=0.08)
        return c * (1 - 0.35 * (((f.Z - Z0) / IN * 28) % 1 < 0.4))[..., None]

    def cap(f):
        c = P.anodised(f, '#1b1b1d', wear_to='#5d6166')
        if abs(f.n[2]) > 0.5:
            r = np.hypot(f.X, f.Y - BORE_Y)
            return np.where((r < IN * 0.18)[..., None], c * 0.08, c)
        knurl = (np.sin(f.Z * 40) * np.sin((f.X + f.Y) * 40)) > 0.3
        return np.where(knurl[..., None], c * 0.7, c)

    def steel(f):
        return P.steel(f, 0.20, tint='#7d8186', spread=0.08)

    def spur(f):
        c = steel(f)
        return c * (1 - 0.3 * ((f.Y * 9) % 1 < 0.35))[..., None]

    def screw(f):
        c = P.steel(f, 0.26, spread=0.08)
        if abs(f.n[0]) > 0.5:
            a, b2 = f.across()
            slot = np.abs(a - b2) < 0.02
            return np.where(slot[..., None], c * 0.3, c)
        return c

    def sight(f):
        c = P.anodised(f, '#161617', wear_to='#4a4d50')
        if f.n[2] > 0.5 or f.n[2] < -0.5:       # the three white dots, on the faces toward the eye
            if f.n[2] > 0.5:
                y = (f.Y - BORE_Y) / IN
                xs = (-0.18, 0.18) if (float(np.mean(f.Z)) - Z0) / IN > 3 else (0.0,)
                cy = SIGHT - 0.02 if len(xs) == 1 else SIGHT + 0.0
                for cx in xs:
                    r = np.hypot((f.X / IN) - cx, y - cy)
                    c = np.where((r < 0.042)[..., None], P.col('#f2efe6'), c)
        return c

    def magsteel(f):
        return P.steel(f, 0.22, tint='#7d8186', spread=0.08)

    def baseplate(f):
        return P.polymer(f, '#161617', 0.1)

    d = dict(slide=slide, serr=serr, frame=frame, grips=grips, barrel=barrel, thread=thread, cap=cap,
             steel=steel, spur=spur, screw=screw, sight=sight, magsteel=magsteel, baseplate=baseplate,
             flash=flash_paint)
    d['_after'] = [lambda f, c: P.grime(f, c, 0.3 * (0.4 + f.wear)),
                   lambda f, c: P.scratches(f, c, 0.25 * (0.3 + f.wear))]
    return d


def flash(m, z_tip, r=0.7):
    """Muzzle flash: crossed glowing planes and a core just past the muzzle (inches from the slide front)."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    L = 2.0 * r
    b.box((-0.02, -1.1 * r, z_tip - L), (0.02, 1.1 * r, z_tip), 'flash', light=15, noshade=True)
    b.box((-1.1 * r, -0.02, z_tip - L), (1.1 * r, 0.02, z_tip), 'flash', light=15, noshade=True)
    b.box((-0.35 * r, -0.35 * r, z_tip - 0.8 * r), (0.35 * r, 0.35 * r, z_tip), 'flash', light=15, noshade=True)


def flash_paint(f):
    a, b = f.across()
    t = np.clip(1 - np.sqrt(a ** 2 + b ** 2) / 0.9, 0, 1)
    return P.mix(P.col('#ff8a1a'), P.col('#fff6c8'), t ** 0.7)
