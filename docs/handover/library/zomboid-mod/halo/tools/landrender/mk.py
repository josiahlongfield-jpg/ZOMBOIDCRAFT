import numpy as np,glob,sys,struct
m=sys.argv[1];Vs=[];Ts=[];n=0
for x in sorted(glob.glob(f'/tmp/claude-0/halo/layout/{m}/*_collision.npz')):
    if '_000_' in x or (m=='m60' and x.endswith('m20_090_collision.npz')): continue
    d=np.load(x);Vs.append(d['V'].astype('f4'));Ts.append(d['T'].astype('u4')+n);n+=len(d['V'])
V=np.concatenate(Vs);T=np.concatenate(Ts)
a=V[T[:,1]]-V[T[:,0]];b=V[T[:,2]]-V[T[:,0]];c=np.cross(a,b);ln=np.linalg.norm(c,axis=1)+1e-9
big=(ln/2>300)&(np.abs(c[:,1]/ln)>0.999)
T=np.concatenate([T[~big],T[big]]);nb=int(big.sum())
open(f'/tmp/claude-0/halo/web/land/{m}.bin','wb').write(struct.pack('<III',len(V),len(T),nb)+V.tobytes()+T.tobytes())
print(m,len(V),len(T),V.min(0).round(),V.max(0).round())
