import struct,sys
sys.path.insert(0,'/tmp/claude-0/halo')
from cemap import Map
m=Map(sys.argv[1]); d=m.d
fo,sz,mg=0x6a298,1089536,0x53ef6000
boff=lambda a:a-mg+fo
hdr=struct.unpack_from('<I',d,fo)[0]; so=boff(hdr); print('sbsp at',hex(so), hex(hdr))
for k in range(0,0x300,4):
    c,a,z=struct.unpack_from('<III',d,so+k)
    if 0<c<200000 and mg<=a<mg+sz: print(hex(k),c,hex(a))
print('--- collision bsp')
c,a,_=struct.unpack_from('<III',d,so+0xb0); e=boff(a)
for k in range(0,96,12):
    c,a,z=struct.unpack_from('<III',d,e+k); print(k//12,c,hex(a))
