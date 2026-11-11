import json,struct,numpy as np,glob,sys
sys.path.insert(0,'/tmp/claude-0/halo/web/land')
m,bd,R=sys.argv[1],sys.argv[2],float(sys.argv[3])
def qm(n):
    if 'matrix' in n: return np.array(n['matrix'],'f8').reshape(4,4).T
    x,y,z,w=n.get('rotation',[0,0,0,1]);t=n.get('translation',[0,0,0]);s=n.get('scale',[1,1,1])
    R_=np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])*s
    M=np.eye(4);M[:3,:3]=R_;M[:3,3]=t;return M
def glb(path):
    b=open(path,'rb').read();jl=struct.unpack('<I',b[12:16])[0];J=json.loads(b[20:20+jl]);b0=20+jl+8
    def acc(i,dt,n):
        a=J['accessors'][i];bv=J['bufferViews'][a['bufferView']];o=b0+bv.get('byteOffset',0)+a.get('byteOffset',0)
        return np.frombuffer(b,dt,a['count']*n,o).reshape(-1,n) if n>1 else np.frombuffer(b,dt,a['count'],o)
    out=[]
    def walk(ni,P):
        n=J['nodes'][ni];M=P@qm(n)
        if 'mesh' in n:
            for p in J['meshes'][n['mesh']]['primitives']:
                V=acc(p['attributes']['POSITION'],'<f4',3).astype('f8');ia=J['accessors'][p['indices']]
                dt={5125:'<u4',5123:'<u2',5121:'u1'}[ia['componentType']];I=acc(p['indices'],dt,1).astype('u4').reshape(-1,3)
                V=V@M[:3,:3].T+M[:3,3];out.append((V.astype('f4'),I,J['materials'][p.get('material',0)].get('name','').lower()))
        for c in n.get('children',[]): walk(c,M)
    for r in J['scenes'][J.get('scene',0)]['nodes']: walk(r,np.eye(4))
    return out
Vs=[];Ts=[];n=0
for x in sorted(glob.glob(f'/tmp/claude-0/halo/layout/{m}/*_collision.npz')):
    if '_000_' in x or (m=='m60' and x.endswith('m20_090_collision.npz')): continue
    d=np.load(x);Vs.append(d['V'].astype('f4'));Ts.append(d['T'].astype('u4')+n);n+=len(d['V'])
VL=np.concatenate(Vs);c=(VL.min(0)+VL.max(0))/2
nl=sum(len(t) for t in Ts)
def cls(nm):
    if any(w in nm for w in ['cloud','ray','black','sky','shadow','card','subsurface','glow','beam','fx','tree']): return -1
    if any(w in nm for w in ['water','ocean','sea','magma','lake']): return 2 if 'magma' not in nm else 5
    if any(w in nm for w in ['glacier','ice','snow']): return 3
    if any(w in nm for w in ['terrain','ground','plains','valley','floor']): return 1
    if any(w in nm for w in ['mountain','rock','cliff','ridge','mesa','pinnacle','boulder','knob']): return 4
    if any(w in nm for w in ['metal','skyscraper','bldg','concrete','window','wall','panel','glass','hulk','ship','tower','build','airfield','airport']): return 6
    return 1
Cs=[np.zeros(n,'u1')]
for V,I,nm in glb(bd):
    k0=cls(nm)
    if k0<0: continue
    t=V[I];lo=t.min(1);hi=t.max(1);k=(lo[:,0]<c[0]+R)&(hi[:,0]>c[0]-R)&(lo[:,2]<c[2]+R)&(hi[:,2]>c[2]-R)
    if not k.any(): continue
    Vs.append(V);Ts.append(I[k]+n);n+=len(V);Cs.append(np.full(len(V),k0,'u1'))
V=np.concatenate(Vs);T=np.concatenate(Ts)
a=V[T[:,1]]-V[T[:,0]];b=V[T[:,2]]-V[T[:,0]];cr=np.cross(a,b);ln=np.linalg.norm(cr,axis=1)+1e-9
e=np.maximum(np.maximum(np.linalg.norm(a,axis=1),np.linalg.norm(b,axis=1)),np.linalg.norm(V[T[:,2]]-V[T[:,1]],axis=1))
big=(((ln/2>300)|((e>25)&(ln/2/(e*e)<0.12)))&(np.abs(cr[:,1]/ln)>0.999));big[nl:]=True
C=np.concatenate(Cs);pad=(-len(C))%4;C=np.concatenate([C,np.zeros(pad,'u1')])
T=np.concatenate([T[~big],T[big]]);nb=int(big.sum())
open(f'/tmp/claude-0/halo/web/land/{m}.bin','wb').write(struct.pack('<III',len(V),len(T),nb)+V.astype('f4').tobytes()+T.astype('u4').tobytes()+C.tobytes())
yy=np.median(VL[:,1]);ex=(VL.max(0)-VL.min(0));print(m,len(T),nb,c.round(),round(float(yy)),ex.round())
