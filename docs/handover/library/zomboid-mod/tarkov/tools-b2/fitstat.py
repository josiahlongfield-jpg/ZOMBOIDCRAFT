import fit,json,collections
P=fit.P
res={}
for g in fit.T:
    ks=fit.kit_sockets(g); per=collections.Counter(); tot=set()
    for pid,p in P.items():
        s=p.get('socket')
        if not s or s=='magazine': continue
        socks=[s]+(['piggyback'] if s=='optic' else [])+(['right_rail'] if s=='side_rail' else [])
        for so in socks:
            if fit.can_fit(g,ks,so,pid): per[so]+=1; tot.add(pid)
    res[g]=(len(tot),dict(per))
    print(g,len(tot),dict(per),flush=True)
json.dump(res,open('fitstat.json','w'))
