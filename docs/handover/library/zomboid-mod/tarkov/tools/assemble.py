import sys,glob,re; sys.path.insert(0,'/tmp/claude-0/tk')
import scene
from scene import *
fs=[f for f in glob.glob(R+'*') if 'animations__simple' not in f and 'spirit' not in f and 'audio' not in f]
env=UnityPy.load(*fs)
ROOTS={}
for o in env.objects:
    if o.type.name=='AssetBundle':
        ab=o.read()
        for path,info in ab.m_Container:
            if path.endswith('.prefab'):
                try:
                    go=info.asset.read()
                except Exception: continue
                if not hasattr(go,'m_Component'): continue
                for c in go.m_Component:
                    co=c.component if hasattr(c,'component') else c
                    try:
                        if co.deref().type.name=='Transform':
                            nm=path.split('/')[-1][:-7]
                            if nm not in ROOTS or 'mesh' in ROOTS[nm][1]: ROOTS[nm]=(co.read(),path)
                    except Exception: pass
ROOTS={k:v[0] for k,v in ROOTS.items()}
for o in env.objects:
    if o.type.name=='Transform':
        t=o.read()
        if t.m_Father.path_id==0:
            ROOTS.setdefault(t.m_GameObject.read().m_Name,t)
print('prefabs',len(ROOTS),sorted(ROOTS)[:80])
BASESLOTS={'mod_pistol_grip','mod_magazine','mod_charge','mod_stock','mod_reciever','mod_magazine_new'}
import os
FIX=[float(x) for x in os.environ.get('FIX','0.70710678,0,0,0.70710678').split(',')]
def export(rootname,spec,tag):
    nodes=[]
    def add(t,parent,spec):
        sub=export_root(env,t,tag)
        base=len(nodes)
        for i,n in enumerate(sub):
            n['parent']= parent if n['parent']==-1 else n['parent']+base
            if 'mesh' in n and re.search(r'_LOD[1-9]',n['name']) : del n['mesh']
            nodes.append(n)
        for i,n in enumerate(sub):
            if n['name'] in spec:
                v=spec[n['name']]
                name,child=(v,{}) if isinstance(v,str) else v
                st=len(nodes)
                add(ROOTS[name],base+i,child)
                if abs(n['q'][3])>0.999 or (n['name']=='mod_handguard' and name.endswith('_lower')) or n['name'] in ('mod_muzzle','mod_tactical_000','mod_mount_000','mod_tactical','mod_stock_003','mod_stock_000'): nodes[st]['q']=FIX
    add(ROOTS[rootname],-1,spec)
    return nodes
PLAIN={'mod_pistol_grip':'pistolgrip_ar15_colt_a2','mod_magazine':'mag_stanag_colt_ar15_std_556x45_30','mod_charge':'charge_ar15_colt_charging_handle',
 'mod_stock':('stock_ar15_colt_stock_tube_std',{'mod_stock_000':'stock_ar15_magpul_moe_carbine_blk'}),
 'mod_reciever':('reciever_ar15_colt_m4a1_std',{'mod_barrel':('barrel_ar15_370mm',{'mod_muzzle':'muzzle_ar15_colt_usgi_a2_556x45','mod_gas_block':'gas_block_ar15_colt_m4_front_sight_gas_block_std'}),
   'mod_handguard':('handguard_ar15_colt_m4_length_std',{'mod_handguard':'handguard_ar15_colt_m4_length_std_lower'}),'mod_sight_rear':'sight_rear_ar15_colt_carry_hande_std'})}
KIT={'mod_pistol_grip':'pistolgrip_ar15_magpul_moe','mod_magazine':'mag_stanag_magpul_pmag_gen_m3_556x45_30_blk','mod_charge':'charge_ar15_geissele_ach',
 'mod_stock':('stock_ar15_colt_stock_tube_std',{'mod_stock_000':'stock_ar15_magpul_ctr_carbine'}),
 'mod_reciever':('reciever_ar15_colt_m4a1_std',{'mod_barrel':('barrel_ar15_370mm',{'mod_muzzle':('muzzle_ar15_surefire_sf4p_fh556rc_flash_hider_556x45',{'mod_muzzle':'silencer_socom_surefire_socom556_rc2_556x45'}),'mod_gas_block':'gas_block_ar15_ba_lo_pro'}),
   'mod_handguard':('handguard_ar15_geissele_smr_mk16_135_inch',{'mod_foregrip':'foregrip_all_kac_vertical_grip','mod_tactical_000':'tactical_all_insight_anpeq15_blk','mod_mount_000':('mount_all_surefire_scout_thumbscrew_mount',{'mod_tactical':'flashlight_surefire_m600_ultra_scout'})}),
   'mod_scope':'scope_all_eotech_exps3'})}
if __name__=='__main__':
    for nm,sp in (('plain',PLAIN),('kit',KIT)):
        n=export('weapon_colt_m4a1_556x45_model',sp,'m4')
        json.dump(n,open(OUT+'m4_%s.json'%nm,'w'))
        print(nm,len(n),'meshes',sum('mesh' in x for x in n))
    hands=export('Usec_hands_skin',{},'hands')
    json.dump(hands,open(OUT+'hands.json','w'))
    # bone paths for hands
    for o in env.objects:
        if o.type.name=='MonoBehaviour':
            try: t=o.read_typetree()
            except Exception: continue
            if '_bonePaths' in t: json.dump({'bones':t['_bonePaths'],'root':t['_rootBonePath']},open(OUT+'hands_bones.json','w')); print('hand bones',len(t['_bonePaths']),t['_bonePaths'][:3])
