# Hand-made reloads, one per gun. Times in seconds. Gun keys: r = [pitch, yaw, roll] deg in the gun's own axes about its
# grip, d = metres in the gun's axes (x right, y up, z back). Hand keys: k = rest | gun | body; gun keys put the palm at a
# point p of the gun (its px) with fingers along fwd and the palm facing palm (gun axes); body keys move from the rest
# palm by d (rig axes). Per-view overrides under 'fp' / 'tp'.
REST = {'k': 'rest'}
POUCH_L = {'k': 'body', 'fp': {'d': [-0.10, -0.55, 0.20]}, 'tp': {'p': [0.19, 0.98, 0.07], 'fwd': [0, -1, 0.3], 'palm': [-1, 0, 0]}, 'curl': 25}
# third person: the gun brought up in front of her for the reload (grip, muzzle direction, gun up; her model space)
TP_UP_RIFLE = {'r': [0, 0, 0], 'd': [0, 0, 0], 'abs': {'grip': [-0.06, 1.2, 0.12], 'fwd': [0.85, -0.3, 0.42], 'up': [0.25, 1, 0.1]}}
TP_UP_PISTOL = {'r': [0, 0, 0], 'd': [0, 0, 0], 'abs': {'grip': [0.04, 1.1, 0.3], 'fwd': [0.45, 0.35, 0.8], 'up': [0.45, 0.8, -0.2]}}
SPECS = {}

# AR-57: P90-style magazine lying on top. Thumb the release at its back, lift the back up, pull it off backward;
# a new one goes in front first, the back pressed down till it clicks; then the charging handle.
MAG57_TOP = [8.0, 9.75, 12.6]
SPECS['AR57_PDW'] = {
    'dur': 3.2,
    'parts': {'mag': {'sel': [{'part': 'mag'}]}, 'mag2': {'clone': 'mag', 'hidden': True}},
    'gun': [(0, {}), (0.35, {'r': [18, 8, -40], 'd': [-0.04, -0.03, -0.1], 'tp': TP_UP_RIFLE}), (2.55, {'r': [18, 8, -40], 'd': [-0.04, -0.03, -0.1], 'tp': TP_UP_RIFLE}), (2.75, {'r': [8, 4, -14]}), (3.2, {})],
    'L': [(0, REST),
          (0.35, {'k': 'gun', 'p': [8.0, 10.6, 13.5], 'fwd': [1, 0, -0.3], 'palm': [0, -1, 0], 'curl': -15}),
          (0.55, {'k': 'gun', 'p': MAG57_TOP, 'fwd': [1, 0, -0.3], 'palm': [0, -1, 0], 'curl': 20}),
          (0.8, {'k': 'gun', 'p': [7.6, 10.8, 13.6], 'fwd': [1, 0.2, -0.3], 'palm': [0, -1, 0.3], 'curl': 20}),
          (1.0, {'k': 'gun', 'p': [6.0, 11.0, 15.0], 'fwd': [1, 0.2, -0.3], 'palm': [0, -1, 0.3], 'curl': 20}),
          (1.45, POUCH_L), (1.6, POUCH_L),
          (2.0, {'k': 'gun', 'p': [8.0, 11.0, 12.0], 'fwd': [1, 0.2, -0.3], 'palm': [0, -1, 0.3], 'curl': 20}),
          (2.25, {'k': 'gun', 'p': MAG57_TOP, 'fwd': [1, 0, -0.3], 'palm': [0, -1, 0], 'curl': 20}),
          (2.4, {'k': 'gun', 'p': [8.0, 10.6, 12.6], 'fwd': [1, 0, -0.3], 'palm': [0, -1, 0], 'curl': -10}),
          (2.75, REST), (3.2, REST)],
    'moves': {'mag': [(0.55, {}), (0.8, {'r': [-8, 0, 0], 'd': [0, 0.4, 0.9]})],
              'mag2': [(0, {})]},
    'events': [(0.55, 'mag', 'grab', 'L'), (1.0, 'mag', 'drop', [250, 0, 60]), (1.6, 'mag2', 'show', 'L', 'mag'), (2.25, 'mag2', 'seat'), (1.6, 'mag', 'hide')],
}

def mag_prims(bottom, axis, length, depth, width, col=(48, 48, 54), plate=(30, 30, 33)):
    import numpy as np
    a = np.array(axis, float); a /= np.linalg.norm(a); b = np.array(bottom, float)
    return [{'t': 'box', 'c': list(b + a * length / 2), 's': [width, depth, length], 'axis': list(a), 'col': col},
            {'t': 'box', 'c': list(b - a * 0.08), 's': [width * 1.3, depth * 1.2, 0.18], 'axis': list(a), 'col': plate}]

# Colt 1911: empty, so the slide is locked back. Release: the empty mag drops free, a new one from the belt pushed
# up the grip, then her thumb drops the slide stop and the slide runs home.
AX1911 = [0, 1, -0.25]
G1911 = {'r': [5, 50, -25], 'd': [-0.04, 0.1, -0.2], 'tp': TP_UP_PISTOL}
SPECS['Colt1911'] = {
    'dur': 2.0,
    'parts': {'slide': {'sel': [{'part': 'body', 'comps': [6, 3, 0, 1, 7, 8, 5]}]}},
    'extra': {'mag': {'prims': mag_prims([8, 6.25, 10.62], AX1911, 2.6, 0.85, 0.3)},
              'mag2': {'prims': mag_prims([8, 6.25, 10.62], AX1911, 2.6, 0.85, 0.3), 'hidden': True}},
    'gun': [(0, {}), (0.3, G1911), (1.3, G1911), (1.38, {**G1911, 'r': [9, 50, -25]}), (1.5, G1911), (1.8, {}), (2.0, {})],
    'L': [(0, REST), (0.25, {'k': 'body', 'fp': {'d': [-0.05, -0.12, 0.05]}, 'tp': {'d': [0.06, -0.1, 0.0]}, 'curl': 10}),
          (0.6, POUCH_L), (0.75, POUCH_L),
          (1.0, {'k': 'gun', 'p': [8.0, 4.2, 11.1], 'fwd': [1, 0, -0.2], 'palm': [0, 1, 0], 'curl': 30}),
          (1.2, {'k': 'gun', 'p': [8.0, 5.95, 10.65], 'fwd': [1, 0, -0.2], 'palm': [0, 1, 0], 'curl': 30}),
          (1.28, {'k': 'gun', 'p': [8.0, 5.7, 10.65], 'fwd': [1, 0, -0.2], 'palm': [0, 1, 0], 'curl': 10}),
          (1.7, REST), (2.0, REST)],
    'R': [(0, REST), (1.25, REST), (1.32, {'k': 'rest', 'curl': -12}), (1.4, REST), (2.0, REST)],
    'moves': {'slide': [(0, {'d': [0, 0, 1.05]}), (1.33, {'d': [0, 0, 1.05]}), (1.37, {})],
              'mag': [(0.28, {}), (0.36, {'d': [0, -0.45, 0.11]})]},
    'events': [(0.36, 'mag', 'drop', [40, 0, 120]), (0.75, 'mag2', 'show', 'L', '@', 1.2), (1.2, 'mag2', 'seat'), (0.9, 'mag', 'hide')],
}

import numpy as _np
def _v(a): return _np.array(a, float)
def _p(x): return [round(float(t), 4) for t in x]
def mag_rifle(magsel, grab, axis, out, gunpose, handle=None, hgrab=None, htravel=None, hfwd=(1, 0, 0), hpalm=(0, -1, 0),
              gfwd=(1, 0, 0), gpalm=(0, 0, 1), approach=(-1.0, 0, 0), hgun=None, extra_parts=None, dur=3.0):
    """a magazine rifle: mag out along its axis, dropped, a new one from the belt seated, then the charging handle"""
    ax = _v(axis) / _np.linalg.norm(axis); g = _v(grab)
    key = lambda p, c=30, f=gfwd, pa=gpalm: {'k': 'gun', 'p': _p(p), 'fwd': list(f), 'palm': list(pa), 'curl': c}
    parts = {'mag': {'sel': magsel}, 'mag2': {'clone': 'mag', 'hidden': True}}
    L = [(0, REST), (0.3, key(g + _v(approach), 0)), (0.5, key(g)), (0.75, key(g + ax * out)), (0.92, key(g + ax * out * 1.6 + _v(approach) * 2)),
         (1.3, POUCH_L), (1.45, POUCH_L), (1.8, key(g + ax * out * 1.3)), (2.05, key(g)), (2.12, key(g - ax * 0.15, 10))]
    events = [(0.5, 'mag', 'grab', 'L'), (0.86, 'mag', 'drop', [200, 0, 60]), (1.45, 'mag2', 'show', 'L', 'mag'), (2.05, 'mag2', 'seat'), (1.4, 'mag', 'hide')]
    moves = {}
    gp = gunpose; gun = [(0, {}), (0.3, gp), (2.2, gp)]
    t_end = 2.4
    if handle:
        parts['handle'] = {'sel': handle}
        h = _v(hgrab); tr = _v(htravel)
        hk = lambda p, c: {'k': 'gun', 'p': _p(p), 'fwd': list(hfwd), 'palm': list(hpalm), 'curl': c}
        L += [(2.35, hk(h + _v(hpalm) * -1.0, 0)), (2.45, hk(h, 35)), (2.62, hk(h + tr, 35)), (2.72, hk(h + tr + _v(hpalm) * -1.2, 0))]
        moves['handle'] = [(2.45, {}), (2.62, {'d': _p(tr)}), (2.66, {'d': _p(tr)}), (2.7, {})]
        gun += [(2.35, hgun or gp), (2.7, hgun or gp)]
        t_end = 2.75
    L += [(dur - 0.2, REST), (dur, REST)]
    gun += [(dur - 0.15, {}), (dur, {})]
    if extra_parts: parts.update(extra_parts)
    return {'dur': dur, 'parts': parts, 'gun': gun, 'L': L, 'moves': moves, 'events': events}

GR = lambda r, d: {'r': r, 'd': d, 'tp': TP_UP_RIFLE}
# Honey Badger (AR family): mag straight down and a touch forward; the T charging handle at the back of the receiver
SPECS['AAC_Honey'] = mag_rifle([{'part': 'mag'}], grab=[8.0, 3.6, 5.9], axis=[0, -1, -0.33], out=3.2, gunpose=GR([10, 10, -30], [-0.04, -0.02, -0.08]),
    handle=[{'part': 'body', 'comps': [31]}], hgrab=[7.3, 9.3, 12.0], htravel=[0, 0, 1.9], hfwd=[0.3, 0, -1], hpalm=[1, -0.3, 0.3],
    hgun=GR([4, 14, -12], [-0.03, -0.02, -0.08]))
# L86 LSW (bullpup): curved mag behind the grip, cocking handle on the right of the receiver
SPECS['L86'] = mag_rifle([{'part': 'mag'}], grab=[8.0, 5.2, 15.4], axis=[0, -1, -0.3], out=3.4, gunpose=GR([0, 45, -30], [0.02, 0.1, -0.3]),
    handle=[{'part': 'body', 'comps': [30]}], hgrab=[8.75, 9.6, 15.45], htravel=[0, 0, 1.6], hfwd=[-0.2, 0.3, -1], hpalm=[-1, 0, 0],
    hgun=GR([0, 40, 25], [0.02, 0.08, -0.3]))
# M2 Carbine: 30-round mag straight down; operating-rod handle on the right side
SPECS['M2Carbine'] = mag_rifle([{'part': 'mag'}], grab=[7.75, 6.3, 14.2], axis=[0, -1, -0.15], out=2.0, gunpose=GR([0, 40, -30], [0.0, 0.08, -0.2]),
    handle=[{'part': 'body', 'comps': [11]}], hgrab=[8.6, 8.25, 14.35], htravel=[0, 0, 1.3], hfwd=[-0.2, 0.3, -1], hpalm=[-1, 0, 0],
    hgun=GR([0, 35, 25], [0.0, 0.06, -0.2]))
# M14-type rifle: mag rocked out, op-rod handle on the right side
SPECS['AssaultRifle2'] = mag_rifle([{'part': 'mag'}], grab=[7.72, 6.0, 12.2], axis=[0, -1, -0.25], out=2.4, gunpose=GR([0, 40, -30], [0.0, 0.08, -0.2]),
    handle=[{'part': 'body', 'comps': [12, 13]}], hgrab=[8.8, 8.4, 12.3], htravel=[0, 0, 1.6], hfwd=[-0.2, 0.3, -1], hpalm=[-1, 0, 0],
    hgun=GR([0, 35, 25], [0.0, 0.06, -0.2]))
# American-180: the pan drum lifted straight off the top, a new one dropped on, then the charging handle
SPECS['American180'] = mag_rifle([{'part': 'body', 'comps': [20]}], grab=[8.0, 8.5, 8.3], axis=[0, 1, 0.1], out=1.4, gunpose=GR([10, 35, -20], [0.0, 0.06, -0.2]),
    gfwd=[1, 0, -0.3], gpalm=[0, -1, 0], approach=[0, 1.0, 0.3],
    handle=[{'part': 'body', 'comps': [8]}], hgrab=[7.7, 7.85, 11.95], htravel=[0, 0, 1.5], hfwd=[0.3, 0.3, -1], hpalm=[1, 0, 0],
    hgun=GR([0, 35, -30], [0.0, 0.06, -0.2]))

def shell_prims(front, back, r, hull=(150, 28, 24), brass=(196, 160, 72), head=0.22):
    import numpy as np
    f, b = np.array(front, float), np.array(back, float); ax = (b - f) / np.linalg.norm(b - f); h = b - ax * head * np.linalg.norm(b - f)
    return [{'t': 'cyl', 'a': list(f), 'b': list(h), 'r': r, 'col': hull, 'n': 10}, {'t': 'cyl', 'a': list(h), 'b': list(b), 'r': r * 1.08, 'col': brass, 'n': 10}]
def round_prims(front, back, r, brass=(196, 160, 72), tip=(150, 110, 70), bullet=0.35):
    import numpy as np
    f, b = np.array(front, float), np.array(back, float); ax = (b - f) / np.linalg.norm(b - f); L = np.linalg.norm(b - f); m = f + ax * L * bullet
    return [{'t': 'cyl', 'a': list(f + ax * L * 0.06), 'b': list(m), 'r': r * 0.8, 'col': tip, 'n': 8}, {'t': 'cyl', 'a': list(f), 'b': list(f + ax * L * 0.06), 'r': r * 0.35, 'col': tip, 'n': 8},
            {'t': 'cyl', 'a': list(m), 'b': list(b), 'r': r, 'col': brass, 'n': 8}]
NEAR_L = {'k': 'body', 'fp': {'d': [-0.08, -0.28, 0.06]}, 'tp': {'p': [0.17, 1.02, 0.1], 'fwd': [0, -1, 0.3], 'palm': [-1, 0, 0]}, 'curl': 30}

def tube_loader(n, t0, step, port_key, push_key, shell_name, push_move, near=NEAR_L):
    """one round at a time from the belt into a tube / gate; returns L keys, events, moves"""
    L, ev, mv = [], [], {}
    for i in range(n):
        a = t0 + i * step; nm = f'{shell_name}{i}'
        L += [(a, near), (a + step * 0.5, port_key), (a + step * 0.72, push_key)]
        ev += [(a, nm, 'show', 'L', '@', a + step * 0.5), (a + step * 0.5, nm, 'seat'), (a + step * 0.8, nm, 'hide')]
        mv[nm] = [(a + step * 0.5, {}), (a + step * 0.72, {'d': push_move})]
    return L, ev, mv

# Remington 870: four shells thumbed into the loading port under the receiver, then the pump racked
_L, _E, _M = tube_loader(4, 0.4, 0.6, {'k': 'gun', 'p': [8.0, 6.45, 6.9], 'fwd': [0, 0.2, -1], 'palm': [0, 1, 0], 'curl': 35},
                         {'k': 'gun', 'p': [8.0, 6.75, 4.6], 'fwd': [0, 0.2, -1], 'palm': [0, 1, 0], 'curl': 35}, 'sh', [0, 0.65, -2.8])
G870 = GR([0, 40, -45], [0.0, 0.08, -0.2])
SPECS['M870_CQB'] = {
    'dur': 3.75,
    'parts': {'pump': {'sel': [{'part': 'body', 'comps': [14]}]}},
    'extra': {f'sh{i}': {'prims': shell_prims([8, 6.9, 5.4], [8, 6.9, 8.2], 0.36), 'hidden': True} for i in range(4)},
    'gun': [(0, {}), (0.3, G870), (2.75, G870), (3.0, GR([0, 15, 10], [0.0, 0.03, -0.1])), (3.6, {}), (3.75, {})],
    'L': [(0, REST), (0.3, NEAR_L)] + _L + [(3.0, REST), (3.15, {'k': 'rest', 'dpx': [0, 0, 2.6]}), (3.32, REST), (3.75, REST)],
    'moves': {'pump': [(3.0, {}), (3.15, {'d': [0, 0, 2.6]}), (3.32, {})], **_M},
    'events': _E,
}

# Winchester 1866: four rounds pushed through the loading gate on the right of the receiver, then the lever worked
GW66 = GR([0, 42, 40], [0.02, 0.15, -0.25])
_L, _E, _M = tube_loader(4, 0.4, 0.55, {'k': 'gun', 'p': [9.15, 6.3, 13.0], 'fwd': [0, 0.3, -1], 'palm': [-1, 0, 0], 'curl': 35},
                         {'k': 'gun', 'p': [8.95, 6.3, 11.6], 'fwd': [0, 0.3, -1], 'palm': [-1, 0, 0], 'curl': 35}, 'rd', [-0.55, 0, -1.5])
SPECS['Winchester1866'] = {
    'dur': 3.55,
    'parts': {'lever': {'sel': [{'part': 'body', 'comps': [5]}], 'pivot': [8, 5.65, 11.9]},
              'hammer': {'sel': [{'part': 'body', 'comps': [9]}], 'pivot': [8, 6.9, 14.0]}},
    'extra': {f'rd{i}': {'prims': round_prims([8.6, 6.5, 11.9], [8.6, 6.5, 13.3], 0.17), 'hidden': True} for i in range(4)},
    'gun': [(0, {}), (0.3, GW66), (2.6, GW66), (2.8, GR([0, 42, -10], [0.02, 0.12, -0.25])), (3.3, GR([0, 42, -10], [0.02, 0.12, -0.25])), (3.55, {})],
    'L': [(0, REST), (0.3, NEAR_L)] + _L + [(2.75, REST), (3.55, REST)],
    'R': [(0, REST), (2.85, REST), (3.02, {'k': 'rest', 'dpx': [0, -1.9, -0.6], 'r': [30, 0, 0]}), (3.2, REST), (3.55, REST)],
    'moves': {'lever': [(2.85, {}), (3.02, {'r': [50, 0, 0]}), (3.2, {})], 'hammer': [(2.85, {}), (3.0, {'r': [-28, 0, 0]})], **_M},
    'events': _E,
}

# S&W 500: thumb the cylinder out, muzzle up and the five empties fall, a speedloader in, cylinder closed
import math as _m
CYL = (8.0, 9.59); PIV500 = [7.55, 9.0, 10.1]
def _ch(k, r=0.3): a = _m.radians(90 + 72 * k); return CYL[0] + r * _m.cos(a), CYL[1] + r * _m.sin(a)
SPENT = [p for k in range(5) for p in [{'t': 'cyl', 'a': [*_ch(k), 9.55], 'b': [*_ch(k), 10.85], 'r': 0.1, 'col': (176, 140, 64), 'n': 8}]]
LOAD = [p for k in range(5) for p in round_prims([*_ch(k), 9.55 + 1.3], [*_ch(k), 10.85 + 1.3], 0.1)]
LOAD += [{'t': 'cyl', 'a': [CYL[0], CYL[1], 12.15], 'b': [CYL[0], CYL[1], 12.7], 'r': 0.52, 'col': (40, 40, 44), 'n': 12}]
OPEN = {'r': [0, 0, 80]}; G500 = {'r': [8, 45, -15], 'd': [-0.03, 0.06, -0.08], 'tp': TP_UP_PISTOL}; G500UP = {'r': [55, 30, 0], 'd': [-0.03, 0.06, -0.08], 'tp': {**TP_UP_PISTOL, 'r': [40, 0, 0]}}
OC = (7.05, 9.55)
SPECS['SW_M500'] = {
    'dur': 3.0,
    'parts': {'cyl': {'sel': [{'part': 'body', 'comps': [9]}], 'pivot': PIV500}},
    'extra': {'spent': {'prims': SPENT, 'pivot': PIV500}, 'load': {'prims': LOAD, 'hidden': True, 'pivot': PIV500}},
    'gun': [(0, {}), (0.3, G500), (0.6, G500), (0.8, G500UP), (0.95, G500UP), (1.15, G500), (2.3, G500), (2.75, {}), (3.0, {})],
    'L': [(0, REST), (0.3, {'k': 'gun', 'p': [7.25, 9.6, 10.1], 'fwd': [0, 0.4, -1], 'palm': [1, 0, 0], 'curl': 20}),
          (0.55, {'k': 'gun', 'p': [7.0, 8.2, 10.4], 'fwd': [0, 0.4, -1], 'palm': [0.5, 1, 0], 'curl': 10}),
          (0.85, {'k': 'gun', 'p': [7.0, 9.2, 8.9], 'fwd': [0, 0.4, -1], 'palm': [0.3, 0, 1], 'curl': 20}),
          (1.2, POUCH_L), (1.35, POUCH_L),
          (1.75, {'k': 'gun', 'p': [OC[0], OC[1], 14.4], 'fwd': [0, 1, 0], 'palm': [0, 0, -1], 'curl': 35}),
          (1.95, {'k': 'gun', 'p': [OC[0], OC[1], 13.1], 'fwd': [0, 1, 0], 'palm': [0, 0, -1], 'curl': 35}),
          (2.1, {'k': 'gun', 'p': [6.6, 9.4, 10.1], 'fwd': [0, 0.4, -1], 'palm': [1, 0, 0], 'curl': 20}),
          (2.3, {'k': 'gun', 'p': [7.25, 9.6, 10.1], 'fwd': [0, 0.4, -1], 'palm': [1, 0, 0], 'curl': 20}), (2.75, REST), (3.0, REST)],
    'moves': {'cyl': [(0.35, {}), (0.55, OPEN), (2.1, OPEN), (2.3, {})],
              'spent': [(0.35, {}), (0.55, OPEN)],
              'load': [(1.75, OPEN), (1.95, {**OPEN, 'd': [0, 0, -1.3]}), (2.1, {**OPEN, 'd': [0, 0, -1.3]}), (2.3, {'d': [0, 0, -1.3]})]},
    'events': [(0.82, 'spent', 'drop', [300, 0, 40]), (1.3, 'spent', 'hide'), (1.35, 'load', 'show', 'L', '@', 1.95), (1.95, 'load', 'seat')],
}

# LAR Grizzly Big Boar (single-shot .50): bolt handle up and back, one round laid in the breech and pushed home, bolt closed
GBOAR = GR([0, 50, 35], [0.04, 0.12, -0.4])
HB = lambda p, c=35: {'k': 'gun', 'p': p, 'fwd': [0, -0.3, -1], 'palm': [-1, 0.3, 0], 'curl': c}
SPECS['BOAR'] = {
    'dur': 3.0,
    'parts': {'bolt': {'sel': [{'part': 'body', 'comps': [8]}], 'pivot': [8.0, 7.6, 21.5]}},
    'extra': {'rnd': {'prims': round_prims([8.0, 8.9, 17.0], [8.0, 8.9, 20.7], 0.3), 'hidden': True}},
    'gun': [(0, {}), (0.3, GBOAR), (2.4, GBOAR), (2.8, {}), (3.0, {})],
    'R': [(0, REST), (0.35, HB([9.6, 7.0, 21.5], 0)), (0.45, HB([9.4, 7.0, 21.5])), (0.6, HB([9.1, 8.5, 21.5])), (0.8, HB([9.1, 8.5, 24.5])),
          (0.9, HB([9.6, 8.8, 24.5], 0)), (1.25, {'k': 'body', 'fp': {'d': [0.12, -0.45, 0.15]}, 'tp': {'p': [-0.2, 1.0, 0.08]}, 'curl': 30}),
          (1.4, {'k': 'body', 'fp': {'d': [0.12, -0.45, 0.15]}, 'tp': {'p': [-0.2, 1.0, 0.08]}, 'curl': 30}),
          (1.7, {'k': 'gun', 'p': [8.0, 9.6, 18.9], 'fwd': [-1, 0, -0.2], 'palm': [0, -1, 0], 'curl': 30}),
          (1.85, {'k': 'gun', 'p': [8.0, 9.2, 16.9], 'fwd': [-1, 0, -0.2], 'palm': [0, -1, 0], 'curl': 10}),
          (2.0, HB([9.1, 8.5, 24.5])), (2.15, HB([9.1, 8.5, 21.5])), (2.3, HB([9.4, 7.0, 21.5])), (2.6, REST), (3.0, REST)],
    'moves': {'bolt': [(0.45, {}), (0.6, {'r': [0, 0, 70]}), (0.8, {'r': [0, 0, 70], 'd': [0, 0, 3.0]}), (2.0, {'r': [0, 0, 70], 'd': [0, 0, 3.0]}),
                       (2.15, {'r': [0, 0, 70]}), (2.3, {})],
              'rnd': [(1.7, {}), (1.85, {'d': [0, -0.75, -2.2]})]},
    'events': [(1.4, 'rnd', 'show', 'R', '@', 1.7), (1.7, 'rnd', 'seat'), (2.05, 'rnd', 'hide')],
}

# RPG-7: a new rocket from her back, slid into the muzzle, then the hammer cocked
GRPG = {'r': [15, 25, 0], 'd': [0.0, -0.05, 0.3], 'tp': TP_UP_RIFLE}
RK = lambda z, c=30: {'k': 'gun', 'p': [8.0, 7.2, z], 'fwd': [1, 0, 0], 'palm': [0, 1, 0], 'curl': c}
SPECS['RPG_7'] = {
    'dur': 2.7,
    'parts': {'rocket': {'sel': [{'part': 'mag'}], 'hidden': True}},
    'gun': [(0, {}), (0.4, GRPG), (1.8, GRPG), (2.3, {}), (2.7, {})],
    'L': [(0, REST), (0.4, POUCH_L), (0.6, POUCH_L), (1.1, RK(-5.0)), (1.45, RK(-2.5)), (1.6, RK(-2.5, 0)), (2.1, REST), (2.7, REST)],
    'R': [(0, REST), (1.65, REST), (1.8, {'k': 'rest', 'curl': -15}), (1.95, REST), (2.7, REST)],
    'moves': {'rocket': [(1.1, {'d': [0, 0, -2.5]}), (1.45, {})]},
    'events': [(0.6, 'rocket', 'show', 'L', '@', 1.1), (1.45, 'rocket', 'seat')],
}

# TenPoint crossbow: the string drawn back to the latch, a bolt laid in the rail and slid back to the string
GXB = GR([20, 30, 0], [0.0, 0.0, -0.15])
SL, SR = [-2.62, 8.6, -0.7], [18.62, 8.6, -0.7]
_ang = _m.degrees(_m.atan2(9.1, 10.62)); _sc = _m.hypot(9.1, 10.62) / 10.62
BOLT = [{'t': 'cyl', 'a': [8, 9.0, -11.0], 'b': [8, 9.0, 8.4], 'r': 0.13, 'col': (34, 34, 38), 'n': 8},
        {'t': 'cyl', 'a': [8, 9.0, -11.8], 'b': [8, 9.0, -11.0], 'r': 0.16, 'col': (150, 150, 155), 'n': 8},
        {'t': 'box', 'c': [8, 9.35, 6.8], 's': [0.04, 0.5, 2.0], 'col': (220, 70, 40)},
        {'t': 'box', 'c': [7.7, 8.85, 6.8], 's': [0.04, 0.5, 2.0], 'axis': [0, 0, 1], 'col': (230, 230, 230)},
        {'t': 'box', 'c': [8.3, 8.85, 6.8], 's': [0.04, 0.5, 2.0], 'col': (230, 230, 230)}]
SK = lambda z, c=35: {'k': 'gun', 'p': [8.0, 9.2, z], 'fwd': [1, 0, 0.2], 'palm': [0, -1, 0.3], 'curl': c}
SPECS['TenPointTurbo_XLT'] = {
    'dur': 3.2,
    'parts': {'strL': {'sel': [{'part': 'body', 'comps': [0, 1, 2, 3]}], 'pivot': SL},
              'strR': {'sel': [{'part': 'body', 'comps': [37, 34, 35, 36]}], 'pivot': SR}},
    'extra': {'bolt': {'prims': BOLT, 'hidden': True}},
    'gun': [(0, {}), (0.35, GXB), (2.7, GXB), (3.0, {}), (3.2, {})],
    'L': [(0, REST), (0.35, SK(-1.6, 0)), (0.5, SK(-0.9)), (1.0, SK(8.1)), (1.1, SK(8.1, 0)), (1.45, POUCH_L), (1.6, POUCH_L),
          (2.0, {'k': 'gun', 'p': [8.0, 9.45, 0.5], 'fwd': [1, 0, 0], 'palm': [0, -1, 0], 'curl': 35}),
          (2.35, {'k': 'gun', 'p': [8.0, 9.45, 3.5], 'fwd': [1, 0, 0], 'palm': [0, -1, 0], 'curl': 35}),
          (2.5, {'k': 'gun', 'p': [8.0, 9.9, 3.5], 'fwd': [1, 0, 0], 'palm': [0, -1, 0], 'curl': 0}), (2.9, REST), (3.2, REST)],
    'moves': {'strL': [(0.5, {}), (1.0, {'r': [0, -_ang, 0], 's': [_sc, 1, 1]})], 'strR': [(0.5, {}), (1.0, {'r': [0, _ang, 0], 's': [_sc, 1, 1]})],
              'bolt': [(2.0, {'d': [0, 0, -3.0]}), (2.35, {})]},
    'events': [(1.6, 'bolt', 'show', 'L', '@', 2.0), (2.35, 'bolt', 'seat')],
}

# DShK: feed cover up, a fresh belt laid across the tray, cover shut, charging handle back
BELT = []
for k in range(8):
    x = 7.9 - k * 0.62
    BELT += round_prims([x, 8.78, 13.4], [x, 8.78, 16.5], 0.22)
    BELT += [{'t': 'box', 'c': [x, 8.78, 15.1], 's': [0.7, 0.5, 0.35], 'col': (60, 60, 58)}]
GDS = {'r': [-5, 25, -15], 'd': [0.0, 0.0, -0.1], 'tp': TP_UP_RIFLE}
CV = {'r': [-65, 0, 0]}
SPECS['DSHK'] = {
    'dur': 3.4,
    'parts': {'cover': {'sel': [{'part': 'body', 'comps': [16]}], 'pivot': [8, 8.6, 12.2]},
              'handle': {'sel': [{'part': 'body', 'comps': [50]}]}},
    'extra': {'belt': {'prims': BELT, 'hidden': True}},
    'gun': [(0, {}), (0.35, GDS), (2.9, GDS), (3.2, {}), (3.4, {})],
    'L': [(0, REST), (0.35, {'k': 'gun', 'p': [8.0, 9.9, 16.9], 'fwd': [1, 0, -0.2], 'palm': [0, -1, 0], 'curl': 20}),
          (0.6, {'k': 'gun', 'p': [8.0, 13.0, 14.5], 'fwd': [1, 0, -0.2], 'palm': [0, -1, 0.5], 'curl': 20}),
          (0.95, POUCH_L), (1.1, POUCH_L),
          (1.5, {'k': 'gun', 'p': [5.0, 10.2, 15.0], 'fwd': [1, 0, -0.2], 'palm': [0, -1, 0], 'curl': 30}),
          (1.75, {'k': 'gun', 'p': [5.0, 9.35, 15.0], 'fwd': [1, 0, -0.2], 'palm': [0, -1, 0], 'curl': 30}),
          (2.0, {'k': 'gun', 'p': [8.0, 13.0, 14.5], 'fwd': [1, 0, -0.2], 'palm': [0, -1, 0.5], 'curl': 20}),
          (2.2, {'k': 'gun', 'p': [8.0, 9.9, 16.9], 'fwd': [1, 0, -0.2], 'palm': [0, -1, 0], 'curl': 0}),
          (2.45, {'k': 'gun', 'p': [9.1, 6.3, 20.6], 'fwd': [0, 0.3, -1], 'palm': [-1, 0, 0], 'curl': 35}),
          (2.65, {'k': 'gun', 'p': [9.1, 6.3, 23.4], 'fwd': [0, 0.3, -1], 'palm': [-1, 0, 0], 'curl': 35}),
          (2.75, {'k': 'gun', 'p': [9.8, 6.3, 23.4], 'fwd': [0, 0.3, -1], 'palm': [-1, 0, 0], 'curl': 0}), (3.1, REST), (3.4, REST)],
    'moves': {'cover': [(0.35, {}), (0.6, CV), (2.0, CV), (2.2, {})], 'handle': [(2.45, {}), (2.65, {'d': [0, 0, 2.8]}), (2.7, {})]},
    'events': [(1.1, 'belt', 'show', 'L', '@', 1.75), (1.75, 'belt', 'seat')],
}
