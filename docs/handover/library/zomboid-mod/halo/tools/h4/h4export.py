import sys,struct,io,json,numpy as np
sys.path.insert(0,'/tmp/claude-0/h4'); sys.path.insert(0,'/tmp/claude-0/halo')
from h4model import *
from glb import GLB
from PIL import Image
S=3.048
def cv(p): return np.stack([p[:,1],p[:,2],p[:,0]],1)
def qcv(q): return [q[1],q[2],q[0],q[3]]
def sid(Sx,v,prefer_b=False):
    a=Sx[v] if v<len(Sx) else ''; b=Sx[v+0x1ab9] if v+0x1ab9<len(Sx) else ''
    if prefer_b: return b if b.startswith('b_') else a
    return a if v<0x1000 else b
def export(r,tag,out,texdir,texmap):
    nm=r.names(); Sx=r.strings(); d=r.d
    k,o=find(r,'mode',tag,nm); M=model(r,o)
    nn,na=blk(r,o,0x30)
    nodes=[]
    for i in range(nn):
        e=na+i*0x70
        nodes.append(dict(name=sid(Sx,struct.unpack_from('<I',d,e)[0],True),parent=struct.unpack_from('<h',d,e+4)[0],t=struct.unpack_from('<3f',d,e+0xc),q=struct.unpack_from('<4f',d,e+0x18)))
    g=GLB()
    for n in nodes: g.j['nodes'].append(dict(name=n['name'],translation=(cv(np.array([n['t']]))[0]*S).tolist(),rotation=qcv(n['q'])))
    for i,n in enumerate(nodes):
        if n['parent']>=0: g.j['nodes'][n['parent']].setdefault('children',[]).append(i)
    roots=[i for i,n in enumerate(nodes) if n['parent']<0]
    def qm(q):
        x,y,z,w=q
        return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
    W=[]
    for n in nodes:
        R=qm(qcv(n['q']));T=cv(np.array([n['t']]))[0]*S
        if n['parent']>=0: PR,PT=W[n['parent']]; W.append((PR@R,PR@T+PT))
        else: W.append((R,T))
    ibm=[]
    for R,T in W:
        X=np.eye(4); X[:3,:3]=R; X[:3,3]=T; ibm.append(np.linalg.inv(X).T.reshape(16))
    g.j['skins']=[dict(joints=list(range(len(nodes))),inverseBindMatrices=g.acc(np.array(ibm,np.float32),'MAT4',5126),skeleton=roots[0])]
    matnames=[nm[x&0xffff].split('\\')[-1] for x in M['mats']]
    mats={}
    def mat(i):
        if i in mats: return mats[i]
        t=None; tn=texmap.get(matnames[i])
        if tn:
            im=Image.open('%s/%s.png'%(texdir,tn)).convert('RGBA'); bio=io.BytesIO(); im.save(bio,'PNG'); t=g.image(bio.getvalue())
        mats[i]=g.material(matnames[i],t); return mats[i]
    nr,ra=blk(r,o,0xc); meshnodes=[]; ntri=0
    regs=[]
    for i in range(nr):
        rn,np_,pa=struct.unpack_from('<III',d,ra+i*16); pa=r.off(r.X(pa)) if np_ else 0
        for j in range(np_):
            pn=struct.unpack_from('<I',d,pa+j*24)[0]; mi,mc=struct.unpack_from('<hH',d,pa+j*24+4)
            regs.append((sid(Sx,rn),sid(Sx,pn),mi,mc))
    for rn,pn,mi,mc in regs:
        prims=[]
        for m in range(mi,mi+mc):
            me=M['meshes'][m]; vb=decode(M['vbs'][me['vbi']],M['comp']); it,ix=M['ibs'][me['ibi']]
            P=cv(vb['p'])*S; N=cv(vb['n']); N/=np.maximum(np.linalg.norm(N,axis=1,keepdims=True),1e-6); UV=vb['uv'].astype(np.float32)
            nmap=M['maps'][m] if m<len(M['maps']) else []
            if 'ni' in vb and me['rigid']<0:
                J=vb['ni'].astype(np.int64); Wt=vb['nw'].astype(np.float32)/255
                if nmap: J=np.array(nmap+[0]*(256-len(nmap)))[J]
            else:
                J=np.zeros((len(P),4),np.int64); J[:,0]=max(me['rigid'],0); Wt=np.zeros((len(P),4),np.float32); Wt[:,0]=1
            s_=Wt.sum(1,keepdims=True); Wt=np.where(s_>0,Wt/np.maximum(s_,1e-6),np.array([1,0,0,0],np.float32))
            for part in me['parts']:
                matn,sort,st,cn=part[:4]
                tr=tris(ix,it,st,cn)
                if not len(tr): continue
                vu=np.unique(tr); rm=np.full(len(P),-1,np.int64); rm[vu]=np.arange(len(vu)); tr2=rm[tr]
                prims.append(dict(attributes=dict(POSITION=g.acc(P[vu].astype(np.float32),'VEC3',5126,34962,True),NORMAL=g.acc(N[vu].astype(np.float32),'VEC3',5126,34962),
                    TEXCOORD_0=g.acc(UV[vu],'VEC2',5126,34962),JOINTS_0=g.acc(J[vu].astype(np.uint8),'VEC4',5121,34962),WEIGHTS_0=g.acc(Wt[vu].astype(np.float32),'VEC4',5126,34962)),
                    indices=g.acc(tr2.astype(np.uint32).reshape(-1),'SCALAR',5125,34963),material=mat(matn)))
                ntri+=len(tr2)
        if prims:
            g.j['meshes'].append(dict(name='%s__%s'%(rn,pn),primitives=prims))
            g.j['nodes'].append(dict(name='%s__%s'%(rn,pn),mesh=len(g.j['meshes'])-1,skin=0)); meshnodes.append(len(g.j['nodes'])-1)
    g.j['scenes'][0]['nodes']=roots+meshnodes
    g.save(out); return dict(tris=ntri,regions=regs,nodes=[n['name'] for n in nodes][:8],mats=matnames)
if __name__=='__main__':
    r=R4(sys.argv[1])
    tm={'storm_cortana_default_body':'storm_cortana_default_body_diff','storm_cortana_default_eyes':'storm_cortana_default_eye_diff','storm_cortana_default_head':'storm_cortana_default_head_diff','storm_cortana_default_hair':'storm_cortana_default_hair_diff'}
    print(export(r,sys.argv[2],sys.argv[3],sys.argv[4],tm))
