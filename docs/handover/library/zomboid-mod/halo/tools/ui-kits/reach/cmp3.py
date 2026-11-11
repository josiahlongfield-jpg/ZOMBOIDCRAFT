import sys; sys.argv=['x']; sys.path.insert(0,'.')
import sheet as S, numpy as np
from PIL import Image, ImageDraw, ImageFont
F='/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/fonts/SairaSemiCondensed-Medium.ttfx.ttf'
f=ImageFont.truetype(F,24)
U='/root/.claude/uploads/87bca096-c08a-5c14-8dff-4bef71334809/'
refs=(('DMR','dmr',U+'a0516833-image.png',(946.5,522.0)),('Sniper','sniper_rifle',U+'92530b4e-image.png',(945.5,521.0)),('Magnum','magnum',U+'93f0ee9d-image.png',(942.0,520.5)))
P=420; r=32  # 64 px window at 1080p
out=Image.new('RGB',(3*P+20,2*P+110),(16,18,22)); d=ImageDraw.Draw(out)
d.text((8,6),'Top: your in-game shots (1080p).  Bottom: ours, same scale, on a copy of your background.',font=f,fill=(230,230,230))
Zc=S.h.Z; cx,cy=S.h.W/2,S.h.H/2
for i,(lab,wn,fn,c) in enumerate(refs):
  im=Image.open(fn).convert('RGBA')
  ref=im.crop((int(c[0]-r),int(c[1]-r),int(c[0]+r),int(c[1]+r)))
  # background: blur of the reference patch so colours compare fairly
  from PIL import ImageFilter
  bg=ref.filter(ImageFilter.GaussianBlur(12)).resize((int(2*r*Zc/1.5),int(2*r*Zc/1.5)))
  X=S.hip(wn); w=r*Zc/1.5
  ours=X.crop((int(cx-w),int(cy-w),int(cx+w),int(cy+w)))
  bg.alpha_composite(ours.resize(bg.size,Image.LANCZOS))
  x0=10+i*(P+0)
  d.text((x0,40),lab,font=f,fill=(230,230,230))
  out.paste(ref.convert('RGB').resize((P-10,P-10),Image.LANCZOS),(x0,75))
  out.paste(bg.convert('RGB').resize((P-10,P-10),Image.LANCZOS),(x0,75+P))
out.save('out/cmp3.png'); out.save('/mnt/project-files/zomboid-mod/halo/previews/reach-dmr-sniper-magnum-vs-yours.jpg',quality=90)
