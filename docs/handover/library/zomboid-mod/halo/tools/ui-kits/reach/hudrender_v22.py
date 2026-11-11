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
    P=(ax+p['offset'][0]*HS[0]*Z,ay+p['offset'][1]*HS[1]*Z); ps=p['scale']; mir=p['mirror']
    OX,OY=HS[0]*ps[0]*Z,HS[1]*ps[1]*Z
    for b in w['bitmaps']:
        if b['name'] in opts.get('skip',()): continue
        if opts.get('only') and b['name'] not in opts['only']: continue
        b=dict(b,_spradd=opts.get('sprite',{}).get(b['name'],0))
        im=piece(b,ps)
        if im is None: continue
        bp=b['place'][0]; sc=bp['scale']
        XS=CROSS if (bp['anchor'] in (17,24) or p['anchor'] in (17,24)) and not opts.get('scope') else 1.0
        NFX=sc[0]*ps[0]<0; NFY=sc[1]*ps[1]<0  # negative scale = flipped about the registration point
        sw,sh=max(1,int(abs(im.size[0]*sc[0]*ps[0])*KX*XS)),max(1,int(abs(im.size[1]*sc[1]*ps[1])*KY*XS))
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
        elif b['bflags']&8:  # scope mask: black, alpha from the mask
            a_=np.array(im.convert('RGBA')).astype(float); A_=a_[...,3] if a_[...,3].min()<255 else a_[...,0]
            o_=np.zeros(a_.shape); o_[...,3]=A_/255*opts.get('mask_alpha',1.0)/max(1e-3,A_.max()/255)
            t=Image.fromarray((np.clip(o_,0,1)*255).astype('uint8'),'RGBA')
        else:
            t=tint(im,opts.get('col',{}).get(b['name']) or colour(rd,0),alpha=opts.get('alpha',{}).get(b['name'],1.0))
        t=t.resize((sw,sh),Image.LANCZOS)
        if b['bflags']&8:  # edges carry on to the screen edge
            a_=np.array(t); a_=np.pad(a_,((0,H),(0,W),(0,0)),mode='edge'); t=Image.fromarray(a_,'RGBA'); sw,sh=t.size
        if bp['anchor']==0: bx,by=P[0]+bp['offset'][0]*OX*XS,P[1]+bp['offset'][1]*OY*XS
        else:
            ax2,ay2=ANCH.get(bp['anchor'],(0,0)); bx,by=ax2+bp['offset'][0]*OX*XS,ay2+bp['offset'][1]*OY*XS
        ang=0.0
        for an in b['anim']:
            if 'crosshair_animations' in an['anim'] or 'scope_animations' in an['anim']:
                ca=chad_cached(an['anim'])
                fr=min(ca.get('frames') or 0, opts.get('frame',50.0)) if ca else 0  # crosshair anims: boot-in to a held state by frame 50; input-driven ones (0..1000) read as resting
                if ca and ca.get('pos'): px_,py_=kinterp(ca['pos'],fr)[:2]; bx+=px_*0.5*OX*XS; by+=py_*0.5*OY*XS  # chad offsets are in 2x-authored units
                if ca and ca.get('rot'): ang=kinterp(ca['rot'],fr)[0]
                if ca and ca.get('size'):  # size keys (e.g. DMR error ring grows 0.2 -> 2.0 as it boots in)
                    k_=kinterp(ca['size'],fr); sw,sh=max(1,int(sw*k_[0])),max(1,int(sh*k_[1])); t=t.resize((sw,sh),Image.LANCZOS)
                break
        if 'fit' in opts and b['name'] in opts['fit']:
            f=opts['fit'][b['name']]*Z/max(sw,sh); sw,sh=max(1,int(sw*f)),max(1,int(sh*f)); t=t.resize((sw,sh),Image.LANCZOS)
        r=list(bp['mirror'])
        if b['name']=='ar_reticule': r=[1.05,1.05]  # measured on MCC: equal gaps on both axes
        TICK=b.get('bitmap','').endswith('hud_reticles') and b['sprite']==34
        if TICK:  # crosshair ticks: keep the tick on its axis (MCC shows them centred), slightly dimmer than the ring
            r[1]=0.406
            t=Image.fromarray((np.array(t).astype(float)*np.array([1,1,1,0.8])).astype('uint8'),'RGBA')
            if opts.get('tick_thick',1)!=1: th_=opts['tick_thick']; t=t.resize((t.width,max(2,math.ceil(t.height*th_+0.4))),Image.LANCZOS); sh=t.height
        if NFX: t=t.transpose(Image.FLIP_LEFT_RIGHT); r[0]=1-r[0]
        if NFY: t=t.transpose(Image.FLIP_TOP_BOTTOM); r[1]=1-r[1]
        rx,ry=r[0]*sw,r[1]*sh
        R=int((max(rx,sw-rx)**2+max(ry,sh-ry)**2)**0.5)+2
        F=Image.new('RGBA',(2*R,2*R),(0,0,0,0)); F.alpha_composite(t,(int(round(R-rx)),int(round(R-ry))))
        if ang: F=F.rotate(-math.degrees(ang),resample=Image.BICUBIC,center=(R,R))
        if TICK:  # snap the tick onto its axis
            if abs(math.sin(ang))<0.5: by=ANCH[17][1]
            else: bx=ANCH[17][0]
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
        bx,by=(P[0]+bp['offset'][0]*OX,P[1]+bp['offset'][1]*OY) if bp['anchor']==0 else (ANCH[bp['anchor']][0]+bp['offset'][0]*OX,ANCH[bp['anchor']][1]+bp['offset'][1]*OY)
        rd=tx['render'][0] if tx['render'] else None
        if isinstance(val,Image.Image):
            im=val; im=im.resize((max(1,int(im.size[0]*sc[0]*ps[0]*ps[0]*GSX)),max(1,int(im.size[1]*sc[1]*ps[1]*ps[1]*GSY))),Image.LANCZOS); sw,sh=im.size  # icon glyphs: parent scale applies twice, MCC keeps the 360-era wide pixel (measured)
            x0,y0=bx-bp['mirror'][0]*sw,by-bp['mirror'][1]*sh
            canvas.alpha_composite(tint(im,colour(rd,0)),(int(x0),int(y0))); continue
        size={8:21,6:17,5:15,1:21}.get(tx['font'],18)*sc[1]*ps[1]*Z
        f=ImageFont.truetype(FONT,int(round(size)))
        col=tuple(int(v*255) for v in (opts.get('tcol',{}).get(tx['name']) or colour(rd,0)))
        l,t_,r_,b_=d.textbbox((0,0),val,font=f); tw,th=r_-l,b_-t_
        d.text((bx-bp['mirror'][0]*tw-l,by-bp['mirror'][1]*th-t_),val,font=f,fill=col+(255,))
    return P
def kinterp(keys,fr):
    keys=sorted(keys)
    if fr<=keys[0][0]: return keys[0][1:]
    for a,b in zip(keys,keys[1:]):
        if a[0]<=fr<=b[0]:
            t=(fr-a[0])/max(1e-6,b[0]-a[0]); return tuple(x+(y-x)*t for x,y in zip(a[1:],b[1:]))
    return keys[-1][1:]
CROSS=0.565
GSX,GSY=1.19,1.07  # MCC draws reticles ~2/3 of the HUD-scale size (measured on a 1080p MCC screenshot)
# 3x3 bow grid from chgd HUD attributes (+0x4..0x48), columns x=-1,0,1, rows y=-1,0,1 (NDC, y down)
BOW=[[(-1,-1),(-0.98,0),(-1,1)],[(0,-0.92),(0,0),(0,0.92)],[(1,-1),(0.98,0),(1,1)]]
def _L(t):
    return ((t*(t-1)/2),(1-t*t),(t*(t+1)/2))
def bow_fwd(u,v):
    lu,lv=_L(u),_L(v); x=y=0.0
    for i in range(3):
        for j in range(3):
            w_=lu[i]*lv[j]; x+=w_*BOW[i][j][0]; y+=w_*BOW[i][j][1]
    return x,y
def bow_warp(im,n=(40,24)):
    W_,H_=im.size; mesh=[]
    def inv(X,Y):
        u,v=X,Y
        for _ in range(20):
            fx,fy=bow_fwd(u,v); u+=X-fx; v+=Y-fy
        return u,v
    def src(px,py):
        u,v=inv(px/W_*2-1,py/H_*2-1); return ((u+1)/2*W_,(v+1)/2*H_)
    for gy in range(n[1]):
        for gx in range(n[0]):
            x0,x1=gx*W_/n[0],(gx+1)*W_/n[0]; y0,y1=gy*H_/n[1],(gy+1)*H_/n[1]
            q=src(x0,y0)+src(x0,y1)+src(x1,y1)+src(x1,y0)
            mesh.append(((int(x0),int(y0),int(x1),int(y1)),q))
    return im.transform(im.size,Image.MESH,mesh,Image.BICUBIC)
def persp_coeffs(dst,src):
    A=[];B=[]
    for (x,y),(X,Y) in zip(dst,src):
        A.append([x,y,1,0,0,0,-X*x,-X*y]); B.append(X)
        A.append([0,0,0,x,y,1,-Y*x,-Y*y]); B.append(Y)
    return list(np.linalg.solve(np.array(A,float),np.array(B,float)))
def tilt(layer,cx,cy,r,k=TILT if 'TILT' in globals() else None):
    # Reach's motion tracker is a disc tilted back in perspective (measured on MCC screenshot)
    t=k or dict(s=0.95,top=0.86,bot=1.04,up=0.62,down=0.72)
    R_=r*t['s']
    src=[(cx-r,cy-r),(cx+r,cy-r),(cx+r,cy+r),(cx-r,cy+r)]
    dst=[(cx-R_*t['top'],cy-R_*t['up']),(cx+R_*t['top'],cy-R_*t['up']),(cx+R_*t['bot'],cy+R_*t['down']),(cx-R_*t['bot'],cy+R_*t['down'])]
    return layer.transform(layer.size,Image.PERSPECTIVE,persp_coeffs(dst,src),Image.BICUBIC)
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
    return im.resize((max(1,int(im.size[0]*Z*2/k)),max(1,int(im.size[1]*Z*2/k))),Image.LANCZOS)
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
AMMO={'assault_rifle':(32,'96'),'magnum':(8,'24'),'dmr':(15,'45'),'shotgun':(6,'18'),'sniper_rifle':(4,'12'),'rocket':(2,'4'),'spartan_laser':(1,''),'grenade_launcher':(1,'7'),
 'needle_rifle':(21,'63'),'needler':(24,'72'),'plasma_pistol':(1,''),'plasma_rifle':(1,''),'plasma_carbine':(1,''),'spike_rifle':(40,'120'),'focus_rifle':(1,''),'plasma_launcher':(4,'8'),
 'fuel_rod_cannon':(5,'15'),'concussion_rifle':(6,'18'),'elite_shot':(6,'18'),'sword':(1,''),'hammer':(1,''),'golf_club':(1,''),'machinegun_turret':(1,''),'plasma_turret':(1,'')}
CIRC=('assault_rifle','magnum','dmr','shotgun','sniper_rifle','rocket','needle_rifle','plasma_rifle','plasma_carbine','plasma_launcher','fuel_rod_cannon','golf_club','sword','focus_rifle','plasma_pistol','spike_rifle')
def roundfix(X,weapon):
    # Reach authored some reticles for the 360's 1152-wide stretch and uses off-centre quarter registrations;
    # MCC shows them as true circles, so squash/stretch the vertical to match the horizontal.
    if weapon not in CIRC: return X
    a=np.array(X)[...,3]; ys,xs=np.nonzero(a>40)
    if not len(xs): return X
    cx,cy=X.width/2,X.height/2
    ex=max(cx-xs.min(),xs.max()+1-cx); ey=max(cy-ys.min(),ys.max()+1-cy)
    k=ex/ey
    if abs(k-1)<0.01: return X
    nh=int(round(X.height*k)); Y=X.resize((X.width,nh),Image.LANCZOS)
    out=Image.new('RGBA',X.size,(0,0,0,0)); out.alpha_composite(Y,(0,int(round(cy-nh/2)))) if nh<=X.height else out.alpha_composite(Y.crop((0,int((nh-X.height)/2),X.width,int((nh-X.height)/2)+X.height)),(0,0))
    return out
REST={'magnum':19.0,'dmr':4.2}  # matched to Josiah's in-game screenshots: magnum ticks 0.55R..1.05R; DMR error ring at rest (0.43 of the outer ring)
WSCALE={'magnum':1.2,'dmr':1.2}
TICK_THICK={'magnum':1.4}
WSKIP={'dmr':('error_reticule_small','headshot')}
WALPHA={'dmr':{'error_reticule':0.55}}  # magnum reticle measured 1.28x the data size on his in-game screenshot (scaled by the shield-bar width)
def wscale(X,weapon):
    k=WSCALE.get(weapon)
    if not k: return X
    Y=X.resize((int(X.width*k),int(X.height*k)),Image.LANCZOS)
    return Y.crop(((Y.width-X.width)//2,(Y.height-X.height)//2,(Y.width-X.width)//2+X.width,(Y.height-X.height)//2+X.height))
def crosshair_layer(weapon,frame=None,size=None):
    frame=REST.get(weapon,50.0) if frame is None else frame
    X=Image.new('RGBA',size or (W,H),(0,0,0,0))
    for w in c.parse('ui\\chud\\'+weapon):
        if w['special']==2 and not w['name'].startswith('zoom') and 'scope' not in w['name']:
            draw_widget(X,w,dict(frame=frame,skip=('ghost_reticule','headshot')+WSKIP.get(weapon,()),tick_thick=TICK_THICK.get(weapon,1),alpha=WALPHA.get(weapon,{})))
    return wscale(roundfix(X,weapon),weapon)
def compass(L,heading=90.0):
    # Reach compass: compass_alpha strip = 360 deg over 3072 texels (E at 595, 768 per 90 deg), looping; the widget shows a
    # 0.4-of-the-strip window (144 deg) at 1 texel = 0.5 x HUD scale px, centred above the shield (health_meter + (0,-20)), soft ends.
    im=img('ui\\chud\\bitmaps\\compass_alpha').convert('L'); a=np.array(im)
    tw=a.shape[1]; win=int(0.4*tw); c=int(595+(heading-90)/90*768)%tw  # visible window measured on MCC: ~533 px at 720p
    idx=(np.arange(win)-win//2+c)%tw; strip=a[:,idx].astype(float)/255
    x=np.linspace(-1,1,win); fade=np.clip((1-np.abs(x))/0.14,0,1); strip*=fade[None,:]
    rgb=np.array(G[2][1:])*1.15
    o=np.zeros(strip.shape+(4,)); o[...,:3]=np.clip(rgb,0,1); o[...,3]=strip
    t=Image.fromarray((o*255).astype('uint8'),'RGBA').resize((int(win*0.5*HS[0]*Z),int(a.shape[0]*0.5*HS[1]*Z)),Image.LANCZOS)
    cx,cy=ANCH[2][0],ANCH[2][1]-20*HS[1]*Z
    _comp(L,t,(int(cx-t.width/2),int(cy-t.height/2)))
def render(bg,out,shield=0.72,health=1.0,blip=True,weapon='assault_rifle',backpack='magnum',fill=0.75,frame=None,flat=False,res=None,heading=85.0):
    L=Image.new('RGBA',(W,H),(0,0,0,0)); T=Image.new('RGBA',(W,H),(0,0,0,0))
    compass(L,heading)
    sp=c.parse('ui\\chud\\spartan')
    for w in sp:
        if w['name']=='?80632':
            draw_widget(L,w,dict(skip=('flahsy','meter_sup2','meter_sup3','meter_sup4','meter_border_flash'),fill={'meter':shield}))
        if w['name']=='health_meter': draw_widget(L,w,dict(skip=('flahsy','shield_boot_blob','compass_additive','compass'),fill={'health_meter':health}))
        if w['name']=='?80630':
            draw_widget(T,w,dict(skip=('voice_broadcast1','voice_broadcast2','ping_boot','?8003e'),alpha={'motion_tracker_background':0.72}))
            draw_widget(L,w,dict(only=('__none__',),text={'25m':'25m'}))
        if w['name']=='?80631': draw_widget(L,w,dict(text={'grenade_count_hilite':'2','grenade_count_backpack':'2'},sprite={'unhilight':1}))
    if blip: blips(T,[(-34,-40,(240,40,40),9),(-20,-52,(240,40,40),9),(30,-30,(240,40,40),11),(10,-20,(245,215,40),9)])
    cx,cy=ANCH[8]
    L.alpha_composite(T if flat else tilt(T,cx,cy,80*Z))
    n,res0=AMMO.get(weapon,(1,'')); res=res if res is not None else res0
    for w in c.parse('ui\\chud\\'+weapon):
        if w['special']==1 and w['name']!='backpack_ammo_area':
            draw_widget(L,w,dict(skip=('red_flash',),fill={'meter':fill},text={'shematic':glyph(GLYPH.get(weapon,0xe112)),'reserve_count':res}))
    if backpack:
        for w in c.parse('ui\\chud\\'+backpack):
            if w['name']=='backpack_ammo_area': draw_widget(L,w,dict(text={'shematic_backpack':glyph(GLYPH.get(backpack,0xe123)),'ammo_count':AMMO.get(backpack,(1,''))[1]}))
    for w in c.parse('ui\\chud\\personal_equipment\\e_speed_burst')[:1]:
        draw_widget(L,w,dict(fill={'energy_meter':1.0},fit={'?80635':30}))
    if not flat: L=bow_warp(L)
    L.alpha_composite(crosshair_layer(weapon,frame))
    cv=bg.copy(); cv.alpha_composite(L); cv.save(out); return L
if __name__=='__main__':
    from PIL import ImageFilter
    bg=Image.open(sys.argv[1]).convert('RGBA').resize((W,H)) if os.path.exists(sys.argv[1]) else Image.new('RGBA',(W,H),(0,0,0,0) if sys.argv[1]=='clear' else (40,46,52,255))
    render(bg,sys.argv[2])
