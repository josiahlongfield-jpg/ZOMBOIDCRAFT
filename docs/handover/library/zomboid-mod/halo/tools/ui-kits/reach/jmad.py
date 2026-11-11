import sys,struct,collections
sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
from rres import RR
from reach import X
def blk(r,o,k):
    c,a=struct.unpack_from('<II',r.d,o+k); return c,(r.off(X(a)) if c else 0)
def graph(r,k):
    d=r.d; o=r.off(X(r.tags[k][2]))
    na,aa=blk(r,o,0x94); ng,ga=blk(r,o,0x1ac); ns,sa=blk(r,o,0x48)
    skel=[r.sid(struct.unpack_from('<I',d,sa+i*0x2c)[0]) for i in range(ns)]
    groups=[struct.unpack_from('<II',d,ga+i*12) for i in range(ng)]
    anims=[]
    for i in range(na):
        e=aa+i*60; name=r.sid(struct.unpack_from('<I',d,e)[0])
        n,b=blk(r,e,0x30)
        if n:
            nodes,frames=struct.unpack_from('<HH',d,b); typ=d[b+4]; frinfo=d[b+5]
            gi,mi=struct.unpack_from('<HH',d,b+0x20)
            anims.append(dict(name=name,frames=frames,nodes=nodes,type=typ,frinfo=frinfo,group=gi,member=mi,ref=None))
        else:
            ref=struct.unpack_from('<Ih',d,e+0x28); anims.append(dict(name=name,ref=ref))
    return dict(skel=skel,groups=groups,anims=anims,o=o)
_resc={}
def anim_data(r,G,a):
    cnt,dat=G['groups'][a['group']]
    key=dat&0xffff
    if key not in _resc:
        R=r.resource(key); _resc.clear(); _resc[key]=R
    R=_resc[key]; c=R['ctrl']; p=bytes(R['prim']) if R['prim'] else b''
    e=a['member']*0x64
    hdr=struct.unpack_from('<IIHH8I',c,e)
    size=struct.unpack_from('<I',c,e+0x50)[0]; ptr=R['fix'][e+0x5c]
    src=p if (ptr>>28)==4 else bytes(R['sec'])
    off=ptr&0x0fffffff
    return hdr,src[off:off+size]
if __name__=='__main__':
    r=RR(sys.argv[1]); k=int(sys.argv[2]); G=graph(r,k)
    print(len(G['skel']),G['skel'][:10],len(G['anims']))
    C=collections.Counter()
    for a in G['anims']:
        if a['ref']: C['ref']+=1; continue
        try:
            hdr,D=anim_data(r,G,a)
            st=D[0]; sz=hdr[4]; an=D[sz] if sz<len(D) else None
            C[(st,an)]+=1
        except Exception as ex: C['err '+str(ex)[:40]]+=1
    print(C)
