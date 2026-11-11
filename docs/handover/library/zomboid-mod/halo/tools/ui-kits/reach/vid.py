import sys,os;sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
from multiprocessing import Pool
CLIPS=[('alert:rifle:move_front','Run',2),('alert:rifle:walk_front','Walk',2),('alert:rifle:idle','Idle',1),
 ('combat:rifle:melee_strike_1','Melee',1),('combat:rifle:reload_1','Reload',1),('combat:rifle:throw_grenade','Grenade throw',1)]
OUT='/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach/vid'
def job(args):
    idx,name,label,f=args
    from rseq import graph,r,prep,pose_at,to_abs,shot
    from PIL import Image,ImageDraw
    global CACHE
    if 'CACHE' not in globals(): CACHE={'G':graph(r,9891)}
    if name not in CACHE: CACHE[name]=prep(CACHE['G'],name)
    rots,trans=pose_at(CACHE[name],f)
    p=f'{OUT}/f{idx:05d}.png'
    shot('/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach/exp/noble/spartan_carter.glb',p,absq=to_abs(rots,trans),views=((1,0.25,0.8),),W=480,H=540,fixc=((0,1.0,0),2.6))
    im=Image.open(p).convert('RGB'); d=ImageDraw.Draw(im); d.text((12,10),f'{label}  ({name})',fill=(255,255,255)); im.save(p)
if __name__=='__main__':
    from rseq import graph,r
    G=graph(r,9891); jobs=[]; i=0
    for name,label,loops in CLIPS:
        a=[a for a in G['anims'] if a['name']==name][0]; fr=a['frames']
        for l in range(loops):
            for f in range(fr-1 if loops>1 else fr):
                jobs.append((i,name,label,f)); i+=1
    print(len(jobs),flush=True)
    os.makedirs(OUT,exist_ok=True)
    with Pool(int(sys.argv[1])) as p: p.map(job,jobs,chunksize=4)
    print('done')
