import sys,json,re
sys.path.insert(0,"/tmp/claude-0/tk/b1")
sys.argv=[sys.argv[0]]+sys.argv[1:]
import kitexp as k
from db import I
import UnityPy, scene
items={'f1':I['5710c24ad2720bc3458b45a3']['_props']['Prefab']['path'],'kukri':I['65ca457b4aafb5d7fc0dcb5d']['_props']['Prefab']['path'],'kabar':'assets/content/weapons/kabar/weapon_kabar_usmc_fighter_container.bundle'}
for g,key in items.items():
    fs=[k.RAW+k.P+x.replace('/','__') for x in k.closure({key}) if 'audio' not in x]
    env=UnityPy.load(*fs)
    for o in env.objects:
        if o.type.name=='AssetBundle':
            ab=o.read()
            if ab.m_Name==key:
                print(g,[p for p,i in ab.m_Container if p.endswith('.prefab')])
                r=k.prefab_root(ab,'_model.generated.prefab') or k.prefab_root(ab,'.prefab')
                n=scene.export_root(env,r,g)
                for x in n:
                    if 'mesh' in x and (re.search(r'_LOD[1-9]',x['name']) or re.search(r'collider|mining_kit',x['name'])): del x['mesh']
                json.dump(n,open(scene.OUT+'b1_%s.json'%g,'w')); print(g,len(n),sum('mesh' in x for x in n))
