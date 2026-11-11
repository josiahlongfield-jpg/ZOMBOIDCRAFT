import sys,struct
sys.path.insert(0,'/tmp/claude-0/h4')
from h4 import H4
r=H4('m90.map'); N=r.names(); S=r.strings(); d=r.d
k=int(sys.argv[1]); o=r.off(r.X(r.tags[k][2]))
print(N[k], 'bank', N[struct.unpack_from('<I',d,o+0x30)[0]&0xffff])
for fo in (0x34,0x40):
    c,a=struct.unpack_from('<II',d,o+fo); p=r.off(r.X(a))
    a2=struct.unpack_from('<I',d,o+fo+12+4)[0]
    sz=(a2-a)*4//c if fo==0x34 else 0
    print('field',hex(fo),c,'est size',sz)
    for i in range(0,0x1c0,16): print('  ',hex(i),d[p+i:p+i+16].hex(' '))
