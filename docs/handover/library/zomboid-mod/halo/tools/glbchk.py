import sys;sys.path.insert(0,'/tmp/claude-0/halo')
from viewglb import load,shot
import numpy as np
path,clip,f,out=sys.argv[1],sys.argv[2],int(sys.argv[3]),sys.argv[4]
j,acc,img=load(path)
A=[a for a in j['animations'] if a['name'].startswith(clip)][0]
absq={}
for c in A['channels']:
    s=A['samplers'][c['sampler']]; o=acc(s['output']).astype(float)
    if j['accessors'][s['output']]['componentType']==5122: o=o/32767.
    v=o[min(f,len(o)-1)]; n=j['nodes'][c['target']['node']]
    nm=n['name']; q,t=absq.get(nm,(np.array(n.get('rotation',[0,0,0,1])),np.array(n.get('translation',[0,0,0]))))
    if c['target']['path']=='rotation': q=v
    else: t=v
    absq[nm]=(q,t)
print(A['name'],len(A['channels']))
shot(path,out,absq=absq,views=((1,0.25,0.8),(0,0.15,1)),W=300,H=360,fixc=None if len(sys.argv)>5 else ((0,1.4,0),3.5))
