import base64, json, os
R='/home/user/zomboidcraft/src/main/resources/assets/zomboidcraft/'
def tex_path(ref):
    ns,p=ref.split(':');return R+'textures/'+p+'.png'
names={
 'hunting_rifle':'weapon/hunting_rifle/body','hunting_rifle_mag':'weapon/hunting_rifle/mag','hunting_rifle_scope':'weapon/hunting_rifle/scope_4x','hunting_rifle_handle':'weapon/hunting_rifle/charging_handle',
 'assault_rifle':'weapon/assault_rifle/body','assault_rifle_mag':'weapon/assault_rifle/mag','assault_rifle_stock':'weapon/assault_rifle/stock','assault_rifle_irons':'weapon/assault_rifle/irons_up','assault_rifle_handle':'weapon/assault_rifle/charging_handle','assault_rifle_red_dot':'weapon/assault_rifle/red_dot','assault_rifle_foregrip':'weapon/assault_rifle/foregrip',
 'shotgun':'weapon/shotgun/body','shotgun_handle':'weapon/shotgun/charging_handle',
 'pistol':'weapon/pistol/body','pistol_mag':'weapon/pistol/mag','pistol_handle':'weapon/pistol/charging_handle',
}
for m in ['machete','fire_axe','fire_axe_worn','katana','baseball_bat','baseball_bat_worn','crowbar','kitchen_knife','spiked_bat','hammer','sledgehammer','golf_club','lead_pipe','frying_pan','shovel','pipe_wrench','hockey_stick','pistol_ammo','rifle_ammo','hunting_rifle_ammo','shotgun_shell']:
    names[m]='weapon/'+m
for b in ['military_rucksack','plate_carrier','hiking_backpack','school_backpack','duffel_bag','fanny_pack']: names[b]='bag/'+b
for p in ['scope_4x','red_dot','holo','suppressor','laser','light','foregrip','stock','mag','pistol_suppressor','hunting_rifle_magazine','pistol_magazine']: names['part_'+p]='weapon/part/'+p
for c in ['ammo_can','pistol_case','rifle_case']: names[c]='case/'+c
out={}
for k,p in names.items():
    mj=json.load(open(R+'models/item/'+p+'.json'))
    if 'elements' not in mj: print('skip',k); continue
    els=[]
    for e in mj['elements']:
        d={'from':e['from'],'to':e['to'],'faces':{f:{'uv':v['uv'],**({'rotation':v['rotation']} if 'rotation' in v else {})} for f,v in e['faces'].items() if 'uv' in v}}
        if 'rotation' in e: d['rotation']=e['rotation']
        els.append(d)
    t=mj['textures'].get('0') or list(mj['textures'].values())[0]
    out[k]={'elements':els,'gui':mj.get('display',{}).get('gui'),'tex':'data:image/png;base64,'+base64.b64encode(open(tex_path(t),'rb').read()).decode()}
flat={}
for f in ['bandage','splint','nails','digital_watch']:
    flat[f]='data:image/png;base64,'+base64.b64encode(open(R+'textures/item/'+f+'.png','rb').read()).decode()
json.dump({'models':out,'flat':flat},open('models.json','w'))
print(len(out),'models',os.path.getsize('models.json')//1024,'KB')
