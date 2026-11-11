"""Full Reach shader -> glTF material (base/detail/bump/illum maps, transforms, blend, extras.halo with every parameter)."""
import re,os
TEXLIB=os.environ.get('ZC_TEXLIB')
SHIPPED=set(open(os.environ['ZC_SHIPPED']).read().split()) if os.environ.get('ZC_SHIPPED') and os.path.exists(os.environ['ZC_SHIPPED']) else set()
import sys,io,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
import rshader2, rbitmap
from rmodel import find_tag as _ft
from PIL import Image
KEEP=('base_map','alpha_map','detail_map','detail_map2','detail_map3','bump_map','bump_detail_map','self_illum_map','self_illum_detail_map','alpha_test_map','opacity_texture','specular_mask_texture','overlay_map','overlay_detail_map','height_map','change_color_map','blend_map')+tuple(f'{a}_m_{i}' for a in ('base_map','detail_map','bump_map','detail_bump') for i in range(4))
class Mats:
    def __init__(s,g,r,nm,maxsz=512):
        s.g,s.r,s.nm,s.maxsz=g,r,nm,maxsz; s.texidx={}; s.matidx={}
    def texture(s,bname,kind):
        g,r,nm=s.g,s.r,s.nm
        if bname in s.texidx: return s.texidx[bname]
        t=None
        lim=s.maxsz*2 if kind in ('base_map','bump_map','blend_map') or kind.startswith('base_map_m') else s.maxsz
        nrm=kind.startswith('bump') or kind.startswith('detail_bump')
        if TEXLIB:
            key=re.sub(r'[^A-Za-z0-9_.-]','_',bname.replace('\\','__'))+('__n' if nrm else '')+'_%d'%lim
            if key in SHIPPED or os.path.exists(f'{TEXLIB}/{key}.png'):
                s.texidx[bname]=t=s.g.image_uri(f'tex/{key}.png'); return t
        try:
            kb,bo=_ft(r,'bitm',bname,nm); im,(w,h,fmt,typ)=rbitmap.bitmap(r,bo)
            if fmt==38 or nrm:
                a=np.asarray(im.convert('RGB'),np.float32)/255*2-1
                if fmt==38:
                    a[:,:,2]=np.sqrt(np.clip(1-a[:,:,0]**2-a[:,:,1]**2,0,1))
                im=Image.fromarray(((a*0.5+0.5)*255).clip(0,255).astype(np.uint8))
            if max(im.size)>lim: im.thumbnail((lim,lim))
            if TEXLIB:
                os.makedirs(TEXLIB,exist_ok=True); im.save(f'{TEXLIB}/{key}.png.tmp','PNG'); os.replace(f'{TEXLIB}/{key}.png.tmp',f'{TEXLIB}/{key}.png'); t=g.image_uri(f'tex/{key}.png')
            else:
                bio=io.BytesIO(); im.save(bio,'PNG'); t=g.image(bio.getvalue())
        except Exception as ex: print('  tex fail',bname.split('\\')[-1],str(ex)[:60])
        s.texidx[bname]=t; return t
    def curve(s,bname):
        try:
            import struct
            from reach import X
            kb,bo=_ft(s.r,'bitm',bname,s.nm); ni,ia=struct.unpack_from('<II',s.r.d,bo+0x7c); ia=s.r.off(X(ia))
            return s.r.d[ia+0x11]
        except Exception: return 0
    def material(s,datum,fallback='mat',info=None):
        if datum in s.matidx: return s.matidx[datum]
        g=s.g; info=info or rshader2.shader_info(s.r,s.nm,datum)
        m=dict(name=info['name'].split('\\')[-1] if info else fallback,pbrMetallicRoughness=dict(metallicFactor=0.0,roughnessFactor=0.8),doubleSided=True)
        if info:
            tx={}
            for tn,bn in info['textures'].items():
                if tn in KEEP and 'default_bitmaps' not in bn:
                    ti=s.texture(bn,tn)
                    if ti is not None: tx[tn]=ti
            rl=info['reals']; op=info['options']
            def tt(tn):
                ref=dict(index=tx[tn]); xf=rl.get(tn)
                if xf and tuple(xf)!=(1,1,0,0): ref['extensions']=dict(KHR_texture_transform=dict(scale=[xf[0],xf[1]],offset=[xf[2],xf[3]]))
                return ref
            if 'base_map' in tx: m['pbrMetallicRoughness']['baseColorTexture']=tt('base_map')
            ac=rl.get('albedo_color')
            if ac: m['pbrMetallicRoughness']['baseColorFactor']=[ac[0],ac[1],ac[2],1.0]
            if 'bump_map' in tx: m['normalTexture']=tt('bump_map')
            if 'self_illum_map' in tx:
                m['emissiveTexture']=tt('self_illum_map'); sc=rl.get('self_illum_color',[1,1,1,1])
                m['emissiveFactor']=[min(1.0,sc[0]),min(1.0,sc[1]),min(1.0,sc[2])]
            bm=op.get('blend_mode'); grp=info['group']
            if bm in ('alpha_blend','pre_multiplied_alpha','additive') or grp in ('rmgl',) or (grp=='rmhg' and bm!='opaque'): m['alphaMode']='BLEND'
            if op.get('alpha_test') not in (None,'none') or 'alpha_test_map' in tx: m['alphaMode']='MASK'
            m['extras']=dict(halo=dict(shader=info['name'],group=grp,template=info.get('template'),options=op,reals=rl,textures=tx,texture_names=info['textures'],curves={tn:s.curve(info['textures'][tn]) for tn in tx}))
        g.j['materials'].append(m); s.matidx[datum]=len(g.j['materials'])-1; return s.matidx[datum]
