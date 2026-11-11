import json,sys
sys.path.insert(0,'/mnt/project-files/zomboid-mod/brita/tools')
import conv
S=conv.S
m=json.load(open(S+'pz/models_all.json')); b=json.load(open(S+'pz/brita.json'))
idx={}
for k,v in b.items():
    for e in v: idx.setdefault(e[0],e)
src=json.load(open(S+'w3/src.json'))
out=[]
for gid,s in src.items():
    e=idx[s]; mm=m[e[2]]
    p=conv.find('models_X/'+mm['mesh'],{'.x'})
    if not p: print('NOMESH',gid); continue
    tris,lo,hi=conv.mesh(p,gid); t=conv.tex('textures/'+mm['tex'],gid)
    name=e[1].replace(' w/Extended Stock','').replace(' w/Extend Stock','')
    out.append({'id':gid,'src':s,'name':name,'model':e[2],'tris':tris,'lo':lo,'hi':hi,'tex':t,'att':mm['att']})
    print(gid,tris,t,[round(a-b,3) for a,b in zip(hi,lo)],flush=True)
json.dump(out,open(conv.OUT+'guns3.json','w'))
