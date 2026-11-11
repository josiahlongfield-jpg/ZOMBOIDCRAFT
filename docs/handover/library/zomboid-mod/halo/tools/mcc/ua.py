import struct
class R:
    def __init__(s,b,o=0): s.b=b; s.o=o
    def u(s,f):
        v=struct.unpack_from('<'+f,s.b,s.o); s.o+=struct.calcsize('<'+f); return v if len(v)>1 else v[0]
    def fstr(s):
        n=s.u('i')
        if n==0: return ''
        if n<0:
            d=s.b[s.o:s.o-2*n]; s.o+=-2*n; return d.decode('utf-16-le')[:-1]
        d=s.b[s.o:s.o+n]; s.o+=n; return d[:-1].decode('latin1')
def summary(path):
    b=open(path,'rb').read(); r=R(b)
    tag,leg=r.u('Ii'); 
    if leg!=-4: r.u('i')
    ue4,lic=r.u('ii')
    ncv=r.u('i'); r.o+=20*ncv
    total=r.u('i'); folder=r.fstr(); flags=r.u('I')
    ncount,noff=r.u('ii')
    if ue4>=459: r.u('ii')
    ecount,eoff,icount,ioff=r.u('iiii')
    r.o=noff; names=[]
    for i in range(ncount):
        names.append(r.fstr()); r.u('I')  # hash (ue4>=504)
    # imports
    r.o=ioff; imports=[]
    for i in range(icount):
        cp,cn,cnn,outer,on,onn=r.u('iiiiii'); imports.append((names[cn],names[on],outer))
    r.o=eoff; exports=[]
    for i in range(ecount):
        cls,sup=r.u('ii')
        if ue4>=508: r.u('i')
        outer,on,onn,oflags=r.u('iiiI')
        ssize,soff=r.u('qq')
        r.o+=4*3  # forced export, not for client, not for server
        r.o+=16   # guid
        r.u('I')  # package flags
        r.u('i')  # not always loaded for editor game
        r.u('i')  # is asset
        r.u('iiiii') # preload deps
        exports.append(dict(cls=cls,name=names[on],size=ssize,off=soff))
    return dict(ue4=ue4,names=names,imports=imports,exports=exports,total=total)
def props(r,names,depth=0):
    out={}
    while True:
        ni,nn=r.u('ii'); n=names[ni]
        if n=='None': break
        ti,tn=r.u('ii'); t=names[ti]; size,idx=r.u('ii')
        extra=None
        if t=='StructProperty':
            sn=names[r.u('ii')[0]]; r.o+=16; extra=sn
        elif t in('BoolProperty',):
            v=r.u('B'); r.u('B'); out[n]=v; continue
        elif t in('ByteProperty','EnumProperty'):
            extra=names[r.u('ii')[0]]
        elif t=='ArrayProperty':
            extra=names[r.u('ii')[0]]
        elif t=='MapProperty':
            extra=(names[r.u('ii')[0]],names[r.u('ii')[0]])
        hasguid=r.u('B')
        if hasguid: r.o+=16
        start=r.o; data=r.b[start:start+size]; r.o=start+size
        out[n]=(t,extra,data)
    return out
