import sys,struct
sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
from rres import RR
r=RR(sys.argv[1]); i=int(sys.argv[2])
R=r.resource(i)
print('typ',R['typ'],'ctrl',len(R['ctrl']),'root',hex(R['root']),'prim',len(R['prim']),'sec',len(R['sec']))
c=R['ctrl']
for k in sorted(R['fix']): print(' fix',hex(k),hex(R['fix'][k]))
for o in range(0,min(len(c),0x200),16): print('%04x'%o,c[o:o+16].hex(' '))
