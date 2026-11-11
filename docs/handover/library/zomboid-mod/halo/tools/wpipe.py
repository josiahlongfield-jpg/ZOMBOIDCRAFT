import os,time,subprocess,json,sys
from concurrent.futures import ThreadPoolExecutor
H='/tmp/claude-0/halo'; UP='/mnt/user-data/uploads/Halo- The Master Chief Collection/Content/haloreach/maps'
SP='/mnt/user-data/uploads/Zomboidcraft-Desktop--additions/halo-reach/split'
SIZES=json.load(open(H+'/mapsizes.json'))
ORDER=sys.argv[1].split(',')
KEEP={'shared.map','campaign.map','20_sword_slayer.map'}
def ready(m):
    j=f'{H}/maps/{m}.map'
    if os.path.exists(j) and not os.path.islink(j) and os.path.getsize(j)==SIZES[m+'.map']: return ('joined',j)
    f=f'{UP}/{m}.map'
    if os.path.exists(f) and os.path.getsize(f)==SIZES[m+'.map']: return ('one',f)
    p1,p2=f'{SP}/{m}.map.part1',f'{SP}/{m}.map.part2'
    if os.path.exists(p1) and os.path.exists(p2) and os.path.getsize(p1)+os.path.getsize(p2)==SIZES[m+'.map']: return ('parts',p1,p2)
    return None
def work(m):
    while True:
        r=ready(m)
        if r: break
        time.sleep(5)
    dst=f'{H}/maps/{m}.map'
    if r[0]=='joined': pass
    elif r[0]=='one':
        if os.path.lexists(dst): os.remove(dst)
        os.symlink(r[1],dst)
    else:
        with open(dst,'wb') as o:
            for p in r[1:]:
                with open(p,'rb') as i:
                    while True:
                        b=i.read(1<<24)
                        if not b: break
                        o.write(b)
                os.remove(p)
    log=open(f'{H}/world/{m}.log','w')
    t=time.time(); rc=subprocess.call(['python3',f'{H}/wexport.py',dst,f'{H}/world/{m}'],stdout=log,stderr=subprocess.STDOUT,cwd=H,env=dict(os.environ,ZC_TEXLIB=f'{H}/outbox/tex',ZC_SHIPPED=f'{H}/shipped_tex.txt'))
    open(f'{H}/world/{m}.DONE','w').write(f'rc={rc} secs={time.time()-t:.0f}\n')
    if m+'.map' not in KEEP:
        tgt=os.path.realpath(dst); os.remove(dst)
        if tgt!=dst and os.path.exists(tgt): os.remove(tgt)
    print('done',m,rc,flush=True)
with ThreadPoolExecutor(3) as ex: list(ex.map(work,ORDER))
print('ALL DONE',flush=True)
