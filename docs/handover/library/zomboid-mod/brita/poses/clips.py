import numpy as np, zlib, struct, sys
sys.path.insert(0,'.')
import zsk2
def load(path, model):
    d=zlib.decompress(zsk2.unscramble(open(path,'rb').read(), model)); r=zsk2.R(d)
    assert r.raw(4)==b'ZSKN' and r.i()==1
    for g in range(r.i()):
        r.utf();r.utf();r.utf(); r.by();r.by(); nv=r.i(); r.raw(nv*27); nt=r.i(); r.raw(nt*12)
    for t in range(r.i()): r.utf(); r.raw(r.i())
    names=[];par=[];inv=[]
    for i in range(r.i()):
        names.append(r.utf()); par.append(r.by()); m=np.array([r.f() for _ in range(12)]).reshape(3,4); M=np.eye(4); M[:3,:]=m; inv.append(M)
    for i in range(r.i()): r.utf(); r.by(); r.f();r.f();r.f()
    clips={}
    for c in range(r.i()):
        name=r.utf(); fr=r.i(); fps=r.f(); nb=r.i()
        a=np.frombuffer(r.raw(fr*nb*14),'>i2').reshape(fr,nb,7).astype(float)
        q=a[:,:,:4]/32767; q/=np.linalg.norm(q,axis=2,keepdims=True); p=a[:,:,4:]/1000
        clips[name]=dict(q=q,p=p,fps=fps)
    return names,par,inv,clips
