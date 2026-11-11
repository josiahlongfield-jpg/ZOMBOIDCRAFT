import sys,struct
sys.path.insert(0,'/tmp/claude-0/h4')
from h4 import H4
r=H4('m90.map'); N=r.names(); S=r.strings(); d=r.d
k=int(sys.argv[1]); o=r.off(r.X(r.tags[k][2]))
c,a=struct.unpack_from('<II',d,o+0x1c); b=r.off(r.X(a))
print(N[k])
for fo in range(0,0x78,12):
    c,a=struct.unpack_from('<II',d,b+fo)
    if not c: continue
    p=r.off(r.X(a)); print('field',hex(fo),'count',c)
    for i in range(0,min(c*32,0x1a0),16):
        raw=d[p+i:p+i+16]; print('  ',hex(i),raw.hex(' '),' %.4g %.4g %.4g %.4g'%struct.unpack('<4f',raw))
