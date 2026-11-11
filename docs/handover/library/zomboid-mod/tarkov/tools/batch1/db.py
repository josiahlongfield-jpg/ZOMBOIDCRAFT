import json, functools
D='/mnt/user-data/uploads/tarkov-pilot/spt/'
I=json.load(open(D+'items.json')); G=json.load(open(D+'globals.json')); L=json.load(open(D+'en.json'))
def name(t): return L.get(t+' Name', I.get(t,{}).get('_name','?'))
def short(t): return L.get(t+' ShortName', name(t))
def parent_chain(t):
    out=[]
    while t and t in I:
        out.append(I[t]['_name']); t=I[t].get('_parent')
    return out
def is_a(t, kind): return kind in parent_chain(t)
def slots(t):
    return [(s['_name'], s['_props']['filters'][0].get('Filter',[]) if s['_props'].get('filters') else [], s.get('_required',False)) for s in I[t]['_props'].get('Slots',[])]
SCOPE_KINDS=('Sights','Collimator','CompactCollimator','AssaultScope','OpticScope','SpecialScope','IronSight')
def kind(t):
    ch=parent_chain(t); return ch[1] if len(ch)>1 else ch[0]
@functools.lru_cache(None)
def reach(t, depth=0):
    """all items reachable by mounting under t (ids)"""
    out=set()
    if depth>6: return frozenset()
    for sn,f,req in slots(t):
        for c in f:
            if c in I:
                out.add(c); out|=reach(c,depth+1)
    return frozenset(out)
PRESETS={}
for pid,p in G['ItemPresets'].items():
    PRESETS.setdefault(p['_items'][0]['_tpl'],[]).append(p)
