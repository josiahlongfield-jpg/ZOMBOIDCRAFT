import sys,glob,os;sys.path.insert(0,'/tmp/claude-0/halo')
from fpmerge import merge
from animexp import loadglb
import animexp2
SPECIAL={'fp_ball':'mp_skull','fp_flag':'mp_fp_flag','fp_assault_bomb':'mp_assault_bomb','fp_target_laser':None}
for p in sorted(glob.glob('/tmp/claude-0/halo/clipdb/*__fp__weapons__*.pkl')):
    base=os.path.basename(p)[:-4]; who='spartan' if '__spartans__' in base else 'elite'
    wn=base.split('__')[-1]; mesh=SPECIAL.get(wn,wn[3:])
    arms=f'/tmp/claude-0/halo/exp/fp/{who}_fp_arms.glb'
    tmp=f'/tmp/claude-0/halo/anims/fp/_tmp_{who}_{wn}.glb'
    wp=f'/tmp/claude-0/halo/exp/weapons/{mesh}.glb' if mesh else None
    if wp and os.path.exists(wp): merge(arms,wp,'r_hand').save(tmp)
    else: loadglb(arms).save(tmp); print('no weapon mesh for',wn)
    animexp2.run(tmp,f'/tmp/claude-0/halo/anims/fp/{who}_{wn}.glb',[p]); os.remove(tmp)
