import sys,json
sys.path.insert(0,'/tmp/claude-0/halo/worldlay'); sys.path.insert(0,'/tmp/claude-0/halo')
from overlay import ov
J=lambda m: json.load(open(f'/tmp/claude-0/halo/survey/{m}.json'))
for m in (sys.argv[1:] or ['m10','m20','m30','m35','m50','m52','m60','m70','m45']):
    d=J(m); play=[b['bsp'] for b in d['bsps'] if b['kind']=='play']; back=[b['bsp'] for b in d['bsps'] if b['kind']=='backdrop']
    if m=='m45': play=['m45_001','m45_005','m45_010','m45_015']; back=['m45_000']
    try: ov('bd_'+m,m,play+back,6.0,(),True,play,4000)
    except Exception as e: print(m,'ERR',repr(e),flush=True)
