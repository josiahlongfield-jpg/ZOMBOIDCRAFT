import fit,json,collections,sys
P=fit.P; C=json.load(open('catalog.json'))
def socks(s): return [s]+(['piggyback'] if s=='optic' else [])+(['right_rail'] if s=='side_rail' else [])
def chains(g,target,maxd=5):
    """item chains from the base down to target (BFS over slot filters)"""
    base=fit.T[g]['base']; out=[]; q=collections.deque([[base]])
    while q:
        c=q.popleft()
        if len(c)>maxd: continue
        for port in P[c[-1]]['slots'].values():
            for m in port['filter']:
                if m==target: out.append(c[1:]+[m]); 
                elif m in P and m not in c and len(c)<maxd: q.append(c+[m])
        if len(out)>=20: break
    return out
def try_chain(g,ch,start):
    f=dict(start)
    for i in ch:
        s=P[i].get('socket')
        if not s or s=='magazine': continue
        if s in f and f[s]!=i and s=='optic': s='piggyback'
        f[s]=i
    return fit.build(g,f)[2], f
def check(g):
    ks=fit.kit_sockets(g)
    want=[pid for pid,c in C.items() if g in c.get('guns',[]) and P.get(pid,{}).get('socket') not in (None,'magazine')]
    bad=[]
    for pid in want:
        s=P[pid]['socket']
        if any(fit.can_fit(g,ks,so,pid) or fit.can_fit(g,{},so,pid) for so in socks(s)): continue
        ok=False
        for ch in chains(g,pid):
            for start in (ks,{}):
                if try_chain(g,ch,start)[0]: ok=True; break
            if ok: break
        if not ok: bad.append((s,P[pid]['kind'],P[pid].get('short'),pid))
    return want,bad
if __name__=='__main__':
    for g in sys.argv[1:] or fit.T:
        w,b=check(g); print(g,len(w),'unreachable',len(b),b[:8],flush=True)
