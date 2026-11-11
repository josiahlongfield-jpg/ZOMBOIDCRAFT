import sys,struct
sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
from reach import Reach,X
r=Reach(sys.argv[1])
for a in sys.argv[2:]:
    addr,n=a.split(':'); o=r.off(X(int(addr,16))); n=int(n,16)
    print('==',addr)
    for q in range(0,n,16): print('%04x'%q,r.d[o+q:o+q+16].hex(' '))
