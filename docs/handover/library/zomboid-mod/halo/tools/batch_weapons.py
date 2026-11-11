import sys,json,traceback;sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
import export
M=sys.argv[1]; r=RR(M); nm=r.names()
done=json.load(open('/tmp/claude-0/halo/exp/done.json')) if __import__('os').path.exists('/tmp/claude-0/halo/exp/done.json') else {}
for k,t in enumerate(r.tags):
    if t[0]!='mode': continue
    n=nm[k]
    if n.startswith('objects\\weapons\\') and '\\projectiles\\' not in n: sub='weapons'
    elif n.startswith('objects\\equipment\\'): sub='equipment'
    else: continue
    base=n.split('\\')[-1]
    if n.startswith('objects\\weapons\\multiplayer'): base='mp_'+base
    out='/tmp/claude-0/halo/exp/%s/%s.glb'%(sub,base)
    if base in done: continue
    try:
        export.TEXCACHE.clear()
        res=export.export(r,nm,n,out); done[base]=dict(tag=n,map=M.split('/')[-1],tris=res['tris'])
        print(base,res['tris'],flush=True)
    except Exception as e:
        print('FAIL',n,e,flush=True); traceback.print_exc()
json.dump(done,open('/tmp/claude-0/halo/exp/done.json','w'),indent=1)
