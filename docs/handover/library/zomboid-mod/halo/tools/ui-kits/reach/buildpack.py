import os,sys,json,shutil
os.environ['HUDZ']='2'
sys.argv=['x']; sys.path.insert(0,'.')
import hudrender as h, sheet as S
from PIL import Image
P='hudpack2'; shutil.rmtree(P,ignore_errors=True)
for d in ('reticles','scopes','glyphs/weapons','glyphs/abilities','layout','tools','previews'): os.makedirs(P+'/'+d,exist_ok=True)
W,H=h.W,h.H; Z=h.Z
OWN_SCOPE_RETICLE=('dmr','sniper_rifle','focus_rifle','plasma_launcher','spartan_laser')
zoom={}
for wn,nice in S.NAMES.items():
    X=S.hip(wn); X.crop((W//2-int(75*Z),H//2-int(75*Z),W//2+int(75*Z),H//2+int(75*Z))).save(f'{P}/reticles/{wn}.png')
    sc=S.scope(wn)
    zz=S.Z.get(wn,{})
    lv=zz.get('zoom_levels',0); mags=[round(m,2) for m in zz.get('magnification',[0,0])[:max(1,lv)]] if lv else []
    zoom[wn]=dict(name=nice,zoom_levels=lv,magnification=mags,zoom_time_s=zz.get('zoom_time'),fp_scope_size=zz.get('fp_scope_size'),weapon_tag=zz.get('tag'),
                  reticle=f'reticles/{wn}.png',scope_overlay=(f'scopes/{wn}_scope.png' if sc is not None else None),
                  hip_reticle_stays_when_zoomed=(sc is None or wn not in OWN_SCOPE_RETICLE) and lv>0)
    if sc is not None:
        if wn not in OWN_SCOPE_RETICLE: sc.alpha_composite(X)
        sc.save(f'{P}/scopes/{wn}_scope.png')
json.dump(zoom,open(f'{P}/weapon_zoom.json','w'),indent=1)
for wn,code in h.GLYPH.items():
    g=h.glyph(code,2); g=g.crop(g.getbbox()); h.tint(g,h.G[2][1:]).save(f'{P}/glyphs/weapons/{wn}.png')
for code,nm in {0xe036:'sprint',0xe037:'hologram',0xe038:'evade',0xe042:'active_camo',0xe043:'jetpack',0xe046:'armor_lock',0xe050:'drop_shield'}.items():
    g=h.glyph(code,3); g=g.crop(g.getbbox()); h.tint(g,h.G[2][1:]).save(f'{P}/glyphs/abilities/{nm}.png')
clear=Image.new('RGBA',(W,H),(0,0,0,0))
h.render(clear,f'{P}/hud_overlay_2x.png',res='320')
h.render(clear,f'{P}/hud_overlay_2x_flat.png',res='320',flat=True)
for f in os.listdir('out/layout'): shutil.copy('out/layout/'+f,f'{P}/layout/'+f)
shutil.copy('out/chgd.json',f'{P}/layout/chgd_colours_and_screen.json')
for f in ('hudrender.py','sheet.py','chad.py','chdt.py','chgd.py','weapons.py','chsum.py','buildpack.py'): 
    if os.path.exists(f): shutil.copy(f,f'{P}/tools/'+f)
shutil.copy('../hfont/fontpkg.py',f'{P}/tools/fontpkg.py')
print('done',sum(len(fs) for _,_,fs in os.walk(P)))
# --- v2.4 spread: per-weapon spread animations (all 6 weapons that have them in the files) ---
os.makedirs(P+'/reticles/spread',exist_ok=True)
sp={}
for wn in S.NAMES:
    pieces={}
    for w in h.c.parse('ui\\chud\\'+wn):
        if w['special']!=2 or S.is_scope(w): continue
        for b in w['bitmaps']:
            for an in b['anim']:
                if an['fn']!=2: continue
                ca=h.chad_cached(an['anim']) or {}
                pieces[b['name']]=dict(anim=an['anim'].split('\\')[-1],frames=ca.get('frames'),**{k:v for k,v in ca.items() if k!='frames' and v})
    if not pieces: continue
    cal=h.SPREAD.get(wn)
    sp[wn]=dict(pieces=pieces,
        mapping=(dict(frame='f0 + spread*(f1-f0)',f0=cal['f0'],f1=cal['f1'],pos_units_x=cal['posk'],pos_extends_past_last_key=True,
                      note='calibrated on Josiah\'s 1080p screenshots + firing/reload video') if cal else
                 dict(note='shapes from the files; on-screen distance and timing still to be calibrated from a clip of this weapon')),
        timing=h.SPREAD_TIME.get(wn,{}))
    if cal:
        for k in range(11):
            X=h.crosshair_layer(wn,spread=k/10)
            X.crop((W//2-int(110*Z),H//2-int(110*Z),W//2+int(110*Z),H//2+int(110*Z))).save(f'{P}/reticles/spread/{wn}_{k*10:03d}.png')
json.dump(sp,open(f'{P}/layout/reticle_spread.json','w'),indent=1)
shutil.copy('out/anim_inventory.txt',f'{P}/layout/chud_animation_inventory.txt')
print('spread weapons',list(sp))
