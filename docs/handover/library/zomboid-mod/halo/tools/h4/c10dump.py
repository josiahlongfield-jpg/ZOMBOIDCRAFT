import sys,struct,pickle; sys.path.insert(0,'/tmp/claude-0/h4')
from h4res import R4
import jm4
r=R4('m90.map'); nm=r.names(); S=r.strings()
jname=sys.argv[1]
o,an,g=jm4.jmad(r,jname,nm,S)
mem=[]
for cnt,datum in g:
    R=r.resource(datum&0xffff); c=bytes(R['ctrl']); p=R['prim']
    for i in range(cnt):
        e=c[i*0x68:(i+1)*0x68]; frames,nodes=struct.unpack_from('<HB',e,8)
        sz=struct.unpack_from('<6I',e,0x10); tot=struct.unpack_from('<I',e,0x54)[0]
        off=R['fix'][i*0x68+0x60]&0xfffffff
        mem.append(dict(frames=frames,nodes=nodes,sz=sz,D=bytes(p[off:off+tot]),hdr=e))
out={}
for i,a in enumerate(an):
    nmn=a['name'][1] or a['name'][0]; sh=a['shared']
    if not sh: continue
    member=struct.unpack_from('<H',sh,0x1a)[0]
    if member<len(mem): out[nmn]=mem[member]
pickle.dump(out,open(sys.argv[2],'wb'))
for k,m in out.items():
    D=m['D']; print(k,'frames',m['frames'],'nodes',m['nodes'],'sz',m['sz'],'tot',len(D)); print('   ',D[:64].hex(' '))
