import sys,json,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from voxel import voxelize
PAL=['air','concrete_panel','hs_wall','concrete_dark','glass_spandrel','ceiling_light','marble_white','ch_ashlar','floor_concrete']
def cat(n):
    n=n.lower()
    if 'glass' in n: return 4
    if 'illum' in n: return 5
    if 'marble' in n or 'floor' in n: return 6
    if any(k in n for k in ('rock','boulder','glacier','terrain','cliff','dirt','airfield')): return 7
    if 'concrete' in n: return 8
    if 'trim' in n or 'dark' in n: return 3
    if 'int_panel' in n or 'stealth' in n: return 2
    return 1
z=np.load(sys.argv[1]); V=z['V']; T=z['T']; M=z['mats']
lo=[float(x) for x in sys.argv[2].split(',')]; hi=[float(x) for x in sys.argv[3].split(',')]
lab=np.array([cat(M[m]) if m>=0 else 1 for m in T[:,3]])
grid=None
for k in range(1,len(PAL)):
    sel=lab==k
    if not sel.any(): continue
    occ=voxelize(V,T[sel][:,:3].astype(int),lo,hi)
    if grid is None: grid=np.zeros(occ.shape,np.uint16)
    order_ok = grid==0
    grid[occ & order_ok]=k
mc=grid[:, ::-1, :]; vox=np.transpose(mc,(2,1,0))
sy,sz,sx=vox.shape
out=sys.argv[4]
json.dump({'size':[sx,sy,sz],'origin':[0,40,0],'palette':PAL},open(out+'.json','w'))
np.ascontiguousarray(vox).astype('<u2').tofile(out+'.vox')
print(sx,sy,sz,[int((grid==k).sum()) for k in range(len(PAL))])
