"""Export a whole Reach map: every BSP (render GLB via lexport + collision npz), props, manifest."""
import sys,os,json,struct,time,traceback,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
from reach import X
from rcoll import coll_tris,blocks_at
import lexport,lprops,export
S=3.048
def cv(a): a=np.asarray(a); return np.stack([a[...,1],a[...,2],a[...,0]],-1)
def collision(r,sb_dat):
    d=r.d; r5=r6=None
    for i,o in enumerate(r.res):
        if struct.unpack_from('<I',d,o+12)[0]==sb_dat:
            t=d[o+18]
            if t==5: r5=i
            if t==6: r6=i
    if r5 is None:
        for i,o in enumerate(r.res):
            if struct.unpack_from('<I',d,o+12)[0]==sb_dat and d[o+18]==4: r5=i
    if r5 is None: return None
    R=r.resource(r5); c=R['ctrl']; rt=R['root']
    Vs=[];Ts=[];Ms=[];nv=0
    n=struct.unpack_from('<I',c,rt)[0]; f=R['fix'].get(rt+4)
    if n and f is not None:
        V,T=coll_tris(R['prim'],blocks_at(R,f&0x0fffffff))
        if T:
            V=np.array(V,np.float64); T=np.array(T,np.int64)
            Vs.append(cv(V)*S); Ts.append(T[:,:3]+nv); Ms.append(T[:,3]); nv+=len(V)
    nd=struct.unpack_from('<I',c,rt+0x18)[0]; fd=R['fix'].get(rt+0x1c)
    defs={}
    if nd and fd is not None:
        base=fd&0x0fffffff
        for i in range(nd):
            try:
                V,T=coll_tris(R['prim'],blocks_at(R,base+i*324+8))
                if T: defs[i]=(np.array(V,np.float64),np.array(T,np.int64))
            except Exception: pass
    ninst=0
    if r6 is not None and defs:
        R6=r.resource(r6); c6=R6['ctrl']; rt6=R6['root']
        ni=struct.unpack_from('<I',c6,rt6+0x48)[0]; ib=R6['fix'][rt6+0x4c]&0x0fffffff
        for i in range(ni):
            e=ib+i*156; fl=struct.unpack_from('<13f',c6,e); df=struct.unpack_from('<h',c6,e+0x34)[0]
            if df not in defs: continue
            V,T=defs[df]; sc=fl[0]; fw=np.array(fl[1:4]); lf=np.array(fl[4:7]); up=np.array(fl[7:10]); pos=np.array(fl[10:13])
            W=pos+sc*(V[:,0:1]*fw+V[:,1:2]*lf+V[:,2:3]*up)
            Vs.append(cv(W)*S); Ts.append(T[:,:3]+nv); Ms.append(T[:,3]+1000); nv+=len(V); ninst+=1
    if not Vs: return None
    return np.concatenate(Vs).astype(np.float32),np.concatenate(Ts).astype(np.int32),np.concatenate(Ms).astype(np.int16),ninst
def run(mapp,out,maxsz=512,only=None,props=True):
    os.makedirs(out,exist_ok=True)
    r=RR(mapp); nm=r.names()
    lb={nm[k] for k,t in enumerate(r.tags) if t[0]=='Lbsp'}
    sbs=[(k,nm[k]) for k,t in enumerate(r.tags) if t[0]=='sbsp']
    man=dict(map=os.path.basename(mapp),bsps=[],scenario=[nm[k] for k,t in enumerate(r.tags) if t[0]=='scnr'])
    for k,name in sbs:
        short=name.split('\\')[-1]
        if only and short not in only: continue
        ent=dict(name=name,short=short); t0=time.time()
        try:
            res=collision(r,r.tags[k][1])
            if res:
                V,T,M,ni=res; np.savez_compressed(f'{out}/{short}_collision.npz',V=V,T=T,M=M)
                ent.update(coll_tris=int(len(T)),coll_instances=ni,min=V.min(0).round(2).tolist(),max=V.max(0).round(2).tolist())
        except Exception as ex: ent['coll_error']=repr(ex); traceback.print_exc()
        if name in lb:
            try:
                lexport.run(mapp,name,f'{out}/{short}.glb',maxsz); export.TEXCACHE.clear()
                ent['glb']=f'{short}.glb'; ent['glb_mb']=round(os.path.getsize(f'{out}/{short}.glb')/1e6,1)
            except Exception as ex: ent['glb_error']=repr(ex); traceback.print_exc()
        ent['secs']=round(time.time()-t0,1); man['bsps'].append(ent); print('BSP',json.dumps(ent),flush=True)
        json.dump(man,open(f'{out}/manifest.json','w'),indent=1)
    if props:
        try: lprops.run(mapp,f'{out}/props',''); man['props']='props/placements.json'
        except Exception as ex: man['props_error']=repr(ex); traceback.print_exc()
    json.dump(man,open(f'{out}/manifest.json','w'),indent=1)
    print('DONE',out,flush=True)
if __name__=='__main__':
    only=sys.argv[3].split(',') if len(sys.argv)>3 and sys.argv[3] else None
    run(sys.argv[1],sys.argv[2],only=only,props=not (len(sys.argv)>4 and sys.argv[4]=='noprops'))
