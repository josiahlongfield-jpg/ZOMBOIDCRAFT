"""Export a Reach render model to GLB (skinned, textured, every region/permutation as its own mesh node)."""
import sys,struct,io,numpy as np
sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
from rres import RR
from reach import X
from rmodel import find_tag,model,decode_vb,strips
from rshader import shader_bitmaps,shader_datums
from rbitmap import bitmap
from glb import GLB
from PIL import Image
S=3.048
def cv(p): return np.stack([p[:,1],p[:,2],p[:,0]],1)  # halo (fwd,left,up) -> gl (X=left,Y=up,Z=fwd)
def qcv(q): return [q[1],q[2],q[0],q[3]]
def blk(r,o,k):
    c,a=struct.unpack_from('<II',r.d,o+k); return c,(r.off(X(a)) if c else 0)
def info(r,o):
    d=r.d
    nn,na=blk(r,o,0x30)
    names=[r.sid(struct.unpack_from('<I',d,na+i*0x60)[0]) for i in range(nn)]
    c,a=blk(r,o,0xb0); maps=[]
    for i in range(c):
        n,b=struct.unpack_from('<II',d,a+i*12); b=r.off(X(b)) if n else 0; maps.append(list(d[b:b+n]))
    nr,ra=blk(r,o,0xc); regs=[]
    for i in range(nr):
        rn,np_,pa=struct.unpack_from('<III',d,ra+i*16); pa=r.off(X(pa)) if np_ else 0
        perms=[(r.sid(struct.unpack_from('<I',d,pa+j*24)[0]),)+struct.unpack_from('<hH',d,pa+j*24+4)+(struct.unpack_from('<4I',d,pa+j*24+8),) for j in range(np_)]
        regs.append((r.sid(rn),perms))
    return names,maps,regs
TEXCACHE={}
COLORS=None
COLTAG=''
def tex_for(r,nm,sd,maxsz):
    bl=shader_bitmaps(r,sd,nm)
    if not bl or not bl[0]: return None,None
    b=bl[0]
    cc=next((x for x in bl[1:] if x and (x.endswith('_cc') or 'change_color' in x or x.endswith('_cc_mask'))),None)
    key=(b,cc,tuple(tuple(c) if c else None for c in COLORS) if (cc and COLORS) else None)
    if key in TEXCACHE: return TEXCACHE[key],b+('_'+COLTAG if key[2] else '')
    try:
        k,bo=find_tag(r,'bitm',b,nm); im,inf=bitmap(r,bo); im=im.convert('RGBA')
        if max(im.size)>maxsz: im.thumbnail((maxsz,maxsz))
        if key[2]:
            k2,co=find_tag(r,'bitm',cc,nm); cm,_=bitmap(r,co); cm=np.asarray(cm.convert('RGBA').resize(im.size),np.float32)/255
            a=np.asarray(im,np.float32)/255; f=np.ones_like(a[:,:,:3])
            if cm[:,:,3].min()>0.99: cm=cm.copy(); cm[:,:,3]=0  # DXT1 masks have no alpha channel
            for ch,col in enumerate(COLORS[:4]):
                if col is None: continue
                m=cm[:,:,ch:ch+1]; f*=(1-m)+m*np.array(col,np.float32)
            a[:,:,:3]*=f; im=Image.fromarray((np.clip(a,0,1)*255).astype(np.uint8))
        TEXCACHE[key]=im
    except Exception as e:
        print('  tex fail',b,e); TEXCACHE[key]=None
    return TEXCACHE[key],b+('_'+COLTAG if key[2] else '')
def export(r,nm,tagname,out,maxsz=1024,only=None,want_png_dir=None,pick=None,colors=None):
    """pick: {region: perm} keeps only those permutations (and their add-on pieces); colors: 4 rgb change colours baked into textures"""
    if pick is not None: only=lambda rn,pn: pick.get(rn)==pn
    global COLORS,COLTAG
    COLORS=colors; COLTAG=('c%x'%(abs(hash(str(colors)))&0xffffff)) if colors else ''
    k,o=find_tag(r,'mode',tagname,nm)
    Mo=model(r,o,nm); names,maps,regs=info(r,o)
    g=GLB()
    # skeleton
    nodes=Mo['nodes']; base=len(g.j['nodes'])
    for i,n in enumerate(nodes):
        g.j['nodes'].append(dict(name=names[i],translation=(cv(np.array([n['t']]))[0]*S).tolist(),rotation=qcv(n['q'])))
    for i,n in enumerate(nodes):
        if n['parent']>=0: g.j['nodes'][base+n['parent']].setdefault('children',[]).append(base+i)
    roots=[base+i for i,n in enumerate(nodes) if n['parent']<0]
    # inverse bind from FK
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
        M=np.eye(4); M[:3,:3]=R; M[:3,3]=T; ibm.append(np.linalg.inv(M).T.reshape(16))
    g.j['skins']=[dict(joints=list(range(base,base+len(nodes))),inverseBindMatrices=g.acc(np.array(ibm,np.float32),'MAT4',5126),skeleton=roots[0])]
    # materials
    sds=shader_datums(r,o); matidx={}; texidx={}
    def mat(i):
        if i in matidx: return matidx[i]
        im,b=tex_for(r,nm,sds[i],maxsz) if i<len(sds) else (None,None)
        t=None
        if im is not None:
            if b in texidx: t=texidx[b]
            else:
                bio=io.BytesIO(); im.save(bio,'PNG'); t=g.image(bio.getvalue()); texidx[b]=t
            if want_png_dir: im.save('%s/%s.png'%(want_png_dir,b.split('\\')[-1]))
        sname=nm[sds[i]&0xffff].split('\\')[-1] if i<len(sds) and (sds[i]&0xffff)<len(nm) else 'mat%d'%i
        matidx[i]=g.material(sname,t,alpha=False); return matidx[i]
    comp=Mo['comps'][0] if Mo['comps'] else None
    meshnodes=[]; tris=0
    used=set()
    for rname,perms in regs:
        for pname,mi,mc,_mask in perms:
            if mi<0 or mc==0: continue
            if only and not only(rname,pname): continue
            prims=[]
            for m in range(mi,mi+mc):
                me=Mo['meshes'][m]
                if me['vbi'][0]<0 or me['ibi'][0]<0: continue
                vb=decode_vb(Mo['vbs'][me['vbi'][0]],comp)
                it,ix=Mo['ibs'][me['ibi'][0]]
                P=cv(vb['p'])*S; N=cv(vb['n']); N/=np.maximum(np.linalg.norm(N,axis=1,keepdims=True),1e-6)
                UV=vb['uv'].astype(np.float32)
                nmap=maps[m] if m<len(maps) else []
                if 'ni' in vb and me['rigid']<0:
                    J=vb['ni'].astype(np.int64); Wt=vb['nw'].astype(np.float32)/255.0
                    if nmap: J=np.array(nmap+[0]*(256-len(nmap)))[J]
                else:
                    J=np.zeros((len(P),4),np.int64); J[:,0]=max(me['rigid'],0); Wt=np.zeros((len(P),4),np.float32); Wt[:,0]=1
                s_=Wt.sum(1,keepdims=True); Wt=np.where(s_>0,Wt/np.maximum(s_,1e-6),np.array([1,0,0,0],np.float32))
                for (matn,sort,st,cn,ss,sc,ptype,flags,vc) in me['parts']:
                    seg=ix[st:st+cn]
                    tr=np.array(strips(seg) if it==5 else [tuple(seg[q:q+3]) for q in range(0,len(seg)-2,3)],np.int64).reshape(-1,3)
                    if not len(tr): continue
                    vu=np.unique(tr); remap=np.full(len(P),-1,np.int64); remap[vu]=np.arange(len(vu))
                    tr2=remap[tr]
                    # halo winding is clockwise w.r.t. gl after axis remap? keep and check
                    a=dict(attributes=dict(POSITION=g.acc(P[vu].astype(np.float32),'VEC3',5126,34962,True),NORMAL=g.acc(N[vu].astype(np.float32),'VEC3',5126,34962),
                        TEXCOORD_0=g.acc(UV[vu],'VEC2',5126,34962),JOINTS_0=g.acc(J[vu].astype(np.uint8),'VEC4',5121,34962),WEIGHTS_0=g.acc(Wt[vu].astype(np.float32),'VEC4',5126,34962)),
                        indices=g.acc(tr2.astype(np.uint32).reshape(-1),'SCALAR',5125,34963),material=mat(matn))
                    prims.append(a); tris+=len(tr2)
            if prims:
                g.j['meshes'].append(dict(name='%s__%s'%(rname,pname),primitives=prims))
                g.j['nodes'].append(dict(name='%s__%s'%(rname,pname),mesh=len(g.j['meshes'])-1,skin=0)); meshnodes.append(len(g.j['nodes'])-1)
    # instanced attachments (armor pieces): one subpart per instance placement, rigid to its node
    inst=[]
    ic,ia=blk(r,o,0x20); imesh=struct.unpack_from('<h',r.d,o+0x1c)[0]
    if ic and 0<=imesh<len(Mo['meshes']):
        me=Mo['meshes'][imesh]; e=r.off(X(struct.unpack_from('<I',r.d,o+0x6c)[0]))+imesh*0x5c
        ns,sa=blk(r,e,0xc); subs=[struct.unpack_from('<iiHH',r.d,sa+i*16) for i in range(ns)]
        vb=decode_vb(Mo['vbs'][me['vbi'][0]],comp); it,ix=Mo['ibs'][me['ibi'][0]]
        P=cv(vb['p'])*S; N=cv(vb['n']); N/=np.maximum(np.linalg.norm(N,axis=1,keepdims=True),1e-6); UV=vb['uv'].astype(np.float32)
        for i in range(min(ic,ns)):
            iname=r.sid(struct.unpack_from('<I',r.d,ia+i*60)[0]); node=struct.unpack_from('<i',r.d,ia+i*60+4)[0]
            st,cn,pi,vc=subs[i]; seg=ix[st:st+cn]
            tr=np.array(strips(seg) if it==5 else [tuple(seg[q:q+3]) for q in range(0,len(seg)-2,3)],np.int64).reshape(-1,3)
            inst.append(iname)
            if not len(tr): continue
            if pick is not None and not any(pn==pick.get(rn) and ((mask[i//32]>>(i%32))&1) for rn,ps in regs for (pn,_a,_b,mask) in ps): continue
            vu=np.unique(tr); remap=np.full(len(P),-1,np.int64); remap[vu]=np.arange(len(vu)); tr2=remap[tr]
            J=np.zeros((len(vu),4),np.uint8); J[:,0]=max(node,0); Wt=np.zeros((len(vu),4),np.float32); Wt[:,0]=1
            a=dict(attributes=dict(POSITION=g.acc(P[vu].astype(np.float32),'VEC3',5126,34962,True),NORMAL=g.acc(N[vu].astype(np.float32),'VEC3',5126,34962),
                TEXCOORD_0=g.acc(UV[vu],'VEC2',5126,34962),JOINTS_0=g.acc(J,'VEC4',5121,34962),WEIGHTS_0=g.acc(Wt,'VEC4',5126,34962)),
                indices=g.acc(tr2.astype(np.uint32).reshape(-1),'SCALAR',5125,34963),material=mat(me['parts'][pi][0]))
            tris+=len(tr2)
            nmn='inst__'+iname.split('__(')[0]+'__'+(iname.split('__(')[1].rstrip(')') if '__(' in iname else '')
            g.j['meshes'].append(dict(name=nmn,primitives=[a])); g.j['nodes'].append(dict(name=nmn,mesh=len(g.j['meshes'])-1,skin=0)); meshnodes.append(len(g.j['nodes'])-1)
    # which pieces each region/permutation turns on
    manifest={}
    for rn,ps in regs:
        for pn,mi,mc,mask in ps:
            on=[inst[b] for b in range(len(inst)) if (mask[b//32]>>(b%32))&1]
            manifest.setdefault(rn,{})[pn]=dict(mesh='%s__%s'%(rn,pn) if mi>=0 and mc else None,pieces=on)
    g.j['scenes'][0]['nodes']=roots+meshnodes
    g.j['scenes'][0]['extras']=dict(permutations=manifest)
    g.save(out); return dict(tris=tris,meshes=len(meshnodes),manifest=manifest,instances=inst)
if __name__=='__main__':
    r=RR(sys.argv[1]); nm=r.names()
    print(export(r,nm,sys.argv[2],sys.argv[3]))
