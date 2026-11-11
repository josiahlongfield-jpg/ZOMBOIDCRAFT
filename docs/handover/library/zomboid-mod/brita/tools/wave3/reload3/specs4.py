# Wave-3 reloads, part 2: pistols, SMGs, revolvers, shotguns, lever and bolt guns, each ported from one of Josiah's own
# edited reloads (redit/port.js) the way wave 2 did it (wave2/specs2.py). Most of these meshes are one 'body' part, so
# moving pieces are picked by components or boxes, and pistol magazines are built from boxes in the grip.
# LOCAL-ONLY data (scratch, project files, private artifacts).
import json, math, re, numpy as np
import specs3 as S3
import specs2 as W2
from specs3 import SP, ALTP, MF, grip, body, surface, mag_anchor, shift_anchor, MAGPARTS, right_handle, right_at
import comps
_specs = W2._specs
G = {}
WIN = dict(S3.WIN); WIN['L86'] = {'mag': (0.01, 2.2), 'handle': (2.3, 2.8)}; WIN['M2Carbine'] = {'mag': (0.01, 2.12), 'handle': (2.3, 2.8)}
NOFLICK = W2.NOFLICK
_tris = {}
def tris(g):
    if g not in _tris:
        hdr, png, tex, parts, rest = comps.load(g)
        _tris[g] = np.concatenate([(np.stack([r['x'], r['y'], r['z']], -1) / 1000.).reshape(-1, 3, 3) for n, r in parts if n != 'mag'])
    return _tris[g]

# ---------------------------------------------------------------- pistols, from his Colt 1911 (as wave 2's G17/USP)
def pframe(g, zlo=-1.6, zhi=1.8, ybot=None):
    """centre line of the grip below the trigger guard (pistol magazine axis), its width and depth"""
    T = tris(g); r = np.random.default_rng(0).dirichlet([1, 1, 1], (len(T), 40)); P = np.einsum('tkj,tjc->tkc', r, T).reshape(-1, 3)
    gr = grip(g); P = P[(P[:, 2] > gr[2] + zlo) & (P[:, 2] < gr[2] + zhi)]
    yb = ybot if ybot is not None else float(P[:, 1].min()); pts = []
    for y in np.arange(yb + 0.15, gr[1] - 0.3, 0.2):
        s = P[np.abs(P[:, 1] - y) < 0.08]
        if len(s) < 6: continue
        pts.append([y, (s[:, 2].min() + s[:, 2].max()) / 2, s[:, 2].max() - s[:, 2].min(), s[:, 0].max() - s[:, 0].min()])
    pts = np.array(pts); A = np.polyfit(pts[:, 0], pts[:, 1], 1)
    ax = np.array([0, 1, A[0]]); ax /= np.linalg.norm(ax)
    return {'o': [8.0, round(yb, 3), round(float(A[0] * yb + A[1]), 3)], 'ax': [round(float(x), 4) for x in ax],
            'depth': float(np.median(pts[:, 2])), 'width': float(np.median(pts[:, 3]))}
box = W2.box; T1911 = W2.T1911
PIST = {  # gun: slide bottom y, slide travel, pframe options; slide None = no slide (fixed upper, side handle)
    'G21': (9.3, 1.0, {}), 'AMT1911': (9.15, 1.0, {}), 'B93R': (9.2, 1.0, {}), 'M9': (9.3, 1.0, {}), 'G34': (9.4, 1.0, {}),
    'VP70': (9.15, 1.0, {}), 'MP45': (9.5, 1.0, {}), 'XD4': (9.6, 1.0, {}), 'Makarov': (9.45, 0.85, {}), 'FN_57': (9.4, 1.0, {}),
    'DEagle': (9.2, 1.3, {}), 'M1911_Carbine': (7.9, 1.0, {'zlo': -1.2, 'zhi': 1.0, 'ybot': 4.5}), 'MPA57DMG': (None, 0, {})}
PF = {}
for g, (ys, travel, opt) in PIST.items():
    f = pframe(g, **opt); PF[g] = f; ax = f['ax']; o = f['o']; ytop = o[1]
    top = ys if ys is not None else grip(g)[1] + 1.4
    L = round((top - 0.98 - ytop) / ax[1], 3)
    w = min(0.66, max(0.3, f['width'])); dp = f['depth']
    c_body = [o[i] + ax[i] * L / 2 for i in range(3)]; c_base = [o[i] - ax[i] * 0.27 / 2 for i in range(3)]
    prims = [box(c_body, [round(w * 0.62, 3), round(dp * 0.47, 3), L], ax, [48, 48, 54]), box(c_base, [round(w * 0.85, 3), round(dp * 0.68, 3), 0.27], ax, [30, 30, 33])]
    MF[g] = {'o': o, 'ax': ax, 'L': L}
    W = {'template': 'Colt1911', 'extra': {'mag': {'prims': prims}, 'mag2': {'prims': prims, 'hidden': True}},
         'anchors': [{'name': 'mag', 'T': {'o': T1911['o'], 'ax': T1911['ax']}, 'N': {'o': o, 'ax': ax}, 's': max(0.85, min(1.2, L / 2.6))}],
         'match': [{'anchor': 'mag', 't0': 0.01, 't1': 1.31, 'k': 0.8}]}
    if ys is None: W.update(parts={}, drop=['slide'], after=[NOFLICK])
    else: W.update(parts={'slide': {'sel': [{'part': 'body', 'box': [0, ys, -99, 16, 99, 99]}]}},
                   after=[f"for (const [t, k] of (s.moves.slide || [])) if (k.d) k.d = [0, 0, {travel}];"])
    G[g] = W

# ---------------------------------------------------------------- SMGs
# magazine in the grip, open bolt (bolt already back on empty): his 1911's magazine change, no thumb flick (as UZI)
for g in ['MAC10', 'UZI_Micro']:
    N = MF[g]
    G[g] = {'template': 'Colt1911', 'parts': MAGPARTS, 'drop': ['slide'], 'after': [NOFLICK],
            'anchors': [{'name': 'mag', 'T': {'o': T1911['o'], 'ax': T1911['ax']}, 'N': {'o': N['o'], 'ax': N['ax']}, 's': max(0.85, min(1.2, N['L'] / 2.6))}],
            'match': [{'anchor': 'mag', 't0': 0.01, 't1': 1.31, 'k': 0.8}]}
LH = {}
def left_pull(g, tpl, h, parts=MAGPARTS, extra=None):
    LH[g] = h
    G[g] = {'template': tpl, 'parts': parts, 'drop': ['handle'], 'anchors': [mag_anchor(tpl, g)], 'extra': extra or {},
            'match': [{'anchor': 'mag', 't0': 0.01, 't1': 2.2, 'k': 0.8}], 'after': [W2.left_action('pull', h)]}
# KG-9: magazine ahead of the grip, cocking knob on the left at the rear of the receiver (his rule: the left hand works it)
left_pull('KG9', 'AAC_Honey', [6.7, 9.6, 17.2])
# KRISS Vector: magazine (built from boxes, the mesh has none) in the well ahead of the trigger, cocking handle on the left
MF['KRISS'] = {'o': [8.0, 4.6, 12.0], 'ax': [0.0, 1.0, 0.0], 'L': 3.0}
_kp = [box([8.0, 6.1, 12.0], [0.45, 1.0, 3.0], [0, 1, 0], [40, 40, 44]), box([8.0, 4.47, 12.0], [0.6, 1.25, 0.26], [0, 1, 0], [30, 30, 33])]
left_pull('KRISS', 'AAC_Honey', [surface('KRISS', 8.9, 13.5, -1) - 0.05, 8.9, 13.5], parts={},
          extra={'mag': {'prims': _kp}, 'mag2': {'prims': _kp, 'hidden': True}})
# handle on the right of the receiver: his M14 (left hand magazine, right hand handle)
RH = {}
def right_ak(g, h):
    RH[g] = h
    G[g] = {'template': 'AssaultRifle2', 'parts': MAGPARTS, 'drop': ['handle'],
            'anchors': [mag_anchor('AssaultRifle2', g), shift_anchor('handle', [8.8, 8.4, 12.3], h)]}
right_ak('Model55', right_handle('Model55'))
# box-magazine shotguns with the AR's handle on top at the back (USAS-12, SPAS-15): his Honey Badger's top handle
def top_handle(g, h):
    RH[g] = h
    G[g] = {'template': 'AAC_Honey', 'parts': MAGPARTS, 'drop': ['handle'],
            'anchors': [mag_anchor('AAC_Honey', g), shift_anchor('handle', [8.7, 9.3, 12.0], h)]}
top_handle('SPAS_15', [8.0, 10.25, 15.2]); top_handle('USAS_12', [8.0, 11.1, 15.3])
# CAWS: bullpup (magazine behind the grip), handle on the right: his L86
RH['CAWS'] = right_at('CAWS', 9.6, 13.0)
G['CAWS'] = {'template': 'L86', 'parts': MAGPARTS, 'drop': ['handle'], 'anchors': [mag_anchor('L86', 'CAWS'), shift_anchor('handle', [8.75, 8.9, 15.45], RH['CAWS'])]}
# AR-7: his M2 Carbine (magazine ahead of the trigger, the left hand reaches over and pulls the bolt handle on the right)
RH['AR7'] = [surface('AR7', 7.6, 11.5, 1) + 0.1, 7.6, 11.5]
G['AR7'] = {'template': 'M2Carbine', 'parts': MAGPARTS, 'drop': ['handle'],
            'anchors': [mag_anchor('M2Carbine', 'AR7'), shift_anchor('handle', [8.6, 8.25, 14.35], RH['AR7'])]}

# MAS-49: magazine is body piece 17 (the mesh has no separate part), handle on the right of the receiver: his M14
MF['MAS49'] = {'o': [8.0, 6.24, 11.4], 'ax': [0.0, 1.0, 0.0], 'L': 1.2}
RH['MAS49'] = right_at('MAS49', 8.3, 13.5)
G['MAS49'] = {'template': 'AssaultRifle2', 'drop': ['handle'],
              'parts': {'mag': {'sel': [{'part': 'body', 'comps': [17]}]}, 'mag2': {'clone': 'mag', 'hidden': True}},
              'anchors': [mag_anchor('AssaultRifle2', 'MAS49'), shift_anchor('handle', [8.8, 8.4, 12.3], RH['MAS49'])]}

# ---------------------------------------------------------------- revolvers, from his S&W 500 (as wave 2's SW_327)
T500 = W2.T500
REV = {  # gun: cylinder selection box, centre (x, y), front z, rear z, radius, chambers
    'Judge513_45LC': ([7.0, 8.55, 6.55, 9.0, 10.05, 9.05], (8.0, 9.3), 6.6, 9.0, 0.7, 5),
    'GP100_6': ([7.3, 9.0, 7.55, 8.7, 10.1, 9.4], (8.0, 9.55), 7.6, 9.35, 0.5, 6),
    'M29_44Carbine': ([7.0, 5.55, 9.15, 9.0, 7.35, 11.25], (8.0, 6.45), 9.2, 11.2, 0.85, 6),
    'TXS_804_45LC': ([7.3, 7.6, 9.35, 8.7, 8.8, 12.15], (8.0, 8.2), 9.4, 12.1, 0.55, 5)}
for g, (bx, c, z0, z1, R, n) in REV.items():
    s = R / T500['R']; rear = z1 + 0.08 * s; Ln = rear - z0
    rel = [T500['piv'][0] - T500['c'][0], T500['piv'][1] - T500['c'][1], T500['piv'][2] - T500['rear']]
    piv = [round(c[0] + rel[0] * s, 4), round(c[1] + rel[1] * s, 4), round(rear + rel[2] * s, 4)]
    cr = T500['cr'] * s * (0.95 if n > 6 else 1.0); rr = min(T500['rr'] * s, cr * math.sin(math.pi / n) * 0.8)
    ch = [(c[0] + cr * math.cos(math.radians(90 + 360 / n * k)), c[1] + cr * math.sin(math.radians(90 + 360 / n * k))) for k in range(n)]
    spent = [{'t': 'cyl', 'a': [x, y, rear - Ln], 'b': [x, y, rear], 'r': rr, 'col': [176, 140, 64], 'n': 8} for x, y in ch]
    load = [p for x, y in ch for p in _specs.round_prims([x, y, rear], [x, y, rear + Ln], rr)]
    for p in load: p['a'] = [float(v) for v in p['a']]; p['b'] = [float(v) for v in p['b']]; p['col'] = list(p['col'])
    sl = [{'t': 'cyl', 'a': [c[0], c[1], rear + Ln], 'b': [c[0], c[1], rear + Ln + 0.55 * s], 'r': R, 'col': [40, 40, 44], 'n': 12}]
    G[g] = {'template': 'SW_M500', 'parts': {'cyl': {'sel': [{'part': 'body', 'box': bx}], 'pivot': piv}},
            'extra': {'spent': {'prims': spent, 'pivot': piv}, 'load': {'prims': load, 'hidden': True, 'pivot': piv},
                      'sl': {'prims': sl, 'hidden': True, 'pivot': piv}},
            'anchors': [{'name': 'cyl', 'T': {'o': [T500['c'][0], T500['c'][1], T500['rear']]}, 'N': {'o': [c[0], c[1], rear]}, 's': s,
                         'parts': ['cyl', 'spent', 'load', 'sl']}],
            'after': [f"for (const n of ['load', 'sl']) for (const [t, k] of (s.moves[n] || [])) if (k.d) k.d = [0, 0, {-Ln:.4f}];"]}

# ---------------------------------------------------------------- tube shotguns, from his M870 (four shells, then the pump)
PORT_T = [8.0, 6.45, 6.9]
PUMP = {  # gun: loading port (where her hand starts a shell, under the receiver), pump selection box
    'M870_Wingmaster': ([8.0, 6.8, 12.3], [6.5, 7.0, 3.0, 9.5, 8.5, 11.0]),
    'M870_MCS': ([8.0, 7.3, 10.3], [6.5, 7.7, 2.7, 9.5, 9.0, 5.1]),
    'SPAS12': ([8.0, 7.29, 9.0], [6.5, 7.5, -1.6, 9.5, 9.9, 6.6]),
    'KS23_S': ([8.0, 5.4, 11.5], [6.5, 6.0, -3.8, 9.5, 8.05, 9.9])}
for g, (port, bx) in PUMP.items():
    d = [port[i] - PORT_T[i] for i in range(3)]
    G[g] = {'template': 'M870_CQB', 'parts': {'pump': {'sel': [{'part': 'body', 'box': bx}]}}, 'extra': W2.m870_shells(d),
            'anchors': [shift_anchor('port', PORT_T, port)], 'match': [{'anchor': 'port', 't0': 0.29, 't1': 2.76, 'k': 0.8}]}
# semi-auto tube shotguns: shells through the port as on the M870, then her right hand draws the bolt handle on the right
# of the receiver and lets it slam home (wave 2's M1014)
def semi_bolt(h):
    return W2.M1014_BOLT.replace(W2.jp([W2.M14H[0] + 0.45, W2.M14H[1] + 0.3, W2.M14H[2]]), W2.jp([h[0] + 0.45, h[1] + 0.3, h[2]])) \
        .replace(W2.jp([W2.M14H[0] + 0.2, W2.M14H[1] + 0.25, W2.M14H[2]]), W2.jp([h[0] + 0.2, h[1] + 0.25, h[2]])) \
        .replace(W2.jp([W2.M14H[0] + 0.2, W2.M14H[1] + 0.25, W2.M14H[2] + 1.8]), W2.jp([h[0] + 0.2, h[1] + 0.25, h[2] + 1.8])) \
        .replace(W2.jp([W2.M14H[0] + 0.7, W2.M14H[1] + 0.4, W2.M14H[2] + 1.8]), W2.jp([h[0] + 0.7, h[1] + 0.4, h[2] + 1.8]))
for g, port, h in [('VIPER_G2', [8.0, 6.9, 13.3], [8.45, 8.4, 13.0]), ('A400', [8.0, 6.8, 12.8], [8.45, 8.5, 13.5])]:
    RH[g] = h; d = [port[i] - PORT_T[i] for i in range(3)]
    G[g] = {'template': 'M870_CQB', 'parts': {}, 'drop': ['pump'], 'extra': W2.m870_shells(d),
            'anchors': [shift_anchor('port', PORT_T, port)], 'match': [{'anchor': 'port', 't0': 0.29, 't1': 2.76, 'k': 0.8}],
            'after': [semi_bolt(h)]}

# ---------------------------------------------------------------- lever guns, from his Winchester 1866
WG_T = [9.0, 6.3, 12.3]; WL_T = [8.0, 5.65, 11.9]
def win_rounds(d):
    out = {}
    for i in range(4):
        out[f'rd{i}'] = {'prims': [{**p, 'a': [float(p['a'][j] + d[j]) for j in range(3)], 'b': [float(p['b'][j] + d[j]) for j in range(3)],
                                    'col': list(p['col'])} for p in _specs.SPECS['Winchester1866']['extra'][f'rd{i}']['prims']], 'hidden': True}
    return out
LEVER = {  # gun: loading gate (right side), lever pivot, lever selection
    'M1887': ([9.0, 7.6, 14.2], [8.0, 7.0, 14.9], [{'part': 'body', 'comps': [1]}]),
    'Viper': ([9.0, 5.9, 15.0], [8.0, 5.3, 16.0], [{'part': 'body', 'box': [7.5, 3.7, 15.9, 8.5, 5.25, 18.8]}])}
for g, (gate, piv, sel) in LEVER.items():
    d = [gate[i] - WG_T[i] for i in range(3)]
    G[g] = {'template': 'Winchester1866', 'drop': ['hammer'], 'parts': {'lever': {'sel': sel, 'pivot': piv}}, 'extra': win_rounds(d),
            'anchors': [shift_anchor('gate', WG_T, gate), {**shift_anchor('lever', WL_T, piv), 'parts': ['lever']}]}

# ---------------------------------------------------------------- bolt guns
bolt_keys, HB, jp = W2.bolt_keys, W2.HB, W2.jp
def mag_bolt(knob, piv, travel):
    """his M14's magazine change (left hand), then the right hand cycles the bolt (wave 2's SV-98)"""
    B = bolt_keys(knob, piv, travel)
    out = W2.SV98_BOLT
    B98 = bolt_keys([8.48, 6.97, 13.63], [7.83, 8.0, 13.3], 2.6)
    for k in ('near', 'grab', 'up', 'back'): out = out.replace(jp(B98[k]), jp(B[k]))
    return out.replace('d: [0, 0, 2.6]', f'd: [0, 0, {travel}]')
BOXBOLT = {  # gun: knob, bore pivot, travel, bolt selection, magazine (frame, selection or None for its own part)
    'TAC50': ([8.7, 6.85, 14.5], [7.75, 7.9, 14.4], 3.4, [{'part': 'body', 'comps': [21]}], ({'o': [7.73, 4.9, 11.4], 'ax': [0, 1, 0], 'L': 2.0}, None)),
    'CheyTac50': ([9.6, 6.4, 16.5], [7.5, 7.8, 16.4], 3.4, [{'part': 'body', 'comps': [45]}], ({'o': [7.5, 4.0, 12.5], 'ax': [0, 1, 0], 'L': 2.3}, None)),
    'TOZ106': ([8.8, 7.2, 10.3], [8.0, 8.4, 10.3], 2.6, [{'part': 'body', 'comps': [7], 'box': [0, 0, 9.0, 16, 16, 12]}],
               ({'o': [8.0, 5.54, 6.9], 'ax': [0, 1, 0], 'L': 1.5}, [{'part': 'body', 'comps': [4]}])),
    'L96': ([8.85, 6.5, 12.5], [7.6, 7.9, 12.5], 2.6, [{'part': 'body', 'comps': [30]}],
            ({'o': [7.65, 4.92, 9.9], 'ax': [0, 1, 0], 'L': 1.6}, [{'part': 'body', 'comps': [3]}]))}
for g, (knob, piv, travel, bsel, (mf, msel)) in BOXBOLT.items():
    MF[g] = mf
    parts = {'mag': {'sel': msel or [{'part': 'mag'}]}, 'mag2': {'clone': 'mag', 'hidden': True}, 'bolt': {'sel': bsel, 'pivot': piv}}
    G[g] = {'template': 'AssaultRifle2', 'drop': ['handle'], 'parts': parts, 'anchors': [mag_anchor('AssaultRifle2', g)],
            'match': [{'anchor': 'mag', 't0': 0.01, 't1': 2.21, 'k': 0.8}], 'after': [mag_bolt(knob, piv, travel)]}
# internal magazines: bolt open as on his Big Boar, four rounds thumbed down into the action, bolt home (wave 2's M40A3)
M40K, M40P = W2.M40_KNOB, W2.M40_PIV
def internal_load(knob, piv):
    d = [piv[0] - M40P[0], piv[1] - M40P[1], knob[2] - M40K[2]]
    js = W2.M40_LOAD.replace('[7.74, 9.75, 11.6]', jp([7.74 + d[0], 9.75 + d[1], 11.6 + d[2]])).replace('[7.74, 9.05, 11.4]', jp([7.74 + d[0], 9.05 + d[1], 11.4 + d[2]]))
    rnd = _specs.round_prims([7.74 + d[0], 8.95 + d[1], 10.15 + d[2]], [7.74 + d[0], 8.95 + d[1], 12.25 + d[2]], 0.15)
    for p in rnd: p['a'] = [float(v) for v in p['a']]; p['b'] = [float(v) for v in p['b']]; p['col'] = list(p['col'])
    return js, rnd
INTERNAL = {'Sako85': ([8.45, 6.45, 12.9], [7.86, 7.9, 12.9], [{'part': 'body', 'comps': [13]}]),
            'M40': ([8.75, 6.6, 14.0], [7.68, 8.0, 14.0], [{'part': 'body', 'comps': [19]}]),
            'M1903': ([8.65, 6.55, 13.2], [7.73, 8.0, 13.2], [{'part': 'body', 'comps': [6]}])}
for g, (knob, piv, bsel) in INTERNAL.items():
    js, rnd = internal_load(knob, piv)
    G[g] = {'template': 'BOAR', 'parts': {'bolt': {'sel': bsel, 'pivot': piv}}, 'extra': {f'rnd{i}': {'prims': rnd, 'hidden': True} for i in range(W2.NR)},
            'anchors': [shift_anchor('bolt', [9.28, 6.44, 21.5], knob)], 'after': [js]}

for g, W in G.items():
    t = W['template']; A = {a['name'] for a in W['anchors']}
    W.setdefault('match', [{'anchor': n, 't0': t0, 't1': t1, 'k': 0.8} for n, (t0, t1) in WIN.get(t, {}).items() if n in A])
LATER = ['ROYAL_SXS', 'Coachgun', 'NEF_Handi_410', 'Thompson_Center_3006', 'M1208', 'KAC_M203', 'M2A1', 'WA2000', 'Colt1855']
if __name__ == '__main__':
    for g in PF: print(g, {k: (round(v, 2) if isinstance(v, float) else v) for k, v in PF[g].items()}, MF[g]['L'])
    print(len(G), 'guns'); print({**RH, **LH})
