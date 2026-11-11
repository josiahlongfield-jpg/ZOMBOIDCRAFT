import sys,json
sys.argv=['x']; sys.path.insert(0,'.')
import hudrender as h
from PIL import Image,ImageDraw,ImageFont
W,H=h.W,h.H
Z={}
for k,v in json.load(open('out/weapons_zoom.json')).items():
    if v.get('hud') and 'monitor' not in k and 'turret' not in k:
        n=v['hud'].split('\\')[-1]
        if n not in Z or v['zoom_levels']>Z[n]['zoom_levels']: Z[n]=dict(v,tag=k)
NAMES={'assault_rifle':'MA37 Assault Rifle','magnum':'M6G Magnum','dmr':'M392 DMR','shotgun':'M45 Shotgun','sniper_rifle':'SRS99 Sniper','rocket':'M41 Rocket Launcher','spartan_laser':'Spartan Laser','grenade_launcher':'M319 Grenade Launcher',
 'needle_rifle':'Needle Rifle','needler':'Needler','plasma_pistol':'Plasma Pistol','plasma_rifle':'Plasma Rifle','plasma_carbine':'Plasma Repeater','spike_rifle':'Spiker','focus_rifle':'Focus Rifle','plasma_launcher':'Plasma Launcher',
 'fuel_rod_cannon':'Fuel Rod Gun','elite_shot':'Concussion Rifle','sword':'Energy Sword','hammer':'Gravity Hammer','golf_club':'Golf Club'}
SKIP=('ghost','headshot','_small','flash','lock','target')
def is_scope(w): n=w['name'].lower(); return 'scope' in n or 'zoom' in n or 'mask' in n
def hip(weapon,spread=None):
    if weapon in h.SPREAD: return h.crosshair_layer(weapon,spread=spread)
    X=Image.new('RGBA',(W,H),(0,0,0,0))
    for w in h.c.parse('ui\\chud\\'+weapon):
        if w['special']==2 and not is_scope(w) and w['name'] not in ('binoculars','orbital_strike_nav_point'):
            only=[b['name'] for b in w['bitmaps'] if not any(s in b['name'].lower() for s in SKIP) and b['place'] and b['place'][0]['anchor'] not in (22,)]
            only=[o for o in only if o not in h.WSKIP.get(weapon,())]
            h.draw_widget(X,w,dict(only=tuple(only) or ('__none__',),frame=h.REST.get(weapon,50.0),tick_thick=h.TICK_THICK.get(weapon,1),alpha=h.WALPHA.get(weapon,{}),centre=weapon in h.CENTRE,tick_alpha=h.TICK_ALPHA.get(weapon,0.8),psize=h.PSIZE.get(weapon,{}),rcol=h.RCOL))
    return h.wscale(h.roundfix(X,weapon),weapon)
def scope(weapon):
    X=Image.new('RGBA',(W,H),(0,0,0,0)); found=False
    for w in h.c.parse('ui\\chud\\'+weapon):
        if w['special']==2 and is_scope(w):
            h.draw_widget(X,w,dict(fill={},scope=True)); found=True
    return X if found else None
if __name__=='__main__':
    order=list(NAMES)
    font=ImageFont.truetype(h.FONT,18); small=ImageFont.truetype(h.FONT,14)
    cw,ch=300,300
    sheet=Image.new('RGBA',(cw*7,(ch+40)*3),(22,26,32,255)); d=ImageDraw.Draw(sheet)
    scopes=[]
    for i,wn in enumerate(order):
        X=hip(wn); zz_=h.Z; crop=X.crop((W//2-int(75*zz_),H//2-int(75*zz_),W//2+int(75*zz_),H//2+int(75*zz_))).resize((cw,cw-0),Image.LANCZOS)
        x0,y0=(i%7)*cw,(i//7)*(ch+40)
        sheet.alpha_composite(crop,(x0,y0+40))
        z=Z.get(wn,{}); mag=z.get('magnification'); lv=z.get('zoom_levels',0)
        ztxt=('zoom %s'%('/'.join('%gx'%round(m,1) for m in mag[:max(1,lv)])) if lv else 'no zoom') if mag else 'no zoom'
        d.text((x0+8,y0+6),NAMES[wn],font=font,fill=(220,230,240,255)); d.text((x0+8,y0+26),ztxt,font=small,fill=(120,190,255,255))
        S=scope(wn)
        if S is not None: scopes.append((wn,S,ztxt))
    sheet.save('out/reticle_sheet_2x.png')
    # scopes sheet
    sw,shh=640,360
    n=len(scopes); cols=2
    ss=Image.new('RGBA',(sw*cols,(shh+30)*((n+cols-1)//cols)),(22,26,32,255)); d=ImageDraw.Draw(ss)
    for i,(wn,S,zt) in enumerate(scopes):
        x0,y0=(i%cols)*sw,(i//cols)*(shh+30)
        bg=Image.new('RGBA',(W,H),(70,80,90,255)); bg.alpha_composite(S)
        ss.alpha_composite(bg.resize((sw,shh),Image.LANCZOS),(x0,y0+30)); d.text((x0+8,y0+6),NAMES[wn]+'  '+zt,font=font,fill=(220,230,240,255))
    ss.save('out/scope_sheet.png'); print([s[0] for s in scopes])
