import ua, struct, numpy as np
BASE='/tmp/claude-0/mcc/out/MCC/Content/UI/Features/Customization/Characters/Spartans/'
def load(name):
    A=BASE+'Animations/'+name
    S=ua.summary(A+'.uasset'); b=open(A+'.uexp','rb').read()
    e=S['exports'][0]; r=ua.R(b,e['off']-S['total']); P=ua.props(r,S['names'])
    nf=struct.unpack('<i',P['NumFrames'][2])[0]
    tt=P['TrackToSkeletonMapTable'][2]; n=struct.unpack_from('<i',tt,0)[0]
    # struct array: inner tag header (name,type,size,idx,structname,guid,hasguid) then elements as tagged props (BoneTreeIndex Int)
    rr=ua.R(tt,4); rr.u('ii'); rr.u('ii'); rr.u('ii'); rr.u('ii'); rr.o+=16; rr.u('B')
    t2s=[]
    for i in range(n):
        p=ua.props(rr,S['names']); t2s.append(struct.unpack('<i',p['BoneTreeIndex'][2])[0])
    r.u('i'); r.o+=16          # object guid flag, skeleton guid
    r.u('BB'); cooked=r.u('i')
    ke,tf,rf,sf=r.u('BBBB')
    nto=r.u('i'); to=r.u('%di'%nto) if nto else ()
    nso=r.u('i'); so=r.u('%di'%nso) if nso else (); strip=r.u('i')
    nbs=r.u('i'); bs=r.b[r.o:r.o+nbs]; r.o+=nbs
    return dict(nf=nf,t2s=t2s,fmt=(ke,tf,rf,sf),to=to,so=so,strip=strip,bs=bs,rest=r.b[r.o:r.o+40])
if __name__=='__main__':
    a=load('A_HR_Rifle_Hero_Pose')
    print(a['nf'],len(a['t2s']),a['fmt'],len(a['to'])//4,len(a['so']),a['strip'],len(a['bs']),a['rest'].hex(' '))
    print(a['to'][:24]); print(a['t2s'][:20])

def full(name):
    """Per track: bone index, translation keys (n,3), rotation keys (n,4 xyzw)."""
    A=BASE+'Animations/'+name
    S=ua.summary(A+'.uasset'); b=open(A+'.uexp','rb').read()
    e=S['exports'][0]; r=ua.R(b,e['off']-S['total']); P=ua.props(r,S['names'])
    nf=struct.unpack('<i',P['NumFrames'][2])[0]
    r.u('i'); r.o+=16; r.u('BB'); r.u('i')
    ke,tf,rf,sf=r.u('BBBB')
    nto=r.u('i'); to=r.u('%di'%nto)
    nso=r.u('i'); so=r.u('%di'%nso) if nso else (); r.u('i')
    nseg=r.u('i'); assert nseg==0
    nt=r.u('i'); t2s=list(r.u('%di'%nt)) if nt>1 else [r.u('i')]
    ua.props(r,S['names'])
    rawsize=r.u('i'); nbs=r.u('i'); bs=r.b[r.o:r.o+nbs]
    assert (ke,tf,sf)==(0,0,0) and rf in (0,1), (ke,tf,rf,sf)
    tracks=[]
    for i in range(nt):
        tof,tn,rof,rn=to[4*i:4*i+4]
        T=np.frombuffer(bs,'<f4',tn*3,tof).reshape(tn,3) if tn else np.zeros((1,3))
        if rf==1:
            q=np.frombuffer(bs,'<f4',rn*3,rof).reshape(rn,3)
            w=np.sqrt(np.clip(1-(q**2).sum(1),0,1)); Q=np.c_[q,w]
        else:
            Q=np.frombuffer(bs,'<f4',rn*4,rof).reshape(rn,4)
        tracks.append(dict(bone=t2s[i],T=T.copy(),Q=Q.copy()))
    return dict(nf=nf,tracks=tracks)
