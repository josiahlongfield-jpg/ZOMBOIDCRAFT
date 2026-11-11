"""Export a Reach level BSP (Lbsp render geometry + sbsp instances) to one textured GLB."""
import sys,struct,io,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
from reach import X
from rmodel import strips
import rsbsp, export
from glb import GLB
S=3.048
def cv(p): return np.stack([p[:,1],p[:,2],p[:,0]],1)
def half(u): return u.view('<f2').astype(np.float32)
def run(mapp,sbsp_name,out,maxsz=512,inst_res=None):
    r=RR(mapp); nm=r.names(); d=r.d
    ks=[k for k,t in enumerate(r.tags) if t[0]=='sbsp' and nm[k]==sbsp_name][0]
    kl=[k for k,t in enumerate(r.tags) if t[0]=='Lbsp' and nm[k]==sbsp_name][0]
    os_=r.off(X(r.tags[ks][2])); ol=r.off(X(r.tags[kl][2]))
    # Lbsp render geometry struct: find the resource datum
    want={}
    for i,o in enumerate(r.res): want[(struct.unpack_from('<H',d,o+16)[0]<<16)|i]=i
    G=None
    for rg in [j-0x94 for j in range(0,0x400,4) if j>=0x94 and struct.unpack_from('<I',d,ol+j)[0] in want]:
        try:
            G=rsbsp.geometry(r,ol,rg)
            if G['meshes']: break
        except Exception: G=None
    if G is None: raise Exception('no Lbsp render geometry found')
    sds=export.shader_datums(r,os_+0x144-0x48)
    # instances live in the sbsp type-6 resource
    sb_dat=r.tags[ks][1]; r6=None
    for i,o in enumerate(r.res):
        if struct.unpack_from('<I',d,o+12)[0]==sb_dat and d[o+18]==6: r6=i
    R=r.resource(r6); c=R['ctrl']; rt=R['root']
    ni=struct.unpack_from('<I',c,rt+0x48)[0]; ib=R['fix'][rt+0x4c]&0x0fffffff
    nclu=struct.unpack_from('<I',c,rt+0x30)[0]
    insts=[]
    for i in range(ni):
        e=ib+i*156; f=struct.unpack_from('<13f',c,e); df=struct.unpack_from('<h',c,e+0x34)[0]
        insts.append((f[0],np.array(f[1:4]),np.array(f[4:7]),np.array(f[7:10]),np.array(f[10:13]),df))
    g=GLB()
    ndef=len(G['comps']); nmesh=len(G['meshes'])
    c_,a_=struct.unpack_from('<II',d,ol+0x60); a_=r.off(X(a_))
    LE=[struct.unpack_from('<hhhh',d,a_+i*12) for i in range(c_)]
    c_,a_=struct.unpack_from('<II',d,ol+0xf4); a_=r.off(X(a_))
    LF=[struct.unpack_from('<h',d,a_+i*2)[0] for i in range(c_)]
    T6=[i for i,v in enumerate(G['vbs']) if v[1]==6]
    def inst_light(k):
        if k<len(LF) and LF[k]>=0: return ('lm',LF[k])
        if k<len(LE) and LE[k][1]>=19 and LE[k][1]-19<len(T6): return ('pv',T6[LE[k][1]-19])
        return None
    # lightmap atlas (layer 0 of the dual-VMF intensity array)
    lmtex=None
    try:
        import rbitmap
        kb=[k for k,t in enumerate(r.tags) if t[0]=='bitm' and nm[k].startswith(sbsp_name.replace('\\levels','levels')+'_lightmap') and 'intensity' in nm[k]]
        if kb:
            bo=r.off(X(r.tags[kb[0]][2])); w,h=struct.unpack_from('<HH',d,r.off(X(struct.unpack_from('<I',d,bo+0x80)[0])))
            nr,ra=struct.unpack_from('<II',d,bo+0xa8); RL=r.resource(struct.unpack_from('<I',d,r.off(X(ra)))[0]&0xffff)
            src=RL['sec'] if len(RL['sec']) else RL['prim']; L=w*h
            im=rbitmap.dds(w,h,16,bytes(src[:L])).convert('RGB')
            bio=io.BytesIO(); im.save(bio,'PNG'); lmtex=g.image(bio.getvalue()); print('lightmap',w,h)
    except Exception as ex: print('lightmap fail',ex)
    print('meshes',nmesh,'defs',ndef,'clusters',nclu,'instances',ni,'materials',len(sds))
    matidx={}; texidx={}
    import lmat
    MATS=lmat.Mats(g,r,nm,maxsz)
    def mat(i):
        return MATS.material(sds[i] if 0<=i<len(sds) else 0xffffffff,'mat%d'%i)
    meshcache={}
    def mesh(mi,light=None):
        key=(mi,light)
        if key in meshcache: return meshcache[key]
        me=G['meshes'][mi]
        if me['vbi'][0]<0 or me['ibi'][0]<0: meshcache[key]=None; return None
        if mi>=ndef and me['vbi'][1]>=0: light=('lm',me['vbi'][1])
        LUV=LCOL=None
        if light and light[0]=='lm':
            LUV=np.frombuffer(G['vbs'][light[1]][3],np.uint8).reshape(-1,4).copy().view('<u2').reshape(-1,2).astype(np.float32)/65535
        elif light and light[0]=='pv':
            rb_=G['vbs'][light[1]][3]; LCOL=np.frombuffer(rb_[:len(rb_)//6*6],np.uint8).reshape(-1,6)[:,3:6].astype(np.float32)/255
        vb=G['vbs'][me['vbi'][0]]; cnt,typ,stride,raw=vb
        a=np.frombuffer(raw,dtype=np.uint8).reshape(cnt,stride)
        p=a[:,0:12].copy().view('<f4').reshape(cnt,3).astype(np.float64)
        if mi<ndef:
            x0,x1,y0,y1,z0,z1,u0,u1,v0,v1=G['comps'][mi]
            p=p*np.array([x1-x0,y1-y0,z1-z0])+np.array([x0,y0,z0])
            uv=a[:,16:20].copy().view('<u2').reshape(cnt,2).astype(np.float32)/65535.0
            uv=uv*np.array([u1-u0,v1-v0],np.float32)+np.array([u0,v0],np.float32)
        else:
            uv=half(a[:,16:20].copy()).reshape(cnt,2)
        P=(cv(p)*S).astype(np.float32)
        it,ix=G['ibs'][me['ibi'][0]]
        prims=[]
        for (matn,sort,st,cn,ss,sc,ptype,flags,vc) in me['parts']:
            seg=ix[st:st+cn]
            tr=np.array(strips(seg) if it==5 else [tuple(seg[q:q+3]) for q in range(0,len(seg)-2,3)],np.int64).reshape(-1,3)
            if not len(tr): continue
            vu=np.unique(tr); remap=np.full(cnt,-1,np.int64); remap[vu]=np.arange(len(vu)); tr2=remap[tr]
            at=dict(POSITION=g.acc(P[vu],'VEC3',5126,34962,True),TEXCOORD_0=g.acc(np.nan_to_num(uv[vu]).astype(np.float32),'VEC2',5126,34962))
            if LUV is not None and len(LUV)>=cnt: at['TEXCOORD_1']=g.acc(LUV[vu],'VEC2',5126,34962)
            if LCOL is not None and len(LCOL)>=cnt: at['COLOR_0']=g.acc(LCOL[vu],'VEC3',5126,34962)
            prims.append(dict(attributes=at,indices=g.acc(tr2.astype(np.uint32).reshape(-1),'SCALAR',5125,34963),material=mat(matn)))
        if not prims: meshcache[key]=None; return None
        g.j['meshes'].append(dict(name='mesh%d'%mi,primitives=prims)); meshcache[key]=len(g.j['meshes'])-1
        return meshcache[key]
    root=dict(name=sbsp_name.split('\\')[-1],children=[]); g.j['nodes'].append(root); g.j['scenes'][0]['nodes']=[0]
    for mi in range(ndef,nmesh):
        m=mesh(mi)
        if m is None: continue
        g.j['nodes'].append(dict(name='cluster%d'%(mi-ndef),mesh=m)); root['children'].append(len(g.j['nodes'])-1)
    for k,(sc,fw,lf,up,pos,df) in enumerate(insts):
        if df<0 or df>=ndef: continue
        m=mesh(df,inst_light(k))
        if m is None: continue
        c0=cv((sc*lf)[None])[0]; c1=cv((sc*up)[None])[0]; c2=cv((sc*fw)[None])[0]; t=cv(pos[None])[0]*S
        M=[*c0,0,*c1,0,*c2,0,*t,1]
        g.j['nodes'].append(dict(name='inst%d_def%d'%(k,df),mesh=m,matrix=[float(x) for x in M])); root['children'].append(len(g.j['nodes'])-1)
    # decals: sbsp decal list (+0x328, 28 B) + per-decal ranges (+0x334) into the sbsp decal geometry (render geometry @0x340)
    try:
        import rshader2
        DG=rsbsp.geometry(r,os_,0x340)
        c1,a1=struct.unpack_from('<II',d,os_+0x328); a1=r.off(X(a1)) if c1 else 0
        c2,a2=struct.unpack_from('<II',d,os_+0x334); a2=r.off(X(a2)) if c2 else 0
        dnode=dict(name='decals',children=[]); g.j['nodes'].append(dnode); root['children'].append(len(g.j['nodes'])-1); nd=0
        for i in range(min(c1,c2)):
            e=a1+i*28; dat=struct.unpack_from('<I',d,e)[0]; var=d[e+4]; mi=d[e+5]
            ist,icn,vst,vcn=struct.unpack_from('<4H',d,a2+i*28)
            if mi>=len(DG['meshes']): continue
            me=DG['meshes'][mi]
            if me['vbi'][0]<0 or me['ibi'][0]<0: continue
            vb=DG['vbs'][me['vbi'][0]]; it,ix=DG['ibs'][me['ibi'][0]]
            A=np.frombuffer(vb[3],np.uint8).reshape(vb[0],vb[2])
            p=A[:,0:12].copy().view('<f4').reshape(-1,3).astype(np.float64); uv=A[:,16:20].copy().view('<f2').reshape(-1,2).astype(np.float32)  # decal uvs are half floats
            seg=ix[ist:ist+icn].astype(np.int64); seg=np.where(seg==0xffff,seg,seg+vst)  # decal indices are relative to the decal's vertex start
            tr=np.array(strips(seg) if it==5 else [tuple(seg[q:q+3]) for q in range(0,len(seg)-2,3)],np.int64).reshape(-1,3)
            if not len(tr): continue
            vu=np.unique(tr); remap=np.full(len(p),-1,np.int64); remap[vu]=np.arange(len(vu))
            # decal definition 'var' inside the decs tag: block +0x2c, render method at entry+0x8
            k=dat&0xffff; do=r.off(X(r.tags[k][2])); dc,da=struct.unpack_from('<II',d,do+0x2c); da=r.off(X(da)) if dc else 0
            vv=var if var<dc else 0; ES=0x200
            # find entry stride from the block (single entry common)
            info=rshader2.shader_info(r,nm,None,at=da+vv*ES+0x8 if dc>1 else da+0x8,name=nm[k],grp='decal') if dc else None
            mt=MATS.material(('decal',dat,vv),nm[k].split('\\')[-1],info=info)
            g.j['materials'][mt].setdefault('alphaMode','BLEND')
            Pd=(cv(p[vu])*S).astype(np.float32)
            g.j['meshes'].append(dict(name='decal_'+nm[k].split('\\')[-1],primitives=[dict(attributes=dict(POSITION=g.acc(Pd,'VEC3',5126,34962,True),TEXCOORD_0=g.acc(uv[vu],'VEC2',5126,34962)),indices=g.acc(remap[tr].astype(np.uint32).reshape(-1),'SCALAR',5125,34963),material=mt)]))
            g.j['nodes'].append(dict(name='decal_%d'%i,mesh=len(g.j['meshes'])-1)); dnode['children'].append(len(g.j['nodes'])-1); nd+=1
        print('decals',nd)
    except Exception as ex:
        import traceback; traceback.print_exc()
    if lmtex is not None: g.j['scenes'][0]['extras']=dict(lightmapTexture=lmtex,lightmapNote='TEXCOORD_1 samples lightmapTexture (baked light, multiply with base); COLOR_0 = baked per-vertex light; neither = probe/ambient lit')
    g.j.setdefault('extensionsUsed',[]).append('KHR_texture_transform')
    g.save(out); print('saved',out,len(g.bin)/1e6,'MB', len(g.j['meshes']),'meshes',len(g.j['nodes']),'nodes')
if __name__=='__main__':
    run(sys.argv[1],sys.argv[2],sys.argv[3],int(sys.argv[4]) if len(sys.argv)>4 else 512)
