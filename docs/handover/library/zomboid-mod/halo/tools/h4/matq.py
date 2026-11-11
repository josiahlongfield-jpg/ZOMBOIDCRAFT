import sys,struct
sys.path.insert(0,'/tmp/claude-0/h4')
from h4 import H4
r=H4('m90.map'); N=r.names(); S=r.strings(); d=r.d
def sn(v):
    v&=0xffffff
    for x in (v+0x1ab9, v):
        if 0<=x<len(S) and S[x]: return S[x]
    return '?'
for k in map(int,sys.argv[1:]):
    o=r.off(r.X(r.tags[k][2]))
    c,a=struct.unpack_from('<II',d,o+0x1c); b=r.off(r.X(a))
    c,a=struct.unpack_from('<II',d,b+0x54); p=r.off(r.X(a))
    print('==',N[k],c)
    # textures
    tc,ta=struct.unpack_from('<II',d,b); tp=r.off(r.X(ta))
    print(' textures:',[N[struct.unpack_from('<I',d,tp+i*24+12)[0]&0xffff] for i in range(tc)])
    for i in range(c):
        e=p+i*0x2c
        w=struct.unpack_from('<11I',d,e)
        print(' ',i,sn(w[0]),hex(w[0]),[hex(x) for x in w[1:]])
