import json,struct,numpy as np,sys,collections
m=sys.argv[1]
f=open(f'/tmp/claude-0/halo/bdglb/{m}.glb','rb'); f.read(12); l,t=struct.unpack('<II',f.read(8)); j=json.loads(f.read(l))
acc=j['accessors']
def mbox(mi):
    lo=np.array([1e9]*3);hi=-lo
    for p in j['meshes'][mi]['primitives']:
        a=acc[p['attributes']['POSITION']]; lo=np.minimum(lo,a['min']);hi=np.maximum(hi,a['max'])
    return lo,hi
def mat(n):
    if 'matrix' in n: return np.array(n['matrix']).reshape(4,4).T
    M=np.eye(4)
    if 'translation' in n: M[:3,3]=n['translation']
    if 'rotation' in n:
        x,y,z,w=n['rotation']
        R=np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
        M[:3,:3]=R
    if 'scale' in n: M[:3,:3]=M[:3,:3]@np.diag(n['scale'])
    return M
rows=[]
def walk(i,P):
    n=j['nodes'][i]; M=P@mat(n)
    if 'mesh' in n:
        lo,hi=mbox(n['mesh'])
        c=np.array([[x,y,z,1] for x in (lo[0],hi[0]) for y in (lo[1],hi[1]) for z in (lo[2],hi[2])])@M.T
        rows.append((n.get('name'),n['mesh'],c[:,:3].min(0),c[:,:3].max(0)))
    for c in n.get('children',[]): walk(c,M)
for r in j['scenes'][0]['nodes']: walk(r,np.eye(4))
print(len(rows))
rows.sort(key=lambda r:-(r[3][1]-r[2][1]))
for nm,mi,lo,hi in rows[:60]:
    s=hi-lo; print(f"{nm:20s} mesh{mi:5d} {j['meshes'][mi].get('name','')[:30]:30s} size {s[0]:7.0f}{s[1]:7.0f}{s[2]:7.0f}  base {lo[0]:8.0f}{lo[1]:7.0f}{lo[2]:8.0f}")

print('\n==== distinct tall defs')
import re
D=collections.defaultdict(list)
for nm,mi,lo,hi in rows:
    mm=re.match(r'inst\d+_def(\d+)',nm or '')
    if mm: D[int(mm.group(1))].append((lo,hi,nm,mi))
def matnames(mi):
    c=collections.Counter()
    for p in j['meshes'][mi]['primitives']:
        if 'material' in p: c[j['materials'][p['material']]['name']]+=1
    return [k for k,_ in c.most_common(4)]
out=[]
for d,L in D.items():
    lo,hi,nm,mi=L[0]; s=hi-lo
    if s[1]<120 or max(s[0],s[2])>700: continue
    ntri=sum(acc[p['indices']]['count']//3 for p in j['meshes'][mi]['primitives'])
    dist=min(np.hypot((l[0]+h[0])/2-250,(l[2]+h[2])/2-200) for l,h,_,_ in L)
    out.append((d,len(L),s,ntri,dist,matnames(mi),[nm for _,_,nm,_ in L][:3]))
out.sort(key=lambda r:r[4])
for d,n,s,ntri,dist,mats,nms in out:
    print(f"def{d:4d} x{n:3d} size {s[0]:5.0f}{s[1]:6.0f}{s[2]:5.0f} tris {ntri:6d} nearest {dist:6.0f} m  {mats}")
