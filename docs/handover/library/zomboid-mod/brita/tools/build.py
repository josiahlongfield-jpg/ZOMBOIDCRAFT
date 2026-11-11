import json, os, subprocess, re, sys
OUT=os.path.dirname(os.path.abspath(__file__))
SND=os.path.join(OUT,'snd'); os.makedirs(SND,exist_ok=True)
AR="/mnt/user-data/uploads/108600/2297098490/mods/ARSENAL(26)GunFighter[MOD 2.0]/media/sound/"
BR="/mnt/user-data/uploads/108600/2200148440/mods/Brita's Weapon Pack/media/sound/"
OURS="/home/user/zomboidcraft/src/main/resources/assets/zomboidcraft/sounds/"
done={}
def conv(src, key):
    if key in done: return done[key]
    dst=os.path.join(SND,key+'.mp3')
    if not os.path.exists(dst):
        r=subprocess.run(['ffmpeg','-v','error','-y','-i',src,'-ac','1','-ar','44100','-b:a','112k',dst],capture_output=True,text=True)
        if r.returncode: print('FAIL',src,r.stderr[:200]); return None
    done[key]='snd/'+key+'.mp3'; return done[key]
def slug(s): return re.sub(r'[^A-Za-z0-9]+','_',s).strip('_')
def A(f): return ('a', AR+f, 'Arsenal', f.rsplit('.',1)[0])
def B(f): return ('b', BR+f, 'Brita', f.rsplit('.',1)[0])
def O(p): return ('o', OURS+p+'/1.ogg', 'ZomboidCraft now', p.replace('/',' · '))
def shots(c): return [('Shot',[A('[1]Shot_%s.wav'%c)]),('Suppressed shot',[A('SD[1]Shot_%s.wav'%c)]),('Compensator shot',[A('COMP[1]Shot_%s.wav'%c)])]
G=[
 ('Pistols','Colt M1911','.45 ACP', shots('45')+[('Mag out',[B('Stormy1911ClipEject.wav')]),('Mag in',[B('Stormy1911ClipLoad.wav')]),('Slide rack',[B('1911rack.wav')])],
   {'Shot':'weapon/pistol/shot','Suppressed shot':'weapon/pistol/shot_suppressed','Mag out':'weapon/pistol/mag_out','Mag in':'weapon/pistol/mag_in','Slide rack':'weapon/pistol/slide'}),
 ('Revolvers','S&W Model 500','.50 Magnum', [('Shot',[A('[1]Shot_50MAG.wav'),B('DEshot.wav')])]+shots('50MAG')[1:]+[('Round in',[B('stormy500InsertRound.wav')]),('Speedloader',[B('SpeedLoader.ogg')])],
   {'Shot':'weapon/magnum/shot','Round in':'weapon/magnum/round_in','Speedloader':'weapon/magnum/eject'}),
 ('SMGs','AR-57 PDW','5.7x28', shots('57')+[('Mag out',[B('StormyP90ClipEject.wav'),B('StormyARClipEject.wav')]),('Mag in',[B('StormyP90ClipLoad.wav'),B('StormyARClipLoad.wav')]),('Charging handle',[B('P90rack.wav'),B('ARrack.wav')])],
   {'Shot':'weapon/smg/shot','Suppressed shot':'weapon/smg/shot_suppressed','Mag out':'weapon/smg/mag_out','Mag in':'weapon/smg/mag_in','Charging handle':'weapon/smg/cock'}),
 ('Pistol-calibre carbine','M2 Carbine','9mm (Arsenal setting)', shots('9')+[('Mag out',[B('StormySMGClipEject.wav'),B('StormySUB2000ClipEject.wav')]),('Mag in',[B('StormySMGClipLoad.wav'),B('StormySUB2000ClipLoad.wav')]),('Bolt',[B('SMGrack.wav'),B('SUB2000rack.wav')])],
   {'Shot':'weapon/smg/shot','Suppressed shot':'weapon/smg/shot_suppressed','Mag out':'weapon/smg/mag_out','Mag in':'weapon/smg/mag_in','Bolt':'weapon/smg/cock'}),
 ('Assault rifles','AAC Honey Badger','5.56 NATO', shots('556')+[('Mag out',[B('StormyARClipEject.wav')]),('Mag in',[B('StormyARClipLoad.wav')]),('Charging handle',[B('ARrack.wav')])],
   {'Shot':'weapon/rifle/shot','Suppressed shot':'weapon/rifle/shot_suppressed','Mag out':'weapon/rifle/mag_out','Mag in':'weapon/rifle/mag_in','Charging handle':'weapon/rifle/bolt'}),
 ('Battle rifles','M14','.308', shots('308')+[('Mag out',[B('StormyFALClipEject.wav')]),('Mag in',[B('StormyFALClipLoad.wav')]),('Op rod',[B('FALrack.wav'),B('M1rack.wav')])],
   {'Shot':'weapon/battle_rifle/shot','Suppressed shot':'weapon/battle_rifle/shot_suppressed','Mag out':'weapon/rifle/mag_out','Mag in':'weapon/rifle/mag_in','Op rod':'weapon/rifle/bolt'}),
 ('Sniper rifles','BOAR .50','.50 BMG', [('Shot',[A('[1]Shot_50.wav'),B('M40shot.wav')])]+shots('50')[1:]+[('Bolt open/close',[B('Bolt1Rack.wav'),B('M40rack.wav'),B('M82rack.wav')]),('Round in',[B('StormyM82ClipLoad.wav')]),('Round out',[B('StormyM82ClipEject.wav')])],
   {'Shot':'weapon/heavy_sniper/shot','Suppressed shot':'weapon/heavy_sniper/shot_suppressed','Bolt open/close':'weapon/hunting_rifle/bolt','Round in':'weapon/rifle/load_round'}),
 ('Shotguns','Remington 870 CQB','12 gauge', shots('12g')+[('Shell in',[B('WinStormyShotgunInsertRound.wav'),B('R11-87StormyShotgunInsertRound.wav'),B('M1897StormyShotgunInsertRound.wav')]),('Pump',[B('WinstormyShotgunPump.wav'),B('R11-87stormyShotgunPump.wav'),B('M1897stormyShotgunPump.wav')])],
   {'Shot':'weapon/shotgun/shot','Shell in':'weapon/shotgun/shell_in','Pump':'weapon/shotgun/pump'}),
 ('Lever and single-shot','Winchester 1866','.45 Colt', shots('45LC')+[('Round in',[B('MR_insert.wav'),B('MR2_insert.wav')]),('Lever / cock',[B('coch.wav')])],
   {'Shot':'weapon/lever_action/shot','Round in':'weapon/lever_action/round_in','Lever / cock':'weapon/lever_action/lever'}),
 ('Rimfire','American-180','.22 LR', shots('22')+[('Pan off',[B('AmericanClipEject.wav'),B('Stormy22LRClipEject.wav')]),('Pan on',[B('AmericanClipLoad.wav'),B('Stormy22LRClipLoad.wav')]),('Bolt',[B('AmericanRack.wav'),B('22LRrack.wav')])],
   {'Shot':'weapon/rimfire/shot','Suppressed shot':'weapon/rimfire/shot_suppressed','Pan off':'weapon/rimfire/mag_out','Pan on':'weapon/rimfire/mag_in','Bolt':'weapon/rimfire/cock'}),
 ('Machine guns','L86 LSW','5.56 NATO', shots('556')+[('Mag out',[B('StormyARClipEject.wav'),B('StormyK3ClipEject.wav')]),('Mag in',[B('StormyARClipLoad.wav'),B('StormyK3ClipLoad.wav')]),('Charging handle',[B('ARrack.wav'),B('K3rack.wav')])],
   {'Shot':'weapon/rifle/shot','Suppressed shot':'weapon/rifle/shot_suppressed','Mag out':'weapon/rifle/mag_out','Mag in':'weapon/rifle/mag_in','Charging handle':'weapon/rifle/bolt'}),
 ('Launchers','RPG-7','HE rocket', [('Shot',[A('[1]Shot_Rocket.wav')]),('Rocket in',[B('M32StormyShotgunInsertRound.wav')]),('Tube out',[B('StormyM32ClipEject.wav')]),('Hammer cock',[B('M32Rack.wav')]),('Rocket blast',[B('K11BigExplosion.wav')])],
   {'Shot':'weapon/grenade_launcher/shot'}),
 ('Bows and crossbows','TenPoint Turbo XLT','Bolt', [('Shot',[B('TAC15Shot.wav')]),('Draw / cock',[B('TAC15Reload.wav')]),('Bolt in',[B('TAC15Insert.wav')]),('Bolt out',[B('TAC15Eject.wav')])],
   {'Shot':'weapon/crossbow/shot','Draw / cock':'weapon/crossbow/cock'}),
 ('Heavy','DShK','12.7x108', [('Shot',[A('[1]Shot_50.wav'),A('zzz[6]Minigun_308_NOTRAIL.wav')]),('Compensator shot',[A('COMP[1]Shot_50.wav')]),('Belt box off',[B('StormyM240ClipEject.wav')]),('Belt box on',[B('StormyM240ClipLoad.wav')]),('Charging handle',[B('M240Rack.wav')])],
   {'Shot':'world/strike/chaingun'}),
]
def withsingle(opts):
    out=[]
    for o in opts:
        if o[0]=='a':
            cf=os.path.join(OUT,'clean',re.sub(r'[\[\]]','',o[3])+'_single.wav')
            if os.path.exists(cf): out.append(('c',cf,'Arsenal, one-shot fix',o[3]+' (single)'))
        out.append(o)
    return out
G=[(c,n,cal,[(sn,withsingle(op)) for sn,op in slots],ours) for c,n,cal,slots,ours in G]
EXTRA=[('Suppressor screwed on',[B('SD_On.wav')]),('Suppressor taken off',[B('SD_Off.wav')]),('Light on',[A('Torch_ON.wav')]),('Light off',[A('Torch_OFF.wav')]),('Laser / light click',[A('weaponLight.ogg')]),('Mag check, part full',[B('PartialClip.ogg')]),('Mag check, empty',[B('ZeroClip.ogg')]),('Solvent trap adapter',[A('SolventTrap_Adapter.ogg')]),('Empty-clip ping',[A('M1PingOnly.wav')])]
def opt(t):
    kind,src,label,name=t
    if not os.path.exists(src): print('MISSING',src); return None
    p=conv(src, kind+'_'+slug(name)); 
    return p and {'src':p,'from':label,'name':name}
data=[]
for cat,name,cal,slots,ours in G:
    gs={'cat':cat,'name':name,'cal':cal,'slots':[]}
    for sname,opts in slots:
        os_=[o for o in (opt(x) for x in opts) if o]
        if sname in ours: 
            o=opt(O(ours[sname]))
            if o: os_.append(o)
        gs['slots'].append({'slot':sname,'opts':os_})
    data.append(gs)
extra=[{'slot':s,'opts':[o for o in (opt(x) for x in opts) if o]} for s,opts in EXTRA]
json.dump({'guns':data,'extra':extra},open(os.path.join(OUT,'data.json'),'w'),indent=1)
tot=sum(os.path.getsize(os.path.join(SND,f)) for f in os.listdir(SND))
print(len(os.listdir(SND)),'files',tot//1024,'KB')
