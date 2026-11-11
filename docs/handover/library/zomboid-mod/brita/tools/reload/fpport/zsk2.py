import numpy as np, zlib, struct, io
def jhash(s):
    h=0
    for c in s: h=(31*h+ord(c)) & 0xffffffff
    return h
def unscramble(b, model):
    x=(0x3C6EF372 ^ jhash(model)) & 0xffffffff
    if x==0: x=1
    n=(len(b)+3)//4; ks=np.empty(n,np.uint32)
    for i in range(n):
        x^=(x<<13)&0xffffffff; x^=x>>17; x^=(x<<5)&0xffffffff; ks[i]=x
    k=ks.byteswap().view(np.uint8)[:len(b)]
    return (np.frombuffer(b,np.uint8)^k).tobytes()
class R:
    def __init__(s,b): s.b=io.BytesIO(b)
    def i(s): return struct.unpack('>i',s.b.read(4))[0]
    def by(s): return struct.unpack('>b',s.b.read(1))[0]
    def f(s): return struct.unpack('>f',s.b.read(4))[0]
    def utf(s): n=struct.unpack('>H',s.b.read(2))[0]; return s.b.read(n).decode('utf8','replace')
    def raw(s,n): return s.b.read(n)
def load(path, model):
    d=unscramble(open(path,'rb').read(), model); d=zlib.decompress(d); r=R(d)
    assert r.raw(4)==b'ZSKN' and r.i()==1
    G=[]
    for g in range(r.i()):
        mesh,piece,tex=r.utf(),r.utf(),r.utf(); flags,level,nv=r.by(),r.by(),r.i()
        rec=np.frombuffer(r.raw(nv*27),dtype=[('p','>f4',3),('n','i1',3),('uv','>u2',2),('j','i1',4),('w','u1',4)])
        nt=r.i(); tris=np.frombuffer(r.raw(nt*3*4),'>i4').reshape(-1,3)
        G.append(dict(mesh=mesh,piece=piece,level=level,uv=rec['uv']/65535.0,pos=rec['p'].astype(float),joint=rec['j'].astype(int),weight=rec['w']/255.0,tris=tris))
    TEX={}
    for t in range(r.i()):
        k=r.utf(); TEX[k]=r.raw(r.i())
    global LASTTEX; LASTTEX=TEX
    names=[];par=[];inv=[]
    for i in range(r.i()):
        names.append(r.utf()); par.append(r.by()); m=np.array([r.f() for _ in range(12)]).reshape(3,4); M=np.eye(4); M[:3,:]=m; inv.append(M)
    return G,names,par,inv
