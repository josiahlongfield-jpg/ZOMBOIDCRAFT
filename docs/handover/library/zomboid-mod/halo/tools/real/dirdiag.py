import sys,numpy as np,itertools
sys.path.insert(0,'/tmp/claude-0/halo')
import lmbake,lmdec
cap={}
orig=lmdec.irradiance
def fake(L,n,sl=None): cap['L']=L; cap['n']=n; return orig(L,n)
lmdec.irradiance=fake
mp,sb=sys.argv[1],sys.argv[2]
m=mp.split("/")[-1][:-4]; lmbake.bake(mp,"levels\\solo\\"+m+"\\"+sb,f"real/{m}/{sb}.glb",'/tmp/claude-0/halo/real/diag_tmp')
L=cap['L']; n=cap['n']; cov=np.load if 0 else None
from PIL import Image
c=np.asarray(Image.open('/tmp/claude-0/halo/real/diag_tmp_cov.png'))>200
d=L['Ddir'][c]; nn=n[c]; lum=L['Dcol'][c].mean(1); I=L['Icol'][c].mean(1)
print('texels',c.sum(),'Dlum mean',lum.mean(),'Ilum',I.mean(),'mask',L['mask'][c].mean())
up=nn[:,2]>0.8
print('up-facing texels',up.sum(),'mean dir',d[up].mean(0),'Dcol-weighted dir',(d[up]*lum[up,None]).sum(0)/lum[up].sum())
best=[]
for perm in itertools.permutations(range(3)):
  for sg in itertools.product((1,-1),repeat=3):
    dd=d[:,perm]*np.array(sg); dot=(dd*nn).sum(1)
    cc=np.corrcoef(dot,lum)[0,1]; best.append((cc,perm,sg))
best.sort(reverse=True)
for b in best[:6]: print(b)
