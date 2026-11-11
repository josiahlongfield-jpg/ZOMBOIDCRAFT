import sys,struct
sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
from reach import X
r=RR(sys.argv[1]); nm=r.names()
want=sys.argv[2]
for k,t in enumerate(r.tags):
    if t[0]==want.split(':')[0] and nm[k]==want.split(':')[1]:
        o=r.off(X(t[2])); break
d=r.d
print(hex(o))
def blk(o,k):
    c,a=struct.unpack_from('<II',d,o+k); return c,(r.off(X(a)) if c else 0)
size=int(sys.argv[3],16) if len(sys.argv)>3 else 0x110
for q in range(0,size,16): print('%04x'%q,d[o+q:o+q+16].hex(' '))
