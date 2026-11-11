"""Python twin of combat/TarkovFit (same rules), for checking and previews."""
import json
P=json.load(open('/tmp/claude-0/tk/b2/parts_game.json')); T=json.load(open('/tmp/claude-0/tk/b2/kit_trees.json')); T.update(json.load(open('/tmp/claude-0/tk/b2/kit_trees_b2.json')))
ORDER=['receiver','barrel','gas_block','handguard','pistol_grip','stock','buttpad','charging','muzzle','suppressor','rear_sight','front_sight','optic','piggyback','underbarrel','side_rail','right_rail','bipod']
def slot(p): return P[p].get('socket') if p in P else None
def kit_sockets(g):
    out={}; kit=T[g]['kit']
    for k in kit:
        s=slot(k['item'])
        if not s or s=='magazine': continue
        if s=='optic' and s in out: s='piggyback'
        elif s=='side_rail' and s in out: s='right_rail'
        if s in out:
            had=out[s]; hp=next((x['path'] for x in kit if x['item']==had),'')
            if k['path'].startswith(hp+'/'): out[s]=k['item']
            continue
        out[s]=k['item']
    return out
def link(m,p,socket,fitted):
    if P[m]['kind']=='Mount' or not P[m].get('socket'): return True
    if slot(m) and slot(m)==slot(p) and slot(m)!='optic': return True
    return socket=='suppressor' and 'muzzle' not in fitted and P[m]['kind'] in('MuzzleCombo','FlashHider')
def routes(sp,port,pid,socket,fitted,depth):
    out=[]
    if pid in port['filter']: out.append([] if depth==0 else [(sp,pid)])
    if depth>=3: return out
    for m in port['filter']:
        if m not in P or not link(m,pid,socket,fitted): continue
        for sn,p2 in P[m]['slots'].items():
            for rest in routes(sp+'/'+sn,p2,pid,socket,fitted,depth+1):
                out.append([(sp,m)]+rest)
    return out
def conflicts(pid,nodes,kit=()):
    for n in nodes.values():
        if pid in kit and n['item'] in kit: continue
        if n['item'] in P.get(pid,{}).get('conflicts',[]) or pid in P.get(n['item'],{}).get('conflicts',[]): return True
    return False
def build(g,fitted):
    kitAt={k['path']:k['item'] for k in T[g]['kit']}
    nodes={'':{'path':'','item':T[g]['base'],'parent':None,'kit':True,'socket':None}}
    bysock={}; complete=True; ks=kit_sockets(g)
    kitPaths={}
    for s,item in ks.items():
        for k in T[g]['kit']:
            if k['item']==item: kitPaths.setdefault(s,[]).append(k['path'])
    def add(path,item,socket):
        par=path[:path.rfind('/')]; pn=nodes.get(par)
        n={'path':path,'item':item,'parent':par,'slot':path[path.rfind('/')+1:],'kit':bool(pn and pn['kit'] and kitAt.get(path)==item),'socket':socket}
        nodes[path]=n; return n
    kitItems=set(kitAt.values())
    # parts whose kit place hangs under another's go after it (the SV-98's chassis before its handguard), the rest in ORDER
    def depth(s):
        ps=kitPaths.get(s)
        return ps[0].count('/') if ps else 99
    # a part with no place yet goes round again once the rest are on (a chassis stock carries the handguard)
    todo=[s for s in sorted(ORDER,key=lambda s:(depth(s),ORDER.index(s))) if fitted.get(s) in P]
    while todo:
      left=[]
      for socket in todo:
        pid=fitted.get(socket)
        best=None; order=0; optic=bysock.get('optic')
        for n in list(nodes.values()):
            for sn,port in P[n['item']]['slots'].items():
                sp=n['path']+'/'+sn
                if sp in nodes: continue
                for chain in routes(sp,port,pid,socket,fitted,0):
                    target=chain[-1][0] if chain else sp; chain=chain[:-1] if chain else chain
                    if conflicts(pid,nodes,kitItems) or any(conflicts(i,nodes,kitItems) for _,i in chain): continue
                    score=order+100*len(chain); order+=1
                    if kitAt.get(target)==pid: score-=10000
                    elif target in kitPaths.get(socket,[]): score-=5000
                    for pth,i in chain:
                        if kitAt.get(pth)==i: score-=2000
                    if socket=='piggyback' and optic and target.startswith(optic['path']+'/'): score-=3000
                    if socket=='optic' and target.rsplit('/',1)[1].startswith('mod_scope'): score-=500
                    if best is None or score<best[0]: best=(score,chain,target)
        if best is None: left.append(socket); continue
        for pth,i in best[1]: add(pth,i,None)
        bysock[socket]=add(best[2],pid,socket)
      if len(left)==len(todo): complete=False; break
      todo=left
    benched=set(ks.values())
    for k in T[g]['kit']:
        if not k['path'] or k['path'] in nodes or k['item'] in benched: continue
        if k['item'] not in P: continue
        par=k['path'][:k['path'].rfind('/')]; pn=nodes.get(par)
        if not pn or not pn['kit'] or conflicts(k['item'],nodes,kitItems): continue
        add(k['path'],k['item'],None)
    return sorted(nodes.values(),key=lambda n:n['path'].count('/')), bysock, complete
def can_fit(g,fitted,socket,pid):
    p=P.get(pid)
    if not p: return False
    s=p.get('socket')
    if not (s==socket or s=='optic' and socket=='piggyback' or s=='side_rail' and socket=='right_rail'): return False
    f=dict(fitted); f[socket]=pid
    return build(g,f)[2]
