"""Wave 4 item models, part two: the military kit (survival roster 'Military & service', FieldGearItem and friends).
Built from the photos in refs/items/military/<id>/. Same conventions as gen_loot.py: inches, standing on y = 0, front
toward +z; table(it) returns the entries.
"""
import math

import art as A
import paint as P
import shapes as sh
import wave3
from wave4 import blob, flat, flat_arc

OD, OD2, TAN, KHAKI = '#4a5236', '#5a6040', '#b89a6a', '#8a7a52'
OD_METAL = lambda f: P.paint(f, OD, steel_base=0.45, chips=0.3)
OD_PLASTIC = A.plastic(OD)


# ================================================================== small kit


def notebook(m):
    """A waterproof field notebook: yellow-grey cover, top spiral, the pencil in its loop."""
    m.box([-2.0, 0, -3.0], [2.0, 0.4, 3.0], 'label')
    for x in [-1.6 + i * 0.4 for i in range(9)]:
        m.rod(x - 0.12, x + 0.12, 0.18, 'wire', cx=0.45, cz=-3.0, axis=0)
    m.rod(-2.4, 2.4, 0.15, 'wood', cx=0.55, cz=2.4, axis=0)
    m.box([2.4, 0.42, 2.25], [2.8, 0.68, 2.55], 'dark')


def signal_panel(m):
    """A folded signal panel: day-glo orange one side, cerise the other, tie tapes at the corners."""
    m.box([-3.5, 0, -2.5], [3.5, 0.5, 2.5], 'body')
    m.box([-3.5, 0.5, -2.5], [3.5, 0.62, 2.5], 'cloth')
    for x, z in ((-3.4, -2.4), (3.4, -2.4), (-3.4, 2.4), (3.4, 2.4)):
        m.box([x - 0.2, 0.1, z - 0.2], [x + 0.2, 0.3, z + 0.2], 'strap')
    m.box([-3.6, 0.15, -0.3], [3.6, 0.4, 0.3], 'strap')


def wire_spool(m):
    """A field wire reel: steel drum and flanges, black twisted wire wound on, the carry handle across."""
    for z in (-1.8, 1.8):
        m.rod(z - 0.15, z + 0.15, 3.0, 'metal', cx=0, cz=3.0, axis=2)
    m.rod(-1.65, 1.65, 2.5, 'wire', cx=0, cz=3.0, axis=2)
    m.rod(-2.2, 2.2, 0.35, 'metal', cx=0, cz=3.0, axis=2)
    m.seg((-2.6, 3.0), (-2.6, 6.6), 0.3, 0.3, 'metal', z=-2.1)
    m.seg((-2.6, 3.0), (-2.6, 6.6), 0.3, 0.3, 'metal', z=2.1)
    m.rod(-2.1, 2.1, 0.3, 'grip', cx=-2.6, cz=6.6, axis=2)
    m.box([-0.2, 0, -2.1], [0.2, 0.3, 2.1], 'metal')


def trip_flare(m):
    """A trip flare: olive canister on its mounting bracket, the trigger lever, the spool of trip wire."""
    m.rod(0.6, 5.6, 0.7, 'body', cx=0, cz=0)
    m.rod(5.6, 6.0, 0.55, 'metal', cx=0, cz=0)
    m.box([-0.15, 3.8, 0.6], [0.15, 6.4, 0.9], 'metal')
    m.box([-1.6, 0, -0.9], [1.6, 0.6, 0.9], 'metal')
    m.box([-0.2, 0.6, -0.3], [0.2, 1.2, 0.3], 'metal')
    m.rod(0.0, 0.5, 0.6, 'wire', cx=2.4, cz=0.8)
    m.box([-1.2, 2.4, -0.71], [1.2, 3.4, -0.69], 'label')


def patch_set(m):
    """A handful of unit patches: arched shields and a tab, embroidered edges."""
    for i, (x, z, c) in enumerate(((-1.6, -0.6, 'body'), (0.9, 0.4, 'lid'), (-0.2, 1.8, 'red'))):
        m.box([x - 1.0, i * 0.06, z - 1.25], [x + 1.0, 0.12 + i * 0.06, z + 1.0], c)
        m.rod(z - 1.25, z + 1.0, 0.05, 'strap', cx=x - 1.0, cz=0.08 + i * 0.06, axis=2)
        m.rod(z - 1.25, z + 1.0, 0.05, 'strap', cx=x + 1.0, cz=0.08 + i * 0.06, axis=2)
        blob(m, (x, 0.08 + i * 0.06, z - 1.25), (1.0, 0.06, 0.5), c)
        m.box([x - 0.5, 0.13 + i * 0.06, z - 0.6], [x + 0.5, 0.15 + i * 0.06, z + 0.4], 'gold')
    m.box([-2.2, 0.3, -2.4], [0.6, 0.42, -1.6], 'dark')


def angle_light(m):
    """The angle-head flashlight: olive body, the head turned at a right angle, belt clip, the red filter cap."""
    m.rod(0, 5.0, 0.75, 'body', cx=0, cz=0)
    m.box([-0.8, 5.0, -0.8], [0.8, 6.4, 0.8], 'body')
    m.rod(0.8, 1.5, 0.75, 'body', cx=0, cz=5.7, axis=2)
    m.rod(1.5, 1.6, 0.6, 'lens', cx=0, cz=5.7, axis=2)
    m.box([-0.5, 1.5, -0.95], [0.5, 4.5, -0.8], 'metal')
    m.box([-0.3, 6.4, -0.3], [0.3, 6.7, 0.3], 'metal')
    m.box([0.8, 3.0, -0.3], [1.0, 3.8, 0.3], 'dark')
    m.rod(0.0, 0.3, 0.7, 'red', cx=1.7, cz=0.0)


def battery_pack(m):
    """A military radio battery: a grey-green steel box, the twin terminals and the label."""
    m.box([-1.7, 0, -1.2], [1.7, 4.4, 1.2], 'body')
    m.box([-1.75, 4.2, -1.25], [1.75, 4.5, 1.25], 'metal')
    for x in (-0.7, 0.7):
        m.rod(4.5, 4.8, 0.35, 'dark', cx=x, cz=0)
    m.box([-1.4, 1.2, 1.2], [1.4, 3.2, 1.22], 'label')


def lensatic(m):
    """A lensatic compass, open: olive case, the dial window, the cover with its sighting wire up, the lens arm."""
    m.box([-1.2, 0, -1.4], [1.2, 0.6, 1.2], 'body')
    m.rod(0.6, 0.65, 1.0, 'dial', cx=0, cz=-0.1)
    m.box([-1.1, 0.6, 1.2], [1.1, 2.6, 1.35], 'body', rot=dict(axis='x', angle=-15, origin=[0, 0.6, 1.2]))
    m.box([-0.05, 0.9, 1.4], [0.05, 2.4, 1.45], 'wire')
    m.box([-0.4, 0.6, -1.9], [0.4, 1.8, -1.75], 'body', rot=dict(axis='x', angle=20, origin=[0, 0.6, -1.8]))
    m.box([-1.4, 0.15, -0.3], [-1.2, 0.45, 0.3], 'metal')
    m.box([-0.5, 0.65, -0.2], [0.5, 0.67, 0.0], 'red')


def topo_maps(m):
    """A set of folded topographic sheets in a clear map case."""
    for i, (dx, c) in enumerate(((-0.2, 'white'), (0.3, 'cloth'), (0.0, 'label'))):
        m.box([-3.5 + dx, i * 0.18, -4.5 + dx], [3.5 + dx, 0.18 + i * 0.18, 4.5 + dx], c)
    m.box([-3.8, 0, -4.8], [3.8, 0.62, 4.8], 'glass')
    m.box([-3.8, 0.0, 4.4], [3.8, 0.66, 4.8], 'strap')


def ghillie(m):
    """A ghillie kit: a rolled mesh base with loose jute strands in browns and greens, tied with cord."""
    m.rod(-4.0, 4.0, 1.8, 'cloth', axis=0)
    for i in range(14):
        a = i * 0.45
        x = -3.6 + i * 0.55
        m.box([x - 0.2, 1.2 + 0.5 * math.sin(a), -2.4 - (i % 3) * 0.3], [x + 0.2, 1.5 + 0.5 * math.sin(a), 2.4 + (i % 2) * 0.4],
              ('lid', 'red', 'body')[i % 3])
    for x in (-2.0, 2.0):
        m.rod(x - 0.25, x + 0.25, 1.9, 'strap', axis=0)
    sh.shift_up(m, 1.8)


def periscope(m):
    """A vehicle periscope block: the angled steel body, the two glass faces, the olive casting."""
    m.box([-3.0, 0, -1.0], [3.0, 2.6, 1.0], 'body')
    m.box([-3.0, 2.6, -1.0], [3.0, 3.8, 0.2], 'body')
    m.box([-2.6, 0.4, 1.0], [2.6, 1.8, 1.05], 'glass')
    m.box([-2.6, 2.8, 0.2], [2.6, 3.6, 0.25], 'glass')
    m.box([-3.2, 1.0, -1.2], [3.2, 1.3, 1.2], 'metal')
    m.box([-2.0, 3.8, -0.8], [2.0, 4.0, 0.0], 'metal')


def binoculars(m):
    """Military binoculars: twin rubber-armoured barrels, the hinge, the eyecups and the neck strap."""
    for x in (-1.2, 1.2):
        m.rod(-2.4, 2.0, 1.0, 'body', cx=x, cz=1.0, axis=2)
        m.rod(2.0, 2.4, 1.05, 'dark', cx=x, cz=1.0, axis=2)
        m.rod(2.4, 2.45, 0.85, 'glass', cx=x, cz=1.0, axis=2)
        m.rod(-3.4, -2.4, 0.6, 'rubber', cx=x, cz=1.0, axis=2)
    m.box([-0.4, 0.6, -1.6], [0.4, 1.4, 1.6], 'body')
    m.rod(-2.0, -1.0, 0.35, 'metal', cx=0, cz=1.6, axis=2)
    m.seg((-2.2, 1.0), (-3.6, 0.2), 0.3, 0.1, 'strap')


def water_bladder(m):
    """A collapsible water bladder, half full: a soft black pillow, the screw cap and the carry handle."""
    blob(m, (0, 1.6, 0), (5.0, 1.6, 4.0), 'body')
    m.rod(3.0, 3.6, 0.7, 'cap', cx=2.5, cz=2.0)
    m.box([-3.0, 3.0, -0.6], [-1.0, 3.6, 0.6], 'strap')


def ration_heater(m):
    """A flameless ration heater: a printed sleeve with the heater pad half out."""
    m.box([-1.8, 0, -3.5], [1.8, 0.4, 3.5], 'label')
    m.box([-1.4, 0.05, 2.0], [1.4, 0.3, 4.8], 'cardboard')
    m.box([-1.4, 0.3, 3.5], [1.4, 0.32, 4.6], 'white')


def blast_bag(m):
    """A folded blast wall bag: a stack of tan geotextile panels held in a steel mesh frame, banded."""
    m.box([-6.0, 0, -4.0], [6.0, 3.0, 4.0], 'cloth')
    for x in (-6.0, -2.0, 2.0, 6.0):
        m.box([x - 0.15, 0, -4.1], [x + 0.15, 3.1, 4.1], 'wire')
    for y in (0.5, 1.5, 2.5):
        m.box([-6.1, y - 0.1, -4.1], [6.1, y + 0.1, 4.1], 'wire')
    m.box([-0.4, 0, -4.2], [0.4, 3.2, 4.2], 'strap')


def guidon(m):
    """A unit guidon, furled: the swallow-tailed flag wrapped round its staff, the spear point, the streamer."""
    m.rod(-12.0, 12.0, 0.35, 'wood', axis=0)
    m.rod(-6.0, 8.0, 0.9, 'cloth', axis=0)
    m.rod(-2.0, 2.0, 0.95, 'red', axis=0)
    m.rod(12.0, 13.0, 0.4, 'gold', axis=0)
    m.seg((13.0, 0.0), (14.4, 0.0), 0.5, 0.4, 'gold')
    m.rod(-12.6, -12.0, 0.4, 'gold', axis=0)
    m.rod(8.0, 8.6, 0.97, 'strap', axis=0)
    sh.shift_up(m, 0.95)


def field_manual(m):
    """A survival field manual: small olive softback, the stencilled title and a dog-eared corner."""
    m.box([-2.3, 0, -3.3], [2.3, 0.5, 3.3], 'white')
    m.box([-2.4, 0.5, -3.4], [2.4, 0.58, 3.4], 'label')
    m.box([-2.4, 0, -3.4], [2.4, 0.08, 3.4], 'body')
    m.box([-2.4, 0, -3.4], [-2.2, 0.58, 3.4], 'body')
    m.box([1.5, 0.58, 2.6], [2.4, 0.6, 3.4], 'white')


def footlocker(m):
    """An army footlocker: the olive plywood box with steel corners, the hasp, the handles and stencilled name."""
    m.box([-9.0, 0, -5.5], [9.0, 10.0, 5.5], 'body')
    m.box([-9.1, 8.0, -5.6], [9.1, 10.4, 5.6], 'body')
    for x in (-9.0, 9.0):
        for z in (-5.5, 5.5):
            m.box([x - 0.6, 0, z - 0.6], [x + 0.6, 10.4, z + 0.6], 'metal')
    m.box([-0.6, 6.6, 5.5], [0.6, 8.6, 5.75], 'metal')
    for x in (-9.3, 9.3):
        m.box([x - 0.2, 6.0, -1.5], [x + 0.2, 6.6, 1.5], 'dark')
    m.box([-6.0, 3.0, 5.5], [6.0, 5.5, 5.52], 'label')


def field_radio(m):
    """A manpack field radio: the olive steel case, the front panel of knobs, the handset on its curly cord,
    the antenna base and the whip folded alongside."""
    m.box([-4.5, 0, -2.0], [4.5, 10.0, 2.0], 'body')
    m.box([-4.6, 10.0, -2.1], [4.6, 11.0, 2.1], 'metal')
    for x, y in ((-2.5, 10.4), (0.0, 10.4), (2.5, 10.4)):
        m.rod(11.0, 11.7, 0.55, 'dark', cx=x, cz=0)
    m.rod(11.0, 12.2, 0.4, 'metal', cx=3.6, cz=1.2)
    m.box([-4.6, 2.0, 2.0], [4.6, 2.4, 2.15], 'metal')
    m.box([-3.6, 4.0, 2.0], [3.6, 8.0, 2.05], 'label')
    m.seg((3.6, 12.2), (-9.0, 9.0), 0.3, 0.3, 'dark', z=1.4)
    blob(m, (5.6, 1.2, 0.0), (0.6, 1.2, 0.8), 'dark')
    m.box([5.0, 0.0, -0.6], [6.2, 2.4, 0.6], 'dark')
    m.seg((5.6, 2.4), (4.5, 6.0), 0.25, 0.25, 'dark', z=-1.6)


def rangefinder(m):
    """A handheld laser rangefinder: the olive body, two big objective lenses, the eyepiece and the fire button."""
    m.box([-3.0, 0, -2.4], [3.0, 3.2, 2.4], 'body')
    for x in (-1.4, 1.4):
        m.rod(2.4, 3.0, 1.0, 'dark', cx=x, cz=1.6, axis=2)
        m.rod(3.0, 3.05, 0.8, 'glass', cx=x, cz=1.6, axis=2)
    m.rod(-3.4, -2.4, 0.6, 'rubber', cx=-1.4, cz=1.6, axis=2)
    m.rod(3.2, 3.5, 0.4, 'red', cx=1.6, cz=-1.0)
    m.box([-2.8, 0.4, -2.6], [2.8, 0.8, -2.4], 'strap')


def detector(m):
    """A metal detector lying down: the search coil, the telescopic shaft, the control box, grip and arm cuff."""
    blob(m, (-9.0, 0.3, 0), (3.2, 0.3, 2.4), 'body')
    m.box([-6.5, 0.3, -0.3], [-5.5, 1.0, 0.3], 'dark')
    m.seg((-6.0, 0.9), (10.0, 2.4), 0.5, 0.5, 'alu')
    m.box([4.0, 2.2, -1.0], [8.0, 3.6, 1.0], 'body')
    m.box([4.6, 3.6, -0.6], [7.0, 3.65, 0.6], 'dark')
    m.rod(8.0, 10.5, 0.45, 'grip', cx=2.7, cz=0, axis=0)
    m.box([11.0, 2.4, -1.4], [12.2, 4.0, 1.4], 'body')


def door_ram(m):
    """A hydraulic door ram: the spreader jaws, the steel cylinder, the pump handle and the red hose."""
    m.rod(-6.0, 4.0, 1.0, 'metal', axis=0)
    m.box([4.0, -0.9, -1.2], [7.0, 0.9, 1.2], 'body')
    m.box([7.0, -1.0, -1.2], [8.2, 0.0, 1.2], 'metal', rot=dict(axis='z', angle=-12, origin=[7.0, 0, 0]))
    m.box([7.0, 0.0, -1.2], [8.2, 1.0, 1.2], 'metal', rot=dict(axis='z', angle=12, origin=[7.0, 0, 0]))
    m.box([-7.5, -1.2, -1.4], [-6.0, 1.2, 1.4], 'body')
    m.seg((-7.5, 0.0), (-10.0, 3.0), 0.5, 0.5, 'grip')
    flat_arc(m, (-4.0, -3.0), 2.0, 0, 300, 0.35, 0.35, 'red', y0=-1.0, steps=10)
    sh.shift_up(m, 1.4)


def quiet_gen(m):
    """A tactical quiet generator: a sand-coloured box on skids, louvres, the control panel, lifting eyes."""
    m.box([-10.0, 1.0, -7.0], [10.0, 15.0, 7.0], 'body')
    for z in (-6.0, 6.0):
        m.box([-11.0, 0, z - 0.6], [11.0, 1.0, z + 0.6], 'metal')
    for y in range(4, 13, 2):
        m.box([-9.0, y, 7.0], [-1.0, y + 0.6, 7.2], 'dark')
    m.box([1.5, 5.0, 7.0], [8.5, 13.0, 7.15], 'dark')
    for x, y in ((3.0, 11.0), (5.0, 11.0), (7.0, 11.0)):
        m.rod(7.15, 7.5, 0.5, 'red' if x == 5.0 else 'metal', cx=x, cz=y, axis=2)
    m.box([2.0, 6.0, 7.15], [8.0, 9.0, 7.2], 'label')
    for x in (-8.0, 8.0):
        m.rod(15.0, 16.0, 0.6, 'metal', cx=x, cz=0)


def sensor_kit(m):
    """A ground sensor kit: the sensor spike, the transmitter box with its whip antenna, and the coil of cable."""
    m.box([-3.0, 0, -2.0], [1.0, 2.6, 2.0], 'body')
    m.rod(2.6, 9.0, 0.12, 'dark', cx=-2.4, cz=-1.4)
    m.box([-2.6, 2.6, -1.6], [-2.2, 2.9, -1.2], 'dark')
    m.rod(0, 3.4, 0.5, 'body', cx=3.2, cz=0.0)
    m.box([2.7, 3.4, -0.5], [3.7, 3.8, 0.5], 'dark')
    flat_arc(m, (1.0, 2.8), 1.4, 0, 340, 0.25, 0.25, 'dark', y0=0.0, steps=10)
    m.box([-2.8, 1.0, 2.0], [0.8, 2.0, 2.05], 'label')


def floodlight(m):
    """A tactical floodlight, folded: the light head with its cage, the tripod legs clamped beside, the cable."""
    m.box([-3.0, 0, -2.0], [3.0, 5.0, 2.0], 'body')
    m.box([-2.6, 0.4, 2.0], [2.6, 4.6, 2.05], 'lens')
    for x in (-1.5, 0.0, 1.5):
        m.box([x - 0.08, 0.4, 2.05], [x + 0.08, 4.6, 2.3], 'metal')
    m.box([-3.4, 2.0, -2.4], [3.4, 2.6, 2.4], 'metal')
    for z in (-2.8, -2.5):
        m.rod(-8.0, 8.0, 0.3, 'alu', cx=0.3, cz=z, axis=0)
    flat_arc(m, (5.0, 0.0), 1.2, 0, 330, 0.3, 0.3, 'dark', y0=0.0, steps=10)


def codebook(m):
    """A codebook: a red-bordered softback, SECRET across the cover, its tear-off pages and a lead-weighted spine."""
    m.box([-2.5, 0, -3.5], [2.5, 0.6, 3.5], 'white')
    m.box([-2.6, 0.6, -3.6], [2.6, 0.68, 3.6], 'label')
    m.box([-2.6, 0, -3.6], [-2.3, 0.68, 3.6], 'dark')
    m.box([-2.6, 0, -3.6], [2.6, 0.06, 3.6], 'label')


def overlay(m):
    """A map overlay sheet in its case: clear acetate with grease-pencil marks, folded over a map."""
    m.box([-4.0, 0, -5.0], [4.0, 0.3, 5.0], 'cloth')
    m.box([-4.1, 0.3, -5.1], [4.1, 0.34, 5.1], 'glass')
    for (x0, z0), (x1, z1) in (((-3.0, -3.0), (1.0, 2.0)), ((1.0, 2.0), (3.0, -1.0)), ((-2.0, 3.5), (2.5, 3.0))):
        flat((m), (x0, z0), (x1, z1), 0.15, 0.03, 'red', y0=0.34)
    for x, z in ((-3.0, -3.0), (1.0, 2.0), (3.0, -1.0)):
        m.box([x - 0.3, 0.34, z - 0.3], [x + 0.3, 0.38, z + 0.3], 'dark')


def weapons_locker(m):
    """A steel weapons locker: tall olive cabinet, louvred doors, the hasp and padlock, a rifle rack inside the
    open door."""
    m.box([-8.0, 0, -6.0], [8.0, 34.0, 6.0], 'body')
    m.box([-7.6, 1.0, 6.0], [-0.1, 33.0, 6.2], 'lid')
    m.box([0.1, 1.0, 6.0], [7.6, 33.0, 6.2], 'lid')
    for y in (28.0, 29.0, 30.0, 3.0, 4.0, 5.0):
        for x0, x1 in ((-6.0, -2.0), (2.0, 6.0)):
            m.box([x0, y, 6.2], [x1, y + 0.3, 6.3], 'dark')
    m.box([-0.3, 16.0, 6.2], [0.3, 18.0, 6.5], 'metal')
    m.box([-0.5, 14.6, 6.4], [0.5, 15.8, 6.8], 'gold')


def chute(m):
    """A packed parachute: the olive canopy pack, the harness straps folded over, the ripcord handle."""
    m.box([-6.0, 0, -4.0], [6.0, 5.0, 4.0], 'body')
    for x in (-3.0, 3.0):
        m.box([x - 0.6, 0, -4.1], [x + 0.6, 5.1, 4.1], 'strap')
    m.box([-6.1, 2.2, -4.1], [6.1, 2.8, 4.1], 'strap')
    m.box([-1.0, 5.0, -1.0], [1.0, 5.4, 1.0], 'metal')
    m.box([2.0, 5.0, 1.5], [4.0, 5.6, 2.3], 'red')


def grapple(m):
    """A grappling hook with a rope ladder: three tines, the shank, and the ladder rolled up with its rungs."""
    m.rod(0, 4.0, 0.35, 'metal', cx=-6.0, cz=0)
    for a in (0, 120, 240):
        c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
        m.seg((-6.0, 0.6), (-6.0 + 2.4 * c, 0.2), 0.3, 0.3, 'metal', z=2.4 * s)
        m.box([-6.0 + 2.4 * c - 0.15, 0.2, 2.4 * s - 0.15], [-6.0 + 2.4 * c + 0.15, 1.4, 2.4 * s + 0.15], 'metal')
    m.rod(3.6, 4.4, 0.5, 'metal', cx=-6.0, cz=0)
    m.rod(-3.0, 6.0, 1.8, 'cloth', axis=0)
    for x in range(-2, 6, 2):
        m.rod(x - 0.2, x + 0.2, 1.85, 'wood', axis=0)
    m.seg((-6.0, 4.0), (-3.0, 1.8), 0.25, 0.25, 'cloth')
    sh.shift_up(m, 0.0)


def thermal(m):
    """A thermal monocular: the black rubber body, the germanium lens up front, the eyecup, the buttons."""
    m.box([-1.4, 0, -2.6], [1.4, 2.6, 2.0], 'body')
    m.rod(2.0, 3.2, 1.2, 'dark', cx=0, cz=1.3, axis=2)
    m.rod(3.2, 3.25, 1.0, 'lens', cx=0, cz=1.3, axis=2)
    m.rod(-3.6, -2.6, 0.7, 'rubber', cx=0, cz=1.4, axis=2)
    for z in (-1.4, -0.6, 0.2):
        m.box([-0.25, 2.6, z - 0.25], [0.25, 2.8, z + 0.25], 'metal')


def drone(m):
    """A hand-launched recon drone: the white fuselage, the long wing, the tailplane and the pusher prop."""
    m.rod(-7.0, 7.0, 0.8, 'body', axis=0)
    blob(m, (7.0, 0.8, 0), (1.2, 0.8, 0.8), 'body', axis=0)
    m.box([-0.5, 1.4, -12.0], [2.5, 1.7, 12.0], 'body')
    m.box([-8.0, 1.2, -3.5], [-6.6, 1.4, 3.5], 'body')
    m.box([-8.0, 1.4, -0.12], [-6.6, 3.6, 0.12], 'body')
    m.box([-7.6, 0.4, -2.0], [-7.4, 1.2, 2.0], 'dark')
    m.box([7.6, 0.3, -0.4], [8.0, 0.8, 0.4], 'lens')
    for z in (-6.0, 6.0):
        m.box([0.0, 1.7, z - 0.2], [2.0, 1.72, z + 0.2], 'red')
    sh.shift_up(m, 0.1)


def map_table(m):
    """A field map table: the folding plywood top with a map pinned under acetate, steel legs, the lamp clamp."""
    m.box([-14.0, 28.0, -9.0], [14.0, 29.5, 9.0], 'wood')
    m.box([-13.0, 29.5, -8.0], [13.0, 29.6, 8.0], 'label')
    m.box([-13.2, 29.6, -8.2], [13.2, 29.65, 8.2], 'glass')
    for x in (-12.5, 12.5):
        for z in (-7.5, 7.5):
            m.rod(0, 28.0, 0.5, 'metal', cx=x, cz=z)
    m.seg((-12.5, 6.0), (12.5, 6.0), 0.4, 0.4, 'metal', z=-7.5)
    m.rod(29.6, 34.0, 0.3, 'metal', cx=12.0, cz=-7.0)
    m.box([10.0, 33.6, -8.0], [12.4, 35.0, -6.0], 'body')
    for x, z in ((-6.0, -3.0), (3.0, 2.0), (7.0, -4.0)):
        m.rod(29.65, 30.3, 0.3, 'red', cx=x, cz=z)


def sat_terminal(m):
    """A rugged satellite terminal: the hard case open, the folding flat antenna panel up, the handset and screen."""
    m.box([-6.0, 0, -4.0], [6.0, 3.0, 4.0], 'body')
    m.box([-6.0, 3.0, -4.6], [6.0, 11.0, -3.8], 'lid', rot=dict(axis='x', angle=-15, origin=[0, 3.0, -4.0]))
    m.box([-5.4, 3.6, -3.8], [5.4, 10.4, -3.7], 'dark', rot=dict(axis='x', angle=-15, origin=[0, 3.0, -4.0]))
    m.box([-5.0, 3.0, -3.0], [1.0, 3.1, 2.0], 'keys')
    m.box([2.0, 3.0, -3.0], [5.0, 3.1, 0.0], 'lens')
    blob(m, (3.5, 3.6, 2.2), (1.8, 0.5, 0.6), 'dark', axis=0)


def drum(m):
    """A regimental drum: the painted shell with the unit's crest, the rope tensioners zigzagging between the
    hoops, the drag ropes, the sticks crossed on top."""
    m.rod(0.0, 9.0, 5.5, 'label')
    for y in (0.0, 8.0):
        m.rod(y, y + 1.0, 5.8, 'red')
    for i in range(12):
        a0, a1 = math.radians(i * 30), math.radians(i * 30 + 15)
        x0, z0, x1, z1 = 5.85 * math.cos(a0), 5.85 * math.sin(a0), 5.85 * math.cos(a1), 5.85 * math.sin(a1)
        m.box([min(x0, x1) - 0.1, 1.0, min(z0, z1) - 0.1], [max(x0, x1) + 0.1, 8.0, max(z0, z1) + 0.1], 'white')
    m.rod(9.0, 9.05, 5.3, 'cloth')
    for a in (30, -30):
        flat(m, (-5.0 * math.cos(math.radians(a)), -5.0 * math.sin(math.radians(a))),
             (5.0 * math.cos(math.radians(a)), 5.0 * math.sin(math.radians(a))), 0.4, 0.4, 'wood', y0=9.05)


def blueprints(m):
    """Bunker blueprints: a roll of cyan sheets and one spread flat, pinned with a red stamp."""
    m.box([-5.0, 0, -3.5], [5.0, 0.06, 3.5], 'label')
    m.rod(-5.5, 5.5, 0.8, 'label', cx=0.8, cz=-4.4, axis=0)
    m.rod(-5.6, 5.6, 0.3, 'white', cx=0.8, cz=-4.4, axis=0)
    m.box([2.6, 0.06, 1.6], [4.0, 0.1, 2.8], 'red')


def shell_lamp(m):
    """A shell casing lamp: a polished brass artillery case as the stand, a pleated shade, the switch."""
    m.rod(0.0, 0.6, 2.4, 'brass')
    m.rod(0.6, 10.0, 1.8, 'brass')
    m.rod(10.0, 11.0, 1.4, 'brass')
    m.rod(11.0, 13.0, 0.3, 'brass')
    m.rod(12.0, 18.0, 4.2, 'cloth')
    m.rod(18.0, 18.2, 3.8, 'cloth')
    m.box([1.8, 4.0, -0.3], [2.1, 4.6, 0.3], 'dark')
    m.box([-1.0, 4.0, 1.75], [1.0, 6.0, 1.82], 'gold')


def ejection_seat(m):
    """An ejection seat: the rail frame, the padded seat and back, the yellow-and-black firing handle between the
    knees and the head box with its warning stripes."""
    m.box([-6.0, 0, -6.0], [6.0, 12.0, 6.0], 'body')
    m.box([-5.0, 12.0, -5.0], [5.0, 14.0, 5.0], 'cloth')
    m.box([-6.0, 12.0, -6.0], [6.0, 36.0, -3.0], 'body')
    m.box([-5.0, 14.0, -3.0], [5.0, 34.0, -2.0], 'cloth')
    m.box([-5.5, 36.0, -6.5], [5.5, 42.0, -1.0], 'body')
    m.box([-5.6, 38.0, -1.0], [5.6, 40.0, -0.9], 'lid')
    for x in (-6.5, 6.5):
        m.box([x - 0.5, 0, -6.5], [x + 0.5, 44.0, -5.5], 'metal')
    m.seg((0.0, 12.0), (0.0, 14.6), 0.6, 0.6, 'lid', z=5.5)
    m.box([-1.6, 14.4, 5.1], [1.6, 15.0, 5.9], 'lid')
    for x in (-3.0, 3.0):
        m.box([x - 0.6, 14.0, -3.0], [x + 0.6, 30.0, -1.8], 'strap')


def launch_keys(m):
    """Two launch keys on a steel ring with the brass tag, in an opened clear sealed sleeve."""
    m.box([-2.5, 0, -1.6], [2.5, 0.1, 3.4], 'glass')
    flat_arc(m, (-0.6, 0.0), 0.7, 0, 360, 0.12, 0.12, 'metal', y0=0.1, steps=12)
    for z, x in ((-0.3, 0.4), (0.4, 0.2)):
        flat(m, (x, z), (x + 2.6, z + 0.9), 0.5, 0.14, 'metal', y0=0.12)
        m.box([x - 0.2, 0.12, z - 0.5], [x + 0.6, 0.3, z + 0.5], 'red')
    m.box([-2.2, 0.1, 0.8], [-0.8, 0.18, 1.8], 'brass')


def cipher(m):
    """A rotor cipher machine in its wooden box: the keyboard, the lamp board, three rotors, the plugboard."""
    m.box([-6.0, 0, -5.0], [6.0, 5.0, 5.0], 'wood')
    m.box([-5.5, 5.0, -4.5], [5.5, 5.2, 4.5], 'dark')
    for row, (z, n) in enumerate(((2.8, 9), (1.8, 8), (0.8, 9))):
        for i in range(n):
            x = -4.4 + i * 1.1 + (0.5 if n == 8 else 0)
            m.rod(5.2, 5.7, 0.38, 'keys', cx=x, cz=z)
            m.rod(5.7, 5.75, 0.32, 'white', cx=x, cz=z)
    for row, z in enumerate((-0.8, -1.8)):
        for i in range(9):
            m.rod(5.2, 5.35, 0.36, 'lens', cx=-4.4 + i * 1.1, cz=z)
    for x in (-1.2, 0.0, 1.2):
        m.box([x - 0.45, 5.2, -4.4], [x + 0.45, 6.4, -3.0], 'metal')
    m.box([-5.5, 1.0, 5.0], [5.5, 4.0, 5.05], 'dark')
    m.box([-6.1, 5.0, -5.0], [6.1, 5.4, -4.6], 'wood')


def camo_net(m):
    """A bundled camouflage net: the green and brown scrim folded into a fat roll and tied."""
    m.rod(-6.0, 6.0, 2.6, 'cloth', axis=0)
    for i in range(18):
        a = i * 20
        x = -5.5 + (i * 0.65)
        m.box([x - 0.3, 2.2 + 0.4 * math.sin(math.radians(a * 3)), -2.9], [x + 0.3, 2.9, 2.9], ('lid', 'body', 'red')[i % 3])
    for x in (-3.5, 3.5):
        m.rod(x - 0.3, x + 0.3, 2.7, 'strap', axis=0)
    sh.shift_up(m, 2.6)


def cot(m):
    """A folding army cot, folded: the canvas wrapped round the wooden side rails, the steel leg bows."""
    for z in (-1.0, 1.0):
        m.rod(-18.0, 18.0, 0.6, 'wood', cx=0.6, cz=z, axis=0)
    m.rod(-16.0, 16.0, 1.3, 'cloth', cx=0.9, cz=0.0, axis=0)
    for x in (-15.0, -5.0, 5.0, 15.0):
        flat(m, (x, -1.6), (x, 1.6), 0.25, 1.8, 'metal', y0=0.0)
    m.box([-2.0, 1.8, -1.4], [2.0, 2.4, 1.4], 'label')


def table(it):
    return {
        'waterproof_notebook': it(notebook, 'ALL WEATHER', '#d8c060', band='#3a3a2a', word_ink='#1e1f20'),
        'signal_panel': it(signal_panel, body='#f06a20', cloth=A.fabric('#e0306a'), strap=A.fabric('#2a2a2a')),
        'field_wire_spool': it(wire_spool, metal=OD_METAL, wire=A.flat('#2a2a28', 0.2)),
        'tripwire_flare': it(trip_flare, 'TRIP FLARE', OD, band=OD2, word_ink='#e8e0a0', body=OD, metal=OD_METAL,
                             wire=A.flat('#8a8a80')),
        'unit_patch_set': it(patch_set, body='#e86a1a', lid=A.flat('#2a3a7a'), red=A.flat('#7a1a2a'), gold=wave3.GOLD,
                             dark=A.fabric('#4a5236'), strap=A.fabric('#1e1f20')),
        'angle_flashlight': it(angle_light, body=OD, metal=OD_METAL, lens=A.flat('#f0ecd0'), red=A.plastic('#b02a1a')),
        'radio_battery_pack': it(battery_pack, 'BA-5590', '#c8c8b8', band='#2a2a2a', word_ink='#1e1f20', body='#6a6e60',
                                 metal=A.alu),
        'lensatic_compass': it(lensatic, body=OD, dial=A.flat('#e8e4d0'), wire=A.steel),
        'topo_map_set': it(topo_maps, 'TOPOGRAPHIC', '#d8d0a8', band='#4a7a3a', word_ink='#2a2a1a',
                           cloth=A.paper('#c8d8b0'), white=A.paper('#e8e0c8'), strap=A.fabric(OD)),
        'ghillie_kit': it(ghillie, cloth=A.fabric('#5a5a3a'), lid=A.fabric('#6a7040'), red=A.fabric('#7a6040'),
                          body='#4a4a30', strap=A.fabric('#3a3a2a')),
        'periscope': it(periscope, body=OD, metal=OD_METAL, tint='#7a9aa0'),
        'military_binoculars': it(binoculars, body=OD, tint='#6a8a90', strap=A.fabric('#2a2a2a')),
        'water_bladder': it(water_bladder, body='#1e1f20', cap=OD, strap=A.fabric(OD)),
        'ration_heater': it(ration_heater, 'HEATER', '#c8b888', band='#5a4a2a', word_ink='#2a2a1a'),
        'blast_wall_bag': it(blast_bag, cloth=A.fabric(TAN), wire=A.steel, strap=A.fabric('#2a2a2a')),
        'unit_flag': it(guidon, cloth=A.fabric('#1e2a6a'), red=A.fabric('#a8281f'), gold=wave3.BRASS,
                        strap=A.fabric('#e8c040')),
        'survival_field_manual': it(field_manual, 'SURVIVAL', OD, band=OD2, word_ink='#1e1f20', body=OD,
                                    white=A.paper('#e8e0c8')),
        'footlocker': it(footlocker, 'PVT. HALE', OD, band=OD, word_ink='#e8e4da', body=OD, metal=A.steel),
        'field_radio': it(field_radio, 'RT-841', OD, band=OD2, word_ink='#e8e4da', body=OD, metal=OD_METAL),
        'laser_rangefinder': it(rangefinder, body=OD, tint='#4a6a80', red=A.plastic('#b02a1a'),
                                strap=A.fabric('#2a2a2a')),
        'metal_detector': it(detector, body='#2a2a2a', alu=A.alu),
        'door_ram': it(door_ram, body='#d8a020', metal=A.steel, red=A.plastic('#b02a1a')),
        'quiet_generator': it(quiet_gen, 'TQG 3KW', TAN, band='#5a4a2a', word_ink='#1e1f20', body=TAN, metal=A.steel),
        'motion_sensor_kit': it(sensor_kit, 'SENSOR', OD, band=OD2, word_ink='#e8e4da', body=OD),
        'tactical_floodlight': it(floodlight, body='#d8a020', lens=A.flat('#f4f0d8'), alu=A.alu),
        'codebook': it(codebook, 'SECRET', '#e8e0c8', band='#a8281f', word_ink='#a8281f', white=A.paper('#efe8d6')),
        'checkpoint_overlay': it(overlay, cloth=A.paper('#c8d8b0'), tint='#e8f0f0', red=A.flat('#c82a1a')),
        'weapons_locker': it(weapons_locker, body=OD, lid=OD_METAL, gold=wave3.BRASS),
        'parachute': it(chute, body=OD, strap=A.fabric('#6a6040'), red=A.plastic('#b02a1a')),
        'grappling_ladder': it(grapple, cloth=A.fabric('#6a6a52'), metal=A.steel),
        'thermal_monocular': it(thermal, body='#1e1f20', lens=A.flat('#3a3a50')),
        'recon_drone': it(drone, body='#d8d8d0', lens=A.flat('#2a3a4a'), red=A.flat('#c82a1a')),
        'map_table': it(map_table, 'AO NORTH', '#c8d8b0', band='#4a7a3a', word_ink='#2a2a1a', body=OD, tint='#e8f0f0'),
        'satellite_terminal': it(sat_terminal, body='#4a4a42', lid=A.plastic('#5a5a50'), lens=A.flat('#60a0c0')),
        'regimental_drum': it(drum, 'XIV', '#1e2a6a', 'star', '#e8c040', band='#1e2a6a', word_ink='#e8c040',
                              red=A.flat('#a8281f'), white=A.fabric('#efeae0'), cloth=A.flat('#efe6d0')),
        'bunker_blueprints': it(blueprints, 'SHELTER B', '#2a5aa8', band='#2a5aa8', word_ink='#e8f0f0',
                                white=A.paper('#e8e0c8'), red=A.flat('#c82a1a')),
        'shell_casing_lamp': it(shell_lamp, cloth=A.fabric('#d8c8a0'), brass=wave3.BRASS, gold=wave3.GOLD),
        'ejection_seat': it(ejection_seat, body='#2a2a2a', cloth=A.fabric('#4a5236'),
                            lid=lambda f: P.paint(f, '#e8c020', steel_base=0.2, chips=0.2), strap=A.fabric('#4a4a3a')),
        'launch_keys': it(launch_keys, tint='#e8f0f0', red=A.plastic('#b02a1a'), brass=wave3.BRASS),
        'cipher_machine': it(cipher, keys=A.plastic('#1e1f20'), lens=A.flat('#e8e0b0'), metal=A.steel),
        'camo_netting': it(camo_net, cloth=A.fabric('#4a5236'), lid=A.fabric('#6a5a3a'), body='#3a4a2a',
                           red=A.fabric('#5a6a3a')),
        'folding_cot': it(cot, 'U.S.', OD, band=OD, word_ink='#e8e4da', cloth=A.fabric(OD)),
    }
