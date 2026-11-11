import sys;sys.path.insert(0,'/tmp/claude-0/halo')
import poseglb as pg, posed
A='/mnt/user-data/uploads/additions/halo-reach/'; N=A+'noble/'; AN=A+'animations/'
AI=AN+'spartan_ai_animations_part01.glb'; FE=AN+'spartan_female_animations.glb'; JO=AN+'spartan_jorge_animations.glb'
HMG=A+'vehicles/vehicles__human__turrets__machinegun__weapon__machinegun_turret.glb'
OUT=sys.argv[1]
J=[('carter','spartan_carter',AI,'alert:rifle:idle','exp/noble/w_dmr.glb'),
   ('kat','spartan_kat',FE,'alert:pistol:idle','exp/noble/w_magnum.glb'),
   ('jun','spartan_jun',AI,'alert:rifle:idle','exp/noble/w_sniper.glb'),
   ('emile','spartan_emile',AI,'alert:rifle:idle','exp/noble/w_shotgun.glb'),
   ('jorge','spartan_jorge',JO,'alert:turret:idle',HMG),
   ('six','spartan_default',AI,'alert:rifle:idle','exp/noble/w_dmr.glb'),
   ('john','/tmp/claude-0/halo/exp/noble/sp_john117',AI,'alert:rifle:idle','exp/noble/w_ar.glb')]
only=sys.argv[2:]
for n,g,a,c,w in J:
    if only and n not in only: continue
    V=pg.build((g if g.startswith('/') else N+g)+'.glb',a,c,0.0,w); cc,rr=((0,1.15,0),3.1) if n=='jorge' else ((0,1.0,0),2.6)
    posed.shot(*V,f'{OUT}/{n}.png',eye=(-1,0.12,0.6),W=780,H=1040,c=cc,rad=rr)
    posed.shot(*V,f'{OUT}/front_{n}.png',eye=(0,0.12,1) if n=='jorge' else (-0.45,0.12,1),W=780,H=1040,c=cc,rad=rr); print(n,flush=True)
