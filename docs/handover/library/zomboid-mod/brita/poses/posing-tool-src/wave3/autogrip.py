import sys, json; sys.path.insert(0,'.')
from load import *
from PIL import Image, ImageDraw
R = 4  # cells per px
FR = dict(pistol=0.35)
def mask(gid):
    t = np.concatenate([p[1] for p in load(gid)]).reshape(-1, 3, 3)
    z0, y0 = t[...,2].min(), t[...,1].min(); z1, y1 = t[...,2].max(), t[...,1].max()
    W, H = int((z1-z0)*R)+2, int((y1-y0)*R)+2
    im = Image.new('L', (W, H), 0); dr = ImageDraw.Draw(im)
    for tr in t: dr.polygon([((p[2]-z0)*R, (y1-p[1])*R) for p in tr], fill=255)
    return np.array(im) > 0, z0, y1
def guess(gid, hold):
    m, z0, y1 = mask(gid)
    H, W = m.shape
    cols = np.where(m.any(0))[0]
    bot = np.array([np.where(m[:, c])[0].max() if m[:, c].any() else -1 for c in range(W)])  # row of lowest filled
    top = np.array([np.where(m[:, c])[0].min() if m[:, c].any() else H for c in range(W)])
    body = bot[cols]
    rb = np.percentile(body, 35)   # receiver bottom (rows grow downward)
    low = bot > rb + 1.5 * R
    # runs of low columns
    runs, c = [], 0
    while c < W:
        if low[c]:
            s = c
            while c < W and low[c]: c += 1
            runs.append((s, c - 1))
        else: c += 1
    last = cols.max()
    cand = [r for r in runs if r[1] < last - 2 * R and (r[1] - r[0]) >= 0.6 * R]
    if hold == 'pistol':
        cand = runs
        r = max(cand, key=lambda r: r[1] - r[0]) if cand else None
    else:
        r = max(cand, key=lambda r: r[1]) if cand else None   # rearmost protrusion that isn't the stock
    if r is None: return None
    depth = (bot[r[0]:r[1]+1].max() - rb) / R
    f = FR.get(hold, 0.55)
    row = int(rb + depth * f * R)
    # at the palm's height, the grip's own filled span: the run of filled cells that holds the protrusion's deepest column
    deep = r[0] + int(np.argmax(bot[r[0]:r[1]+1]))
    rowcells = m[row]
    a = b = deep
    if not rowcells[deep]:
        near = [c for c in range(r[0], r[1]+1) if rowcells[c]]
        if near: a = b = min(near, key=lambda c: abs(c - deep))
    while a > 0 and rowcells[a-1]: a -= 1
    while b < W-1 and rowcells[b+1]: b += 1
    zc = ((a + b) / 2 / R + z0) if hold == "pistol" else (b / R + z0 - 0.3)
    y = y1 - row / R
    return [8, round(float(y), 1), round(float(zc), 1)]
if __name__ == '__main__':
    W3 = json.load(open('/mnt/project-files/zomboid-mod/brita/tools/wave3/posing-wave3.json'))
    HOLD = dict(assault_rifle='rifle', battle_rifle='rifle', rimfire='rifle', lever_action='rifle', smg='smg', heavy_sniper='sniper',
                shotgun='shotgun', pistol='pistol', magnum='pistol', grenade_launcher='launcher', flamethrower='rifle')
    out = {}
    for g, v in W3.items():
        try: out[g] = guess(g, HOLD[v['anchor']])
        except Exception as e: out[g] = None; print(g, 'ERR', e)
    json.dump(out, open('grips3_auto.json', 'w'))
    print(sum(v is None for v in out.values()), 'missing')
