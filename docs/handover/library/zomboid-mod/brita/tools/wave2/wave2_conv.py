import json,sys
sys.path.insert(0,'/mnt/project-files/zomboid-mod/brita/tools')
import conv
m=json.load(open(conv.S+'pz/models_all.json')); b=json.load(open(conv.S+'pz/brita.json'))
idx={}
for k,v in b.items():
    for e in v: idx.setdefault(e[0],e)
P="Automag G17 HK_USP Colt1903 SW_327 Colt_Service38 AUG9 CAR15SMG MP7 UZI Bush_AR15_MOE AK47 F2000 G36C SCARL FAL SCARH M1 M82 M40A3 SV_98 UTS15 M1014_Wick SPAS12_Fixed Ruger_No1_308 M60 PKM".split()
out=[]
for gid in P:
    e=idx[gid]; mm=m[e[2]]
    src=conv.find('models_X/'+mm['mesh'],{'.x'})
    tris,lo,hi=conv.mesh(src,gid); t=conv.tex('textures/'+mm['tex'],gid)
    out.append({'id':gid,'name':e[1],'model':e[2],'tris':tris,'lo':lo,'hi':hi,'tex':t,'att':mm['att']})
    print(gid,tris,t,[round(a-b,3) for a,b in zip(hi,lo)],flush=True)
json.dump(out,open(conv.OUT+'guns2.json','w'))
