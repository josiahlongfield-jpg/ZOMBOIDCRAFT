import numpy as np,json,struct,sys
m=sys.argv[1]; boxes=json.load(open(sys.argv[2])); bsps=sys.argv[3].split(',') if len(sys.argv)>3 and sys.argv[3] else []
f=open(f'/tmp/claude-0/halo/bdglb/{m}.glb','rb'); f.read(12); l,_=struct.unpack('<II',f.read(8)); j=json.loads(f.read(l)); bl,_=struct.unpack('<II',f.read(8)); B=f.read(bl)
CT={5126:np.float32,5125:np.uint32,5123:np.uint16,5121:np.uint8}; NC={'SCALAR':1,'VEC2':2,'VEC3':3}
def acc(i):
    a=j['accessors'][i]; v=j['bufferViews'][a['bufferView']]
    return np.frombuffer(B,CT[a['componentType']],a['count']*NC[a['type']],v.get('byteOffset',0)+a.get('byteOffset',0)).reshape(a['count'],-1)
clusters=[(n.get('mesh'),np.array(n['matrix']).reshape(4,4).T if 'matrix' in n else None,n) for n in j['nodes'] if (n.get('name') or '').startswith('cluster')]
def M_of(n):
    M=np.eye(4)
    if 'matrix' in n: return np.array(n['matrix']).reshape(4,4).T
    if 'translation' in n: M[:3,3]=n['translation']
    return M  # clusters have no rotation (checked below)
for _,_,n in clusters: assert 'rotation' not in n and 'scale' not in n, n
prims=[]
for mi,_,n in clusters:
    M=M_of(n)
    for p in j['meshes'][mi]['primitives']:
        V=acc(p['attributes']['POSITION']).astype(np.float64)@M[:3,:3].T+M[:3,3]; T=acc(p['indices']).reshape(-1,3).astype(np.int64)
        prims.append((j['materials'][p['material']]['name'] if 'material' in p else 'x',V,T))
cols=[np.load(f'/tmp/claude-0/halo/layout/{m}/{b}_collision.npz') for b in bsps]
def write(path,parts):
    pad=lambda b:b+b'\0'*((4-len(b)%4)%4); buf=b''; bv=[];ac=[];meshes=[];mats=[]
    for name,V,T in parts:
        V=V.astype(np.float32); I=T.astype(np.uint32).ravel()
        bv.append({"buffer":0,"byteOffset":len(buf),"byteLength":V.nbytes}); buf+=pad(V.tobytes())
        bv.append({"buffer":0,"byteOffset":len(buf),"byteLength":I.nbytes}); buf+=pad(I.tobytes())
        ac.append({"bufferView":len(bv)-2,"componentType":5126,"count":len(V),"type":"VEC3","min":V.min(0).tolist(),"max":V.max(0).tolist()})
        ac.append({"bufferView":len(bv)-1,"componentType":5125,"count":len(I),"type":"SCALAR"})
        mats.append({"name":name}); meshes.append({"attributes":{"POSITION":len(ac)-2},"indices":len(ac)-1,"material":len(mats)-1})
    jj={"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"name":"col","mesh":0}],"meshes":[{"primitives":meshes}],"materials":mats,"buffers":[{"byteLength":len(buf)}],"bufferViews":bv,"accessors":ac}
    js=json.dumps(jj).encode(); js+=b' '*((4-len(js)%4)%4)
    open(path,'wb').write(struct.pack('<III',0x46546C67,2,28+len(js)+len(buf))+struct.pack('<II',len(js),0x4E4F534A)+js+struct.pack('<II',len(buf),0x004E4942)+buf)
def crop(V,T,x,z,hw,hd):
    c=V[T].mean(1); k=(abs(c[:,0]-x)<hw)&(abs(c[:,2]-z)<hd)
    if not k.any(): return None
    Tk=T[k]; u,inv=np.unique(Tk,return_inverse=True); return V[u],inv.reshape(-1,3)
for k,(name,x,z,hw,hd) in enumerate(boxes):
    parts=[]
    for nm,V,T in prims:
        r=crop(V,T,x,z,hw,hd)
        if r: parts.append((nm,)+r)
    for d in cols:
        T=d['T']; M=d['M']; T=T[M<999]
        r=crop(d['V'],T,x,z,hw,hd)
        if r: parts.append(('missioncoll',)+r)
    if parts:
        write(f'/tmp/claude-0/halo/web/land/col/{m}_{k:02d}.glb',parts)
    print(k,name,len(parts),sum(len(p[2]) for p in parts))
