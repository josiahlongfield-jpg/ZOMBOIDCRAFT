import json, os, subprocess, sys, numpy as np
from PIL import Image
S='/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad/'
OUT=S+'look/out/'
BR="/mnt/user-data/uploads/108600/2200148440/mods/Brita's Weapon Pack/media/"
AR="/mnt/user-data/uploads/108600/2297098490/mods/ARSENAL(26)GunFighter[MOD 2.0]/media/"
def find(rel, exts):
    for base in (BR, AR):
        d=os.path.dirname(base+rel); n=os.path.basename(rel).lower()
        if os.path.isdir(d):
            for f in os.listdir(d):
                stem,ext=os.path.splitext(f)
                if stem.lower()==n and ext.lower() in exts: return os.path.join(d,f)
        # case-insensitive dir
        parts=rel.split('/'); cur=base
        ok=True
        for p in parts[:-1]:
            m=[x for x in os.listdir(cur) if x.lower()==p.lower()] if os.path.isdir(cur) else []
            if not m: ok=False;break
            cur=os.path.join(cur,m[0])
        if ok and os.path.isdir(cur):
            for f in os.listdir(cur):
                stem,ext=os.path.splitext(f)
                if stem.lower()==n and ext.lower() in exts: return os.path.join(cur,f)
    return None
def toon(img):
    c=np.asarray(img.convert('RGB')).astype(float)/255
    lum=c.mean(-1,keepdims=True)
    c=np.maximum(c,0.16)*0.6+lum*0.4
    c=np.round(c*24)/24
    return Image.fromarray(np.clip(c*255,0,255).astype(np.uint8))
def mesh(src, key):
    tmp=OUT+key+'.obj'
    subprocess.run(['assimp','export',src,tmp,'-ptv','-tri','-jiv'],capture_output=True)
    V=[];T=[];F=[]
    for line in open(tmp):
        if line.startswith('v '): V.append([float(x) for x in line.split()[1:4]])
        elif line.startswith('vt '): T.append([float(x) for x in line.split()[1:3]])
        elif line.startswith('f '):
            idx=[p.split('/') for p in line.split()[1:]]
            if len(idx)==3: F.append([(int(a[0])-1, int(a[1])-1 if len(a)>1 and a[1] else 0) for a in idx])
    os.remove(tmp); 
    for f in (OUT+key+'.mtl',):
        if os.path.exists(f): os.remove(f)
    V=np.array(V,np.float32); T=np.array(T or [[0,0]],np.float32)
    pos=np.array([V[v] for tri in F for v,_ in tri],np.float32)
    uv=np.array([T[t] for tri in F for _,t in tri],np.float32)
    with open(OUT+'m/'+key+'.bin','wb') as fh:
        fh.write(np.array([len(pos)],np.uint32).tobytes()); fh.write(pos.tobytes()); fh.write(uv.tobytes())
    return len(F), pos.min(0).tolist(), pos.max(0).tolist()
def tex(rel, key):
    src=find(rel,{'.png'})
    if not src: return None
    im=Image.open(src).convert('RGB'); im.thumbnail((1024,1024))
    im.save(OUT+'t/'+key+'.png',optimize=True)
    toon(im).save(OUT+'t/'+key+'_toon.png',optimize=True)
    return key
if __name__=='__main__':
    m=json.load(open(S+'pz/models_all.json')); p=json.load(open(S+'pz/pilot.json'))
    guns=[]
    for gid,g in p.items():
        mm=m[g['props']['WeaponSprite']]
        src=find('models_X/'+mm['mesh'],{'.x'})
        tris,lo,hi=mesh(src,gid)
        t=tex('textures/'+mm['tex'],gid)
        guns.append({'id':gid,'name':g['props'].get('DisplayName',gid),'tris':tris,'lo':lo,'hi':hi,'tex':t,'att':mm['att']})
        print(gid,tris,t, [round(a-b,3) for a,b in zip(hi,lo)])
    json.dump(guns,open(OUT+'guns.json','w'))
