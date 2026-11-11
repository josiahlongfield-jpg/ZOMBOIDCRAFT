import sys,struct,numpy as np
sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
from rres import RR
from rcoll import coll_tris,blocks_at,bsp_resources
def level(path,out):
    r=RR(path); names=r.names()
    allV=[];allT=[]
    def add(V,T,M=None,pos=None,scale=1.0,tag=0):
        base=sum(len(v) for v in allV)
        V=np.array(V,dtype=np.float64)
        if len(V)==0: return
        if M is not None: V=scale*(V@M)+pos
        allV.append(V); allT.extend([(a+base,b+base,c+base,(m+moff) if m>=0 else -1,tag) for a,b,c,m in T])
    byd={t[1]:i for i,t in enumerate(r.tags)}
    from reach import X
    matnames=[]
    for dat,ty in bsp_resources(r).items():
        if 5 not in ty: continue
        t=r.tags[byd[dat]]; so=r.off(X(t[2])); c0,a0=struct.unpack_from('<II',r.d,so+0x144); mo=r.off(X(a0))
        mats=[names[byd[struct.unpack_from('<I',r.d,mo+j*44+12)[0]]] if struct.unpack_from('<I',r.d,mo+j*44+12)[0] in byd else '?' for j in range(c0)]
        moff=len(matnames); matnames+=mats
        R=r.resource(ty[5]); c=R['ctrl']; rt=R['root']
        n=struct.unpack_from('<I',c,rt)[0]
        if n and len(R['prim']):
            f=R['fix'][rt+4]&0x0fffffff
            add(*coll_tris(R['prim'],blocks_at(R,f)))
        nd=struct.unpack_from('<I',c,rt+0x18)[0]
        if not nd or 6 not in ty: continue
        dbase=R['fix'][rt+0x1c]&0x0fffffff
        defs=[]
        for k in range(nd):
            try: defs.append(coll_tris(R['prim'],blocks_at(R,dbase+k*324+8)))
            except Exception as e: defs.append(([],[]))
        R6=r.resource(ty[6]); c6=R6['ctrl']; rt6=R6['root']
        ni=struct.unpack_from('<I',c6,rt6+0x48)[0]; ib=R6['fix'][rt6+0x4c]&0x0fffffff
        for k in range(ni):
            e=ib+k*156
            sc,*m=struct.unpack_from('<13f',c6,e); di=struct.unpack_from('<h',c6,e+0x34)[0]
            M=np.array(m[:9]).reshape(3,3); pos=np.array(m[9:12])
            if 0<=di<len(defs): add(*defs[di],M=M,pos=pos,scale=sc,tag=1)
    V=np.concatenate(allV); T=np.array(allT)
    np.savez_compressed(out,V=V,T=T,mats=np.array(matnames))
    with open(out+'.obj','w') as f:
        for v in V: f.write('v %.4f %.4f %.4f\n'%(v[0],v[2],-v[1]))
        for t in T: f.write('f %d %d %d\n'%(t[0]+1,t[1]+1,t[2]+1))
    print(len(V),len(T),V.min(0).round(1),V.max(0).round(1))
if __name__=='__main__': level(sys.argv[1],sys.argv[2])
