"""Exports Tarkov parts for bench fitting: each part's node tree relative to its mount frame (where its slot puts it,
as kitexp places a kit part: root turned FIX * its own stored turn), with its meshes, sub-slots, eye points, grip hold
and fireport. usage: pexp.py OUT_DIR id [id...]   (writes OUT_DIR/<id>.json; meshes/textures into web/data as pt_*)"""
import sys,json,os,re,time
import numpy as np
sys.path.insert(0,'/tmp/claude-0/tk'); sys.path.insert(0,'/tmp/claude-0/tk/b1')
import kitexp as k, UnityPy, scene
from scipy.spatial.transform import Rotation as Rot
CAT=json.load(open('/tmp/claude-0/tk/b2/catalog.json'))
def M(p,q,s=(1,1,1)):
    m=np.eye(4); m[:3,:3]=Rot.from_quat(q).as_matrix()*np.array(s); m[:3,3]=p; return m
def unscaled(m):
    m=m.copy(); m[:3,:3]/=np.linalg.norm(m[:3,:3],axis=0); return m
def pq(m):
    m=unscaled(m)
    if np.linalg.det(m[:3,:3])<0: m[:3,0]*=-1      # a mirrored node: its turn without the mirror
    q=Rot.from_matrix(m[:3,:3]).as_quat()
    return [round(float(x),6) for x in list(m[:3,3])+list(q)]
def mount_turn(rootnode, key):
    a=k.FIX; b=rootnode['q']
    q=[a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1], a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0], a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3], a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2]]
    return q
def export(pid, weapon=False):
    e=CAT.get(pid) or {}
    key=e.get('prefab') or k.I[pid]['_props']['Prefab']['path']
    fs=[k.RAW+k.P+x.replace('/','__') for x in k.closure({key}) if 'audio' not in x]
    env=UnityPy.load(*fs); root=None
    for o in env.objects:
        if o.type.name=='AssetBundle':
            ab=o.read()
            if ab.m_Name==key:
                root=k.prefab_root(ab,'_model.generated.prefab' if '/weapons/' in key else '.prefab') or k.prefab_root(ab,'.prefab')
    if root is None: return {'id':pid,'fail':'no prefab root'}
    nodes=scene.export_root(env,root,'pt')
    # world (mount frame) of every node: the root at the mount turned as kitexp turns it
    W=[None]*len(nodes)
    for i,n in enumerate(nodes):
        if n['parent']==-1:
            q=list(k.TURN.get(n['name'], mount_turn(n,key))) if not weapon else n['q']
            W[i]=M([0,0,0] if not weapon else n['p'],q,n['s'])
        else: W[i]=W[n['parent']]@M(n['p'],n['q'],n['s'])
    act=[True]*len(nodes)
    for i,n in enumerate(nodes):
        act[i]=n.get('active',True) and (n['parent']==-1 or act[n['parent']])
    out={'id':pid,'name':e.get('name'),'kind':e.get('kind'),'root':nodes[0]['name'],'meshes':[],'slots':{},'aims':[],'fireport':None,'holds':[]}
    rootname=nodes[0]['name']
    # the slots that are this part's own (a nested mod_ inside another mod_ belongs to that one)
    def owner_slot(i):
        x=nodes[i]['parent']
        while x!=-1:
            if nodes[x]['name'].startswith('mod_'): return x
            x=nodes[x]['parent']
        return -1
    for i,n in enumerate(nodes):
        nm=n['name']
        if 'mesh' in n and not re.search(r'_LOD[1-9]|collider|mining_kit',nm) and act[i] and owner_slot(i)==-1:
            out['meshes'].append({'node':i,'name':nm,'at':pq(W[i]),'s':[float(np.linalg.norm(W[i][:3,c])) for c in range(3)]})
        if nm.startswith('mod_') and not nm.startswith('mod_aim_camera') and owner_slot(i)==-1:
            out['slots'][nm]={'at':pq(W[i])}
        if nm.startswith('mod_aim_camera') and owner_slot(i)==-1: out['aims'].append({'name':nm,'at':pq(W[i])})
        if nm in('fireport',) and owner_slot(i)==-1 and out['fireport'] is None: out['fireport']=pq(W[i])
        if nm.startswith('Base HumanLPalm') and owner_slot(i)==-1:
            suf=nm[len('Base HumanLPalm'):]; fingers={}
            def walk(j):
                for c,cn in enumerate(nodes):
                    if cn['parent']==j:
                        if cn['name'].startswith('Base HumanLDigit'): fingers[cn['name'][:len(cn['name'])-len(suf)] if suf else cn['name']]=[round(float(x),5) for x in cn['q']]
                        walk(c)
            walk(i); out['holds'].append({'name':nm,'at':pq(W[i]),'fingers':fingers})
    # filters for each own slot
    sl={s['_name']:s for s in k.I[pid]['_props'].get('Slots',[])}
    for sn,v in out['slots'].items():
        s=sl.get(sn)
        if s: v['filter']=[c for c in (s['_props']['filters'][0].get('Filter',[]) if s['_props'].get('filters') else []) if c in CAT]; v['required']=s.get('_required',False)
    out['nodes']=[{'name':n['name'],'parent':n['parent'],'mesh':n.get('mesh'),'mats':n.get('mats'),'active':act[i]} for i,n in enumerate(nodes)]
    out['world']=[W[i].tolist() for i in range(len(nodes))]
    return out
if __name__=='__main__':
    od=sys.argv[1]; os.makedirs(od,exist_ok=True)
    for pid in sys.argv[2:]:
        if os.path.exists(od+'/'+pid+'.json'): continue
        t=time.time()
        try: r=export(pid, weapon=pid not in CAT)
        except Exception as ex: r={'id':pid,'fail':repr(ex)[:200]}
        json.dump(r,open(od+'/'+pid+'.json','w'))
        print(pid, r.get('name'), 'FAIL '+r['fail'] if 'fail' in r else '%d meshes %d slots %d aims %d holds'%(len(r['meshes']),len(r['slots']),len(r['aims']),len(r['holds'])), '%.1fs'%(time.time()-t), flush=True)
