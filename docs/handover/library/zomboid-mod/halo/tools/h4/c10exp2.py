import pickle,struct,sys,json,numpy as np; sys.path.insert(0,'/tmp/claude-0/h4')
import janim; janim.OQMODE='s3'
from h4res import R4
import h4model as M
r=R4('m90.map'); nm=r.names(); d=r.d
k,o=M.find(r,'jmad',sys.argv[3] if len(sys.argv)>3 else 'objects\\characters\\storm_cortana\\storm_cortana',nm)
def blk(off): c,a=struct.unpack_from('<II',d,o+off); return c,r.off(r.X(a))
c,a=blk(0x1f0); SR=np.array(struct.unpack_from('<%dh'%(c*4),d,a)).reshape(c,4)/32767.
c,a=blk(0x1fc); ST=np.array(struct.unpack_from('<%df'%(c*3),d,a)).reshape(c,3)
P=pickle.load(open(sys.argv[1],'rb')); res={}
for kk,m in P.items():
    D=m['D']
    if not D or D[0]!=10: continue
    st=m['sz'][0]; tail=D[st:]
    SF=[janim.bits(tail[j*16:(j+1)*16]) for j in range(3)]
    AF=[janim.bits(tail[48+j*16:48+(j+1)*16]) for j in range(3)]
    n=len(SF[0])+len(SF[1])+len(SF[2]); L=struct.unpack_from('<%dH'%n,D,len(D)-2*n)
    A=bytes([9])+D[1:st]; R=janim.curve_tracks2(A); Tt=janim.trans_tracks(A); F=m['frames']
    rot={}; tr={}
    for i,nd in enumerate(SF[0]):
        q=SR[L[i]]; rot[int(nd)]=[q[[1,2,0,3]].round(5).tolist()]*F
    for i,nd in enumerate(SF[1]):
        p=ST[L[len(SF[0])+i]]; tr[int(nd)]=[(p[[1,2,0]]*3.048).round(5).tolist()]*F
    for t,nd in zip(R,AF[0]):
        q=np.array([janim.sample_track(t,f) for f in range(F)]); rot[int(nd)]=q[:,[1,2,0,3]].round(5).tolist()
    for t,nd in zip(Tt,AF[1]):
        p=np.array([janim.sample_trans(t,f) for f in range(F)]); tr[int(nd)]=(p[:,[1,2,0]]*3.048).round(5).tolist()
    res[kk]=dict(frames=F,rot=rot,trans=tr); print(kk,F,len(rot),len(tr),'static',len(SF[0]),len(SF[1]),max(L))
json.dump(res,open(sys.argv[2],'w'))
