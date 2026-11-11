import sys,os,pickle;sys.path.insert(0,'/tmp/claude-0/halo')
from jmad import *
import jmad
DB='/tmp/claude-0/halo/clipdb'
import json
PREFIXES=tuple(os.environ.get('CLIP_PREFIXES','objects\\characters\\').split(','))
KW=('cruiser','corvette','carrier','frigate','capital','ccs','covenant_ship','supercarrier','spirit','phantom','pelican','falcon','longsword','sabre','savannah','frigate')
def safe(n): return n.replace('\\','__')
def run(mp):
    r=RR(mp); nm=r.names(); tot=0; new=0
    for k,t in enumerate(r.tags):
        if t[0]!='jmad' or not nm[k].startswith(PREFIXES): continue
        try: G=graph(r,k)
        except Exception as e: print('graph err',nm[k],e); continue
        p=f'{DB}/{safe(nm[k])}.pkl'
        db=pickle.load(open(p,'rb')) if os.path.exists(p) else dict(skel=G['skel'],clips={},src={})
        if db['skel']!=G['skel']: print('SKEL MISMATCH',nm[k]); continue
        for a in G['anims']:
            if a['ref'] is not None or a.get('type') is None: continue
            tot+=1
            if a['name'] in db['clips']: continue
            try: hdr,D=anim_data(r,G,a)
            except Exception as e: continue
            if not D: continue
            db['clips'][a['name']]=(a['type'],a['frames'],a['nodes'],hdr,bytes(D)); db['src'][a['name']]=os.path.basename(mp); new+=1
        pickle.dump(db,open(p,'wb'))
        jmad._resc.clear()
    cat='/tmp/claude-0/halo/vehicle_catalog.json'; C=json.load(open(cat)) if os.path.exists(cat) else {}
    for k,t in enumerate(r.tags):
        if t[0] in ('vehi','scen','bloc','mach','mode','hlmt','jmad') and (nm[k].startswith('objects\\vehicles\\') or any(w in nm[k].lower() for w in KW)):
            C.setdefault(t[0]+' '+nm[k],[])
            if os.path.basename(mp) not in C[t[0]+' '+nm[k]]: C[t[0]+' '+nm[k]].append(os.path.basename(mp))
    json.dump(C,open(cat,'w'),indent=0)
    print(os.path.basename(mp),'own clips',tot,'new',new,flush=True)
if __name__=='__main__':
    for m in sys.argv[1:]: run(m)
