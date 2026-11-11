"""Sword Base (any Reach scenario) placed props -> per-prop GLBs + one merged preview GLB + placements json."""
import sys,os,json,struct,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
from reach import X
import export
export.MATHOOK=True
from glb import GLB
import viewglb
S=3.048
SKIP=('respawn','spawn_point','anti_respawn','respawn_zone','mp_cinematic_camera','mp_hill_beacon','mp_flag_base','mp_circle','sky_','teleporter')
BLOCKS=((0xfc,0x108,220,'scenery'),(0x600,0x60c,216,'crate'),(0x180,0x18c,228,'machine'))
def cv(p): return np.array([p[1],p[2],p[0]])
def rotm(y,p,r_):
    cz,sz=np.cos(y),np.sin(y);cy,sy=np.cos(p),np.sin(p);cx,sx=np.cos(r_),np.sin(r_)
    Rz=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]]);Ry=np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]]);Rx=np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
    return Rz@Ry@Rx
P=np.array([[0,1,0],[0,0,1],[1,0,0]],float)  # halo->gl
def run(mapp,outdir,merged):
    r=RR(mapp); nm=r.names(); d=r.d
    modes={nm[k] for k,t in enumerate(r.tags) if t[0]=='mode'}
    k=[k for k,t in enumerate(r.tags) if t[0]=='scnr'][0]; o=r.off(X(r.tags[k][2]))
    os.makedirs(outdir,exist_ok=True); place=[]
    for plc,pal,st,kind in BLOCKS:
        c,a=struct.unpack_from('<II',d,o+pal); a=r.off(X(a)) if c else 0
        names=[]
        for i in range(c):
            v=struct.unpack_from('<I',d,a+i*16+12)[0]; names.append(nm[v&0xffff] if v!=0xffffffff else None)
        n,b=struct.unpack_from('<II',d,o+plc); b=r.off(X(b)) if n else 0
        for i in range(n):
            e=b+i*st; t=struct.unpack_from('<h',d,e)[0]
            if t<0 or not names[t]: continue
            name=names[t]; short=name.split('\\')[-1]
            if any(s in short for s in SKIP) or name not in modes: continue
            pos=struct.unpack_from('<3f',d,e+8); rot=struct.unpack_from('<3f',d,e+0x14); sc=struct.unpack_from('<f',d,e+0x20)[0] or 1.0
            fn=f'{outdir}/{short}.glb'
            if not os.path.exists(fn):
                ko,mo=export.find_tag(r,'mode',name,nm); _,_,regs=export.info(r,mo)
                pick={rn:ps[0][0] for rn,ps in regs if ps}
                try: export.export(r,nm,name,fn,maxsz=512,pick=pick); export.TEXCACHE.clear(); print('prop',short,flush=True)
                except Exception as ex: print('FAIL',short,ex); continue
            if not (-50<pos[0]*S<250 and -600<pos[1]*S<0) and False: pass
            Rh=rotm(*rot)*sc; Rg=P@Rh@P.T; T=cv(pos)*S
            M=np.eye(4); M[:3,:3]=Rg; M[:3,3]=T
            place.append(dict(kind=kind,model=short+'.glb',tag=name,position_m=T.round(4).tolist(),matrix=M.T.reshape(16).round(6).tolist()))
    json.dump(place,open(f'{outdir}/placements.json','w'),indent=1)
    print('placed',len(place))
if __name__=='__main__':
    run(sys.argv[1],sys.argv[2],sys.argv[3])
