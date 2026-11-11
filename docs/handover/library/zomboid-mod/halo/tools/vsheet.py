import sys,numpy as np;sys.path.insert(0,'/tmp/claude-0/halo')
from viewglb import load,shot
from PIL import Image,ImageDraw
V='/tmp/claude-0/halo/vanim/'
L=[('vehicles__human__warthog','Warthog',None),('vehicles__human__mongoose','Mongoose',None),('vehicles__human__scorpion','Scorpion',None),('vehicles__human__falcon','Falcon','vehicle:landing_gear'),
('vehicles__human__pelican','Pelican (rear open)','vehicle:fully_opened'),('vehicles__human__sabre','Sabre',None),('vehicles__human__longsword','Longsword',None),('vehicles__human__civilian__military_truck','Military truck',None),
('vehicles__covenant__ghost','Ghost',None),('vehicles__covenant__banshee','Banshee',None),('vehicles__covenant__revenant','Revenant',None),('vehicles__covenant__wraith','Wraith',None),
('vehicles__covenant__phantom','Phantom (doors open)','vehicle:open_close'),('vehicles__covenant__seraph','Seraph',None),('vehicles__covenant__turrets__shade','Shade',None),('vehicles__covenant__turrets__bfg','AA gun (tyrant)',None),
('vehicles__covenant__covenant_fleet__super_carrier','Supercarrier',None),('vehicles__covenant__covenant_fleet__cruiser','CCS cruiser',None),('vehicles__covenant__covenant_fleet__corvette','Corvette',None),('vehicles__covenant__covenant_fleet__lnos','Long Night of Solace',None),
('vehicles__human__unsc_fleet__unsc_stalwart_class_frigate','UNSC frigate',None),('vehicles__human__unsc_fleet__unsc_halcyon_class_cruiser_poa','Pillar of Autumn',None),('vehicles__human__civilian__civilian_transport_ship','Civilian transport',None),('vehicles__human__turrets__mac_15cm_mark_2488','MAC gun',None)]
tiles=[]
for f,label,clip in L:
    p=V+f+'.glb'; absq=None
    if clip:
        j,acc,img=load(p); A=[a for a in j['animations'] if a['name'].startswith(clip)]
        if A:
            A=A[0]; absq={}
            for c in A['channels']:
                s=A['samplers'][c['sampler']]; o=acc(s['output']).astype(float)
                if j['accessors'][s['output']]['componentType']==5122: o=o/32767.
                v=o[len(o)//2 if 'open_close' in clip else -1]; n=j['nodes'][c['target']['node']]
                q,t=absq.get(n['name'],(np.array(n.get('rotation',[0,0,0,1])),np.array(n.get('translation',[0,0,0]))))
                if c['target']['path']=='rotation': q=v
                else: t=v
                absq[n['name']]=(q,t)
    try:
        shot(p,'/tmp/claude-0/halo/vs.png',absq=absq,views=((1,0.35,0.9),),W=320,H=240)
        im=Image.open('/tmp/claude-0/halo/vs.png').convert('RGB')
    except Exception as e: im=Image.new('RGB',(320,240),(40,40,50)); print('fail',f,e)
    ImageDraw.Draw(im).text((6,6),label,fill=(255,255,255)); tiles.append(im)
o=Image.new('RGB',(320*4,240*6),(30,32,40))
for i,t in enumerate(tiles): o.paste(t,((i%4)*320,(i//4)*240))
o.save('/mnt/project-files/zomboid-mod/halo/previews/reach-vehicles-sheet.png'); print('ok')
