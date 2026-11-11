import sys, os, json, gzip, base64; sys.path.insert(0, '.')
from load import *
sys.path.insert(0, '/tmp/claude-0/-home-user-zomboidcraft/659694dd-f437-5058-9359-dae443b02255/scratchpad/fp')
import handfit
OUT = '/tmp/claude-0/-home-user-zomboidcraft/659694dd-f437-5058-9359-dae443b02255/scratchpad/tool/web/guns/'
os.makedirs(OUT, exist_ok=True)
HOLD = dict(assault_rifle='rifle', battle_rifle='rifle', rimfire='rifle', lever_action='rifle', smg='smg', heavy_sniper='sniper',
            shotgun='shotgun', pistol='pistol', magnum='pistol', crossbow='crossbow', grenade_launcher='launcher', flamethrower='rifle')
# where her trigger hand's palm sits on each gun (gun pixels), read off side views
GRIP = dict(AAC_Honey=[8, 5.4, 12.2], AR57_PDW=[8, 6.0, 18.4], American180=[8, 5.2, 14.2], AssaultRifle2=[8, 6.9, 16.6],
            BOAR=[8, 5.0, 13.8], L86=[8, 6.6, 12.9], M2Carbine=[8, 7.0, 19.0], M870_CQB=[8, 6.0, 12.5], RPG_7=[8, 5.8, 4.6],
            TenPointTurbo_XLT=[8, 6.3, 14.6], Winchester1866=[8, 5.9, 15.2], DSHK=[8, 7.8, 23.3], Colt1911=[8, 8.2, 10.0], SW_M500=[8, 8.2, 12.0])
GRIP.update(json.load(open('grips2.json')))
GRIP.update(json.load(open('grips3.json')))   # wave 3 (88 guns, 2026-11-06): autogrip.py guesses corrected off side sheets   # wave 2 (27 guns, 2026-11-03), read off side views like the first 14
# the tool's own grip (index.json) wins, so a rebuilt gun keeps the grip his poses were made on
try:
    _ix = json.load(open(OUT + 'index.json')); _ix = _ix if isinstance(_ix, list) else _ix.get('guns', list(_ix.values()))
    GRIP.update({g['id']: g['grip'] for g in _ix if isinstance(g, dict) and 'grip' in g})
except FileNotFoundError: pass
HOLD_ID = dict(KAC_M203='rifle', M29_44Carbine='rifle')   # the stand-alone M203 has a stock: shouldered like a rifle
b64gz = b = lambda arr: base64.b64encode(gzip.compress(arr.tobytes(), 9)).decode()
index = []
for gid in (sys.argv[1:] or GUNS):
    j = json.load(open(D + gid + '.json'))
    t = np.concatenate([p[1] for p in load(gid)])
    mid = 'brita_' + gid
    fp._MESH[mid] = t
    f, lo = handfit.field(mid)
    tq = np.round(t.reshape(-1) * 1000).astype('<i2')
    g = dict(id=gid, name=j['name'], hold=HOLD_ID.get(gid, HOLD[j['anchor']]), grip=GRIP[gid], sights=j.get('sights'),
             tris=b64gz(tq), ntri=len(t),
             field=dict(shape=list(f.shape), lo=[float(c) for c in lo], vox=handfit.VOX, data=b64gz(np.clip(f, -127, 127).astype(np.int8))))
    json.dump(g, open(OUT + gid + '.json', 'w'), separators=(',', ':'))
    print(gid, g['hold'], f.shape, os.path.getsize(OUT + gid + '.json') // 1024, 'KB', flush=True)
