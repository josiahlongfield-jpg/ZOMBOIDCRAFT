"""headroom.py DUMP [N]: headroom over every stair in a preview dump (CountyPreview, SitePreview).

Lists, per tread block, the treads with anything solid in the N cells over them (default 4:
a jump up a stair lifts a body's head about 4.05 over the tread block, so 3 clear bumps it),
what is in the way and where, and how many treads sit right under another flight (a stacked
stair, which no clearing pass can fix: lay the flights so the same lane repeats only every
second storey). Treads are any stair or step block bar ladders and upside-down stairs; air,
water, plants, rails, banisters and doors count as clear.
"""
import json, sys, re, collections
import numpy as np
dump = sys.argv[1]; N = int(sys.argv[2]) if len(sys.argv) > 2 else 4
m = json.load(open(dump + ".json")); W, H, D = m["size"]; ox, oy, oz = m.get("world_origin", m["origin"]); pal = m["palette"]
vox = np.fromfile(dump + ".vox", dtype="<u2").reshape(H, D, W)
def base(n): return n.split("[")[0].replace("minecraft:", "")
def prop(n, k):
    mm = re.search(k + r"=([a-z_0-9]+)", n); return mm.group(1) if mm else None
tread = np.zeros(len(pal), bool); passable = np.zeros(len(pal), bool)
for i, n in enumerate(pal):
    b = base(n)
    if ("stair" in b or re.search(r"(^|_)step(_|$)", b)) and "ladder" not in b and prop(n, "half") != "top":
        tread[i] = True
    if b in ("air", "water", "cave_air", "light") or b.startswith("gc_") or "grass" in b or "weeds" in b or "banister" in b \
            or b in ("poppy", "dandelion", "fern", "bush", "oxeye_daisy", "cornflower", "leaf_litter", "vine") or "rail" in b \
            or "door" in b:
        passable[i] = True
tid = np.where(tread)[0]
ys, zs, xs = np.where(np.isin(vox, tid))
stats = collections.Counter(); bad = collections.Counter(); what = collections.defaultdict(collections.Counter); ex = {}
over = collections.Counter()
for y, z, x in zip(ys, zs, xs):
    t = base(pal[vox[y, z, x]]); stats[t] += 1
    if y + N >= H: continue
    hit = None
    for k in range(1, N + 1):
        c = vox[y + k, z, x]
        if tread[c]:
            over[t] += 1; hit = None
            ex.setdefault((t, "flight"), (int(x + ox), int(y + oy), int(z + oz))); break
        if not passable[c]:
            hit = (k, base(pal[c])); break
    if hit:
        bad[t] += 1; what[t][hit] += 1
        ex.setdefault((t, hit[1]), (int(x + ox), int(y + oy), int(z + oz)))
for t in sorted(stats, key=lambda s: -bad[s]):
    print(f"{t:22s} treads {stats[t]:6d}  blocked {bad[t]:5d}  under a flight {over[t]:4d}")
    if over[t]:
        print(f"      {over[t]:5d} under the flight {N} over        e.g. {ex[(t, 'flight')]}")
    for (k, b), n in what[t].most_common(8):
        print(f"      {n:5d} at +{k} {b:30s} e.g. {ex[(t, b)]}")
