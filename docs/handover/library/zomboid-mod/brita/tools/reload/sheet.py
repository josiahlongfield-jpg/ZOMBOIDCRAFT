import sys,glob
from PIL import Image,ImageDraw
d,view,out=sys.argv[1],sys.argv[2],sys.argv[3]; fps=30
fs=sorted(glob.glob(f'{d}/{view}_*.png'))
cols=4; w,h=480,270; rows=(len(fs)+cols-1)//cols
S=Image.new('RGB',(cols*w,rows*h),(0,0,0)); D=ImageDraw.Draw(S)
for i,f in enumerate(fs):
    im=Image.open(f).resize((w,h)); x,y=(i%cols)*w,(i//cols)*h; S.paste(im,(x,y))
    D.text((x+6,y+4),f"{int(f[-8:-4])/fps:.2f}s",fill=(200,0,0))
S.save(out)
