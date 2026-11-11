"""Extract AI squads (spawn points, characters, weapons, vehicles) from a Reach scenario."""
import sys,struct,json
sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
from reach import X
def run(path,out=None):
    r=RR(path); d=r.d; nm=r.names()
    k=[k for k,t in enumerate(r.tags) if t[0]=='scnr'][0]; o=r.off(X(r.tags[k][2]))
    def blk(e,f):
        c,a=struct.unpack_from('<II',d,e+f); return c,(r.off(X(a)) if c else 0)
    def pal(f):
        c,a=blk(o,f); res=[]
        for i in range(c):
            v=struct.unpack_from('<I',d,a+i*16+12)[0]; res.append(nm[v&0xffff].split('\\')[-1] if v!=0xffffffff else None)
        return res
    CH,VE,WE=pal(0x3ec),pal(0x138),pal(0x168)
    def idxlist(e,f,P):
        c,a=blk(e,f); return [P[i] if 0<=i<len(P) else None for i in (struct.unpack_from('<h',d,a+j*16+0xc)[0] for j in range(c))]
    n,ao=blk(o,0x398); S=[]
    for i in range(n):
        e=ao+i*108; name=d[e:e+32].split(b'\0')[0].decode('latin1')
        pts=[]
        for f,es,po in ((0x3c,0x6c,0x14),(0x30,0x6c,0x10)):
            c,a=blk(e,f)
            for j in range(c):
                x,y,z=struct.unpack_from('<3f',d,a+j*es+po); yaw=struct.unpack_from('<f',d,a+j*es+po+0x10)[0]
                pts.append(dict(kind='spawn' if f==0x3c else 'formation',pos=[round(y*3.048,2),round(z*3.048,2),round(x*3.048,2)],halo=[x,y,z],yaw=round(yaw,3)))
        cells=[]
        c,a=blk(e,0x54)
        for j in range(c):
            ce=a+j*0x6c
            vi=struct.unpack_from('<h',d,ce+0x46)[0]
            cells.append(dict(count=struct.unpack_from('<h',d,ce+0x10)[0],chars=idxlist(ce,0x14,CH),weapons=idxlist(ce,0x20,WE),vehicle=VE[vi] if 0<=vi<len(VE) else None))
        S.append(dict(name=name,points=pts,cells=cells))
    res=dict(map=path.split('/')[-1],characters=CH,vehicles=VE,weapons=WE,squads=S)
    if out: json.dump(res,open(out,'w'),indent=0)
    return res
if __name__=='__main__':
    R=run(sys.argv[1],sys.argv[2] if len(sys.argv)>2 else None)
    for s in R['squads'][:200]:
        if any(k in s['name'] for k in ('kat','jorge','gunners','wraith','fork','phantom','elites0','grunts0','ds0')):
            print(s['name'],len(s['points']),s['points'][:1],s['cells'])
