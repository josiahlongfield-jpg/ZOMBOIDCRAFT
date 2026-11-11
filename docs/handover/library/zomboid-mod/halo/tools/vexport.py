import sys,os,json;sys.path.insert(0,'/tmp/claude-0/halo')
from jmad import RR; from export import export
OUT='/tmp/claude-0/halo/exp/vehicles'
KW=('cruiser','corvette','carrier','frigate','capital','covenant_fleet','unsc_fleet','supercarrier','savannah','seraph','sabre','spirit')
def run(mp):
    r=RR(mp); nm=r.names(); n=0
    for k,t in enumerate(r.tags):
        if t[0]!='mode': continue
        name=nm[k]; low=name.lower()
        if '\\garbage\\' in low or 'destroyed' in low or 'debris' in low: continue
        if not (low.startswith('objects\\vehicles\\') or any(w in low for w in KW)): continue
        fn=name.replace('objects\\','').replace('\\','__')
        parts=fn.split('__'); 
        if len(parts)>1 and parts[-1]==parts[-2]: parts=parts[:-1]
        out=f'{OUT}/'+'__'.join(parts)+'.glb'
        if os.path.exists(out): continue
        try: export(r,nm,name,out); n+=1; print('exported',os.path.basename(out),os.path.getsize(out)//1024,'KB',flush=True)
        except Exception as e: print('FAIL',name,str(e)[:80],flush=True)
    print(os.path.basename(mp),'vehicle models new',n)
if __name__=='__main__':
    for m in sys.argv[1:]: run(m)
