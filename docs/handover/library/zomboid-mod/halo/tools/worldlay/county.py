import json,re
F='/home/user/zomboidcraft/src/main/resources/data/zomboidcraft/county_plan.txt'
def pts(toks):
    out=[]
    for t in toks:
        if ',' in t:
            a,b=t.split(','); out.append([round(float(a)),round(float(b))])
    return out
D=dict(places=[],rivers=[],areas=[],lines=[],roads=[],rail=[],lakes=[],islands=[],urban=[],piers=0,ships=0)
pl={}
for ln in open(F):
    t=ln.split()
    if not t or t[0].startswith('#'): continue
    k=t[0]
    if k=='place':
        name=' '.join(t[7:]); D['places'].append(dict(id=t[1],kind=t[2],style=t[3],x=float(t[4]),z=float(t[5]),name=name)); pl[t[1]]=(float(t[4]),float(t[5]))
    elif k=='river': D['rivers'].append(dict(id=t[1],w=float(t[2]),p=pts(t[3:])))
    elif k=='area': D['areas'].append(dict(id=t[1],p=pts(t[2:])))
    elif k=='line': D['lines'].append(dict(id=t[1],p=pts(t[2:])))
    elif k=='road': D['roads'].append(dict(id=t[1],cls=t[2],p=pts(t[4:])))
    elif k in('rail','subway'): D['rail'].append(dict(id=t[1],kind=k,p=pts(t[2:])))
    elif k=='lake': D['lakes'].append(dict(id=t[1],x=float(t[2]),z=float(t[3]),rx=float(t[4]),rz=float(t[5])))
    elif k=='island': D['islands'].append(dict(id=t[1],x=float(t[2]),z=float(t[3]),rx=float(t[4]),rz=float(t[5]),kind=t[6]))
    elif k=='urban':
        if t[1] in pl: D['urban'].append(dict(id=t[1],x=pl[t[1]][0],z=pl[t[1]][1],r=float(t[2])))
    elif k=='pier': D['piers']+=1
    elif k=='ship': D['ships']+=1
json.dump(D,open('/tmp/claude-0/halo/worldlay/county.json','w'),separators=(',',':'))
print({k:(len(v) if isinstance(v,list) else v) for k,v in D.items()})
# simplify long polylines
