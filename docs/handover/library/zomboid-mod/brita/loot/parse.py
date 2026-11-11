import re, json, sys
src = open(sys.argv[1], encoding='latin-1').read().replace('\t',' ')
lines = src.split('\n')
CROSS = ['CIVxLEO','CIVxMIL','LEOxCIV','LEOxMIL','MILxNON','SECxNON','HNTxNON','SURxNON']
def env(cross=0.5, region='USA'):
    e = {}
    for k in re.findall(r'\b(w[A-Za-z0-9]+|x[A-Za-z0-9]+)\b', src):
        if k.startswith('w'): e[k]=1.0
        elif k.startswith('x'): e[k]=0.0
    for k in ['CIVx','LEOx','MILx','SECx','HNTx','SURx']: e[k]=1.0
    for k in CROSS: e[k]=cross
    for r in ['USA','SOV','CZE','KOR','PAC','EUR','ISR']: e['MILx'+r]= 100 if r==region else 0
    e.update(Case=0.17, CCW=0.17, Suppress=5, Optic=2.5, Light=5, Part=2.5, AmmoCan=20, AmmoBox=20, StdMag=10, ExtMag=10,
             DrumMag=10, PolyCan=1, SmKnife=5, LgKnife=5, Sword=0.1, Axe=5, Blunt=5, Spear=5, PowerTool=1, Armor=1, Reload=1, VLR=0.5, x90=0)
    return e
def ev(expr, e):
    expr = expr.strip().rstrip(',').strip()
    if not expr: return None
    try: return eval(expr.replace('^','**'), {}, e)
    except Exception as ex: return None
E = env()
out = []   # (list, gun, expr)
cur = None
pending = None
for i,l in enumerate(lines):
    s = l.strip()
    m = re.match(r'(?:SuburbsDistributions|VehicleDistributions)\[?"?\.?([A-Za-z0-9_]+)"?\]?\s*=\s*\{', s)
    if m: cur = m.group(1); continue
    m = re.match(r'([A-Za-z0-9_]+)\s*=\s*\{\s*(--.*)?$', s)
    if m and m.group(1) not in ('items','junk','procList','counter','list','distributionTable') : cur = m.group(1)
    m = re.match(r'table\.insert\((.+?)\.(?:items|weapons)\s*,\s*"Base\.([^"]+)"\s*\)', s)
    if m:
        pending = (m.group(1), m.group(2), i)
        if '.weapons' in s: out.append((m.group(1), m.group(2), None, i)); pending=None
        continue
    m = re.match(r'table\.insert\((.+?)\.items\s*,\s*(.+)\)\s*;?\s*(--.*)?$', s)
    if m and pending and m.group(1)==pending[0]:
        out.append((pending[0], pending[1], m.group(2), i)); pending=None; continue
    m = re.match(r'"Base\.([^"]+)"\s*,\s*([^"]+?)\s*,?\s*(--.*)?$', s)
    if m and cur: out.append((cur, m.group(1), m.group(2), i))
json.dump(out, open('rows.json','w'))
print(len(out))
