# Tarkov player heads (USEC/BEAR) -> parts.json entries head_<name>, skinned like body parts
import UnityPy, json, os, sys, glob
sys.path.insert(0,'/tmp/claude-0/tk')
import scene
scene.OUT='/tmp/claude-0/tk/web/data/body/'
R='/mnt/user-data/uploads/raw/EscapeFromTarkov_Data__StreamingAssets__Windows__assets__content__characters__character__prefabs__'
P=json.load(open(scene.OUT+'parts.json'))
for f in sorted(glob.glob(R+'*head*.bundle')):
    nm=f[len(R):-7]
    if not (nm.startswith('usec_head') or nm.startswith('bear_head')): continue
    e=UnityPy.load(f); out=[]
    for o in e.objects:
        if o.type.name!='MonoBehaviour': continue
        try: t=o.read_typetree()
        except Exception: continue
        if '_bonePaths' not in t: continue
        pid=t['_skinnedMeshRenderer']['m_PathID']; r=None
        for o2 in e.objects:
            if o2.path_id==pid: r=o2.read(); break
        if r is None: continue
        m=r.m_Mesh.read()
        if 'lod1' in m.m_Name.lower() or 'lod2' in m.m_Name.lower(): continue
        mesh=scene.mesh_bin(m,'head_'+m.m_Name)
        mats=[scene.mat_info(x) for x in r.m_Materials]
        out.append({'name':m.m_Name,'bones':t['_bonePaths'],'mesh':mesh,'mats':mats})
    P['head_'+nm]=out; print(nm,[(p['name'],p['mesh']['n'],[(mm or {}).get('_MainTex') for mm in p['mats']]) for p in out],flush=True)
json.dump(P,open(scene.OUT+'parts.json','w'))
