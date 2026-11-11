import sys,struct; sys.path.insert(0,'/tmp/claude-0/h4'); from h4 import H4
r=H4(sys.argv[1]) if not hasattr(sys,'R') else sys.R
def refl(o,size):
    d=r.d; out=[]
    lo=r.vbase; hi=r.vbase+ (len(d)-r.tbase)
    for k in range(0,size,4):
        c,a=struct.unpack_from('<II',d,o+k)
        if 0<c<100000 and lo<=r.X(a)<hi: out.append((k,c,r.X(a)))
    return out
if __name__=='__main__':
    nm=r.names(); grp=sys.argv[2]; name=sys.argv[3]; size=int(sys.argv[4],0)
    for (g,dt,a),n in zip(r.tags,nm):
        if g==grp and n==name:
            o=r.off(r.X(a)); print('tag at',hex(o))
            for k,c,x in refl(o,size): print(hex(k),c,hex(r.off(x)))
            print(r.d[o:o+size].hex(' ',4))
