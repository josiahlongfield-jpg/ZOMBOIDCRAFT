# python3 tune.py GID SPEC_GID t1 t2 ... : render the spec's poses at given times (both views) into one sheet
import sys,json,subprocess,shutil; sys.path.insert(0,'.')
import engine as e, specs
gid=sys.argv[1]; times=[float(x) for x in sys.argv[2:]]
sp=dict(specs.SPECS[gid]); fps=30
d=e.run(gid,sp,fps)
for k,v in e.WARN.items(): print('SHORT',k,v[0],'..',v[-1],len(v),'frames, worst',max(x[1] for x in v))
idx=[int(round(t*fps)) for t in times]
for v in ('fp','tp'): d['views'][v]=[d['views'][v][i] for i in idx]
json.dump(d,open('d_tune.json','w'))
shutil.rmtree('tune',ignore_errors=True)
subprocess.run(['node','run.js','d_tune.json','tune','1'],check=True,capture_output=True)
from PIL import Image,ImageDraw
n=len(idx); S=Image.new('RGB',(480*n,540)); D=ImageDraw.Draw(S)
for i in range(n):
    for j,v in enumerate(('fp','tp')):
        S.paste(Image.open(f'tune/{v}_{i:04d}.png').resize((480,270)),(i*480,j*270))
    D.text((i*480+5,5),f'{times[i]:.2f}s',fill=(200,0,0))
S.save('tune.png')
