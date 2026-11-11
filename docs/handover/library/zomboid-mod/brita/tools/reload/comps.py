import sys, io, struct, zlib, numpy as np, importlib.util
from PIL import Image
ROOT='/home/user/zomboidcraft'
sp=importlib.util.spec_from_file_location('cp',ROOT+'/tools/cars/pack.py'); cp=importlib.util.module_from_spec(sp); sp.loader.exec_module(cp)
GUNS='/mnt/project-files/zomboid-mod/brita/local-pc/guns'
IDS='AAC_Honey AR57_PDW American180 AssaultRifle2 BOAR Colt1911 DSHK L86 M2Carbine M870_CQB RPG_7 SW_M500 TenPointTurbo_XLT Winchester1866'.split()
def load(gid):
    raw=zlib.decompress(cp.scramble(open(f'{GUNS}/{gid}.zmesh','rb').read(),'local/'+gid))
    b=io.BytesIO(raw); hdr=b.read(12); png=b.read(struct.unpack('>i',b.read(4))[0])
    tex=np.asarray(Image.open(io.BytesIO(png)).convert('RGB')).astype(float)
    parts=[]
    for _ in range(struct.unpack('>i',b.read(4))[0]):
        name=b.read(struct.unpack('>H',b.read(2))[0]).decode(); t=struct.unpack('>i',b.read(4))[0]
        rec=np.frombuffer(b.read(t*39),dtype=[('x','>i2'),('y','>i2'),('z','>i2'),('u','>u2'),('v','>u2'),('s','V3')])
        parts.append((name,rec))
    rest=b.read()
    return hdr,png,tex,parts,rest
def components(pos):
    # pos (T,3,3) -> label per tri, by shared vertex positions
    v=np.round(pos.reshape(-1,3),4); _,inv=np.unique(v,axis=0,return_inverse=True); inv=inv.reshape(-1)
    n=inv.max()+1; par=np.arange(n)
    def f(a):
        while par[a]!=a: par[a]=par[par[a]]; a=par[a]
        return a
    tv=inv.reshape(-1,3)
    for a,b,c in tv:
        ra,rb,rc=f(a),f(b),f(c); par[rb]=ra; par[f(rc)]=ra
    root=np.array([f(t[0]) for t in tv]); _,lab=np.unique(root,return_inverse=True); return lab
if __name__=='__main__':
    for g in IDS:
        hdr,png,tex,parts,rest=load(g)
        for name,rec in parts:
            pos=(np.stack([rec['x'],rec['y'],rec['z']],-1)/1000.0).reshape(-1,3,3)
            lab=components(pos)
            print(g,name,len(pos),'tris',lab.max()+1,'comps','bbox',pos.reshape(-1,3).min(0).round(2),pos.reshape(-1,3).max(0).round(2),'rest',len(rest))
