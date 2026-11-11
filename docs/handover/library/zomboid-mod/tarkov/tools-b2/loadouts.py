import fit, json, random, os
P=fit.P
VIS=['optic','suppressor','muzzle','underbarrel','stock','pistol_grip','handguard','side_rail','piggyback','right_rail','charging','rear_sight','front_sight','gas_block','receiver','barrel','bipod']
def cands(g,f,s):
    out=[]
    for pid,p in P.items():
        if not p.get('model') or pid==f.get(s): continue
        so=p.get('socket')
        if not (so==s or so=='optic' and s=='piggyback' or so=='side_rail' and s=='right_rail'): continue
        out.append(pid)
    return out
def loadout(g,seed,prefer):
    r=random.Random(seed); f=dict(fit.kit_sockets(g)); changed=[]
    order=[s for s in prefer]+[s for s in VIS if s not in prefer]
    for s in order:
        if len(changed)>=6: break
        c=cands(g,f,s); r.shuffle(c)
        for pid in c[:400]:
            if fit.can_fit(g,f,s,pid):
                f[s]=pid; changed.append((s,P[pid].get('short'))); break
    return f,changed
if __name__=='__main__':
    out={}
    for g in fit.T:
        ks=fit.kit_sockets(g)
        a=loadout(g,1,['optic','suppressor','underbarrel','stock','pistol_grip','side_rail'])
        b=loadout(g,7,['handguard','optic','piggyback','muzzle','stock','underbarrel'])
        out[g]={'kit':[ks,[]],'a':list(a),'b':list(b)}
        print(g,'A',a[1]); print(g,'B',b[1])
    json.dump(out,open('loadouts.json','w'))
