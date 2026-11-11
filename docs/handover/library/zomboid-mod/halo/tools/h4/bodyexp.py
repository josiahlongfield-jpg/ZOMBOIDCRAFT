import sys,struct,json,numpy as np; sys.path.insert(0,'/tmp/claude-0/h4')
from h4res import R4
import h4model as M, janim, jm4
def export(r,jname,out,nm,S):
    o,an,g=jm4.jmad(r,jname,nm,S)
    # members across resource groups in order
    mem=[]
    for cnt,datum in g:
        R=r.resource(datum&0xffff); c=bytes(R['ctrl']); p=R['prim']
        for i in range(cnt):
            e=c[i*0x68:(i+1)*0x68]
            frames,nodes=struct.unpack_from('<HB',e,8)
            sz=struct.unpack_from('<6I',e,0x10); tot=struct.unpack_from('<I',e,0x54)[0]
            off=R['fix'][i*0x68+0x60]&0xfffffff
            mem.append(dict(frames=frames,sz=sz,D=bytes(p[off:off+tot])))
    res={}
    for i,a in enumerate(an):
        nmn=a['name'][1] if a['name'][1] else a['name'][0]
        sh=a['shared']; 
        if not sh: continue
        member=struct.unpack_from('<H',sh,0x1a)[0] if len(sh)>0x1b else i
        if member>=len(mem): continue
        m=mem[member]; D=m['D']; st=m['sz'][0]
        if not D or D[0]!=3: res[nmn]=dict(err='codec %d'%(D[0] if D else -1)); continue
        tail=D[st:]; AF=[janim.bits(tail[48+k*16:48+(k+1)*16]) for k in range(3)]
        Rr,Tt=janim.raw3(D)
        rot={int(n):np.array(Rr[k])[:, [1,2,0,3]].round(4).tolist() for k,n in enumerate(AF[0][:len(Rr)])}
        tr={int(n):(np.array(Tt[k])[:, [1,2,0]]*3.048).round(4).tolist() for k,n in enumerate(AF[1][:len(Tt)])}
        res[nmn]=dict(frames=m['frames'],rot=rot,trans=tr)
    json.dump(res,open(out,'w')); return res
if __name__=='__main__':
    r=R4('m90.map'); nm=r.names(); S=r.strings()
    res=export(r,sys.argv[1],sys.argv[2],nm,S)
    for k,v in res.items(): print(k, v.get('frames'), len(v.get('rot',{})), len(v.get('trans',{})), v.get('err',''))
