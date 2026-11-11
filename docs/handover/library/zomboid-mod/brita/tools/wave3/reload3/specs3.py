# The 88 wave-3 guns' reloads, each ported from one of Josiah's own edited reloads (redit/port.js), as wave 2 (specs2.py):
# which gun it copies, its movable parts, and the anchors that carry each hand key from the template gun onto this one.
# LOCAL-ONLY data (scratch, project files, private artifacts).
import json, sys, numpy as np
SP = '/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad'
sys.path[:0] = ['/mnt/project-files/zomboid-mod/brita/tools/reload', f'{SP}/wave2']
import comps
MF = json.load(open(f'{SP}/magframes.json'))                     # the templates' (and wave 2's) magazine frames
MF.update(json.load(open('/mnt/project-files/zomboid-mod/brita/tools/wave3/magframes3.json')))
ALTP = f'{SP}/posing3'
def grip(g): return json.load(open(f'{ALTP}/guns/{g}.json'))['grip']
def magtop(g): f = MF[g]; return [f['o'][i] + f['ax'][i] * f['L'] for i in range(3)]
def mag_anchor(t, n):
    T, N = MF[t], MF[n]; s = max(0.8, min(1.3, N['L'] / T['L']))
    return {'name': 'mag', 'T': {'o': T['o'], 'ax': T['ax']}, 'N': {'o': N['o'], 'ax': N['ax']}, 's': s}
def shift_anchor(name, t_pt, n_pt): return {'name': name, 'T': {'o': list(t_pt)}, 'N': {'o': [round(float(x), 3) for x in n_pt]}, 's': 1}
MAGPARTS = {'mag': {'sel': [{'part': 'mag'}]}, 'mag2': {'clone': 'mag', 'hidden': True}}
_body = {}
def body(g):
    if g not in _body:
        hdr, png, tex, parts, rest = comps.load(g)
        _body[g] = np.concatenate([np.stack([r['x'], r['y'], r['z']], -1).reshape(-1, 3) / 1000. for n, r in parts if n != 'mag'])
    return _body[g]
def surface(g, y, z, side=1, r=0.35):
    """the outermost x of the body near (y, z) on the right (side 1) or left (-1)"""
    P = body(g); m = (np.abs(P[:, 1] - y) < r) & (np.abs(P[:, 2] - z) < r)
    for k in (2, 4, 8):
        if m.any(): break
        m = (np.abs(P[:, 1] - y) < k * r) & (np.abs(P[:, 2] - z) < k * r)
    return float(P[m, 0].max() if side > 0 else P[m, 0].min())
G = {}
WIN = {'Bush_AR15_MOE': {'mag': (0.01, 1.47), 'bc': (1.48, 9)}, 'AssaultRifle2': {'mag': (0.01, 2.21), 'handle': (2.3, 2.8)},
       'AAC_Honey': {'mag': (0.01, 2.21), 'handle': (2.3, 2.8)}, 'L86': {'mag': (0.01, 2.2)}}

# AR family (bolt catch on the left of the lower, as his AR-15): mag out and in by the left hand, then the left palm
# slaps the bolt catch, which sits at the same place relative to the pistol grip on every AR lower.
AR_BC = [7.0, 7.6, 12.5]
for g in ['M723', 'CAR15_Survival', 'AssaultRifle', 'ADAR', 'Bush_XM15', 'Bush_XM15_Custom', 'M4', 'MK47', 'M635S', 'Carbon15_97']:
    G[g] = {'template': 'Bush_AR15_MOE', 'parts': MAGPARTS,
            'anchors': [mag_anchor('Bush_AR15_MOE', g), shift_anchor('bc', AR_BC, [AR_BC[i] - grip('Bush_AR15_MOE')[i] + grip(g)[i] for i in range(3)])]}

# Charging handle on the right (AK, M14 and kin, AR-18): his M14 (left hand mag, then right hand pulls the handle).
# The handle sits on the right of the receiver just above the front of the magazine (his AK-47: 1.15 out, 0.7 up).
def right_handle(g, up=0.7, back=-0.13):
    t = magtop(g); y, z = t[1] + up, t[2] + back
    return [surface(g, y, z, 1) + 0.3, y, z]
RH = {}
for g in ['AK74', 'AK74_Custom', 'AKM_Custom', 'AK12_New', 'AK308', 'AN94', 'Vz58', 'Galil', 'M14EBR', 'Bizon2_01', 'AK74_Alpha', 'AR18', 'SKS']:
    RH[g] = right_handle(g)
    G[g] = {'template': 'AssaultRifle2', 'parts': MAGPARTS, 'drop': ['handle'],
            'anchors': [mag_anchor('AssaultRifle2', g), shift_anchor('handle', [8.8, 8.4, 12.3], RH[g])]}

# Bullpups from his L86 (magazine behind the grip; trigger hand holds the gun out and tips the magazine to the left
# hand); a handle on the right is pulled by the right hand afterwards, as on his L86.
L86_H = [8.75, 8.9, 15.45]
WIN['L86'] = {'mag': (0.01, 2.2), 'handle': (2.3, 2.8)}
def right_at(g, y, z): return [surface(g, y, z, 1) + 0.3, y, z]
for g, (y, z) in {'L85A2': (8.15, 14.9), 'Ots14_4A_03': (8.3, 15.5), 'OTS_03_SVU': (8.0, 14.5)}.items():
    RH[g] = right_at(g, y, z)
    G[g] = {'template': 'L86', 'parts': MAGPARTS, 'drop': ['handle'],
            'anchors': [mag_anchor('L86', g), shift_anchor('handle', L86_H, RH[g])]}
# FAMAS: the handle sits under the carry handle, on top: her right hand reaches up into it
RH['FAMAS'] = [8.0, 9.9, 11.4]
G['FAMAS'] = {'template': 'L86', 'parts': MAGPARTS, 'drop': ['handle'], 'anchors': [mag_anchor('L86', 'FAMAS'), shift_anchor('handle', L86_H, RH['FAMAS'])]}
# XM8: handle on top under the carry handle, at the back, as his Honey Badger's
RH['XM8'] = [8.0, 9.6, 12.5]
G['XM8'] = {'template': 'AAC_Honey', 'parts': MAGPARTS, 'drop': ['handle'],
            'anchors': [mag_anchor('AAC_Honey', 'XM8'), shift_anchor('handle', [8.7, 9.3, 12.0], RH['XM8'])]}
# Machine guns with a box or drum: his M14 (left hand swaps the box, right hand works the handle on the right)
for g, (y, z) in {'M249E3': (8.4, 10.0), 'RPD': (7.7, 11.7)}.items():
    RH[g] = right_at(g, y, z)
    G[g] = {'template': 'AssaultRifle2', 'parts': MAGPARTS, 'drop': ['handle'],
            'anchors': [mag_anchor('AssaultRifle2', g), shift_anchor('handle', [8.8, 8.4, 12.3], RH[g])]}
RH['RPK16'] = right_handle('RPK16')
G['RPK16'] = {'template': 'AssaultRifle2', 'parts': MAGPARTS, 'drop': ['handle'],
              'anchors': [mag_anchor('AssaultRifle2', 'RPK16'), shift_anchor('handle', [8.8, 8.4, 12.3], RH['RPK16'])]}
# HK-style cocking handle forward on the LEFT: after seating the magazine her left hand pulls it and lets it fly
# (his rule: the hand on that side works it; wave 2's F2000 pull), the right hand staying on the grip.
import specs2
LH = {}
for g, (tpl, y, z) in {'G33': ('AAC_Honey', 8.7, 6.0), 'UMP9': ('AAC_Honey', 8.5, 9.0), 'UMP45': ('AAC_Honey', 8.5, 9.0),
                       'EVO': ('AAC_Honey', 8.6, 9.5), 'AUG_A3': ('L86', 8.0, 9.5), 'G21LMG': ('AssaultRifle2', 8.6, 4.0)}.items():
    LH[g] = [surface(g, y, z, -1) - 0.05, y, z]
    G[g] = {'template': tpl, 'parts': MAGPARTS, 'drop': ['handle'], 'anchors': [mag_anchor(tpl, g)],
            'match': [{'anchor': 'mag', 't0': 0.01, 't1': 2.2, 'k': 0.8}], 'after': [specs2.left_action('pull', LH[g])]}

PX2M = 0.756 / 16
for g, W in G.items():
    t = W['template']; A = {a['name'] for a in W['anchors']}
    W.setdefault('match', [{'anchor': n, 't0': t0, 't1': t1, 'k': W.get('k', {}).get(n, 0.8)} for n, (t0, t1) in WIN[t].items() if n in A])
if __name__ == '__main__':
    for g, p in {**RH, **LH}.items(): print(g, np.round(p, 2).tolist())
