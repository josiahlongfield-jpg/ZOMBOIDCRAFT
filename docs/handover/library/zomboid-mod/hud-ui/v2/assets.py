import base64, io, json
from PIL import Image
R='/home/user/zomboidcraft/src/main/resources/assets/zomboidcraft/'
out={}
im=Image.open('/mnt/project-files/zomboid-mod/previews/photon-mockup-dusk-rain.png').convert('RGB')
b=io.BytesIO(); im.save(b,'JPEG',quality=84)
out['backdrop']='data:image/jpeg;base64,'+base64.b64encode(b.getvalue()).decode()
models={}
for key,path,tex in [('bat','models/item/weapon/baseball_bat_worn.json','baseball_bat_worn'),('knife','models/item/weapon/kitchen_knife.json','kitchen_knife'),('hammer','models/item/weapon/hammer.json','hammer'),('rifle','models/item/weapon/assault_rifle/body.json','assault_rifle')]:
    mj=json.load(open(R+path))
    els=[]
    for e in mj['elements']:
        d={k:e[k] for k in ('from','to','faces','rotation') if k in e}
        d['faces']={k:{'uv':[round(v,4) for v in f['uv']]} for k,f in d['faces'].items() if 'uv' in f}
        d['from']=[round(v,4) for v in d['from']];d['to']=[round(v,4) for v in d['to']]
        els.append(d)
    t=base64.b64encode(open(R+'textures/item/weapon/'+tex+'.png','rb').read()).decode()
    models[key]={'elements':els,'gui':mj.get('display',{}).get('gui'),'tex':'data:image/png;base64,'+t}
    print(key,len(els),mj.get('display',{}).get('gui'))
out['models']=models
json.dump(out,open('assets.json','w'),separators=(',',':'))
print(len(json.dumps(out))//1024,'KB')
