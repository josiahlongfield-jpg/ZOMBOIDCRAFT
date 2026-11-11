"""Re-export selected BSPs of a map (noprops) and drop chunked GLBs into outbox/<map>/ for shipping."""
import os,sys,subprocess,shutil
H='/tmp/claude-0/halo'; SP='/mnt/user-data/uploads/Zomboidcraft-Desktop--additions/halo-reach/split'
m,bsps=sys.argv[1],sys.argv[2]
mp=f'{H}/maps/{m}.map'
if not os.path.exists(mp) or os.path.islink(mp):
    p1,p2=f'{SP}/{m}.map.part1',f'{SP}/{m}.map.part2'
    with open(mp+'.tmp','wb') as o:
        for p in (p1,p2):
            with open(p,'rb') as i: shutil.copyfileobj(i,o,1<<24)
    os.replace(mp+'.tmp',mp); os.remove(p1); os.remove(p2)
out=f'{H}/rex/{m}'; os.makedirs(out,exist_ok=True)
env=dict(os.environ,ZC_TEXLIB=f'{H}/outbox/tex',ZC_SHIPPED=f'{H}/shipped_tex.txt')
subprocess.call(['python3',f'{H}/wexport.py',mp,out,bsps,'noprops'],env=env)
ob=f'{H}/outbox/.{m}fix'; os.makedirs(ob,exist_ok=True)
for f in sorted(os.listdir(out)):
    if not f.endswith('.glb'): continue
    b=open(f'{out}/{f}','rb').read()
    if len(b)<=23_500_000: open(f'{ob}/{f}','wb').write(b)
    else:
        for i in range(0,len(b),23_500_000): open(f'{ob}/{f}.part{i//23_500_000:02d}','wb').write(b[i:i+23_500_000])
os.makedirs(f'{H}/outbox/{m}',exist_ok=True)
for f in os.listdir(ob): os.replace(f'{ob}/{f}',f'{H}/outbox/{m}/{f}')
os.rmdir(ob)
os.makedirs(f'{H}/layout/{m}/fix',exist_ok=True)
for f in os.listdir(out):
    if f.endswith('.npz') or f=='manifest.json': shutil.copy(f'{out}/{f}',f'{H}/layout/{m}/fix/{f}')
print('REFIX DONE',m,os.listdir(f'{H}/outbox/{m}'),flush=True)
