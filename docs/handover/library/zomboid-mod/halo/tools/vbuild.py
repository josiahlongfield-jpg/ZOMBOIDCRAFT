import sys,os,glob,shutil,pickle;sys.path.insert(0,'/tmp/claude-0/halo')
import animexp2
src='/tmp/claude-0/halo/exp/vehicles'; out='/tmp/claude-0/halo/vanim'
for g in sorted(glob.glob(src+'/*.glb')):
    b=os.path.basename(g)[:-4]; parts=b.split('__')
    pk=f'/tmp/claude-0/halo/clipdb/objects__{b}__{parts[-1]}.pkl'
    if not os.path.exists(pk): pk=f'/tmp/claude-0/halo/clipdb/objects__{b}.pkl'
    o=f'{out}/{b}.glb'
    if os.path.exists(pk) and pickle.load(open(pk,'rb'))['clips']:
        animexp2.run(g,o,[pk])
    else: shutil.copy(g,o)
