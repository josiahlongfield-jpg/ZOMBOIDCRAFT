import sys,numpy as np
import mccpose,poseglb as pg
from viewglb import qm
def mats(n,fr=0):
    A=mccpose.absq(n,fr); q={k:v[0] for k,v in A.items()}; t={k:v[1] for k,v in A.items()}
    Wd=pg.world(mccpose.G,q,t)
    L=np.eye(4); L[:3,:3]=qm(A['weapon'][0]); L[:3,3]=A['weapon'][1]
    return Wd['b_l_hand']@L, Wd['b_r_grip']
if __name__=='__main__':
    for n in ['A_HR_Rifle_Ready','A_HR_Rifle_Kneel','A_HR_Rifle_Sit','A_HR_Rifle_Buff']:
        Wp,Rg=mats(n); O=np.linalg.inv(Wp)@Rg
        print(n); print(np.round(O,3))
