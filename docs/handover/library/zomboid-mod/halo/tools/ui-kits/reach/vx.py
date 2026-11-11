import sys,struct
sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
from rres import RR
r=RR(sys.argv[1]); R=r.resource(int(sys.argv[2])); p=bytes(R['prim'])
for v in range(6):
    b=p[v*44:v*44+44]; print(b.hex(' '))
    print('  u16',struct.unpack_from('<22H',b))
print('buf2',p[0xb27e0:0xb27e0+32].hex(' '))
print('idx',struct.unpack_from('<30H',p,0xc2b80))
