import sys,io,struct,zlib,json,importlib.util,numpy as np
from PIL import Image
sp=importlib.util.spec_from_file_location('cp','/home/user/zomboidcraft/tools/cars/pack.py'); cp=importlib.util.module_from_spec(sp); sp.loader.exec_module(cp)
gid='M4'; OUT='/tmp/claude-0/tk/web/data/'
raw=zlib.decompress(cp.scramble(open(f'/mnt/project-files/zomboid-mod/brita/local-pc/guns/{gid}.zmesh','rb').read(),'local/'+gid))
b=io.BytesIO(raw); b.read(12); png=b.read(struct.unpack('>i',b.read(4))[0]); open(OUT+'brita_m4.png','wb').write(png)
P=[];U=[]
for _ in range(struct.unpack('>i',b.read(4))[0]):
    b.read(struct.unpack('>H',b.read(2))[0]); t=struct.unpack('>i',b.read(4))[0]
    rec=np.frombuffer(b.read(t*39),dtype=[('x','>i2'),('y','>i2'),('z','>i2'),('u','>u2'),('v','>u2'),('s','V3')])
    P.append(np.stack([rec['x'],rec['y'],rec['z']],-1)/1000.0); U.append(np.stack([rec['u'],rec['v']],-1)/65535.0)
P=np.concatenate(P).astype(np.float32); U=np.concatenate(U).astype(np.float32)
print(len(P), P.min(0), P.max(0))
# px/16 units -> metres: gun length; scale to match Tarkov M4 length later in JS
n=len(P); N=np.zeros_like(P)
tri=P.reshape(-1,3,3); fn=np.cross(tri[:,1]-tri[:,0],tri[:,2]-tri[:,0]); fn/=np.linalg.norm(fn,axis=1,keepdims=True)+1e-9; N=np.repeat(fn,3,0).astype(np.float32)
idx=np.arange(n,dtype=np.uint32)
open(OUT+'brita_m4.bin','wb').write(P.tobytes()+N.tobytes()+U.tobytes()+idx.tobytes())
json.dump([{'name':'brita_m4','parent':-1,'p':[0,0,0],'q':[0,0,0,1],'s':[1,1,1],'mesh':{'n':int(n),'subs':[int(n)],'file':'brita_m4.bin'},'mats':[{'name':'brita','_MainTex':'brita_m4.png','flipY':True}]}],open(OUT+'brita.json','w'))
