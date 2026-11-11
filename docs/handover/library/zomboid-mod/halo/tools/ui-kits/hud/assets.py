import base64, io, json
from PIL import Image
T='/home/user/zomboidcraft/src/main/resources/assets/zomboidcraft/textures/'
def uri(path):
    return 'data:image/png;base64,'+base64.b64encode(open(path,'rb').read()).decode()
out={}
for k in ['webbing','tape','slot','slot_selected','canvas','metal']:
    out[k]=uri(T+'gui/sprites/inventory/'+k+'.png')
for k in ['baseball_bat','kitchen_knife','hammer','bandage','crowbar']:
    out[k]=uri(T+'item/'+k+'.png')
im=Image.open('/mnt/project-files/zomboid-mod/previews/photon-mockup-dusk-rain.png').convert('RGB')
b=io.BytesIO(); im.save(b,'JPEG',quality=80)
out['backdrop']='data:image/jpeg;base64,'+base64.b64encode(b.getvalue()).decode()
json.dump(out,open('assets.json','w'),separators=(',',':'))
print({k:len(v)//1024 for k,v in out.items()})
