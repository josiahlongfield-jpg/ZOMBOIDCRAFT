import json,os,sys,subprocess,numpy as np
from PIL import Image
S='/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad/'
sys.path.insert(0,S+'look'); from conv import find, toon
OUT=S+'review/out/'
m=json.load(open(S+'pz/models_all.json')); mwp=json.load(open(S+'review/mwp.json'))
def mesh(src,key):
    tmp=OUT+key+'.obj'
    subprocess.run(['assimp','export',src,tmp,'-ptv','-tri','-jiv'],capture_output=True)
    V=[];T=[];F=[]
    for line in open(tmp):
        if line.startswith('v '): V.append([float(x) for x in line.split()[1:4]])
        elif line.startswith('vt '): T.append([float(x) for x in line.split()[1:3]])
        elif line.startswith('f '):
            idx=[p.split('/') for p in line.split()[1:]]
            if len(idx)==3: F.append([(int(a[0])-1, int(a[1])-1 if len(a)>1 and a[1] else 0) for a in idx])
    os.remove(tmp)
    if os.path.exists(OUT+key+'.mtl'): os.remove(OUT+key+'.mtl')
    V=np.array(V,np.float32); T=np.array(T or [[0,0]],np.float32)
    pos=np.array([V[v] for tri in F for v,_ in tri],np.float32).reshape(-1,3)
    uv=np.array([T[t] for tri in F for _,t in tri],np.float32).reshape(-1,2)
    with open(OUT+'p/'+key+'.bin','wb') as fh:
        fh.write(np.array([len(pos)],np.uint32).tobytes()); fh.write(pos.tobytes()); fh.write(uv.tobytes())
    return len(F)
need=sorted({x[1] for L in mwp.values() for x in L if x[1] in m})
info={}; texdone={}
for mn in need:
    mm=m[mn]; src=find('models_X/'+mm['mesh'],{'.x','.fbx'})
    if not src: print('NO MESH',mn); continue
    tris=mesh(src,mn)
    tk=None
    if mm.get('tex'):
        tk=os.path.basename(mm['tex'])
        if tk not in texdone:
            ts=find('textures/'+mm['tex'],{'.png'})
            if ts:
                im=Image.open(ts).convert('RGB'); im.thumbnail((512,512)); toon(im).save(OUT+'pt/'+tk+'.png',optimize=True); texdone[tk]=1
            else: print('NO TEX',mn,mm['tex']); tk=None
    info[mn]={'tris':tris,'tex':tk,'att':mm['att'],'scale':mm.get('scale')}
json.dump(info,open(OUT+'parts_models.json','w'))
print(len(info),'models', sum(v['tris'] for v in info.values()),'tris')
