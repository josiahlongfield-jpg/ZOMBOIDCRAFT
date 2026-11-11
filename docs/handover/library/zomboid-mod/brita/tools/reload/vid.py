# python3 vid.py GID "Title" -> previews/reload_GID.mp4 (first person | third person, then once more at half speed)
import sys,os,json,subprocess,shutil,glob; sys.path.insert(0,'.')
from PIL import Image,ImageDraw,ImageFont
import engine as e, specs
gid,title=sys.argv[1],sys.argv[2]
json.dump(e.run(gid,specs.SPECS[gid]),open(f'd_{gid}.json','w'))
fr=f'fr_{gid}'; shutil.rmtree(fr,ignore_errors=True)
subprocess.run(['node','run.js',f'd_{gid}.json',fr,'1'],check=True,capture_output=True)
F=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',26); f2=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',20)
n=len(glob.glob(f'{fr}/fp_*.png')); out=f'{fr}/c'; os.makedirs(out,exist_ok=True); k=0
def comp(i,label):
    global k
    S=Image.new('RGB',(1920,600),(28,28,32)); D=ImageDraw.Draw(S)
    S.paste(Image.open(f'{fr}/fp_{i:04d}.png'),(0,60)); S.paste(Image.open(f'{fr}/tp_{i:04d}.png'),(960,60))
    D.text((16,14),title,font=F,fill=(240,240,240)); D.text((1100,18),label,font=f2,fill=(200,200,200))
    D.text((16,566),'First person',font=f2,fill=(230,230,230)); D.text((976,566),'Third person',font=f2,fill=(230,230,230))
    S.save(f'{out}/{k:05d}.png'); k+=1
for i in range(n): comp(i,'full speed')
for _ in range(15): comp(n-1,'')
for i in range(n):
    comp(i,'half speed'); comp(i,'half speed')
os.makedirs('/mnt/project-files/zomboid-mod/brita/previews/reloads',exist_ok=True)
dst=f'/mnt/project-files/zomboid-mod/brita/previews/reloads/reload_{gid}.mp4'
subprocess.run(['ffmpeg','-y','-loglevel','error','-framerate','30','-i',f'{out}/%05d.png','-c:v','libx264','-pix_fmt','yuv420p','-crf','23',dst],check=True)
print(dst, os.path.getsize(dst))
