# Wave-3 reloads, part 3: guns none of Josiah's 14 templates covers directly. LOCAL-ONLY data.
# Break-actions, from his S&W 500 (its timing, hands and gun tilts): instead of the cylinder swinging out to the left,
# the barrels tip down about the hinge pin, the spent shells pop out of the breech, fresh ones are pushed into the
# chambers by the left hand, and the left hand lifts the barrels shut from under the fore-end.
import json, math, numpy as np
import specs3 as S3, specs2 as W2
from specs3 import SP, ALTP, MF, grip, shift_anchor
_specs = W2._specs
G = {}
T500 = W2.T500
def rot(q, P, th):
    a = math.radians(th); dy, dz = q[1] - P[1], q[2] - P[2]
    return [q[0], P[1] + dy * math.cos(a) - dz * math.sin(a), P[2] + dy * math.sin(a) + dz * math.cos(a)]
r3 = lambda p: [round(float(x), 3) for x in p]
BREAK = {  # gun: hinge pivot, breech face z, bore centres [(x, y)], shell radius, shell length, barrel-group box, fore-end grab, kind
    'ROYAL_SXS': ([8.0, 8.0, 12.6], 12.9, [(7.82, 9.05), (8.18, 9.05)], 0.16, 1.3, [0, 7.75, -99, 16, 99, 12.9], [8.0, 7.75, 9.0], 'shell'),
    'Coachgun': ([8.0, 8.15, 10.9], 11.0, [(7.78, 9.0), (8.22, 9.0)], 0.19, 1.3, [0, 7.75, -99, 16, 99, 11.0], [8.0, 7.75, 7.5], 'shell'),
    'NEF_Handi_410': ([8.0, 7.6, 11.8], 12.0, [(8.0, 8.95)], 0.13, 1.2, [0, 7.35, -99, 16, 99, 12.0], [8.0, 7.25, 8.5], 'shell'),
    'Thompson_Center_3006': ([8.0, 6.6, 12.8], 13.0, [(8.0, 7.25)], 0.12, 1.7, [0, 6.15, -99, 16, 99, 13.0], [8.0, 6.05, 9.0], 'round')}
TH = -35
for g, (piv, zf, bores, rr, Ln, bx, F, kind) in BREAK.items():
    cx = sum(b[0] for b in bores) / len(bores); cy = sum(b[1] for b in bores) / len(bores)
    spent = [{'t': 'cyl', 'a': [x, y, zf - Ln], 'b': [x, y, zf + 0.06], 'r': rr, 'col': [176, 140, 64] if kind == 'round' else [150, 28, 24], 'n': 10} for x, y in bores]
    if kind == 'shell':
        load = []
        for x, y in bores:
            load += [{'t': 'cyl', 'a': [x, y, zf], 'b': [x, y, zf + Ln - 0.25], 'r': rr, 'col': [150, 28, 24], 'n': 10},
                     {'t': 'cyl', 'a': [x, y, zf + Ln - 0.25], 'b': [x, y, zf + Ln], 'r': rr * 1.05, 'col': [190, 160, 80], 'n': 10}]
    else:
        load = [p for x, y in bores for p in _specs.round_prims([x, y, zf + Ln], [x, y, zf], rr)]
        for p in load: p['a'] = [float(v) for v in p['a']]; p['b'] = [float(v) for v in p['b']]; p['col'] = list(p['col'])
    B = [cx, cy, zf]; Bo = rot(B, piv, TH); d = [0, -math.sin(math.radians(TH)), math.cos(math.radians(TH))]
    at = lambda k, off=(0, 0, 0): r3([Bo[i] + d[i] * k + off[i] for i in range(3)])
    Fo = r3(rot(F, piv, TH))
    UNDER = "fwd: [0, 0, -1], palm: [0, 1, 0]"
    keys = {0.3: (F, UNDER, 35), 0.55: (Fo, UNDER, 35), 0.85: (Fo, UNDER, 35), 1.05: (Fo, UNDER, 35),
            1.8: (at(Ln + 0.7, (-0.75, 0.2, 0)), None, None), 1.97: (at(0.75, (-0.6, 0.15, 0)), None, None), 2.12: (at(Ln + 1.2, (-1.0, 0.2, 0)), None, None),
            2.68: (Fo, UNDER, 35), 2.82: (r3(F), UNDER, 35)}
    js_keys = ', '.join(f"'{t:.2f}': [{json.dumps(p)}, {('{' + o + '}') if o else 'null'}, {c if c is not None else 'null'}]" for t, (p, o, c) in keys.items())
    after = f"""
const KEYS = {{{js_keys}}};
for (const [t, k] of s.L) {{ const e = KEYS[(Math.round(t * 100) / 100).toFixed(2)]; if (!e || k.k !== 'gun') continue; k.p = e[0]; if (e[1]) Object.assign(k, e[1]); if (e[2] !== null) k.curl = e[2]; }}
for (const n of ['cyl', 'spent', 'load']) for (const [t, k] of (s.moves[n] || [])) {{ if (k.r) k.r = [{TH}, 0, 0]; if (k.d) k.d = [0, 0, {-Ln:.3f}]; }}
if (view === 'fp') s.gun = s.gun.map(([t, k]) => [t, (t > 0.25 && t < 2.9 && (k.r || k.d)) ? {{r: [8, 35, 25], d: [0, 0.06, -0.2]}} : k]);   // the breech turned to her, as his M40A3
delete s.moves.sl; s.events = s.events.filter(e => e[1] !== 'sl').map(e => e[2] === 'drop' ? [e[0], e[1], 'drop', [0, 160, 260]] : e);
"""
    G[g] = {'template': 'SW_M500', 'parts': {'cyl': {'sel': [{'part': 'body', 'box': bx}], 'pivot': piv}},
            'extra': {'spent': {'prims': spent, 'pivot': piv}, 'load': {'prims': load, 'hidden': True, 'pivot': piv}},
            'anchors': [{'name': 'cyl', 'T': {'o': [T500['c'][0], T500['c'][1], T500['rear']]}, 'N': {'o': B}, 's': 1, 'parts': ['cyl', 'spent', 'load']}],
            'after': [after]}
if __name__ == '__main__':
    for g, W in G.items(): print(g, W['after'][0][:400])

# KAC M203 (stand-alone): his S&W 500's timing and hands again, but the barrel slides FORWARD on its rail (no hinge),
# the spent case falls out of the open breech, a 40mm round is pushed into the back of the barrel, barrel slid home.
def slide_open(g, bsel, zf, c, rr, Ln, travel, F):
    cx, cy = c; B2 = [cx, cy, zf - travel]
    spent = [{'t': 'cyl', 'a': [cx, cy, zf - Ln], 'b': [cx, cy, zf], 'r': rr, 'col': [176, 140, 64], 'n': 14}]
    load = [{'t': 'cyl', 'a': [cx, cy, B2[2]], 'b': [cx, cy, B2[2] + Ln * 0.7], 'r': rr, 'col': [176, 140, 64], 'n': 14},
            {'t': 'cyl', 'a': [cx, cy, B2[2] + Ln * 0.7], 'b': [cx, cy, B2[2] + Ln], 'r': rr * 0.9, 'col': [70, 74, 60], 'n': 14}]
    Fo = [F[0], F[1], F[2] - travel]; UNDER = "fwd: [0, 0, -1], palm: [0, 1, 0]"
    keys = {0.3: (F, UNDER, 35), 0.55: (Fo, UNDER, 35), 0.85: (Fo, UNDER, 35), 1.05: (Fo, UNDER, 35),
            1.8: (r3([B2[0] - 0.9, B2[1] + 0.4, B2[2] + Ln + 0.7]), None, None), 1.97: (r3([B2[0] - 0.8, B2[1] + 0.4, B2[2] + 0.7]), None, None),
            2.12: (r3([B2[0] - 1.2, B2[1] + 0.4, B2[2] + Ln + 1.2]), None, None), 2.68: (Fo, UNDER, 35), 2.82: (F, UNDER, 35)}
    js_keys = ', '.join(f"'{t:.2f}': [{json.dumps(p)}, {('{' + o + '}') if o else 'null'}, {c if c is not None else 'null'}]" for t, (p, o, c) in keys.items())
    after = f"""
const KEYS = {{{js_keys}}};
for (const [t, k] of s.L) {{ const e = KEYS[(Math.round(t * 100) / 100).toFixed(2)]; if (!e || k.k !== 'gun') continue; k.p = e[0]; if (e[1]) Object.assign(k, e[1]); if (e[2] !== null) k.curl = e[2]; }}
s.moves.cyl = [[0.35, {{}}], [0.55, {{d: [0, 0, {-travel}]}}], [2.68, {{d: [0, 0, {-travel}]}}], [2.82, {{}}]];
s.moves.spent = [[0.6, {{}}], [0.75, {{d: [0, 0, 0.2]}}]];
s.moves.load = [[1.77, {{}}], [1.97, {{d: [0, 0, {-Ln}]}}], [2.68, {{d: [0, 0, {-Ln}]}}], [2.82, {{d: [0, 0, {travel - Ln:.3f}]}}]];
if (view === 'fp') s.gun = s.gun.map(([t, k]) => [t, (t > 0.25 && t < 2.9 && (k.r || k.d)) ? {{r: [8, 35, 25], d: [0.05, 0.02, -0.5]}} : k]);
delete s.moves.sl; s.events = s.events.filter(e => e[1] !== 'sl').map(e => e[2] === 'drop' ? [e[0], e[1], 'drop', [0, -60, 40]] : e);
"""
    G[g] = {'template': 'SW_M500', 'parts': {'cyl': {'sel': bsel, 'pivot': [cx, cy, zf]}},
            'extra': {'spent': {'prims': spent, 'pivot': [cx, cy, zf]}, 'load': {'prims': load, 'hidden': True, 'pivot': [cx, cy, zf]}},
            'anchors': [{'name': 'cyl', 'T': {'o': [T500['c'][0], T500['c'][1], T500['rear']]}, 'N': {'o': [cx, cy, zf]}, 's': 1, 'parts': ['cyl', 'spent', 'load']}],
            'after': [after]}
slide_open('KAC_M203', [{'part': 'body', 'comps': [8, 12]}], 10.98, (8.0, 8.39), 0.7, 1.9, 3.0, [8.0, 6.75, 4.0])

# Box magazines that are body pieces in these meshes: WA2000 (bullpup, magazine behind the grip, handle on the right:
# his L86) and SRM M1208 (a four-tube magazine module ahead of the trigger, handle on the right: his M14).
from specs3 import mag_anchor, right_at
MF['WA2000'] = {'o': [8.0, 4.33, 17.5], 'ax': [0.0, 1.0, 0.0], 'L': 1.2}
MF['M1208'] = {'o': [8.0, 4.61, 4.03], 'ax': [0.0, 1.0, 0.0], 'L': 2.2}
for g, tpl, comp, h, tpt in [('WA2000', 'L86', 12, right_at('WA2000', 8.5, 15.0), [8.75, 8.9, 15.45]),
                             ('M1208', 'AssaultRifle2', 3, right_at('M1208', 9.6, 8.0), [8.8, 8.4, 12.3])]:
    G[g] = {'template': tpl, 'drop': ['handle'], 'parts': {'mag': {'sel': [{'part': 'body', 'comps': [comp]}]}, 'mag2': {'clone': 'mag', 'hidden': True}},
            'anchors': [mag_anchor(tpl, g), shift_anchor('handle', tpt, h)],
            'match': [{'anchor': 'mag', 't0': 0.01, 't1': 2.2, 'k': 0.8 if g == 'WA2000' else 0.4}, {'anchor': 'handle', 't0': 2.3, 't1': 2.8, 'k': 0.8 if g == 'WA2000' else 0.4}]}

# Colt 1855 revolving shotgun: PLACEHOLDER with his S&W 500 swing-out (the real gun loads each chamber from the front
# with powder and shot, or has its cylinder swapped); flagged for his call.
_c, _z0, _z1, _R, _n = (8.0, 8.7), 12.15, 14.3, 0.6, 5
_s = _R / T500['R']; _rear = _z1 + 0.08 * _s; _Ln = _rear - _z0
_rel = [T500['piv'][i] - [T500['c'][0], T500['c'][1], T500['rear']][i] for i in range(3)]
_piv = [round([_c[0], _c[1], _rear][i] + _rel[i] * _s, 4) for i in range(3)]
_cr = T500['cr'] * _s; _rr = min(T500['rr'] * _s, _cr * math.sin(math.pi / _n) * 0.8)
_ch = [(_c[0] + _cr * math.cos(math.radians(90 + 72 * k)), _c[1] + _cr * math.sin(math.radians(90 + 72 * k))) for k in range(_n)]
_load = [p for x, y in _ch for p in _specs.round_prims([x, y, _rear], [x, y, _rear + _Ln], _rr)]
for p in _load: p['a'] = [float(v) for v in p['a']]; p['b'] = [float(v) for v in p['b']]; p['col'] = list(p['col'])
G['Colt1855'] = {'template': 'SW_M500', 'parts': {'cyl': {'sel': [{'part': 'body', 'box': [7.3, 8.05, 12.1, 8.7, 9.35, 14.35]}], 'pivot': _piv}},
    'extra': {'spent': {'prims': [{'t': 'cyl', 'a': [x, y, _rear - _Ln], 'b': [x, y, _rear], 'r': _rr, 'col': [176, 140, 64], 'n': 8} for x, y in _ch], 'pivot': _piv},
              'load': {'prims': _load, 'hidden': True, 'pivot': _piv},
              'sl': {'prims': [{'t': 'cyl', 'a': [_c[0], _c[1], _rear + _Ln], 'b': [_c[0], _c[1], _rear + _Ln + 0.55 * _s], 'r': _R, 'col': [40, 40, 44], 'n': 12}], 'hidden': True, 'pivot': _piv}},
    'anchors': [{'name': 'cyl', 'T': {'o': [T500['c'][0], T500['c'][1], T500['rear']]}, 'N': {'o': [_c[0], _c[1], _rear]}, 's': _s, 'parts': ['cyl', 'spent', 'load', 'sl']}],
    'after': [f"for (const n of ['load', 'sl']) for (const [t, k] of (s.moves[n] || [])) if (k.d) k.d = [0, 0, {-_Ln:.4f}];"]}
