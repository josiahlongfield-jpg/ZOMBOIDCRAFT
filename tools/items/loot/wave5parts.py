"""Wave 5 item models: the car parts VEHICLES' CarParts names (stage 7 of wave 4's plan), the three auto manuals
and the Mechanics skill books. Built from the photos in refs/items/vehicle-parts/<id>/ (car_seat, car_tire and
headlight had no usable free photo: the seat from the auto shop refs' pulled seats, the tyre from
vehicle-fuel/spare_tire, the headlight from the tail light and bulb refs). Same conventions as gen_loot.py: inches,
standing on y = 0, front toward +z; table(it) returns the entries.
"""
import math

import art as A
import paint as P
import shapes as sh
import wave3
from wave3 import book
from wave4 import blob, flat

CREAM = '#efe6d0'
CAST = lambda f: P.cast_iron(f, base='#5a5854')      # noqa: E731
ALU_CAST = lambda f: P.steel(f, base=0.62, tint='#a8aaa6', brushed=False, spread=0.25)   # noqa: E731
BLACK_PAINT = lambda f: P.paint(f, '#1e1f20', steel_base=0.4, chips=0.25)               # noqa: E731


# ================================================================== engine bay


def engine_block(m):
    """A bare inline-four long block on its own: the cast block with four bores showing on the deck, the oil pan
    under it, the timing cover at the front, freeze plugs down the side, the bell-housing flange at the back."""
    m.box([-9.0, 4.0, -5.0], [9.0, 15.0, 5.0], 'cast')                 # block
    m.box([-9.2, 15.0, -5.2], [9.2, 15.6, 5.2], 'metal')               # machined deck
    for x in (-6.0, -2.0, 2.0, 6.0):                                   # bores
        m.rod(15.6, 15.65, 1.7, 'dark', cx=x, cz=0.0)
        m.rod(15.62, 15.68, 1.25, 'metal', cx=x, cz=0.0)
    for x in (-7.8, -4.0, 0.0, 4.0, 7.8):                              # head bolt holes
        for z in (-4.3, 4.3):
            m.rod(15.6, 15.66, 0.35, 'dark', cx=x, cz=z)
    m.box([-8.6, 0.0, -4.2], [8.6, 4.0, 4.2], 'pan')                   # oil pan
    m.box([-9.0, 3.6, -4.6], [9.0, 4.2, 4.6], 'metal')                 # pan rail
    m.rod(0.0, 0.6, 0.6, 'metal', cx=-6.0, cz=2.0)                     # drain plug (under)
    m.box([9.0, 4.5, -4.0], [10.6, 14.0, 4.0], 'cast')                 # timing cover
    m.rod(9.0, 12.2, 1.6, 'metal', cx=8.0, cz=0.0, axis=0)             # crank snout + pulley
    m.rod(12.2, 12.9, 2.6, 'dark', cx=8.0, cz=0.0, axis=0)
    m.rod(-11.0, -9.0, 6.0, 'cast', cx=8.5, cz=0.0, axis=0)            # bell-housing flange
    m.rod(-11.05, -10.95, 3.0, 'dark', cx=8.5, cz=0.0, axis=0)
    for x in (-5.0, 0.0, 5.0):                                         # freeze plugs
        m.rod(5.0, 5.12, 0.8, 'metal', cx=x, cz=9.0, axis=2)
    m.rod(5.0, 6.4, 0.9, 'metal', cx=3.0, cz=10.0, axis=2)             # oil filter boss
    m.rod(6.4, 9.0, 1.4, 'filter', cx=3.0, cz=10.0, axis=2)            # oil filter
    for x in (-7.5, -2.5, 2.5, 7.5):                                   # side ribs
        m.box([x - 0.25, 4.5, 5.0], [x + 0.25, 14.0, 5.4], 'cast')
    m.box([-3.0, 14.4, -5.6], [3.0, 15.0, -5.0], 'label')              # cast part number boss


def alternator(m):
    """An alternator: the round aluminium case with its cooling slots, the fan and grooved pulley at the front,
    the two mounting ears, the B+ stud at the back."""
    m.rod(-3.0, 3.0, 3.2, 'alu', axis=2)
    for i in range(12):
        a = math.radians(i * 30)
        x, y = 3.25 * math.cos(a), 3.25 * math.sin(a)
        m.box([x - 0.25, y - 0.25, -2.0], [x + 0.25, y + 0.25, 2.0], 'dark')
    m.rod(3.0, 3.8, 2.8, 'metal', axis=2)                              # fan
    for i in range(10):
        a = math.radians(i * 36)
        m.seg((0, 0), (2.7 * math.cos(a), 2.7 * math.sin(a)), 0.3, 0.7, 'metal', z=3.4, plane='xy')
    m.rod(3.8, 5.2, 1.4, 'metal', axis=2)                              # pulley
    for z in (4.1, 4.5, 4.9):
        m.rod(z, z + 0.15, 1.2, 'dark', axis=2)
    m.rod(5.2, 5.8, 0.35, 'metal', axis=2)                             # nut
    for x in (-3.6, 3.6):                                              # ears
        m.box([x - 0.9, -0.8, -2.6], [x + 0.9, 0.8, -1.2], 'alu')
        m.rod(-2.7, -1.1, 0.4, 'dark', cx=x, cz=0.0, axis=2)
    m.rod(-3.8, -3.0, 0.3, 'brass', cx=1.5, cz=1.0, axis=2)            # B+ stud
    m.box([-1.2, 1.6, -3.4], [1.2, 2.6, -3.0], 'dark')                 # plug socket
    sh.shift_up(m, 3.2)


def starter_motor(m):
    """A starter: the black motor can, the solenoid riding on top with its two studs, the nose housing and the
    bendix pinion poking out front, the mounting flange."""
    m.rod(-5.0, 2.0, 2.2, 'body', axis=0)                              # motor can
    m.rod(-5.6, -5.0, 1.6, 'metal', axis=0)                            # end cap
    for x in (-3.5, 0.5):
        m.rod(x, x + 0.25, 2.3, 'metal', axis=0)                       # through-bolt bands
    m.rod(-2.5, 3.0, 1.2, 'body', cx=3.2, cz=0.0, axis=0)              # solenoid
    m.rod(-3.0, -2.5, 1.0, 'dark', cx=3.2, cz=0.0, axis=0)
    for z in (-0.5, 0.5):
        m.rod(-3.6, -3.0, 0.25, 'brass', cx=3.2, cz=z, axis=0)
    m.rod(2.0, 5.0, 1.8, 'alu', axis=0)                                # nose housing
    m.box([2.6, -2.8, -2.8], [3.2, 2.8, 2.8], 'alu')                   # flange
    for y, z in ((-2.2, -2.2), (2.2, 2.2)):
        m.rod(2.55, 3.25, 0.35, 'dark', cx=y, cz=z, axis=0)
    m.rod(5.0, 6.6, 0.75, 'metal', axis=0)                             # pinion
    for i in range(8):
        a = math.radians(i * 45)
        m.box([5.2, 0.75 * math.cos(a) - 0.12, 0.75 * math.sin(a) - 0.12],
              [6.6, 0.75 * math.cos(a) + 0.12, 0.75 * math.sin(a) + 0.12], 'metal')
    sh.shift_up(m, 2.8)


def radiator(m):
    """A radiator: the finned core between plastic side tanks, the filler neck and cap on top, the upper and lower
    hose necks, the drain cock, the mounting pegs."""
    m.box([-11.0, 1.0, -0.8], [11.0, 15.0, 0.8], 'core')               # core
    for x in [-10.0 + i * 1.0 for i in range(21)]:                     # tubes
        m.box([x - 0.08, 1.0, 0.8], [x + 0.08, 15.0, 0.85], 'metal')
    for x in (-12.4, 11.0):                                            # side tanks
        m.box([x, 0.4, -1.2], [x + 1.4, 15.6, 1.2], 'tank')
    m.box([-12.4, 15.0, -1.0], [12.4, 15.6, 1.0], 'metal')             # top rail
    m.box([-12.4, 0.4, -1.0], [12.4, 1.0, 1.0], 'metal')
    m.rod(15.6, 17.0, 0.7, 'metal', cx=10.0, cz=0.0)                   # filler neck + cap
    m.rod(17.0, 17.5, 1.1, 'dark', cx=10.0, cz=0.0)
    m.rod(12.4, 14.4, 0.8, 'tank', cx=13.0, cz=0.0, axis=0)            # upper hose neck
    m.rod(-14.4, -12.4, 0.8, 'tank', cx=3.0, cz=0.0, axis=0)           # lower hose neck
    m.rod(0.0, 0.4, 0.35, 'brass', cx=-11.6, cz=0.0)                   # drain cock
    for x in (-11.7, 11.7):
        m.rod(-0.6, 0.4, 0.35, 'rubber', cx=x, cz=0.0)                 # pegs
    sh.shift_up(m, 0.6)


def gearbox(m):
    """A five-speed manual box: the ribbed aluminium bell housing at the front, the gear case tapering back to
    the tail housing and output flange, the shifter turret on top, the drain and fill plugs."""
    m.rod(-10.0, -6.0, 6.5, 'alu', cx=6.5, cz=0.0, axis=0)             # bell housing
    m.rod(-10.3, -10.0, 7.0, 'alu', cx=6.5, cz=0.0, axis=0)            # flange
    for i in range(6):
        a = math.radians(i * 60 + 30)
        m.rod(-10.4, -9.9, 0.4, 'metal', cx=6.5 + 6.7 * math.sin(a), cz=6.7 * math.cos(a), axis=0)
    m.rod(-10.5, -9.8, 1.0, 'metal', cx=6.5, cz=0.0, axis=0)           # input shaft
    m.rod(-6.0, 3.0, 4.6, 'alu', cx=5.6, cz=0.0, axis=0)               # gear case
    for x in (-4.0, -1.5, 1.0):
        m.box([x, 1.0, -4.9], [x + 0.4, 10.0, 4.9], 'alu')             # ribs
    m.rod(3.0, 10.0, 2.4, 'alu', cx=5.0, cz=0.0, axis=0)               # tail housing
    m.rod(10.0, 11.0, 1.8, 'metal', cx=5.0, cz=0.0, axis=0)            # output flange
    m.box([0.0, 9.5, -1.8], [4.0, 11.0, 1.8], 'alu')                   # shifter turret
    m.rod(11.0, 15.0, 0.35, 'metal', cx=2.0, cz=0.0)                   # stub of the shift lever
    m.rod(15.0, 15.4, 0.9, 'rubber', cx=2.0, cz=0.0)
    for x in (-3.0, 0.5):
        m.rod(4.5, 5.0, 0.45, 'dark', cx=x, cz=4.7, axis=2)            # drain/fill plugs
    m.box([-5.5, 0.0, -2.5], [-3.5, 1.2, 2.5], 'alu')                  # mount foot
    m.box([4.0, 2.4, -2.0], [7.0, 2.8, 2.0], 'rubber')                 # tail mount


# ================================================================== fuel and exhaust


def fuel_tank(m):
    """A steel fuel tank: the pressed shell with its stiffening ribs, the filler neck elbow, the sender plate and
    its wires on top, the strap channels across it."""
    m.box([-12.0, 0.0, -7.0], [12.0, 7.0, 7.0], 'body')
    m.box([-12.4, 3.2, -7.4], [12.4, 3.8, 7.4], 'body')               # seam flange
    for x in (-6.0, 0.0, 6.0):
        m.box([x - 1.6, 7.0, -6.4], [x + 1.6, 7.25, 6.4], 'body')       # pressed ribs
    for x in (-8.5, 8.5):
        m.box([x - 0.8, 0.0, -7.2], [x + 0.8, 7.4, 7.2], 'strap')      # straps
    m.rod(5.0, 9.0, 1.2, 'metal', cx=-10.0, cz=4.0)                    # filler neck
    m.seg((-10.0, 9.0), (-14.0, 11.5), 2.2, 2.2, 'metal', z=4.0)
    m.rod(-15.0, -13.8, 1.5, 'dark', cx=11.8, cz=4.0, axis=0)           # filler cap
    m.rod(7.25, 7.7, 2.2, 'metal', cx=4.0, cz=0.0)                     # sender plate
    m.rod(7.7, 8.5, 0.4, 'brass', cx=4.6, cz=0.5)
    m.rod(7.7, 8.3, 0.35, 'metal', cx=3.0, cz=-0.6)                    # fuel line stubs
    m.seg((3.0, 8.3), (8.0, 8.3), 0.5, 0.5, 'rubber', z=-0.6)
    m.seg((4.6, 8.5), (9.0, 8.0), 0.3, 0.3, 'wire', z=0.5)
    m.box([-3.0, 2.0, 7.0], [3.0, 5.0, 7.05], 'label')


def fuel_pump(m):
    """An in-tank fuel pump module: the plastic bucket, the pump motor and its sock filter at the foot, the
    float arm of the sender, the top flange with its connector and two pipe stubs."""
    m.rod(0.0, 7.0, 2.4, 'tank')                                       # bucket
    m.rod(7.0, 7.6, 3.4, 'tank')                                       # flange
    m.rod(7.6, 8.4, 0.5, 'tank', cx=-1.2, cz=0.8)                      # pipe stubs
    m.rod(7.6, 8.6, 0.45, 'tank', cx=1.2, cz=0.8)
    m.box([-0.8, 7.6, -2.2], [0.8, 8.8, -1.0], 'dark')                 # connector
    m.rod(1.0, 6.0, 0.9, 'metal', cx=0.0, cz=2.6)                      # pump motor showing
    blob(m, (0.0, 0.6, 3.4), (2.0, 0.6, 1.4), 'cloth')                 # sock filter
    m.seg((2.4, 3.5), (6.5, 1.0), 0.25, 0.25, 'wire', z=0.0)           # float arm
    blob(m, (6.8, 0.9, 0.0), (1.0, 0.6, 0.6), 'dark')                  # float
    m.box([2.3, 2.8, -0.6], [3.0, 4.6, 0.6], 'dark')                   # sender card


def exhaust(m):
    """A muffler with its pipe: the oval can with its rolled seams, the inlet pipe bending up from one end, the
    tailpipe out the other, a hanger rod, a strap clamp."""
    for fz, fy in ((1, 0.55), (0.55, 1), (0.8, 0.8)):
        m.box([-8.0, 3.0 - 3.0 * fy, -4.5 * fz], [8.0, 3.0 + 3.0 * fy, 4.5 * fz], 'metal', round=4.5, axis=0, oval=True)
    for x in (-8.0, 8.0):
        for fz, fy in ((1, 0.55), (0.55, 1), (0.8, 0.8)):
            m.box([x - 0.25, 3.0 - 3.2 * fy, -4.7 * fz], [x + 0.25, 3.0 + 3.2 * fy, 4.7 * fz], 'body', round=4.7,
                  axis=0, oval=True)
    m.rod(-14.0, -8.0, 0.9, 'metal', cx=3.0, cz=-2.0, axis=0)           # inlet
    m.seg((-14.0, 3.0), (-18.0, 6.0), 1.8, 1.8, 'metal', z=-2.0)
    m.rod(8.0, 16.0, 0.9, 'metal', cx=3.0, cz=2.0, axis=0)             # tailpipe
    m.rod(15.4, 16.6, 1.1, 'chrome', cx=3.0, cz=2.0, axis=0)           # tip
    m.rod(-15.2, -14.4, 1.15, 'metal', cx=3.0, cz=-2.0, axis=0)        # clamp
    m.rod(6.0, 8.5, 0.2, 'metal', cx=0.0, cz=0.0)                      # hanger rod
    m.rod(8.2, 8.8, 0.6, 'rubber', cx=0.0, cz=0.0)
    m.box([-3.0, 1.0, 4.5], [3.0, 4.5, 4.6], 'label')


# ================================================================== under and wheels


def steering_rack(m):
    """A rack and pinion: the long tube with the pinion housing at one end, the rubber bellows each side, the
    track rods and their ball-joint ends, the two clamp brackets."""
    m.rod(-9.0, 9.0, 1.1, 'alu', cx=2.0, cz=0.0, axis=0)                # rack tube
    m.rod(-9.5, -5.5, 1.6, 'alu', cx=2.0, cz=0.0, axis=0)               # pinion housing
    m.seg((-7.5, 2.0), (-6.0, 5.6), 1.6, 1.6, 'alu', z=0.0)             # pinion neck
    m.rod(5.4, 6.4, 0.5, 'metal', cx=-5.9, cz=0.0)                     # input spline
    for side in (-1, 1):
        x0 = 9.0 if side > 0 else -9.5
        for i in range(5):                                            # bellows
            x = x0 + side * (0.5 + i * 0.7)
            m.rod(x - 0.3, x + 0.3, 1.2 if i % 2 == 0 else 0.9, 'rubber', cx=2.0, cz=0.0, axis=0)
        xe = x0 + side * 3.8
        m.rod(min(xe, xe + side * 6), max(xe, xe + side * 6), 0.35, 'metal', cx=2.0, cz=0.0, axis=0)   # track rod
        xb = xe + side * 6
        blob(m, (xb + side * 0.6, 2.0, 0.0), (0.9, 0.9, 0.9), 'body')   # tie-rod end
        m.rod(2.0, 3.6, 0.3, 'metal', cx=xb + side * 0.6, cz=0.0)
    for x in (-4.0, 6.0):                                              # clamp brackets
        m.box([x - 0.6, 0.0, -1.6], [x + 0.6, 3.4, 1.6], 'rubber')
        m.box([x - 0.8, 2.9, -1.8], [x + 0.8, 3.4, 1.8], 'metal')


def brake_pads(m):
    """A brake set off the shelf: the drilled disc rotor lying flat, a pair of pads on it with their friction
    blocks and backing plates, the anti-rattle clips."""
    m.rod(0.0, 1.0, 5.5, 'metal')                                      # rotor
    m.rod(1.0, 2.4, 3.0, 'body')                                       # hat
    m.rod(2.4, 2.45, 1.4, 'dark')                                      # centre bore
    for i in range(5):
        a = math.radians(i * 72)
        m.rod(2.4, 2.46, 0.3, 'dark', cx=2.2 * math.cos(a), cz=2.2 * math.sin(a))
    for i in range(24):                                                # vent slots on the rim
        a = math.radians(i * 15)
        m.box([5.5 * math.cos(a) - 0.12, 0.3, 5.5 * math.sin(a) - 0.12],
              [5.5 * math.cos(a) + 0.12, 0.7, 5.5 * math.sin(a) + 0.12], 'dark')
    for z in (-1.0, 1.4):                                              # pads
        m.box([-2.8, 1.0, 3.2 + z], [2.8, 1.3, 4.3 + z], 'metal')
        m.box([-2.5, 1.3, 3.3 + z], [2.5, 1.9, 4.2 + z], 'pad')
    m.seg((-2.6, 2.0), (2.6, 2.0), 0.15, 0.15, 'chrome', z=4.6)


def suspension(m):
    """A coilover strut: the damper body with its lower knuckle bracket, the red coil spring round it, the top
    mount with its three studs, the dust boot."""
    m.rod(0.0, 9.0, 1.1, 'body')                                       # damper body
    m.box([-1.2, 0.0, -1.8], [1.2, 2.4, -0.9], 'body')                 # knuckle bracket
    m.box([-1.2, 0.0, 0.9], [1.2, 2.4, 1.8], 'body')
    m.rod(-1.4, 1.4, 0.3, 'metal', cx=1.2, cz=0.0, axis=0)             # bracket bolt
    m.rod(7.0, 7.6, 2.4, 'metal')                                      # lower seat
    steps, turns = 70, 6.0
    pts = [(2.1 * math.cos(2 * math.pi * turns * i / steps), 7.7 + 9.0 * i / steps,
            2.1 * math.sin(2 * math.pi * turns * i / steps)) for i in range(steps + 1)]
    for (x0, y0, z0), (x1, y1, z1) in zip(pts, pts[1:]):
        L = math.dist((x0, y0, z0), (x1, y1, z1))
        mx, my, mz = (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2
        ay = math.degrees(math.atan2(-(z1 - z0), x1 - x0))
        m.box([mx - L / 2 - 0.1, my - 0.25, mz - 0.25], [mx + L / 2 + 0.1, my + 0.25, mz + 0.25], 'spring',
              rot=dict(axis='y', angle=ay, origin=[mx, my, mz]))
    m.rod(9.0, 16.0, 0.9, 'rubber')                                    # dust boot
    m.rod(16.6, 17.2, 2.4, 'metal')                                    # upper seat
    m.rod(17.2, 18.4, 1.9, 'dark')                                     # top mount
    m.rod(18.4, 19.4, 0.3, 'metal')                                    # piston rod nut
    for i in range(3):
        a = math.radians(i * 120 + 90)
        m.rod(18.4, 19.4, 0.25, 'metal', cx=1.5 * math.cos(a), cz=1.5 * math.sin(a))


def car_tire(m):
    """A loose car tyre stood on its tread, off its rim: the fat rubber ring with tread blocks round the crown,
    the sidewall lettering, the open bead in the middle."""
    R, c = 10.2, 12.6
    sh.torus(m, (0.0, c), R, 4.8, 8.0, 'rubber', z=0.0, steps=32)
    for i in range(32):                                                  # tread blocks
        a = math.radians(i * 360 / 32)
        x, y = (R + 2.35) * math.cos(a), c + (R + 2.35) * math.sin(a)
        for z in (-2.4, 2.4):
            m.box([x - 0.3, y - 0.55, z - 1.4 + (0.4 if i % 2 else -0.4)], [x + 0.3, y + 0.55, z + 1.4 + (0.4 if i % 2 else -0.4)],
                  'rubber', rot=dict(axis='z', angle=i * 360 / 32, origin=[x, y, z]))
    m.box([-3.5, c + R - 0.6, 4.0], [3.5, c + R + 0.6, 4.05], 'label')  # sidewall size


def wheel_rim(m):
    """A steel wheel stood up: the rim barrel with its two flanges, the pressed centre disc with its vent holes,
    the five stud holes round the hub, the valve stem."""
    R, c = 7.6, 8.2
    sh.torus(m, (0.0, c), R, 0.6, 6.5, 'rim', z=0.0, steps=32)         # barrel
    for z in (-3.0, 3.0):
        sh.torus(m, (0.0, c), R + 0.4, 1.0, 0.5, 'rim', z=z, steps=32)  # flanges
    m.rod(-0.3, 0.3, 7.2, 'rim', cx=0.0, cz=c, axis=2)                  # centre disc
    m.rod(0.3, 1.3, 2.6, 'rim', cx=0.0, cz=c, axis=2)                   # hub dish
    m.rod(1.3, 1.35, 1.2, 'dark', cx=0.0, cz=c, axis=2)
    for i in range(5):
        a = math.radians(i * 72)
        m.rod(1.3, 1.36, 0.35, 'dark', cx=1.9 * math.cos(a), cz=c + 1.9 * math.sin(a), axis=2)
    for i in range(6):
        a = math.radians(i * 60 + 30)
        m.rod(0.3, 0.36, 0.8, 'dark', cx=4.9 * math.cos(a), cz=c + 4.9 * math.sin(a), axis=2)
    m.rod(1.5, 3.2, 0.25, 'rubber', cx=5.6, cz=c + 5.0, axis=2)          # valve stem


def headlight(m):
    """A headlight assembly: the chrome bowl and reflector behind the fluted glass lens, the trim ring, the three
    aim screws, the bulb socket and wires at the back."""
    m.rod(-2.0, 1.0, 3.4, 'chrome', axis=2)                             # bowl
    m.rod(1.0, 1.4, 3.7, 'chrome', axis=2)                              # trim ring
    m.rod(1.0, 1.9, 3.3, 'lens', axis=2)                                # lens
    for x in (-2.0, -1.0, 0.0, 1.0, 2.0):                               # flutes
        m.box([x - 0.12, -2.8, 1.85], [x + 0.12, 2.8, 1.95], 'rib')
    for i in range(3):                                                  # aim screws
        a = math.radians(i * 120 + 90)
        m.rod(-0.8, 0.0, 0.35, 'metal', cx=3.8 * math.cos(a), cz=3.8 * math.sin(a), axis=2)
    m.rod(-3.2, -2.0, 1.0, 'dark', axis=2)                              # socket
    m.seg((0.0, -0.4), (-2.6, -3.0), 0.25, 0.25, 'wire', z=-3.0, plane='xy')
    sh.shift_up(m, 3.7)


def tail_light(m):
    """A tail lamp: the chrome bezel, the red lens with its moulded ribs and the amber turn section, the white
    reverse window, the black housing and bulb holders behind."""
    m.box([-5.0, 0.0, -1.6], [5.0, 4.0, 0.0], 'dark')                    # housing
    m.box([-5.3, -0.3, 0.0], [5.3, 4.3, 0.4], 'chrome')                 # bezel
    m.box([-4.8, 0.2, 0.4], [1.8, 3.8, 1.0], 'red')                      # red lens
    m.box([1.9, 0.2, 0.4], [3.4, 3.8, 1.0], 'amber')
    m.box([3.5, 0.2, 0.4], [4.8, 3.8, 1.0], 'lens')
    for y in (0.9, 1.6, 2.3, 3.0):
        m.box([-4.6, y - 0.07, 1.0], [4.6, y + 0.07, 1.06], 'rib')
    for x in (-3.0, 0.0, 3.0):
        m.rod(-2.4, -1.6, 0.6, 'dark', cx=x, cz=2.0, axis=2)
    sh.shift_up(m, 0.3)


def car_door(m):
    """A front door off its hinges, stood up: the painted skin with its swage line and dents, the window frame
    with the glass, the chrome handle and lock barrel, the mirror, the two hinges on the front edge."""
    m.box([-18.0, 0.0, -1.0], [18.0, 20.0, 1.0], 'panel')                 # skin
    m.box([-18.0, 13.0, 1.0], [18.0, 13.4, 1.15], 'panel')                # swage line
    m.box([-16.5, 20.0, -0.5], [16.5, 21.0, 0.5], 'panel')                # window frame base
    m.box([-16.5, 21.0, -0.35], [16.5, 34.0, 0.35], 'glass')            # glass
    for x0, x1 in ((-17.0, -16.0), (16.0, 17.0)):
        m.box([x0, 20.0, -0.6], [x1, 34.5, 0.6], 'dark')                 # frame posts
    m.box([-17.0, 33.8, -0.6], [17.0, 34.8, 0.6], 'dark')
    m.box([-14.0, 15.4, 1.0], [-10.0, 16.4, 1.6], 'chrome')             # handle
    m.rod(1.0, 1.4, 0.45, 'chrome', cx=-9.0, cz=15.9, axis=2)       # lock barrel
    m.box([13.0, 20.5, 0.0], [16.5, 23.5, 3.0], 'dark')                 # mirror
    m.box([13.4, 20.9, 3.0], [16.1, 23.1, 3.1], 'chrome')
    for y in (4.0, 15.0):                                              # hinges
        m.box([17.8, y, -1.4], [19.0, y + 2.4, 0.6], 'metal')
    m.box([-18.05, 2.0, -0.6], [-17.9, 6.0, 0.6], 'metal')              # striker latch
    for x, y in ((-6.0, 6.0), (4.0, 10.0)):                              # dents
        blob(m, (x, y, 1.05), (1.8, 1.2, 0.15), 'dent')


def car_bonnet(m):
    """A bonnet off its hinges, lying flat: the painted panel with its raised centre and crease lines, the
    underside bracing showing at the edges, the hinge plates and the latch striker."""
    m.box([-20.0, 0.0, -16.0], [20.0, 1.0, 16.0], 'panel')
    m.box([-8.0, 1.0, -15.0], [8.0, 1.5, 15.5], 'panel')                 # power bulge
    for x in (-12.0, 12.0):
        m.box([x - 0.2, 1.0, -15.5], [x + 0.2, 1.2, 15.5], 'panel')     # creases
    m.box([-20.0, -0.4, -16.0], [20.0, 0.0, -14.0], 'dark')             # bracing rim
    m.box([-20.0, -0.4, 14.0], [20.0, 0.0, 16.0], 'dark')
    for x in (-17.0, 17.0):
        m.box([x - 1.2, -0.6, -16.0], [x + 1.2, 0.0, -12.0], 'metal')   # hinge plates
    m.box([-1.0, -0.8, 15.0], [1.0, 0.0, 16.0], 'metal')                # striker
    blob(m, (6.0, 1.05, 4.0), (3.0, 0.15, 2.0), 'dent')
    sh.shift_up(m, 0.8)


def boot_lid(m):
    """A boot lid lying flat: the painted panel with the rear lip, the lock and the chrome trim strip, the
    hinge arms, the number plate recess."""
    m.box([-18.0, 0.0, -10.0], [18.0, 1.0, 10.0], 'panel')
    m.box([-18.0, 0.0, 9.0], [18.0, 2.0, 10.0], 'panel')                 # rear lip
    m.box([-17.0, 2.0, 9.4], [17.0, 2.2, 9.8], 'chrome')
    m.box([-5.0, 1.0, 5.0], [5.0, 1.1, 8.5], 'dark')                    # plate recess
    m.rod(1.0, 1.4, 0.5, 'chrome', cx=0.0, cz=4.2)                     # lock
    for x in (-14.0, 14.0):                                            # hinge arms
        m.box([x - 0.4, -0.2, -16.0], [x + 0.4, 0.6, -9.0], 'metal')
    sh.shift_up(m, 0.2)


def car_horn(m):
    """A disc horn: the round black body with its spiral trumpet face, the bent mounting bracket, the spade
    terminal on the back."""
    c = 2.4
    m.rod(0.0, 1.6, 2.4, 'body', cx=0.0, cz=c, axis=2)
    m.rod(1.6, 1.9, 2.2, 'dark', cx=0.0, cz=c, axis=2)
    for i in range(3):
        sh.torus(m, (0.0, c), 0.6 + i * 0.5, 0.18, 0.2, 'metal', z=1.95)
    m.rod(1.9, 2.0, 0.35, 'metal', cx=0.0, cz=c, axis=2)
    m.box([-0.3, c + 2.0, -0.6], [0.3, c + 4.0, -0.2], 'metal')         # bracket
    m.box([-0.3, c + 3.6, -1.6], [0.3, c + 4.0, -0.2], 'metal')
    m.box([-0.15, c - 1.2, -0.4], [0.15, c - 0.6, 0.0], 'brass')        # terminal


def car_radio(m):
    """An old in-dash cassette radio: the chrome face plate with the tuning dial window, the two knobs on their
    shafts, the push-button row, the cassette door, the metal can behind."""
    m.box([-3.6, 0.0, -6.0], [3.6, 2.0, 0.0], 'metal')                  # can
    m.box([-3.8, -0.2, 0.0], [3.8, 2.2, 0.4], 'chrome')                 # face
    m.box([-2.6, 1.2, 0.4], [2.6, 1.9, 0.45], 'dial')                   # dial window
    for x in (-2.3, -1.2, 0.0, 1.2, 2.3):
        m.box([x - 0.02, 1.25, 0.45], [x + 0.02, 1.85, 0.47], 'red')
    m.box([-2.6, 0.15, 0.4], [2.6, 0.95, 0.5], 'dark')                  # cassette door
    for x in (-3.3, 3.3):
        m.rod(0.4, 1.4, 0.15, 'metal', cx=x, cz=1.0, axis=2)            # shafts
        m.rod(1.0, 1.6, 0.35, 'knob', cx=x, cz=1.0, axis=2)
    for i, x in enumerate((-1.6, -0.8, 0.0, 0.8, 1.6)):
        m.box([x - 0.3, -0.1, 0.4], [x + 0.3, 0.1, 0.8], 'keys')
    sh.shift_up(m, 0.2)


def car_seat(m):
    """A front bucket seat pulled out: the cushion and backrest in vinyl with their pleats, the headrest on its
    two chrome posts, the seat runners and the recline lever."""
    m.box([-6.0, 3.0, -7.0], [6.0, 6.0, 7.0], 'seat')                    # cushion
    for x in (-3.0, 0.0, 3.0):
        m.box([x - 0.12, 6.0, -6.6], [x + 0.12, 6.08, 6.6], 'dark')      # pleats
    for x in (-6.3, 5.3):
        m.box([x, 3.2, -6.8], [x + 1.0, 7.0, 6.8], 'seat')               # bolsters
    m.box([-6.0, 5.0, -8.6], [6.0, 22.0, -6.0], 'seat',
          rot=dict(axis='x', angle=-10, origin=[0.0, 5.0, -7.0]))         # backrest
    m.box([-6.6, 6.0, -8.4], [-5.6, 21.0, -6.2], 'seat', rot=dict(axis='x', angle=-10, origin=[0.0, 5.0, -7.0]))
    m.box([5.6, 6.0, -8.4], [6.6, 21.0, -6.2], 'seat', rot=dict(axis='x', angle=-10, origin=[0.0, 5.0, -7.0]))
    for x in (-2.0, 2.0):
        m.rod(22.0, 24.0, 0.2, 'chrome', cx=x, cz=-10.4)
    m.box([-3.5, 24.0, -11.4], [3.5, 27.5, -9.4], 'seat')                # headrest
    for x in (-5.0, 5.0):
        m.box([x - 0.5, 0.0, -7.5], [x + 0.5, 1.2, 7.5], 'metal')       # runners
        m.box([x - 0.8, 1.2, -6.0], [x + 0.8, 3.0, 6.0], 'dark')
    m.seg((6.5, 3.6), (8.0, 4.4), 0.4, 0.5, 'dark', z=-5.0)              # recline lever


def glovebox(m):
    """A glovebox off the dash: the moulded bin, the door with its grain and the chrome push-button latch, the
    hinge rod along the bottom, the check strap."""
    m.box([-6.0, 0.0, -5.0], [6.0, 4.5, 0.0], 'dark')                    # bin
    m.box([-6.4, -0.2, 0.0], [6.4, 4.8, 0.6], 'body')                   # door
    for y in (1.0, 2.0, 3.0, 4.0):
        m.box([-6.2, y - 0.04, 0.6], [6.2, y + 0.04, 0.62], 'dark')       # grain lines
    m.box([-1.6, 3.4, 0.6], [1.6, 4.3, 0.85], 'chrome')                  # latch
    m.rod(0.85, 1.05, 0.25, 'dark', cx=0.0, cz=3.85, axis=2)
    m.rod(-6.0, 6.0, 0.15, 'metal', cx=-0.1, cz=0.3, axis=0)             # hinge rod
    m.seg((5.6, 3.0), (5.6, 0.5), 0.2, 0.2, 'strap', z=-2.0, plane='xy')
    m.box([-4.0, 0.4, -4.6], [-1.0, 2.0, -2.0], 'paper')                  # owner's manual left in it
    sh.shift_up(m, 0.2)


# ================================================================== reading


def manual(m):
    """A spiral-bound repair manual lying flat: the card cover, the wire coil down the spine, the page block."""
    book(8.5, 11.0, 1.0)(m)
    for z in [-5.0 + i * 0.6 for i in range(17)]:
        m.rod(-4.6, -4.0, 0.22, 'wire', cx=0.5, cz=z, axis=0)


def table(it):
    paint = lambda c: (lambda f: P.paint(f, c, steel_base=0.45, chips=0.35))   # noqa: E731
    return {
        'engine_block': it(engine_block, 'L4', '#5a5854', band='#3a3a38', word_ink='#cfcfc8', cast=CAST,
                           pan=BLACK_PAINT, filter=A.plastic('#1e4a8a'), metal=A.steel, dark=A.plastic('#1a1a1a')),
        'alternator': it(alternator, alu=ALU_CAST, metal=A.steel, brass=P.brass),
        'starter_motor': it(starter_motor, body='#1e1f20', alu=ALU_CAST, metal=A.steel, brass=P.brass),
        'radiator': it(radiator, core=A.flat('#5a5a56'), tank=A.plastic('#1e1f20'), metal=A.alu, brass=P.brass),
        'gearbox': it(gearbox, alu=ALU_CAST, metal=A.steel),
        'fuel_tank': it(fuel_tank, 'GAS', '#2a2a2a', band='#5a5a56', word_ink='#e8e4da', body='#3a3a38',
                        strap=BLACK_PAINT, metal=A.steel, brass=P.brass, wire=A.plastic('#a8281f')),
        'fuel_pump': it(fuel_pump, tank=A.plastic('#2a2a2a'), cloth=A.fabric('#e8e4da'), metal=A.steel,
                        wire=A.steel),
        'exhaust': it(exhaust, 'MUFFLER', '#5a5a56', band='#3a3a38', word_ink='#e8e4da', body='#3a3a38',
                      metal=lambda f: P.steel(f, base=0.4, tint='#7a6a5a', spread=0.3), chrome=A.chrome),
        'steering_rack': it(steering_rack, body='#2a2a2a', alu=ALU_CAST, metal=A.steel),
        'brake_pads': it(brake_pads, body='#5a5854', metal=A.steel, pad=A.flat('#3a3632', 0.2), chrome=A.chrome),
        'suspension': it(suspension, body='#1e1f20', spring=paint('#b02a1f'), metal=A.steel),
        'car_tire': it(car_tire, 'P205/65R15', '#1e1f20', band='#1e1f20', word_ink='#cfcfc8',
                       rubber=lambda f: P.rubber(f), dark=A.flat('#0c0c0c')),
        'wheel_rim': it(wheel_rim, body=None, rim=lambda f: P.paint(f, '#5a5c5e', steel_base=0.5, chips=0.3), rubber=lambda f: P.rubber(f)),
        'headlight': it(headlight, lens=A.glass('#e8f0f0'), rib=A.flat('#c8d4d8'), chrome=A.chrome, metal=A.steel,
                        wire=A.plastic('#1e1f20')),
        'tail_light': it(tail_light, red=A.glass('#c82a1a'), amber=A.glass('#e08a1a'), lens=A.glass('#e8f0f0'),
                         rib=A.flat('#a8281f'), chrome=A.chrome),
        'car_door': it(car_door, panel=paint('#2a4a6a'), glass=A.glass('#9ab4b8'), chrome=A.chrome, metal=A.steel,
                       dent=paint('#22405e')),
        'car_bonnet': it(car_bonnet, panel=paint('#8a2a22'), metal=A.steel, dent=paint('#7a241e')),
        'boot_lid': it(boot_lid, panel=paint('#d8d0b8'), chrome=A.chrome, metal=A.steel),
        'car_horn': it(car_horn, body='#1e1f20', metal=A.steel, brass=P.brass),
        'car_radio': it(car_radio, chrome=A.chrome, dial=A.flat('#e8d8a0'), knob=A.plastic('#1e1f20'),
                        keys=A.plastic('#cfcfc8'), metal=A.steel),
        'car_seat': it(car_seat, seat=lambda f: P.leather(f, base='#3a2a22'), metal=A.steel, chrome=A.chrome),
        'glovebox': it(glovebox, body='#4a3a2a', chrome=A.chrome, metal=A.steel, strap=A.fabric('#1e1f20'),
                       paper=A.paper('#e8e0c8')),
        'auto_manual_standard': it(manual, 'CARS', '#c23a22', 'heap', '#2a2a2a', band=CREAM, wire=A.steel),
        'auto_manual_heavy': it(manual, 'TRUCKS', '#d8a020', 'heap', '#2a2a2a', band=CREAM, wire=A.steel),
        'auto_manual_sport': it(manual, 'SPORTS', '#2a5aa8', 'heap', '#2a2a2a', band=CREAM, wire=A.steel),
        'skillbook_mechanics': it(book(6.0, 9.0, 1.0), 'MECHANICS', '#3a3a3a', 'star', '#e07a1a', band=CREAM),
    }
