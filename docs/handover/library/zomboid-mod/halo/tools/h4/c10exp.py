import pickle,struct,sys,json,numpy as np; sys.path.insert(0,'/tmp/claude-0/h4')
import janim
janim.OQMODE='s3'
def fix(D):  # treat codec 10 like Reach codec 9
    return bytes([9])+D[1:]
def export(pkl,out,names=None):
    P=pickle.load(open(pkl,'rb')); res={}
    for k,m in P.items():
        D=m['D']
        if not D or D[0]!=10: continue
        if names and k not in names: continue
        st=m['sz'][0]; tail=D[st:]; AF=[janim.bits(tail[48+j*16:48+(j+1)*16]) for j in range(3)]
        A=fix(D[:st]); R=janim.curve_tracks2(A); Tt=janim.trans_tracks(A); F=m['frames']
        rot={}; tr={}
        for t,n in zip(R,AF[0]):
            q=np.array([janim.sample_track(t,f) for f in range(F)]); rot[int(n)]=q[:,[1,2,0,3]].round(5).tolist()
        for t,n in zip(Tt,AF[1]):
            p=np.array([janim.sample_trans(t,f) for f in range(F)]); tr[int(n)]=(p[:,[1,2,0]]*3.048).round(5).tolist()
        res[k]=dict(frames=F,rot=rot,trans=tr); print(k,F,len(rot),len(tr))
    json.dump(res,open(out,'w'))
if __name__=='__main__': export(sys.argv[1],sys.argv[2])
