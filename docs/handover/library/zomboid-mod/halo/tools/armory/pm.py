import sys,struct;sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
from reach import X
r=RR('/mnt/user-data/uploads/haloreach/maps/20_sword_slayer.map'); nm=r.names(); d=r.d
k=[i for i,t in enumerate(r.tags) if t[0]=='pmcg'][0]; o=r.off(X(r.tags[k][2]))
def sid(v):
    try: s=r.sid(v); return s if s else None
    except Exception: return None
def walk(o,size,depth,label):
    # print strings and recurse into plausible reflexives
    for q in range(0,size,4):
        v=struct.unpack_from('<I',d,o+q)[0]
        s=sid(v) if v else None
        if s and len(s)>1 and s.isprintable(): print('  '*depth+'%s+%x sid %s'%(label,q,s))
    for q in range(0,size-8,4):
        c,a=struct.unpack_from('<II',d,o+q)
        if 0<c<2000 and a:
            try: ao=r.off(X(a))
            except Exception: continue
            if 0<ao<len(d): yield (q,c,ao)
print(len(d))
