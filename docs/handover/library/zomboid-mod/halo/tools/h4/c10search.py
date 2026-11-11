import pickle,json,sys,itertools,numpy as np; sys.path.insert(0,'/tmp/claude-0/h4')
import janim
P=pickle.load(open('/tmp/claude-0/h4/c10_base.pkl','rb')); m=P['any:idle']; D=m['D']; st=m['sz'][0]
tail=D[st:]; AF=[janim.bits(tail[48+j*16:48+(j+1)*16]) for j in range(3)]
R=janim.curve_tracks2(bytes([9])+D[1:st])
keys={n:t['keys'][0] for t,n in zip(R,AF[0])}
B=json.load(open('/tmp/claude-0/web/site/body_m093.json'))
cin={}
for s in B.values():
  for n in keys:
    r=s['rot'].get(str(n))
    if r: cin.setdefault(n,[]).extend([np.array(q)[[2,0,1,3]] for q in r[::5]])
def ang(a,b): return 2*np.degrees(np.arccos(min(1,abs(np.dot(a,b)))))
def dec(k,bitsel,order,scale,shift):
  b=[x&1 for x in k]
  idx=(b[bitsel[0]]<<1)|b[bitsel[1]]
  v=np.array([(x>>1)/16383. if shift else x/32767. for x in k])*scale
  w=np.sqrt(max(0,1-np.dot(v,v)))
  q=np.zeros(4)
  others=[(idx+1+i)%4 for i in range(3)] if order=='cyc' else [i for i in range(4) if i!=idx]
  q[idx]=w; q[others]=v; return q/np.linalg.norm(q)
res=[]
for bitsel in itertools.permutations(range(3),2):
 for order in ['cyc','skip']:
  for scale in [1,np.sqrt(2)]:
   for shift in [0,1]:
    errs=[min(ang(dec(keys[n],bitsel,order,scale,shift),c) for c in cin[n]) for n in keys]
    res.append((np.median(errs),bitsel,order,round(scale,2),shift,np.round(errs).astype(int).tolist()))
# baseline xyz-w
errs=[min(ang(np.array(list(np.array(keys[n])/32767.)+[np.sqrt(max(0,1-np.sum((np.array(keys[n])/32767.)**2)))]),c) for c in cin[n]) for n in keys]
res.append((np.median(errs),'xyzw',errs))
for r in sorted(res,key=lambda r:r[0])[:8]: print(r)
print('--- signed perms, xyz direct')
res=[]
for perm in itertools.permutations(range(3)):
 for sg in itertools.product([1,-1],repeat=3):
  errs=[]
  for n in keys:
    v=np.array(keys[n])/32767.; v=v[list(perm)]*np.array(sg); q=np.array(list(v)+[np.sqrt(max(0,1-v@v))])
    errs.append(min(ang(q,c) for c in cin[n]))
  res.append((np.median(errs),perm,sg,np.round(errs).astype(int).tolist()))
for r in sorted(res,key=lambda r:r[0])[:6]: print(r)
print('--- smallest3 b2+2*b1 idx, b0 sign, cyclic')
def s3(k):
  b=[x&1 for x in k]; idx=b[2]+2*b[1]; sg=-1 if b[0] else 1
  v=np.array(k)/32767./np.sqrt(2); q=np.zeros(4)
  q[[(idx+1+i)%4 for i in range(3)]]=v; q[idx]=sg*np.sqrt(max(0,1-v@v)); return q
errs=[min(ang(s3(keys[n]),c) for c in cin[n]) for n in keys]
print(np.round(errs).astype(int).tolist())
