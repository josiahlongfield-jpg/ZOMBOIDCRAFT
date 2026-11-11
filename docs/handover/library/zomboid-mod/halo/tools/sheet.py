import sys,glob,os;sys.path.insert(0,'/tmp/claude-0/halo')
import numpy as np
from viewglb import scene
from raster import render
from PIL import Image,ImageDraw
files=sorted(glob.glob(sys.argv[1]))
W,H=360,220; cols=int(sys.argv[3]) if len(sys.argv)>3 else 5
rows=(len(files)+cols-1)//cols
out=Image.new('RGB',(W*cols,H*rows),(40,44,52)); dr=ImageDraw.Draw(out)
for i,f in enumerate(files):
    V,T,UV,MM,tex=scene(f)
    c=(V.min(0)+V.max(0))/2; ext=V.max(0)-V.min(0); rad=np.linalg.norm(ext)
    e=np.array([-1.0,0.35,0.35]) if ext[2]>ext[0] else np.array([0.35,0.35,1.0])
    im=render(V,T,None,eye=c+e/np.linalg.norm(e)*rad*1.55,tgt=c,W=W,H=H,UV=UV,tex=tex,M=MM,fov=30)
    out.paste(im,((i%cols)*W,(i//cols)*H)); dr.text(((i%cols)*W+8,(i//cols)*H+6),os.path.basename(f)[:-4],fill=(230,230,230))
out.save(sys.argv[2])
