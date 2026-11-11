"""Brita wave 3 (the 88 new guns, 2026-11-06), from build_local2.py. Brita pilot guns -> local gun files (combat/LocalGuns): <id>.zmesh + <id>.json in OUT, never the repo.
Each gun is packed with pack.py fitted to its anchor (our gun it handles like), its standard magazine as part "mag"."""
import base64, json, os, subprocess, sys
import numpy as np
S = '/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad/'
REPO = '/home/user/zomboidcraft'
OUT = S + 'local/guns3/'; WORK = S + 'local/work/'
os.makedirs(OUT, exist_ok=True); os.makedirs(WORK, exist_ok=True)
BR = "/mnt/user-data/uploads/108600/2200148440/mods/Brita's Weapon Pack/media/textures/"
AR = "/mnt/user-data/uploads/108600/2297098490/mods/ARSENAL(26)GunFighter[MOD 2.0]/media/textures/"
D = json.load(open(S + 'review/out3/data.json')); M = json.load(open(S + 'review/out3/parts.json'))
import glob
for f in glob.glob(S + 'review/out3/m/c*.json'): M.update(json.load(open(f)))
P = json.load(open(S + 'review/parts_export3.json'))
ANCHOR = {'pistol': ('pistol', 'pbr_pistol'), 'magnum': ('magnum', 'pbr_magnum'), 'SMG': ('smg', 'pbr_smg'),
          'assault rifle': ('assault_rifle', 'pbr_assault_rifle'), 'battle rifle': ('battle_rifle', 'pbr_battle_rifle'),
          'heavy sniper': ('heavy_sniper', 'heavy_sniper_mesh'), 'pump shotgun': ('shotgun', 'pbr_shotgun'),
          'lever-action': ('lever_action', 'block_lever_action'), 'rimfire rifle': ('rimfire', 'block_rimfire'),
          'grenade launcher': ('grenade_launcher', 'pbr_grenade_launcher'), 'flamethrower (new)': ('flamethrower', None), 'crossbow': ('crossbow', 'pbr_crossbow')}
AMMO = {'.45 ACP': 'acp45_ammo', '5.7x28mm': 'fn57_ammo', '.45 Colt': 'colt45_ammo', '.500 S&W': 'sw500_ammo', 'rocket': 'pg7v_rocket', '5.45x39mm': 'r545_ammo', '.223 Rem': 'r223_ammo', '4 gauge': 'g4_shell', '10 gauge': 'g10_shell', '20 gauge': 'g20_shell', '.410 bore': 'g410_shell', 'flamer fuel': 'flame_fuel', '40mm grenade': 'grenade_round', '7.62x39mm': 'r762x39_ammo', '.30-06': 'r3006_ammo', '7.62x54R': 'r762x54_ammo', '.38 Special': 'r38_ammo', '.44 Magnum': 'r44_ammo'}
CAL = {'Bullets45': '.45 ACP', 'Bullets50MAG': '.500 S&W', 'Bullets57': '5.7x28mm', 'Bullets9mm': '9mm', '556Bullets': '5.56mm',
       '308Bullets': '.308', '50BMGBullets': '.50 BMG', 'ShotgunShells': '12 gauge', 'Bullets45LC': '.45 Colt',
       'Bullets22': '.22 LR', 'Bolt_Bear': 'crossbow', 'HERocket': 'rocket', 'Bullets44': '.44 Magnum', 'Bullets380': '.380 ACP', 'Bullets38': '.38 Special', '762x39Bullets': '7.62x39mm', '3006Bullets': '.30-06', '762x54rBullets': '7.62x54R', '545x39Bullets': '5.45x39mm', '223Bullets': '.223 Rem', '4gShotgunShells': '4 gauge', '10gShotgunShells': '10 gauge', '20gShotgunShells': '20 gauge', '410gShotgunShells': '.410 bore', 'FlameFuel': 'flamer fuel', '': '40mm grenade'}
KEEP = ['damage', 'range', 'aimSpread', 'hipSpread', 'recoil', 'fireDelayTicks', 'bloomRecover', 'shotNoise', 'magazineSize',
        'selectFire', 'autoRpm', 'pellets']

def raw(key):
    b = base64.b64decode(M[key]); n = int(np.frombuffer(b, np.uint32, 1)[0])
    return np.frombuffer(b, np.float32, n * 3, 4).reshape(-1, 3).copy(), np.frombuffer(b, np.float32, n * 2, 4 + n * 12).reshape(-1, 2).copy()

def find_tex(name):
    for base in (BR, AR):
        for d, _, fs in os.walk(base):
            for f in fs:
                if os.path.splitext(f)[0].lower() == name.lower() and f.lower().endswith('.png'):
                    return os.path.join(d, f)
    return None

def write_obj(path, groups):
    with open(path, 'w') as fh:
        vi = 1
        for name, pos, uv in groups:
            fh.write(f'o {name}\ng {name}\nusemtl {name}\n')
            for p in pos: fh.write(f'v {p[0]:.6f} {p[1]:.6f} {p[2]:.6f}\n')
            for t in uv: fh.write(f'vt {t[0]:.6f} {1 - t[1]:.6f}\n')
            for i in range(0, len(pos), 3):
                fh.write(f'f {vi+i}/{vi+i} {vi+i+1}/{vi+i+1} {vi+i+2}/{vi+i+2}\n')
            vi += len(pos)
    with open(path[:-4] + '.mtl', 'w') as fh:
        for name, _, _ in groups: fh.write(f'newmtl {name}\nKd 1 1 1\n')

only = sys.argv[1:] or None
for g in D['guns']:
    gid = g['id']
    if only and gid not in only: continue
    st = g['stats']; anchor, amesh = ANCHOR[st['anchor']]
    pos, uv = raw('g:' + gid)
    # the muzzle end is +y; the bore's height is the mean z of the most forward vertices; up is the side with less on it
    tip = pos[pos[:, 1] > pos[:, 1].max() - 0.01]; bore = tip[:, 2].mean()
    up = '+z' if g.get('upright') else '-z'   # same flag the review page uses (muzzle offset z < 0); the old count guessed AK74 Alpha, FAMAS, Vz58 wrong
    groups = [('gun', pos, uv)]; tex = [['gun', find_tex(g['tex']) or (S + 'look/out/t/' + g['tex'] + '.png')]]
    for part in P.get(gid, {}).get('parts', []):
        slot = [s for s in g['slots'] if s['slot'] == 'Magazine'][0]
        model = [o for o in slot['opts'] if o['item'] == P[gid]['fitted']['Magazine']][0]['model']
        pp, pu = raw('p:' + model)
        m = np.array(part['matrix'], np.float64).reshape(4, 4).T
        f = pp.astype(np.float64) * [1, 1, -1]                   # into the page's flipped space
        w = (np.c_[f, np.ones(len(f))] @ m.T)[:, :3] * [1, 1, -1]  # placed, then back
        groups.append(('mag', w.astype(np.float32), pu))
        t = D['models'][model].get('tex')
        tex.append(['mag', find_tex(t) if t else None])
    obj = WORK + gid + '.obj'; write_obj(obj, groups)
    spec = {'id': 'local/' + gid, 'obj': obj, 'forward': '+y', 'up': up, 'split': True,
            'groups': {'textures': [[k, v] for k, v in tex if v], 'skip': [k for k, v in tex if not v],
                       'parts': {'mag': ['mag']} if len(groups) > 1 else {}},
            'dest': OUT + gid + '.zmesh'}
    if anchor == 'flamethrower': spec['fit'] = 'battle_rifle'   # new mechanic: sized like a battle rifle
    elif amesh or anchor in ('lever_action', 'rimfire'): spec['fit'] = anchor
    if gid == 'M29_44Carbine': spec['fit'] = 'lever_action'    # a 0.84 m stocked carbine: sized like a carbine, handles as a magnum
    json.dump(spec, open(WORK + gid + '.spec.json', 'w'), indent=1)
    r = subprocess.run(['python3', REPO + '/tools/meshguns/pack.py', WORK + gid + '.spec.json'], capture_output=True, text=True)
    print(gid, anchor, up, (r.stdout.strip().splitlines() or ['?'])[-1][-160:], r.stderr.strip()[-300:])
    stats = {k: v for k, v in st['out'].items() if k in KEEP}
    cal = CAL.get(st['ammo'], st['ammo'])
    spec_out = {'id': gid, 'name': g['name'], 'anchor': anchor, 'anchorMesh': amesh, 'calibre': cal, 'stats': stats}
    if cal in AMMO:
        spec_out['ammo'] = AMMO[cal]
    if gid == 'B93R': spec_out['stats']['burst'] = 3            # Josiah 06:06: add the 3-round burst
    if gid == 'M2A1': spec_out['flame'] = {'reach': 8, 'tank': 100, 'breaksBlocks': False}   # Josiah 06:06: approved
    json.dump(spec_out, open(OUT + gid + '.json', 'w'), indent=1)
