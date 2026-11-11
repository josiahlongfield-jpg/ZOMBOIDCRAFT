"""Decode a Halo Reach BSP's dual-VMF lightmap into per-texel lighting (after TagTool's ReachLightmapConverter).
lightmap(r, nm, sbsp_name) -> dict(brightness, W,H, Ddir (H,W,3), Dcol (H,W,3), Icol (H,W,3), bw (H,W), mask (H,W))
irradiance(L, n) evaluates the diffuse light for normals n (H,W,3) with the 2nd-order SH of both lobes."""
import sys,struct,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from reach import X
import rbitmap
def _layers(r,k):
    d=r.d; o=r.off(X(r.tags[k][2]))
    ni,ia=struct.unpack_from('<II',d,o+0x7c); ia=r.off(X(ia))
    w,h,depth=struct.unpack_from('<HHB',d,ia); fmt=struct.unpack_from('<H',d,ia+8)[0]
    nr,ra=struct.unpack_from('<II',d,o+0xa8); ra=r.off(X(ra))
    R=r.resource(struct.unpack_from('<I',d,ra)[0]&0xffff); P=bytes(R['sec']) if len(R['sec']) else bytes(R['prim'])
    L=rbitmap.top_size(w,h,38 if fmt==44 else fmt)
    out=[]
    for i in range(max(1,depth)):
        im=rbitmap.dds(w,h,38 if fmt==44 else fmt,P[i*L:(i+1)*L]).convert('RGBA'); out.append(np.asarray(im,np.float32)/255)
    return w,h,fmt,out
def lbsp_offset(r,nm,sbsp_name):
    ks=[k for k,t in enumerate(r.tags) if t[0]=='Lbsp' and nm[k]==sbsp_name]
    return r.off(X(r.tags[ks[0]][2])) if ks else None
def lightmap(r,nm,sbsp_name):
    d=r.d; ol=lbsp_offset(r,nm,sbsp_name)
    bright=struct.unpack_from('<f',d,ol+0x18)[0]
    dat_dir=struct.unpack_from('<I',d,ol+0x1c+12)[0]; dat_int=struct.unpack_from('<I',d,ol+0x2c+12)[0]
    if dat_dir==0xffffffff or dat_int==0xffffffff: return None
    kd=dat_dir&0xffff; ki=dat_int&0xffff
    w,h,f0,c0=_layers(r,kd); w2,h2,f1,ci=_layers(r,ki)
    c0=c0[0]
    if f0==38 or f0==44:  # BC5 decoded: R = X (mono), G = Y (alpha)
        mono=c0[...,0]; alpha=c0[...,1]
    else: mono=c0[...,0]; alpha=c0[...,3]
    c1,c2,c3=ci[0],ci[1],ci[2]
    v=np.stack([c1[...,3],c2[...,3],c3[...,3]],-1)*2-1
    ln=np.clip(np.linalg.norm(v,axis=-1),0,1); Ddir=v/np.maximum(ln,1e-6)[...,None]
    inten=np.exp(mono*-6.238325)*bright
    Dcol=(c2[...,:3]*2+c1[...,:3]-1)*inten[...,None]
    Icol=c3[...,:3]*inten[...,None]
    return dict(brightness=bright,W=w,H=h,Ddir=Ddir.astype(np.float32),Dcol=Dcol.astype(np.float32),Icol=Icol.astype(np.float32),bw=ln.astype(np.float32),mask=alpha.astype(np.float32),names=(nm[kd],nm[ki]))
def _bandwidth(y):
    y=np.clip(y,0,1); return np.power(56.976875-y*56.0,-0.93375814)*55.384312-0.81427675
def _czh(k):
    vmf0=0.5/np.sqrt(np.pi)*np.sqrt(4*np.pi); vmf1=np.sqrt(3/np.pi)*np.sqrt(4*np.pi/3)*0.5; vmf2=np.sqrt(5/np.pi)*np.sqrt(4*np.pi/5)*0.5
    k=np.maximum(k,1e-4); y=1/np.tanh(k)
    c1=vmf1*(y*k-1)/k; c2=vmf2*(3+k*k-3*k*y)/(k*k)
    return vmf0, np.minimum(c1,vmf0), np.minimum(c2,vmf0)
def sh9(n):
    x,y,z=n[...,0],n[...,1],n[...,2]
    return np.stack([0.282095*np.ones_like(x),-0.488603*y,0.488603*z,-0.488603*x,1.092548*x*y,-1.092548*y*z,0.315392*(3*z*z-1),-1.092548*x*z,0.546274*(x*x-y*y)],-1)
A=np.array([np.pi]+[2*np.pi/3]*3+[np.pi/4]*5)
def irradiance(L,n,sl=(slice(None),slice(None))):
    """Diffuse light (radiance-scaled irradiance / pi) for unit normals n, Halo axes (z up)."""
    k=_bandwidth(L['bw'][sl]); c0,c1,c2=_czh(k)
    Bd=sh9(L['Ddir'][sl]); Bn=sh9(n)
    zc=np.stack([np.full_like(k,c0),c1,c1,c1,c2,c2,c2,c2,c2],-1)
    # direct lobe projected into SH, convolved with the cosine lobe, evaluated at n
    s=(Bd*zc*Bn*A).sum(-1)/np.pi
    ind=c0*0.282095*0.282095*np.pi/np.pi  # indirect: DC only
    return np.maximum(L['Dcol'][sl]*s[...,None]+L['Icol'][sl]*ind,0)
ICOSA=[(0.0,0.0,1.0),(0.3607,0.0,0.9327),(0.1115,0.3431,0.9327),(0.6729,0.0,0.7397),(0.4795,0.3484,0.8054),(0.2079,0.6399,0.7397),(0.8944,0.0,0.4472),(0.7844,0.3431,0.5168),(0.5687,0.6399,0.5168),(0.2764,0.8507,0.4472),(-0.2918,0.212,0.9327),(-0.1832,0.5637,0.8054),(-0.5444,0.3955,0.7397),(-0.0839,0.852,0.5168),(-0.4329,0.7386,0.5168),(-0.7236,0.5257,0.4472),(-0.2918,-0.212,0.9327),(-0.5927,0.0,0.8054),(-0.5444,-0.3955,0.7397),(-0.8362,0.1835,0.5168),(-0.8362,-0.1835,0.5168),(-0.7236,-0.5257,0.4472),(0.1115,-0.3431,0.9327),(-0.1832,-0.5637,0.8054),(0.2079,-0.6399,0.7397),(-0.4329,-0.7386,0.5168),(-0.0839,-0.852,0.5168),(0.2764,-0.8507,0.4472),(0.4795,-0.3484,0.8054),(0.5687,-0.6399,0.5168),(0.7844,-0.3431,0.5168),(0.9647,-0.212,0.1561),(0.9647,0.212,0.1561),(0.9051,-0.3955,-0.1561),(0.9855,0.0,-0.1699),(0.9051,0.3955,-0.1561),(0.7236,-0.5257,-0.4472),(0.8362,-0.1835,-0.5168),(0.8362,0.1835,-0.5168),(0.7236,0.5257,-0.4472),(0.4998,0.852,0.1561),(0.0965,0.983,0.1561),(0.6558,0.7386,-0.1561),(0.3045,0.9372,-0.1699),(-0.0965,0.983,-0.1561),(0.4329,0.7386,-0.5168),(0.0839,0.852,-0.5168),(-0.2764,0.8507,-0.4472),(-0.6558,0.7386,0.1561),(-0.9051,0.3955,0.1561),(-0.4998,0.852,-0.1561),(-0.7973,0.5792,-0.1699),(-0.9647,0.212,-0.1561),(-0.5687,0.6399,-0.5168),(-0.7844,0.3431,-0.5168),(-0.8944,0.0,-0.4472),(-0.9051,-0.3955,0.1561),(-0.6558,-0.7386,0.1561),(-0.9647,-0.212,-0.1561),(-0.7973,-0.5792,-0.1699),(-0.4998,-0.852,-0.1561),(-0.7844,-0.3431,-0.5168),(-0.5687,-0.6399,-0.5168),(-0.2764,-0.8507,-0.4472),(0.0965,-0.983,0.1561),(0.4998,-0.852,0.1561),(-0.0965,-0.983,-0.1561),(0.3045,-0.9372,-0.1699),(0.6558,-0.7386,-0.1561),(0.0839,-0.852,-0.5168),(0.4329,-0.7386,-0.5168),(0.7973,0.5792,0.1699),(-0.3045,0.9372,0.1699),(-0.9855,0.0,0.1699),(-0.3045,-0.9372,0.1699),(0.7973,-0.5792,0.1699),(0.0,0.0,-1.0),(-0.1115,0.3431,-0.9327),(0.2918,0.212,-0.9327),(-0.2079,0.6399,-0.7397),(0.1832,0.5637,-0.8054),(0.5444,0.3955,-0.7397),(-0.3607,0.0,-0.9327),(-0.6729,0.0,-0.7397),(-0.4795,0.3484,-0.8054),(-0.1115,-0.3431,-0.9327),(-0.2079,-0.6399,-0.7397),(-0.4795,-0.3484,-0.8054),(0.2918,-0.212,-0.9327),(0.5444,-0.3955,-0.7397),(0.1832,-0.5637,-0.8054),(0.5927,0.0,-0.8054)]
