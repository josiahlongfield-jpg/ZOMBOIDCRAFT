import fit,sys
bad=0
for g in fit.T:
    ks=fit.kit_sockets(g); nodes,bys,ok=fit.build(g,ks)
    got={(n['path'],n['item']) for n in nodes if n['path']}
    want={(k['path'],k['item']) for k in fit.T[g]['kit'] if k['path'] and k['item'] in fit.P}
    miss=want-got; extra=got-want
    if miss or extra or not ok: bad+=1
    print(g,'ok' if ok else 'INCOMPLETE','missing',len(miss),'extra',len(extra), sorted(ks.keys()) if 'buttpad' in ks else '', list(miss)[:2], list(extra)[:2])
print('bad',bad)
