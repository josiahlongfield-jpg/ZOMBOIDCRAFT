import sys, os, json, numpy as np
sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad')
import zrender as z, irons_brita as ib
B='/mnt/project-files/zomboid-mod/brita/local-pc/'
g=sys.argv[1]; out=f'v3/{g}'; os.makedirs(out,exist_ok=True)
ib.D=B+'guns'; ib._LC.clear(); m=ib._load(g); z._C['L'+g]=m; it=[('L'+g,k,(0,0,0),None) for k in m]
sp=json.load(open(B+f'guns/{g}.json'))['sights']; F=np.array(sp['front']); R=np.array(sp['rear'])
P=np.concatenate([p.reshape(-1,3) for p,_,_ in m.values()])
core=P[(P[:,0]>6.5)&(P[:,0]<9.8)]; lo,hi=core.min(0),core.max(0); c=(lo+hi)/2
span=max(hi[2]-lo[2],(hi[1]-lo[1])*960/540)*1.15; long_=hi[2]-lo[2]>12
for i in range(12):
    u=i/11; z.render(it,z.R(55+70*u,-18),c,span,(960,540)).save(f'{out}/orb{i:02d}.png')
L=1.5 if not long_ else 2.4
z.render(it,z.R(60,-15),F-np.array([0,0.08*L,0]),L,(960,540)).save(f'{out}/front.png')
z.render(it,z.R(125,-15),R-np.array([0,0.08*L,0]),L,(960,540)).save(f'{out}/rear.png')
z.render(it,z.R(0,0),R,1.1 if not long_ else 1.5,(960,540)).save(f'{out}/pic.png')
print(g,'ok',flush=True)
