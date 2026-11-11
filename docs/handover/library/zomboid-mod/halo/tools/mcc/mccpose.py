import sys, json, numpy as np
sys.path.insert(0,'/tmp/claude-0/halo'); sys.path.insert(0,'/tmp/claude-0/arm'); sys.path.insert(0,'/tmp/claude-0/mcc/anim')
import anim, orender, viewglb, poseglb as pg, posed
SK=json.load(open('/tmp/claude-0/mcc/anim/skel.json')); BN=[b[0] for b in SK['bones']]
G=orender.G
O_GRIP=None
def absq(name, frame=0):
    a=anim.full(name); out={}
    for t in a['tracks']:
        bn=BN[t['bone']]
        q=t['Q'][min(frame,len(t['Q'])-1)]; tr=t['T'][min(frame,len(t['T'])-1)]
        hq=np.array([-q[0],q[1],-q[2],q[3]]); ht=np.array([tr[0],-tr[1],tr[2]])/100.0
        gq=np.array([hq[1],hq[2],hq[0],hq[3]]); gq/=np.linalg.norm(gq); gt=np.array([ht[1],ht[2],ht[0]])*3.048
        out[bn]=(gq,gt)
    return out
def scene(outfit, name, frame=0, weapon=None):
    global O_GRIP
    if weapon is not None and O_GRIP is None:
        import wpn; Wp,Rg=wpn.mats('A_HR_Rifle_Kneel'); O_GRIP=np.linalg.inv(Wp)@Rg
    A=absq(name,frame); keep=orender.nodes_for(outfit)
    V,T,UV,MM,tex=viewglb.scene(G,lambda n: n in keep,{},A)
    if weapon:
        # the gun follows MCC's own 'weapon' bone (child of the left hand); O = that bone -> our grip frame, measured on Rifle_Kneel
        import wpn
        Wp,_=wpn.mats(name,frame); Mg=Wp@O_GRIP
        V2,T2,UV2,MM2,tex2=viewglb.scene(weapon,None)
        g=V2@Mg[:3,:3].T+Mg[:3,3]; off=max(int(MM.max())+1,max(tex.keys(),default=-1)+1)
        T=np.concatenate([T,T2+len(V)]); V=np.concatenate([V,g]); UV=np.concatenate([UV,UV2]); MM=np.concatenate([MM,MM2+off])
        for k,v in tex2.items(): tex[k+off]=v
    R=np.array([[0,0,-1.0],[0,1,0],[1.0,0,0]])  # UE import yaw: turn -90 so the pose faces +z like our model
    return V@R.T,T,UV,MM,tex
if __name__=='__main__':
    import os
    for n in sys.argv[1:]:
        w='/tmp/claude-0/halo/exp/noble/w_ar.glb' if 'Rifle' in n else None
        posed.shot(*scene(orender.BASE,n,0,w),'/tmp/claude-0/mcc/anim/p_%s.png'%n,W=420,H=560,c=(0,0.9,0),rad=2.6)
        print(n)
