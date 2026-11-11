import sys,struct
sys.path.insert(0,'/tmp/claude-0/h4')
from h4 import H4
r=H4('m90.map'); N=r.names(); S=r.strings()
def sid(v):
    # try direct index
    for f in (lambda v:v, lambda v:v&0xffffff, lambda v:(v&0x1ffff)):
        i=f(v)
        if 0<i<len(S) and S[i]: return S[i]
    return hex(v)
k=int(sys.argv[1]); o=r.off(r.X(r.tags[k][2]))
d=r.d
print(N[k]); 
for i in range(0,0x100,16): print(hex(i),d[o+i:o+i+16].hex(' '))
c,a=struct.unpack_from('<II',d,o+0x1c); b=r.off(r.X(a))
print('block',c)
for i in range(0,0x200,16): print(hex(i),d[b+i:b+i+16].hex(' '))
