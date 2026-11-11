import sys,struct,numpy as np
sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
def bits(b):
    v=int.from_bytes(b,'little'); return [i for i in range(len(b)*8) if v>>i&1]
def split(hdr,D):
    st,an,sf,af,mv=hdr[4],hdr[5],hdr[6],hdr[7],hdr[8]
    S=D[:st]; A=D[st:st+an]; F=D[st+an:st+an+sf]; AF=D[st+an+sf:st+an+sf+af]
    n=max(sf,af)//3
    return S,A,[bits(F[i*n:(i+1)*n]) for i in range(3)],[bits(AF[i*n:(i+1)*n]) for i in range(3)],D[st+an+sf+af:]
def static(S,flags):
    if not S: return {},{},{}
    typ,rc,tc,sc=S[:4]; to,so=struct.unpack_from('<II',S,0xc)
    R={};T={};SC={}
    for i,n in enumerate(flags[0][:rc]): R[n]=np.array(struct.unpack_from('<4h',S,0x20+i*8))/32767.
    for i,n in enumerate(flags[1][:tc]): T[n]=np.array(struct.unpack_from('<3f',S,to+i*12))
    for i,n in enumerate(flags[2][:sc]): SC[n]=struct.unpack_from('<f',S,so+i*4)[0]
    return R,T,SC
def curve_tracks(A):
    if not A or A[0]!=9: return []
    typ,nr,nt,ns=A[:4]
    to=struct.unpack_from('<I',A,0x0c)[0]
    offs=list(struct.unpack_from('<%dI'%nr,A,0x20))+[to]
    out=[]
    for i in range(nr):
        o=0x20+offs[i]; K=struct.unpack_from('<H',A,o+2)[0]
        dl=list(A[o+8:o+8+K]); v=A[o+8+K:o+8+K+10*K+6]
        keys=[struct.unpack_from('<3h',v,j*10) for j in range(K+1)]
        tans=[v[j*10+6:j*10+10] for j in range(K)]
        out.append(dict(hdr=A[o:o+8],K=K,deltas=dl,keys=keys,tans=tans,raw=v))
    return out
def octa(u,v):
    x,y=u,v; z=1-abs(u)-abs(v)
    if z<0: x,y=(1-abs(v))*np.sign(u or 1),(1-abs(u))*np.sign(v or 1)
    n=np.array([x,y,z]); l=np.linalg.norm(n); return n/l if l>0 else np.array([0,0,1.])
def key_quat(k,sgn=1):
    u,v,a=k[0]/32767.,k[1]/32767.,k[2]/32767.
    n=octa(u,v); a=max(-1,min(1,a))
    return np.array(list(n*a*sgn)+[np.sqrt(1-a*a)])
def curve_tracks2(A):
    if not A or A[0]!=9: return []
    typ,nr,nt,ns=A[:4]
    to=struct.unpack_from('<I',A,0x0c)[0]
    offs=list(struct.unpack_from('<%dI'%nr,A,0x20))
    out=[]
    for i in range(nr):
        o=0x20+offs[i]; K=struct.unpack_from('<H',A,o+2)[0]; fl=A[o+4]
        if fl:
            keys=[struct.unpack_from('<3h',A,o+8+j*6) for j in range(K+1)]; times=list(range(K+1)); tans=None
        else:
            dl=list(A[o+8:o+8+K]); v=A[o+8+K:o+8+K+10*K+6]
            keys=[struct.unpack_from('<3h',v,j*10) for j in range(K+1)]
            tans=[v[j*10+6:j*10+10] for j in range(K)]
            times=[0]+list(np.cumsum(dl))
        out.append(dict(K=K,flag=fl,keys=keys,times=times,tans=tans))
    return out
OQMODE='h'
def oq(k):
    u,v,a=k[0]/32767.,k[1]/32767.,k[2]/32767.
    if OQMODE=='h':
        # sign of a = sign of the hidden z component; angle encoded by |a|
        z=np.sqrt(max(0,1-u*u-v*v))*(1 if a>=0 else -1)
        n=np.array([u,v,z]); n/=np.linalg.norm(n)
        w=1-2*abs(a); s=np.sqrt(max(0,1-w*w))
        return np.array(list(-n*s)+[w])
    if OQMODE=='w':
        n=np.array([u,v,-np.sqrt(max(0,1-u*u-v*v))]); n/=np.linalg.norm(n)
        w=1-2*abs(a); s=np.sqrt(max(0,1-w*w))*np.sign(a)
        return np.array(list(n*s)+[w])
    th=a*2*np.pi
    return np.array(list(n*np.sin(th/2))+[np.cos(th/2)])
def slerp(a,b,t):
    d=np.dot(a,b)
    if d<0: b=-b; d=-d
    if d>0.9995: q=a+(b-a)*t; return q/np.linalg.norm(q)
    th=np.arccos(d); return (np.sin((1-t)*th)*a+np.sin(t*th)*b)/np.sin(th)
def sample_track(t,f):
    T=t['times']
    if f<=T[0]: return oq(t['keys'][0])
    if f>=T[-1]: return oq(t['keys'][-1])
    j=int(np.searchsorted(T,f,side='right'))-1
    u=(f-T[j])/(T[j+1]-T[j]); return slerp(oq(t['keys'][j]),oq(t['keys'][j+1]),u)
def trans_tracks(A):
    if not A or A[0]!=9: return []
    nr,nt=A[1],A[2]
    if not nt: return []
    to=struct.unpack_from('<I',A,0x0c)[0]
    offs=list(struct.unpack_from('<%dI'%nt,A,0x20+to))
    out=[]
    for i in range(nt):
        o=0x20+offs[i]; K=struct.unpack_from('<H',A,o+2)[0]; fl=A[o+4]
        f0,bx,by,bz,rng=struct.unpack_from('<5f',A,o+4)
        base=np.array([bx,by,bz])
        if fl:
            keys=[np.array(struct.unpack_from('<3h',A,o+24+j*6))/32767.*rng+base for j in range(K+1)]; times=list(range(K+1))
        else:
            dl=list(A[o+24:o+24+K]); v=o+24+K
            keys=[np.array(struct.unpack_from('<3h',A,v+j*9))/32767.*rng+base for j in range(K+1)]
            times=[0]+list(np.cumsum(dl))
        out.append(dict(K=K,flag=fl,keys=keys,times=times))
    return out
def sample_trans(t,f):
    T=t['times']
    if f<=T[0]: return t['keys'][0]
    if f>=T[-1]: return t['keys'][-1]
    j=int(np.searchsorted(T,f,side='right'))-1
    u=(f-T[j])/(T[j+1]-T[j]); return t['keys'][j]*(1-u)+t['keys'][j+1]*u
