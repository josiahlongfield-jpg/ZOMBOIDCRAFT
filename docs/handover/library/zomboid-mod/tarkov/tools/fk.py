import json,numpy as np,sys
S=json.load(open('web/data/body/skeleton.json')); A=json.load(open('web/data/body/anims.json'))
names=[n['name'] for n in S]
def qm(q):
    x,y,z,w=q; return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
def fk(L,f):
    ch={k.split('/')[-1]:v for k,v in L['bones'].items()}
    W=[None]*len(S)
    for i,n in enumerate(S):
        c=ch.get(n['name'],{})
        p=c['p'][min(f,len(c['p'])-1)] if 'p' in c else n['p']
        q=c['q'][min(f,len(c['q'])-1)] if 'q' in c else n['q']
        M=np.eye(4); M[:3,:3]=qm(q); M[:3,3]=p
        W[i]=M if n['parent']<0 else W[n['parent']]@M
    return W
def pos(W,n): return W[names.index(n)][:3,3]
for clip in sys.argv[1:]:
  for li,L in enumerate(A[clip]):
    if L['nb']<79: continue
    out=[]
    for f in (0,L['nf']//2):
        W=fk(L,f); pe=pos(W,'Base HumanPelvis'); hd=pos(W,'Base HumanHead'); r=pos(W,'Base HumanRPalm'); l=pos(W,'Base HumanLPalm')
        out.append('f%d head-pelvis %s Rpalm %s Lpalm %s'%(f,(hd-pe).round(2),r.round(2),l.round(2)))
    print(clip,li,L['nf'],' | '.join(out))
