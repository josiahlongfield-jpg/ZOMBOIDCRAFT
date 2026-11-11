import sys; sys.argv=['x']; sys.path.insert(0,'.')
import sheet as S, numpy as np
from PIL import Image, ImageDraw, ImageFont
F='/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/fonts/SairaSemiCondensed-Medium.ttfx.ttf'
f=ImageFont.truetype(F,26)
out=Image.new('RGB',(1010,1110),(16,18,22)); d=ImageDraw.Draw(out)
m=Image.open('../ref/mag_ref_crop.png').resize((500,500)); dr=Image.open('../ref/dmr_ref_crop.png').resize((500,500))
Zc=S.h.Z; cx,cy=S.h.W/2,S.h.H/2
for i,(wn,ref,lab) in enumerate((('magnum',m,'Magnum'),('dmr',dr,'DMR'))):
  X=S.hip(wn)
  if wn=='magnum': win=74.5*Zc; note='ours (same scale)'
  else:
    a=np.array(X)[...,3]; ys,xs=np.nonzero(a>60); dia=xs.max()-xs.min()+1
    win=dia*500/362; note='ours (zoomed to match)'   # his DMR outer ring spans ~362 of the 500 px panel
  c=X.crop((int(cx-win/2),int(cy-win/2),int(cx+win/2),int(cy+win/2))).resize((500,500),Image.LANCZOS)
  bg=Image.new('RGBA',(500,500),(40,44,48,255)); bg.alpha_composite(c)
  y0=i*555
  d.text((8,y0+8),lab+': your screenshot',font=f,fill=(230,230,230)); d.text((518,y0+8),lab+': '+note,font=f,fill=(230,230,230))
  out.paste(ref,(0,y0+50)); out.paste(bg.convert('RGB'),(510,y0+50))
out.save('out/magdmr_cmp.png'); out.save('/mnt/project-files/zomboid-mod/halo/previews/reach-magnum-dmr-vs-yours.jpg',quality=90)
