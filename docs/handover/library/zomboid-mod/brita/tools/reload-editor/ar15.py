# AR-15 (Bushmaster MOE) empty reload, built in Josiah's AR-57 style: right index drops the mag, left hand fetches a
# new one from the belt, pushes it up into the well and tugs it, then slaps the bolt catch on the left of the receiver.
import numpy as np, json
from specs import POUCH_L, REST, TP_UP_RIFLE
AX = np.array([0, 3.02, 0.73]); AX = AX / np.linalg.norm(AX)          # up into the magwell
G = np.array([8.0, 4.45, 11.25])                                     # her hand round the mag's lower half
STY = [0, -20, 20]                                                    # his hand turn on the AR-57 (left hand on the gun)
p = lambda v: [round(float(x), 4) for x in v]
def MK(pt, c=30): return {'k': 'gun', 'p': p(pt), 'fwd': [1, 0, 0], 'palm': [0, 0, 1], 'curl': c, 'r2': STY}
def BC(x, c=5): return {'k': 'gun', 'p': [x, 7.6, 12.5], 'fwd': [0, 0.45, -1], 'palm': [1, 0, 0], 'curl': c, 'r2': [0, 0, 0]}
GF = {'r': [20, 15, -38], 'd': [-0.08, 0.03, -0.05], 'tp': {'r': [10, 10, -30], 'd': [-0.04, -0.02, -0.08], 'abs': TP_UP_RIFLE['abs']}, 'ta': {'r': [0, 0, 12], 'd': [0, 0, 0], 'abs': None}}
def jolt(g, dp):
    o = json.loads(json.dumps(g)); o['r'] = [o['r'][0] + dp, o['r'][1], o['r'][2]]
    for v in ('tp', 'ta'): o[v]['r'] = [o[v]['r'][0] + dp, o[v]['r'][1], o[v]['r'][2]]
    return o
SPEC = {
    'dur': 2.9, 'solver': 'natural',
    'parts': {'mag': {'sel': [{'part': 'mag'}]}, 'mag2': {'clone': 'mag', 'hidden': True}},
    'gun': [(0, {}), (0.3, GF), (1.45, GF), (1.5, jolt(GF, 2.5)), (1.6, GF), (1.8, GF), (1.85, jolt(GF, -2)), (1.95, GF), (2.35, {}), (2.9, {})],
    'R': [(0, REST), (0.3, REST), (0.36, {'k': 'rest', 'curl': -14}), (0.46, REST), (2.9, REST)],
    'L': [(0, REST), (0.3, {**MK(G - AX * 4.5 + np.array([-1.5, -1.0, 0]), 10)}), (0.7, POUCH_L), (0.85, POUCH_L),
          (1.2, MK(G - AX * 3.0, 30)), (1.45, MK(G, 30)), (1.52, MK(G - AX * 0.25, 34)), (1.58, MK(G, 30)),
          (1.72, {**BC(6.5, 0), 'flat': 0.8}), (1.8, {**BC(7.15, 0), 'flat': 1}), (1.92, {**BC(6.4, 0), 'flat': 0.6}), (2.3, REST), (2.9, REST)],
    'moves': {'mag': [(0.36, {}), (0.45, {'d': p(-AX * 0.7)})],
              'mag2': [(1.2, {'d': p(-AX * 3.0)}), (1.45, {}), (1.52, {'d': p(-AX * 0.25)}), (1.58, {})]},
    'events': [(0.45, 'mag', 'drop', [70, 0, 25]), (0.85, 'mag2', 'show', 'L', '@', 1.45), (1.45, 'mag2', 'seat'), (1.3, 'mag', 'hide')],
    'style': {'fp': {'L': {'elbow': [-0.3634, 0.0957, -0.9267]}}, 'ta': {'L': {'elbow': [-0.4236, 0.7081, -0.565]}}},
}
