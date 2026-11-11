import json
H=4096
W=dict(size=8192,
areas=[
 dict(id='plains',p=[[-H,700],[-2600,700],[-1300,300],[200,600],[600,1100],[800,H],[-H,H]]),
 dict(id='ridges',p=[[-H,-1450],[-3000,-1450],[-1300,-1100],[-1300,300],[-2600,700],[-H,700]]),
 dict(id='highland',p=[[-1250,-H],[2000,-H],[2100,-3400],[1300,-2100],[200,-1300],[-1300,-1100],[-3000,-1450],[-1250,-2350]]),
 dict(id='alpine',p=[[-H,-H],[-1250,-H],[-1250,-2350],[-3000,-1450],[-H,-1450]]),
 dict(id='city',p=[[200,-1300],[1300,-2100],[2100,-3400],[3300,-2050],[3350,0],[3000,900],[1300,1000],[400,1050],[200,600],[-1300,300],[-1300,-1100]]),
 dict(id='coast',p=[[800,H],[600,1100],[1300,1000],[3000,900],[3900,H]]),
 dict(id='sea',p=[[1900,-H],[1950,-3550],[2600,-3000],[3300,-2050],[3380,-900],[3350,0],[3250,700],[3150,1300],[2900,1900],[2880,2600],[3050,3050],[3650,3450],[3850,H],[H,H],[H,-H]]),
],
islands=[dict(id='forge-i',x=3550,z=-3200,rx=520,rz=820,kind='cliff'),dict(id='tempest-i',x=3700,z=2250,rx=330,rz=230,kind='low')],
lakes=[dict(id='reservoir',x=-2520,z=-700,rx=270,rz=210)],
rivers=[dict(id='river',w=110,p=[[-1950,-650],[-1500,-350],[-1000,150],[-300,700],[400,1070],[1300,1075],[2300,1010],[3000,900],[3300,860]])],
roads=[
 dict(id='valley',cls='i',p=[[-2700,-3100],[-2400,-1900],[-1700,-1300],[-300,-950],[500,-600],[900,-300],[2400,-800]]),
 dict(id='farms',cls='ky',p=[[-800,-2600],[-300,-950]]),
 dict(id='sabre',cls='ky',p=[[-800,-2600],[300,-2900],[900,-3000],[1650,-3100]]),
 dict(id='coastway',cls='i',p=[[2400,-800],[2700,500],[1900,2600],[1100,3300]]),
 dict(id='plains',cls='ky',p=[[-300,-950],[-900,600],[-1200,1600],[300,1500],[1900,2600]]),
 dict(id='ridge',cls='loc',p=[[-2400,-1900],[-3300,-700],[-2100,-700],[-1700,-1300]]),
 dict(id='boardw',cls='loc',p=[[2700,500],[3150,1300]]),
 dict(id='snow',cls='loc',p=[[-2700,-3100],[-3500,-2000]]),
],
rail=[],urban=[],places=[],
kit=[
 dict(id='k1',name='Farmstead',style='farm',x=-1900,z=-2600),dict(id='k2',name='Farmstead',style='farm',x=200,z=-2000),
 dict(id='k3',name='Relay outpost',style='unsc',x=-2200,z=300),dict(id='k4',name='Plains farm',style='farm',x=-2300,z=1700),
 dict(id='k5',name='Plains farm',style='farm',x=-500,z=3000),dict(id='k6',name='Fishing village',style='coast',x=1200,z=3900),
 dict(id='k7',name='Highway town',style='city',x=1500,z=-1900),dict(id='k8',name='UNSC outpost',style='unsc',x=-3700,z=1700),
 dict(id='k9',name='Ranger station',style='unsc',x=-3600,z=-1100),dict(id='k10',name='Harbour',style='coast',x=3000,z=-2300),
])
json.dump(W,open('/tmp/claude-0/halo/worldlay/world.json','w'))
