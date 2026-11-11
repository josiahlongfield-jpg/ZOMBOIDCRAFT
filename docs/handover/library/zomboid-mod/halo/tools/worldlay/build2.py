import json,os
from PIL import Image
W='/tmp/claude-0/halo/worldlay'; O='/tmp/claude-0/-home-user-zomboidcraft/bb435080-348c-5e82-b018-9d51a3f60885/scratchpad/worldlay'
# id, overlay, title, source, region, old place, x, z, rot, note
S=[
# City
('na','m52','New Alexandria','Campaign: New Alexandria','city','Grayhaven Island downtown',3150,-1250,0,
 'Tower roofs, skybridges, tram rings and landing pads at true scale. Street level and building interiors are built by us from Reach city kit so every block is enterable inside and out. The ONI building pyramid becomes the island landmark.'),
('reflection','52_ivory_tower','Reflection','Multiplayer: Reflection','city','Broadcast Tower penthouse',3100,-2050,0,
 'The penthouse lobby sits at the top of the Broadcast Tower, inside the New Alexandria skyline.'),
('penance','cex_damnation','Penance','Anniversary: Penance','city','Power Station (below ground)',3850,-1800,0,
 'Industrial interior under the Power Station, reached from the plant floor.'),
('exodus','m50','Exodus','Campaign: Exodus','city','Midtown and Uptown (north bank)',1850,-3500,0,
 'Beach, stadium and old-town streets run north from the river bank. Beachhead (Firefight) is already inside it.'),
('breakneck','cex_headlong','Breakneck','Anniversary: Breakneck','city','Foundry Row',1300,1950,0,
 'Industrial block of the city, it replaces Foundry Row.'),
# North
('swordbase','m20','Sword Base','Campaign: ONI Sword Base','north','Fort Caldwell Main Post',-2700,-5900,0,
 'The base, its courtyard and the canyon. The Sword Base multiplayer map and the Courtyard Firefight map are the base interior and yard, so they are already here.'),
('sabre','m45g','Sabre launch facility','Campaign: Long Night of Solace (ground)','north','Caldwell Army Airfield',-1300,-6900,0,
 'Where the mission starts. The Sabre launch pad is the beam-up point to the corvette, and the corvette hangs in the sky above it.'),
('countdown','45_launch_station','Countdown','Multiplayer: Countdown','north','Beside the launch facility',-450,-6650,0,
 'The launch station interior, joined to the Sabre facility by a service road.'),
('breakpoint','dlc_invasion','Breakpoint','Multiplayer: Breakpoint','north','Caldwell Tank Ranges',-3000,-7350,0,
 'Snowbound ONI site on the ranges, between Sword Base and the mountains.'),
('highnoon','cex_hangemhigh','High Noon','Anniversary: High Noon','north','Munitions Depot',-3900,-6600,0,
 'Walled canyon yard, it replaces the Munitions Depot.'),
# Northwest
('package','m60','The Package','Campaign: The Package','northwest','Ridgeview Labs',-6600,-6000,0,
 'Sword Base valley under snow, plus Dr. Halsey\'s lab and the ice caves. Glacier and Outpost (Firefight) are parts of it.'),
('nightfall','m30','Nightfall','Campaign: Nightfall','northwest','Camp Black Pine',-6600,-3400,0,
 'The rocky ridge and pools at night. Waterfront (Firefight) is part of it.'),
('powerhouse','30_settlement','Powerhouse','Multiplayer: Powerhouse','northwest','Black Pine Dam',-5400,-2350,0,
 'The dam and its village, the purge goal from the PLANS list.'),
('solitary','cex_prisoner','Solitary','Anniversary: Solitary','northwest','Riverside Quarantine Zone',-3500,-2850,0,
 'Prison block for the quarantine camp.'),
# West
('winter','m10','Winter Contingency','Campaign: Winter Contingency','west','West farmland (Millbrook to Fairview)',-5000,1900,0,
 'Visegrád farms, the relay outpost and the Covenant camp. Its farmhouses set the look for every rebuilt farm town.'),
('highlands','trainingpreserve','Highlands','Multiplayer: Highlands','west','Dunmore',-6100,1300,0,
 'Hilltop training preserve west of the farms.'),
('overlook','ff10_prototype','Overlook','Firefight: Overlook','west','Fairview',-5300,3650,0,
 'The lookout bluff at the south end of Winter Contingency.'),
# South
('spear','m35','Tip of the Spear','Campaign: Tip of the Spear','south','South farmland, below Kingsbridge',300,7200,0,
 'Open plains, the Spire and the Covenant camp. The Spire multiplayer map is the spire itself, so it is already here.'),
('unearthed','ff_unearthed','Unearthed','Firefight: Unearthed','south','East of Tip of the Spear',1750,7000,0,
 'Dig site beside the plains.'),
('battlecanyon','cex_beaver_creek','Battle Canyon','Anniversary: Battle Canyon','south','Hollow Creek',-1400,5400,0,
 'Creek canyon, it replaces the Hollow Creek hamlet.'),
('ridgeline','cex_timberland','Ridgeline','Anniversary: Ridgeline','south','South forest',-3500,7000,0,
 'Forest valley inside the south woods.'),
# Coast
('aszod','m70','The Pillar of Autumn','Campaign: The Pillar of Autumn','coast','Pelican Point Shipyard',5600,2300,0,
 'Aszod ship-breaking yards and the Pillar of Autumn on the coast. Boneyard (multiplayer) and Holdout (Firefight) are parts of it.'),
('lonewolf','m70_bonus','Lone Wolf','Campaign: Lone Wolf','coast','Pelican Point Refinery',7050,1600,0,
 'The cliff with the anti-air gun, at the tip of the point.'),
('boardwalk','50_panopticon','Boardwalk','Multiplayer: Boardwalk','coast','Seabright Boardwalk',4550,6050,0,
 'Seaside promenade, it replaces the Seabright boardwalk.'),
('tempest','dlc_medium','Tempest','Multiplayer: Tempest','coast','Hollis Island',6400,6200,0,
 'Forerunner island off the south coast.'),
('forge','forge_halo','Forge World','Multiplayer: Forge World','coast','Ravenrock island',6600,-1500,0,
 'Its cliffs, beach and rock pieces also go into the coast where sites meet the sea.'),
('inst04','cex_ff_halo','Installation 04','Anniversary: Installation 04','coast','Cape Merrow',6700,-5150,0,
 'Cliffs and waterfall on the north cape.'),
]
ORB=[ # inset, true m45 coordinates; aft moved into the unbuilt aft half
('cv_ext','Corvette hull','Long Night of Solace exterior',None,None,'The outside of the ship, also used as the model you see from the ground.'),
('cv_bridge','Command deck','m45 bow',None,None,'Bow. Final fight and the slipspace drive.'),
('cv_hall','Long hall','m45',None,None,'Connects the bow to the hangar.'),
('cv_hangar','Hangar','m45, Corvette (Firefight)',None,None,'Where the Sabre lands. Beam-up arrives here.'),
('cv_reactor','Reactor room','m45',None,None,'Round reactor room at the middle of the ship.'),
('cv_aft','Aft decks','Zealot (multiplayer)',230,0,'The mission never builds the back half of the ship. Zealot is a Covenant ship interior, so it becomes the aft decks and engines.'),
('anchor9','Anchor 9','Anchor 9 (multiplayer)',-200,-520,'UNSC station in the same orbit; reachable by dropship from the hangar.'),
('condemned','Condemned','Condemned (multiplayer)',80,-560,'The damaged Anchor 9 section, docked to it.'),
]
KIT=[('riverside','Riverside','city'),('ashby','Ashby','city'),('pinecrest','Pinecrest Estates','city'),('ashby-hts','Ashby Heights','city'),
 ('millbrook','Millbrook','farm'),('fairview','Fairview','farm'),('dunmore','Dunmore','farm'),('timberline','Timberline','farm'),('kingsbridge','Kingsbridge','farm'),('shadyacres','Shady Acres','farm'),
 ('seabright','Seabright','coast'),('gullharbor','Gull Harbor','coast'),('capemerrow','Cape Merrow','coast'),('megaport','Grayhaven Megaport','coast'),('term-n','North Terminal','coast'),
 ('naval','Naval Station Merrow Point','unsc'),('airport','Grayhaven International Airport','unsc'),('guard','National Guard Depot','unsc'),('camp','Camp Black Pine','unsc')]
NEW={'na': (2400, -800, 0, 'East coast city'), 'reflection': (2300, -1350, 0, 'Penthouse at the top of a New Alexandria tower'), 'penance': (2950, -150, 0, 'Under the New Alexandria power plant'), 'exodus': (900, -300, 0, 'City outskirts west of New Alexandria'), 'breakneck': (1700, 750, 0, 'Industrial edge of the city'), 'swordbase': (-2400, -1900, 0, 'Head of the highland valley'), 'sabre': (900, -3000, 0, 'North-east highlands'), 'countdown': (1650, -3100, 0, 'Beside the launch facility'), 'breakpoint': (-3500, -2000, 0, 'Snowfield below The Package'), 'highnoon': (-3500, 500, 0, 'Canyon in the western ridges'), 'package': (-2700, -3100, 0, 'Snowbound top of the valley (same valley as Sword Base, as in the game)'), 'nightfall': (-3300, -700, 0, 'Western ridges'), 'powerhouse': (-2100, -700, 0, 'Dam at the head of the river'), 'solitary': (-300, -150, 0, 'Holding block on the city road'), 'winter': (-800, -2600, 0, 'Highland farms (Visegrád)'), 'highlands': (-1500, -3650, 0, 'Hilltop above the farms'), 'overlook': (-300, -950, 0, 'Bluff at the south end of the farms'), 'spear': (-1200, 1600, 0, 'Southern plains'), 'unearthed': (300, 1500, 0, 'Dig site on the plains'), 'battlecanyon': (-2900, 2600, 0, 'Creek canyon in the plains'), 'ridgeline': (-3300, 3400, 0, 'South-west woods'), 'aszod': (1900, 2600, 0, 'South coast'), 'lonewolf': (3250, 3350, 0, 'Cliff tip of the south coast'), 'boardwalk': (3150, 1300, 0, 'Seafront below the city'), 'tempest': (3700, 2250, 0, 'Island off the south-east coast'), 'forge': (3550, -3200, 90, 'Island off the north-east coast'), 'inst04': (-3500, -3750, 0, 'Waterfall cliffs in the north-west mountains')}
C=json.load(open(f'{W}/world.json'))
kit=C.pop('kit')
def conv(name,q=82):
    im=Image.open(f'{W}/ov/{name}.png'); im.save(f'{O}/ov/{name}.webp','WEBP',quality=q,method=6)
    return json.load(open(f'{W}/ov/{name}.json'))
sites=[]
for sid,ovn,t,src,reg,old,x,z,rot,note in S:
    x,z,rot,old=NEW[sid]
    i=conv(ovn)
    sites.append(dict(id=sid,img=f'ov/{ovn}.webp',title=t,src=src,region=reg,old=old,x=x,z=z,rot=rot,w=round(i['w'],1),d=round(i['d'],1),
        km2=i['area_km2'],y0=i['y_min'],y1=i['y_max'],note=note))
orb=[]
for ovn,t,src,ox,oz,note in ORB:
    i=conv(ovn,85)
    lx,lz=i['lo']; cx,cz=lx+i['w']/2,lz+i['d']/2
    if ox is not None: cx,cz=ox,oz
    orb.append(dict(id=ovn,img=f'ov/{ovn}.webp',title=t,src=src,x=round(cx,1),z=round(cz,1),w=round(i['w'],1),d=round(i['d'],1),y0=i['y_min'],y1=i['y_max'],note=note))
json.dump(dict(world=C,sites=sites,orbit=orb,kit=kit),open(f'{O}/data.json','w'),separators=(',',':'))
print(len(sites),len(orb),len(kit),sum(s['km2'] for s in sites))
