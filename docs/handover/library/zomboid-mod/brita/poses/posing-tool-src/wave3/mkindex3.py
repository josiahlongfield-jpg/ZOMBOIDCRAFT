import json, sys; sys.path.insert(0, '.')
from load import *
WEB = '../tool/web/guns/'
ix = json.load(open(WEB + 'index.json'))
have = {g['id'] for g in ix}
W3 = json.load(open('/mnt/project-files/zomboid-mod/brita/tools/wave3/posing-wave3.json'))
for gid in W3:
    if gid in have: continue
    j = json.load(open(WEB + gid + '.json'))
    t = np.concatenate([p[1] for p in load(gid)]).reshape(-1, 3)
    z0 = t[:, 2].min(); s = t[t[:, 2] < z0 + 0.15]
    bore = [round(float((s[:, 0].min() + s[:, 0].max()) / 2), 3), round(float((s[:, 1].min() + s[:, 1].max()) / 2), 3), round(float(z0), 3)]
    e = dict(id=gid, name=j['name'], hold=j['hold'], grip=j['grip'], sights=j.get('sights'), bore=bore)
    ix.append(e)
json.dump(ix, open(WEB + 'index.json', 'w'), separators=(',', ':'))
print(len(ix))
