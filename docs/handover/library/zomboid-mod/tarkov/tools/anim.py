import UnityPy,struct,json,sys,collections
R='/mnt/user-data/uploads/raw/'
def load(bundle):
    env=UnityPy.load(R+bundle)
    tos={}
    for o in env.objects:
        if o.type.name=='Avatar':
            a=o.read()
            for k,v in a.m_TOS: tos[k]=v
    clips=collections.defaultdict(list)
    for o in env.objects:
        if o.type.name=='AnimationClip':
            c=o.read(); clips[c.m_Name].append(c)
    return env,tos,clips
def streamed(data):
    u=data; b=struct.pack('<%dI'%len(u),*u)
    frames=[];i=0
    while i<len(b):
        t,=struct.unpack_from('<f',b,i); n,=struct.unpack_from('<I',b,i+4); i+=8
        keys=[]
        for _ in range(n):
            idx,=struct.unpack_from('<I',b,i); c=struct.unpack_from('<4f',b,i+4); i+=20
            keys.append((idx,c))
        frames.append((t,keys))
    return frames
def sample(c,fps=30):
    mc=c.m_MuscleClip; d=mc.m_Clip.data
    sf=streamed(d.m_StreamedClip.data); ns=d.m_StreamedClip.curveCount
    dn=d.m_DenseClip; nd=dn.m_CurveCount
    cv=list(d.m_ConstantClip.data); nc=len(cv)
    start,stop=mc.m_StartTime,mc.m_StopTime
    nf=max(1,int(round((stop-start)*fps))+1)
    out=[]
    last={}  # curve idx -> (time, coeffs)
    fi=0
    dv=list(dn.m_SampleArray)
    for k in range(nf):
        t=start+k/fps
        while fi<len(sf) and sf[fi][0]<=t+1e-6:
            for idx,co in sf[fi][1]: last[idx]=(sf[fi][0],co)
            fi+=1
        vals=[0.0]*(ns+nd+nc)
        for idx,(kt,co) in last.items():
            x=t-kt; vals[idx]=((co[0]*x+co[1])*x+co[2])*x+co[3]
        if nd:
            f=(t-dn.m_BeginTime)*dn.m_SampleRate; f0=int(max(0,min(dn.m_FrameCount-1,f))); f1=min(dn.m_FrameCount-1,f0+1); a=max(0,min(1,f-f0))
            for j in range(nd): vals[ns+j]=dv[f0*nd+j]*(1-a)+dv[f1*nd+j]*a
        vals[ns+nd:]=cv
        out.append(vals)
    return out,nf,stop-start
def bones(c,tos,fps=30):
    vals,nf,dur=sample(c,fps)
    gb=c.m_ClipBindingConstant.genericBindings
    res=collections.defaultdict(dict); ci=0
    for b in gb:
        p=tos.get(b.path,str(b.path)); a=b.attribute
        n={1:3,2:4,3:3,4:3}.get(a,1) if b.typeID==4 else 1
        res[p][{1:'p',2:'q',3:'s',4:'e'}.get(a,'f%d'%a) if b.typeID==4 else 'f%d_%d'%(b.typeID,a)]=[v[ci:ci+n] for v in vals]
        ci+=n
    return dict(res),nf,dur,ci,len(vals[0])
if __name__=='__main__':
    env,tos,clips=load('assets__content__weapons__m4a1__client_assets.bundle')
    for n in ['m4a1_mag_out_0','m4a1_mag_in_0','m4a1_reload_charge_0','m4a1_idle_armed','m4a1_fire','m4a1_hands_in','m4a1_look','m4a1_reload_start','m4a1_reload_end']:
        for c in clips[n]:
            r,nf,dur,used,tot=bones(c,tos)
            print(n,round(dur,2),'s bones',len(r),'curves',used,tot, [k for k in list(r)[:6]])
