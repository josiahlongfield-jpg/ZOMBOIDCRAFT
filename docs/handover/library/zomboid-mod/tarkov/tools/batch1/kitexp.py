"""Export a batch-1 gun with its full kit as a node tree for the three.js preview page (web/index.html build()).
Each mod root is turned so its LOD0 mesh frame equals its slot frame. usage: kitexp.py <gun> [fixmode]"""
import sys,json,os,re
TK2='/tmp/claude-0/-home-user-zomboidcraft/10e3aa49-17b2-5030-8c7e-5f297751f80d/scratchpad/tk2'
sys.path.insert(0,TK2); sys.path.insert(0,'/tmp/claude-0/tk')
cwd=os.getcwd(); os.chdir(TK2)
from db import *; from kit import resolve
os.chdir(cwd)
import UnityPy, scene
RAW='/tmp/claude-0/-home-user-zomboidcraft/10e3aa49-17b2-5030-8c7e-5f297751f80d/scratchpad/raw1/'
P='EscapeFromTarkov_Data__StreamingAssets__Windows__'
W=json.load(open('/mnt/user-data/uploads/Escape from Tarkov/build/EscapeFromTarkov_Data/StreamingAssets/Windows/Windows.json'))
K=json.load(open(TK2+'/kits.json'))
FIX=[0.70710678,0,0,0.70710678]
def closure(keys):
    seen=set(); st=list(keys)
    while st:
        k=st.pop()
        if k in seen or k not in W: continue
        seen.add(k); st+=W[k].get('Dependencies',[])
    return seen
def prefab_root(ab, want):
    for p,i in ab.m_Container:
        if p.endswith(want):
            go=i.asset.read()
            for c in go.m_Component:
                o=c.deref() if hasattr(c,'deref') else c.component.deref()
                if o.type.name=='Transform': return o.read()
def gun(g, tag=None):
    v=K[g]; tag=tag or g
    parts=[('',v['id'])]+resolve(v['id'],v['kit'])
    key={c:I[c]['_props']['Prefab']['path'] for p,c in parts}
    fs=[RAW+P+k.replace('/','__') for k in closure(set(key.values())) if 'audio' not in k]
    env=UnityPy.load(*fs)
    roots={}
    for o in env.objects:
        if o.type.name=='AssetBundle':
            ab=o.read()
            if ab.m_Name in key.values():
                roots[ab.m_Name]=prefab_root(ab,'_model.generated.prefab' if '/weapons/' in ab.m_Name else '.prefab') or prefab_root(ab,'.prefab')
    nodes=[]; placed={}
    def add(t,parent):
        sub=scene.export_root(env,t,tag); base=len(nodes)
        for n in sub:
            n['parent']=parent if n['parent']==-1 else n['parent']+base
            if 'mesh' in n and (re.search(r'_LOD[1-9]',n['name']) or re.search(r'collider|mining_kit',n['name'])): del n['mesh']
            nodes.append(n)
        return base,len(sub)
    span={}
    for path,c in parts:
        if path=='':
            span['']=add(roots[key[c]],-1); continue
        par,slot=path.rsplit('/',1); b,n=span[par]
        hit=[i for i in range(b,b+n) if nodes[i]['name']==slot]
        if not hit: print('!! slot',slot,'not in',par or 'gun'); continue
        st=len(nodes); span[path]=add(roots[key[c]],hit[0]); nodes[st]['p']=[0,0,0]
        # the part's mesh frame lines up with the slot: undo the turn its LOD0 carries under its own root
        lod=[x for x in nodes[st+1:] if x['parent']==st and re.search(r'_LOD0$',x['name'])]
        lod=[x for x in lod if x['name']==nodes[st]['name']+'_LOD0'] or lod
        q=lod[0]['q'] if lod else [-FIX[0],0,0,FIX[3]]
        nodes[st]['q']=[-q[0],-q[1],-q[2],q[3]]
        # a part only shows where it is mounted: hide its own LOD1+ and skip
    return nodes
if __name__=='__main__':
    g=sys.argv[1]; n=gun(g)
    os.makedirs(scene.OUT,exist_ok=True); json.dump(n,open(scene.OUT+'b1_%s.json'%g,'w'))
    print(g,len(n),'nodes',sum('mesh' in x for x in n),'meshes', sum('meshfail' in x for x in n),'fails')
