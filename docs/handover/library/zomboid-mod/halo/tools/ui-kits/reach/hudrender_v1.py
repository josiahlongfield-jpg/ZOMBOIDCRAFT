import sys,os,json,struct,math
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from chdt import C
from PIL import Image,ImageDraw,ImageFont,ImageFilter
import numpy as np
MAP='/mnt/user-data/uploads/haloreach/maps/20_sword_slayer.map'
c=C(MAP); G=json.load(open('out/chgd.json'))['colors']
BM='out/ui/chud/bitmaps/'
Z=float(os.environ.get('HUDZ','1'))
W,H=int(1280*Z),int(720*Z)
KX,KY=0.5*0.86*Z,0.5*0.87*Z  # bitmaps authored at 2x, then HUD scale
FONT='/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/fonts/SairaSemiCondensed-Medium.ttfx.ttf'
ANCH0={1:(0,0),2:(640,0),3:(1280,0),4:(640,360),5:(0,720),6:(640,720),7:(1280,720),8:(88,656),17:(640,360),24:(640,360),26:(640,360)}
HS=(0.86,0.87)
def sa(x,y): return (640+(x-640)*HS[0],360+(y-360)*HS[1])
ANCH={k:sa(*v) for k,v in ANCH0.items()}
ANCH[8]=sa(88,656); ANCH[17]=ANCH[24]=ANCH[26]=(640,360)
ANCH={k:(v[0]*Z,v[1]*Z) for k,v in ANCH.items()}
def seqs(name):
    k=c.byname.get(('bitm',name)); o=c.tago(k); out=[]
    for s in c.items(o,0x70,0x40):
        sp=c.items(s,0x34,0x20)
        out.append([c.f(p+8,'4f') for p in sp])
    return out
_img={}
def img(name):
    if name not in _img:
        p=BM+name.replace('ui\\chud\\bitmaps\\','').replace('\\','/')+'.png'
        _img[name]=Image.open(p)
    return _img[name]
def colour(rd,i=0,fb=2):
    v=rd['outc'][i] if rd else 0
    if v>=18 and v-18<len(G): return G[v-18][1:]
    if v<4 and rd and any(rd['local'][v][1:]): return rd['local'][v][1:]
    if rd:
        for u in rd['outc']:
            if u>=18: return G[u-18][1:]
    return G[fb][1:]
def tint(im,rgb,alpha=1.0,mode='simple',fill=1.0,dark=None):
    a=np.array(im.convert('RGBA')).astype(float)/255
    if im.mode=='L': A=a[...,0]; V=np.ones_like(A)
    else: A=a[...,3]; V=a[...,:3].max(-1)
    out=np.zeros(a.shape)
    col=np.array(rgb)
    if mode=='meter2':
        R=a[...,0]; mx=R[A>0.05].max() if (A>0.05).any() else 1
        m=(R<=fill*mx+1e-3).astype(float)
        out[...,:3]=col; out[...,3]=A*m*alpha
        dk=np.array(dark if dark else rgb); out[...,:3]=col*m[...,None]+dk*(1-m[...,None]); out[...,3]=A*alpha*(m+(1-m)*0.55)
        return Image.fromarray((np.clip(out,0,1)*255).astype('uint8'),'RGBA')
    if mode=='meter':
        m=(a[...,0]<=fill+1e-3).astype(float)
        dk=np.array(dark if dark else rgb)
        g=a[...,1][...,None]
        out[...,:3]=dk*(1-g)+col*g
        out[...,3]=A*m*alpha
        # empty part: faint dark
        out[...,3]+=A*(1-m)*0.18*alpha
    else:
        out[...,:3]=col; out[...,3]=A*V*alpha if im.mode!='L' else A*alpha
    return Image.fromarray((np.clip(out,0,1)*255).astype('uint8'),'RGBA')
def piece(b,scale_parent):
    name=b.get('bitmap')
    if not name: return None
    im=img(name)
    s=b['sprite']+b.get('_spradd',0); sq=seqs(name)
    if sq and s<len(sq) and sq[s]:
        l,r,t,bo=sq[s][0]; w,h=im.size
        im=im.crop((int(l*w),int(t*h),int(round(r*w)),int(round(bo*h))))
    if b['bflags']&0x800000:
        tc=b['texcoord']; per=int(round((tc[3]-tc[1])/8)); rows=max(1,int(round(tc[2]/12)))
        a=np.array(im.convert('RGBA')).astype(int)
        cols=(a[...,3]>100).any(0); starts=[x for x in range(1,len(cols)) if cols[x] and not cols[x-1]]+([0] if cols[0] else [])
        starts.sort(); sp=starts[1]-starts[0] if len(starts)>1 else 8
        rowmask=(a[...,3]>100).any(1); rs=[y for y in range(1,len(rowmask)) if rowmask[y] and not rowmask[y-1]]+([0] if rowmask[0] else [])
        rs.sort(); rh=(rs[1]-rs[0]) if len(rs)>1 else a.shape[0]
        im=im.crop((0,0,min(a.shape[1],starts[0]+per*sp),min(a.shape[0],(rs[0] if rs else 0)+rows*rh)))
        im.info['ticks']=(per,rows,sp,starts[0],rh,rs[0] if rs else 0)
    return im
def draw_widget(canvas,w,opts={}):
    p=w['place'][0] if w['place'] else dict(anchor=0,offset=(0,0),mirror=(0,0),scale=(1,1))
    ax,ay=ANCH.get(p['anchor'],(640,360))
    P=(ax+p['offset'][0]*Z,ay+p['offset'][1]*Z); ps=p['scale']; mir=p['mirror']
    for b in w['bitmaps']:
        if b['name'] in opts.get('skip',()): continue
        if opts.get('only') and b['name'] not in opts['only']: continue
        b=dict(b,_spradd=opts.get('sprite',{}).get(b['name'],0))
        im=piece(b,ps)
        if im is None: continue
        bp=b['place'][0]; sc=bp['scale']
        sw,sh=max(1,int(im.size[0]*sc[0]*ps[0]*KX)),max(1,int(im.size[1]*sc[1]*ps[1]*KY))
        rd=b['render'][0] if b['render'] else None
        sh_=rd['shader'] if rd else 0
        fill=opts.get('fill',{}).get(b['name'],1.0)
        if sh_==4:
            t=tint(im,colour(rd,0),mode='meter',fill=fill,dark=colour(rd,1))
        elif sh_==2 and 'ticks' in im.info:
            per,rows,sp,x0_,rh,y0_=im.info['ticks']; a=np.array(im.convert('RGBA')).astype(float)/255
            n=int(round(fill*per*rows)); out=np.zeros(a.shape)
            def cc(i):
                v=rd['outc'][i]; return np.array(G[v-18][1:]) if v>=18 else None
            br=cc(1) if cc(1) is not None else np.array(G[2][1:]); dk=cc(2) if cc(2) is not None else np.array(G[0][1:])
            H_,W_=a.shape[:2]
            for y in range(H_):
                r_=min(rows-1,max(0,(y-y0_)//rh))
                for x in range(W_):
                    k=min(per-1,max(0,(x-x0_)//sp)); idx=r_*per+k
                    on=idx<n
                    out[y,x,:3]=br if on else dk*1.6; out[y,x,3]=a[y,x,3]*(1 if on else 0.6)
            t=Image.fromarray((np.clip(out,0,1)*255).astype('uint8'),'RGBA')
        elif sh_==2:
            def cc(i):
                v=rd['outc'][i]; return G[v-18][1:] if v>=18 else None
            br=cc(1) or cc(0) or G[2][1:]; dk=cc(2) or G[0][1:]
            t=tint(im,br,mode='meter2',fill=fill,dark=dk)
        else:
            t=tint(im,opts.get('col',{}).get(b['name']) or colour(rd,0),alpha=opts.get('alpha',{}).get(b['name'],1.0))
        t=t.resize((sw,sh),Image.LANCZOS)
        if bp['anchor']==0: bx,by=P[0]+bp['offset'][0]*Z,P[1]+bp['offset'][1]*Z
        else:
            ax2,ay2=ANCH.get(bp['anchor'],(0,0)); bx,by=ax2+bp['offset'][0]*Z,ay2+bp['offset'][1]*Z
        ang=0.0
        for an in b['anim']:
            if 'crosshair_animations' in an['anim'] or 'scope_animations' in an['anim']:
                ca=chad_cached(an['anim'])
                if ca and ca.get('pos'): bx+=ca['pos'][-1][1]*Z; by+=ca['pos'][-1][2]*Z
                if ca and ca.get('rot'): ang=ca['rot'][-1][1]
                break
        if 'fit' in opts and b['name'] in opts['fit']:
            f=opts['fit'][b['name']]*Z/max(sw,sh); sw,sh=max(1,int(sw*f)),max(1,int(sh*f)); t=t.resize((sw,sh),Image.LANCZOS)
        r=bp['mirror']; rx,ry=r[0]*sw,r[1]*sh
        R=int((max(rx,sw-rx)**2+max(ry,sh-ry)**2)**0.5)+2
        F=Image.new('RGBA',(2*R,2*R),(0,0,0,0)); F.alpha_composite(t,(int(round(R-rx)),int(round(R-ry))))
        if ang: F=F.rotate(-math.degrees(ang),resample=Image.BICUBIC,center=(R,R))
        pos=(int(round(bx-R)),int(round(by-R)))
        mh=bool(b['bflags']&1); mv=bool(b['bflags']&2)
        canvas.alpha_composite(F,pos) if 0 else _comp(canvas,F,pos)
        if mh: _comp(canvas,F.transpose(Image.FLIP_LEFT_RIGHT),pos)
        if mv: _comp(canvas,F.transpose(Image.FLIP_TOP_BOTTOM),pos)
        if mh and mv: _comp(canvas,F.transpose(Image.ROTATE_180),pos)
    d=ImageDraw.Draw(canvas)
    for tx in w['texts']:
        val=opts.get('text',{}).get(tx['name'])
        if val is None: continue
        bp=tx['place'][0]; sc=bp['scale']
        bx,by=(P[0]+bp['offset'][0]*Z,P[1]+bp['offset'][1]*Z) if bp['anchor']==0 else (ANCH[bp['anchor']][0]+bp['offset'][0]*Z,ANCH[bp['anchor']][1]+bp['offset'][1]*Z)
        rd=tx['render'][0] if tx['render'] else None
        if isinstance(val,Image.Image):
            im=val; im=im.resize((max(1,int(im.size[0]*sc[0]*ps[0])),max(1,int(im.size[1]*sc[1]*ps[1]))),Image.LANCZOS); sw,sh=im.size
            x0,y0=bx-bp['mirror'][0]*sw,by-bp['mirror'][1]*sh
            canvas.alpha_composite(tint(im,colour(rd,0)),(int(x0),int(y0))); continue
        size={8:18,6:15,5:13,1:18}.get(tx['font'],16)*sc[1]*ps[1]*1.05*Z
        f=ImageFont.truetype(FONT,int(round(size)))
        col=tuple(int(v*255) for v in (opts.get('tcol',{}).get(tx['name']) or colour(rd,0)))
        l,t_,r_,b_=d.textbbox((0,0),val,font=f); tw,th=r_-l,b_-t_
        d.text((bx-bp['mirror'][0]*tw-l,by-bp['mirror'][1]*th-t_),val,font=f,fill=col+(255,))
    return P
def _comp(cv,im,pos):
    x,y=pos; W_,H_=cv.size; w,h=im.size
    l,t_=max(0,-x),max(0,-y); r_,b_=min(w,W_-x),min(h,H_-y)
    if r_<=l or b_<=t_: return
    cv.alpha_composite(im.crop((l,t_,r_,b_)),(x+l,y+t_))
_CH={}
def chad_cached(n):
    if n not in _CH:
        from chad import chad
        _CH[n]=chad(c,n)
    return _CH[n]
def widgets(name): return {w['name']:w for w in c.parse(name)}
sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/hfont')
_GL={}
def glyph(code,font=2):
    import fontpkg
    key=(code,font)
    if key not in _GL:
        best=None
        for f,k in (('font_package_icon_x2.bin',2),('font_package_icon.bin',1)):
            fonts,cs=fontpkg.chars('/mnt/user-data/uploads/haloreach/maps/fonts/'+f)
            for ch in cs:
                if ch['font']==font and ch['ch']==code:
                    im,_=fontpkg.image(ch); best=(im,k); break
            if best: break
        _GL[key]=best
    im,k=_GL[key]
    return im.resize((max(1,int(im.size[0]*Z/k)),max(1,int(im.size[1]*Z/k))),Image.LANCZOS)
GLYPH={'assault_rifle':0xe112,'dmr':0xe114,'grenade_launcher':0xe116,'needle_rifle':0xe117,'sword':0xe119,'fuel_rod_cannon':0xe11f,'hammer':0xe120,'elite_shot':0xe121,'magnum':0xe123,'golf_club':0xe124,'needler':0xe125,'focus_rifle':0xe126,'plasma_launcher':0xe128,'plasma_pistol':0xe129,'plasma_rifle':0xe12a,'spartan_laser':0xe12c,'rocket':0xe12d,'plasma_carbine':0xe12e,'shotgun':0xe12f,'sniper_rifle':0xe131,'spike_rifle':0xe132}
def schem(n,w):
    im=Image.open('out/schem_%s.png'%n); h=int(im.size[1]*w/im.size[0]); return im.resize((w,h),Image.LANCZOS)
def blips(cv,pts):
    sb=img('ui\\chud\\bitmaps\\sensor_blips')
    cx,cy=ANCH[8]
    for (dx,dy,col,sz) in pts:
        dx,dy,sz=dx*Z,dy*Z,int(sz*Z)
        b=Image.new('RGBA',(sz,sz),(0,0,0,0)); dd=ImageDraw.Draw(b)
        dd.ellipse((0,0,sz-1,sz-1),fill=col+(235,))
        g=b.filter(ImageFilter.GaussianBlur(2)); cv.alpha_composite(g,(int(cx+dx-sz/2),int(cy+dy-sz/2))); cv.alpha_composite(b,(int(cx+dx-sz/2),int(cy+dy-sz/2)))
def render(bg,out,shield=0.72,health=1.0,blip=True):
    cv=bg.copy()
    sp=c.parse('ui\\chud\\spartan')
    for w in sp:
        if w['name']=='?80632':
            draw_widget(cv,w,dict(skip=('flahsy','meter_sup2','meter_sup3','meter_sup4','meter_border_flash'),fill={'meter':shield}))
        if w['name']=='health_meter': draw_widget(cv,w,dict(skip=('flahsy','shield_boot_blob','compass_additive','compass'),fill={'health_meter':health}))
        if w['name']=='?80630': draw_widget(cv,w,dict(skip=('voice_broadcast1','voice_broadcast2','ping_boot','?8003e'),text={'25m':'25m'},alpha={'motion_tracker_background':0.55}))
        if w['name']=='?80631': draw_widget(cv,w,dict(text={'grenade_count_hilite':'2','grenade_count_backpack':'2'},sprite={'unhilight':1}))
    if blip: blips(cv,[(-34,-40,(240,40,40),9),(-20,-52,(240,40,40),9),(30,-30,(240,40,40),11),(10,-20,(245,215,40),9)])
    for w in c.parse('ui\\chud\\assault_rifle'):
        draw_widget(cv,w,dict(skip=('red_flash',),fill={'meter':24/32},text={'shematic':glyph(0xe112),'reserve_count':'96'}))
    for w in c.parse('ui\\chud\\magnum'):
        if w['name']=='backpack_ammo_area': draw_widget(cv,w,dict(text={'shematic_backpack':glyph(0xe123),'ammo_count':'24'}))
    for w in c.parse('ui\\chud\\personal_equipment\\e_speed_burst')[:1]:
        draw_widget(cv,w,dict(fill={'energy_meter':1.0},fit={'?80635':30}))
    cv.save(out)
if __name__=='__main__':
    from PIL import ImageFilter
    bg=Image.open(sys.argv[1]).convert('RGBA').resize((W,H)) if os.path.exists(sys.argv[1]) else Image.new('RGBA',(W,H),(0,0,0,0) if sys.argv[1]=='clear' else (40,46,52,255))
    render(bg,sys.argv[2])
