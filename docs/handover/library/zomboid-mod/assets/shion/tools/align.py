import json,base64,numpy as np,sys
from scipy.spatial import cKDTree
S='/tmp/claude-0/-home-user-zomboidcraft/c36db822-1318-52b4-8c86-c6bf032f108a/scratchpad/shion/'
J=json.load(open('ex/mix.json'))
dec=lambda s,t: np.frombuffer(base64.b64decode(s),t)
mb=next(m for m in J['meshes'] if m['name']=='body'); MB=dec(mb['pos'],np.float32).reshape(-1,3)
G=json.load(open(S+'tgeo.json')); blob=open(S+'tgeo.bin','rb').read()
def mesh(i):
    m=G['meshes'][i]; return np.frombuffer(blob,np.float32,m['n']*3,m['pos']).reshape(-1,3)
bare=[i for i in G['looks']['bare|0'] if G['meshes'][i]['name'].startswith('Nude_Body')]
B=np.concatenate([mesh(i) for i in bare])
print('mix bbox',MB.min(0).round(3),MB.max(0).round(3)); print('ours bbox',B.min(0).round(3),B.max(0).round(3))
t=np.zeros(3); t[1]=MB[:,1].min()-B[:,1].min()
tr=cKDTree(MB)
for it in range(6):
    d,j=tr.query(B+t); t+= (MB[j]-(B+t))[d<0.05].mean(0)
d,j=tr.query(B+t); print('t',t.round(4),'nn dist median',np.median(d).round(5),'p99',np.percentile(d,99).round(4),'max',d.max().round(4))
json.dump({'t':t.tolist()},open('align.json','w'))
