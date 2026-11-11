import json,struct,numpy as np,sys,collections,re,pickle
exec(open('/tmp/claude-0/-home-user-zomboidcraft/bb435080-348c-5e82-b018-9d51a3f60885/scratchpad/towers.py').read().split("print(len(rows))")[0])
R=[r for r in rows if (r[0] or '').startswith('inst') and max(r[3][0]-r[2][0],r[3][2]-r[2][2])<400 and r[3][1]-r[2][1]>8]
n=len(R); par=list(range(n))
def f(a):
    while par[a]!=a: par[a]=par[par[a]]; a=par[a]
    return a
lo=np.array([r[2] for r in R]); hi=np.array([r[3] for r in R]); g=2.0
for a in range(n):
    ov=np.where((lo[:,0]<=hi[a,0]+g)&(hi[:,0]>=lo[a,0]-g)&(lo[:,2]<=hi[a,2]+g)&(hi[:,2]>=lo[a,2]-g)&(lo[:,1]<=hi[a,1]+g)&(hi[:,1]>=lo[a,1]-g))[0]
    for b in ov: par[f(b)]=f(a)
G=collections.defaultdict(list)
for a in range(n): G[f(a)].append(a)
B=[]
for k,L in G.items():
    l=lo[L].min(0); h=hi[L].max(0); s=h-l
    if s[1]<150: continue
    tris=sum(sum(acc[p['indices']]['count']//3 for p in j['meshes'][R[a][1]]['primitives']) for a in L)
    mats=collections.Counter()
    for a in L:
        for p in j['meshes'][R[a][1]]['primitives']:
            if 'material' in p: mats[j['materials'][p['material']]['name']]+=1
    defs=set(re.match(r'inst\d+_def(\d+)',R[a][0]).group(1) for a in L)
    B.append(dict(names=[R[a][0] for a in L],lo=l.tolist(),hi=h.tolist(),tris=tris,defs=len(defs),mats=[m for m,_ in mats.most_common(6)],
                  dist=float(np.hypot((l[0]+h[0])/2-250,(l[2]+h[2])/2-200))))
B.sort(key=lambda b:-(b['hi'][1]-b['lo'][1]))
json.dump(B,open('/tmp/claude-0/-home-user-zomboidcraft/bb435080-348c-5e82-b018-9d51a3f60885/scratchpad/bldg_%s.json'%m,'w'))
print(len(B),'buildings over 150 m')
for i,b in enumerate(B[:70]):
    s=np.array(b['hi'])-np.array(b['lo']); c=(np.array(b['hi'])+np.array(b['lo']))/2
    print(f"{i:3d} h{s[1]:5.0f} w{s[0]:4.0f}x{s[2]:4.0f} top{b['hi'][1]:5.0f} at {c[0]:6.0f},{c[2]:6.0f} d{b['dist']:5.0f} parts{len(b['names']):3d} defs{b['defs']:3d} tris{b['tris']:6d} {','.join(x[:22] for x in b['mats'][:4])}")
