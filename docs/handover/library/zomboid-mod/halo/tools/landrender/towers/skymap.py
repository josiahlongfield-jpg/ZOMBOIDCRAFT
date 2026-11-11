import json,numpy as np,os
os.chdir('/tmp/claude-0/halo/web/land')
src=open('land.py').read()
exec(src.split('# ---- mission windows')[0])
exec('names = list(OFF)'+src.split('names = list(OFF)')[1].split('Dk = np.stack(Dk)')[0])
Dk=np.stack(Dk)  # metres-ish distance to each mission footprint on 16 m grid
names=list(OFF)
# Lone Wolf as its own small region
yy,xx=np.mgrid[0:N16,0:N16]; wx=xx/N16*16000-8000+8; wz=yy/N16*16000-8000+8
lw=np.hypot(wx-2300,wz-6900); 
D=np.concatenate([Dk,lw[None]*1.0+0],0); names=names+['lw']
d=D/64.0*16 if False else D
np.save('/tmp/claude-0/-home-user-zomboidcraft/bb435080-348c-5e82-b018-9d51a3f60885/scratchpad/skyD.npy',D.astype(np.float32))
print(D.shape,[float(np.percentile(x,50)) for x in D])
