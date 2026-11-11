import sys,numpy as np
sys.argv=['x','m20','/tmp/claude-0/halo/bdglb/m20.glb','1']
src=open('/tmp/claude-0/halo/web/land/mk2.py').read()
exec(src.split('Vs=[];Ts=[]')[0])
exec('def cls(nm):'+src.split('def cls(nm):')[1].split('Cs=[')[0])
from PIL import Image,ImageDraw
off={"m10":(-5257,-4204),"m20":(-167,-5261),"m60":(4936,-5992),"m35":(-4299,3102),"m52":(3983,1544),"m50":(6225,-2533),"m70":(655,5986)}
S=2048;sc=S/16000
mask=Image.new('RGBA',(S,S),(0,0,0,0));d=ImageDraw.Draw(mask)
col={2:(40,110,170,200),3:(235,242,250,215),1:None}
stats={}
for m,(ox,oz) in off.items():
    import glob
    VL=np.concatenate([np.load(x)['V'] for x in glob.glob(f'/tmp/claude-0/halo/layout/{m}/*_collision.npz') if '_000_' not in x])
    c=(VL.min(0)+VL.max(0))/2
    for V,I,nm in glb(f'/tmp/claude-0/halo/bdglb/{m}.glb'):
        k=cls(nm)
        if k not in (2,3,5): continue
        t=V[I];cen=t.mean(1);keep=(np.abs(cen[:,0]-c[0])<2600)&(np.abs(cen[:,2]-c[2])<2600)
        # big triangles: clip by drawing anyway, PIL clips to canvas; limit to window by intersect later
        for tri in t[(np.abs(t[:,:,0].mean(1)-c[0])<20000)]:
            xs=(tri[:,0]+ox+8000)*sc;zs=(tri[:,2]+oz+8000)*sc
            d.polygon(list(zip(xs,zs)),fill=col[3] if k==3 else col[2])
        stats[(m,k)]=stats.get((m,k),0)+len(t)
    # window mask: keep only within 2600 m of mission centre
    w=Image.new('L',(S,S),0);ImageDraw.Draw(w).rectangle([(c[0]-2600+ox+8000)*sc,(c[2]-2600+oz+8000)*sc,(c[0]+2600+ox+8000)*sc,(c[2]+2600+oz+8000)*sc],fill=255)
    globals().setdefault('win',Image.new('L',(S,S),0));win.paste(255,mask=w)
print(stats)
a=np.array(mask);a[...,3]=np.minimum(a[...,3],np.array(win));Image.fromarray(a).save('climate_mask.png')
