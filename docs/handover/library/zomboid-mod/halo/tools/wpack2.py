"""Move a finished map into the outbox as <=23.5MB chunks; keep compact layout data (collision, manifest, placements) in layout/."""
import os,sys,zipfile,shutil
H='/tmp/claude-0/halo'; m=sys.argv[1]; src=f'{H}/world/{m}'; O=f'{H}/outbox/.{m}'; Lo=f'{H}/layout/{m}'
os.makedirs(O,exist_ok=True); os.makedirs(Lo,exist_ok=True)
CH=23_500_000
def chunk(p,name):
    if os.path.getsize(p)<=CH: shutil.copy(p,f'{O}/{name}'); return
    with open(p,'rb') as i:
        k=0
        while True:
            b=i.read(CH)
            if not b: break
            open(f'{O}/{name}.part{k:02d}','wb').write(b); k+=1
for f in sorted(os.listdir(src)):
    p=f'{src}/{f}'
    if f.endswith('.glb'): chunk(p,f); os.remove(p)
    elif f.endswith('_collision.npz') or f=='manifest.json': shutil.copy(p,Lo)
zc=f'{H}/outbox/.{m}_c.zip'
with zipfile.ZipFile(zc,'w',zipfile.ZIP_STORED) as z:
    for f in sorted(os.listdir(Lo)):
        if f.endswith('.npz'): z.write(f'{Lo}/{f}',f)
chunk(zc,f'{m}_collision.zip'); os.remove(zc)
shutil.copy(f'{src}/manifest.json',f'{O}/manifest.json')
if os.path.isdir(f'{src}/props'):
    if os.path.exists(f'{src}/props/placements.json'): shutil.copy(f'{src}/props/placements.json',f'{Lo}/placements.json')
    zp=f'{H}/outbox/.{m}_p.zip'
    with zipfile.ZipFile(zp,'w',zipfile.ZIP_STORED) as z:
        for f in sorted(os.listdir(f'{src}/props')): z.write(f'{src}/props/{f}',f)
    chunk(zp,f'{m}_props.zip'); os.remove(zp)
shutil.rmtree(src)
for f in os.listdir(Lo):
    if f.endswith('.npz'): pass
n=len(os.listdir(O))
if os.path.exists(f'{H}/outbox/{m}'):
    for f in os.listdir(O): os.replace(f'{O}/{f}',f'{H}/outbox/{m}/{f}')
    os.rmdir(O)
else: os.rename(O,f'{H}/outbox/{m}')
print('packed',m,n,'files')
