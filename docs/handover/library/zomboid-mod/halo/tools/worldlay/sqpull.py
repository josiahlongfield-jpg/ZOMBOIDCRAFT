import sys,os,subprocess,shutil
sys.path.insert(0,'/tmp/claude-0/halo/worldlay')
from squads import run
U='/mnt/user-data/uploads'
m=sys.argv[1]; out=f'/tmp/claude-0/halo/maps/{m}.map'
parts=[f'{U}/Zomboidcraft-Desktop--additions/halo-reach/split/{m}.map.part1',f'{U}/Zomboidcraft-Desktop--additions/halo-reach/split/{m}.map.part2']
whole=f'{U}/Halo- The Master Chief Collection/Content/haloreach/maps/{m}.map'
if os.path.exists(parts[0]):
    with open(out,'wb') as f:
        for p in parts: shutil.copyfileobj(open(p,'rb'),f,16<<20)
    for p in parts: os.remove(p)
    src=out
else: src=whole
R=run(src,f'/tmp/claude-0/halo/worldlay/sq_{m}.json')
print(m,len(R['squads']),'squads',sum(len(s['points']) for s in R['squads']),'points')
os.remove(src)
