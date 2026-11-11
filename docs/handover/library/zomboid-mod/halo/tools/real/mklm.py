"""Convert baked light atlases (<bsp>_light.npy) into RGBM pngs (rgb * a * R, R = 2) for the previews."""
import sys,glob,os,numpy as np
from PIL import Image
R=2.0
for f in sys.argv[1:]:
    E=np.load(f); M=np.clip(E.max(-1)/R,1/255,1); M=np.ceil(M*255)/255
    rgb=np.clip(E/(M[...,None]*R),0,1)
    out=f.replace('_light.npy','_lm.png')
    Image.fromarray((np.dstack([rgb,M[...,None]])*255+0.5).astype(np.uint8),'RGBA').save(out); print(out)
