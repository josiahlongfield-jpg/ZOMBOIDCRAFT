import sys,struct; sys.path.insert(0,'/tmp/claude-0/h4')
from h4res import R4
import h4model as M
r=R4('m90.map'); nm=r.names(); d=r.d
k,o=M.find(r,'jmad','objects\\characters\\storm_cortana\\storm_cortana',nm)
print(k,o)
for off in range(0,0x300,4):
    c,a=struct.unpack_from('<II',d,o+off)
    if 0<c<100000 and a>0x10000:
        try:
            p=r.off(r.X(a))
            print(hex(off),c,hex(a), d[p:p+32].hex(' '))
        except Exception as e: pass
