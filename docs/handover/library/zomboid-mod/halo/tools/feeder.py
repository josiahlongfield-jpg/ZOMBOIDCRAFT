"""Keep transit filled with <=220MB of outbox chunks; write commit json of what's in transit."""
import os,time,shutil,json
H='/tmp/claude-0/halo'; T='/mnt/project-files/zomboid-mod/halo/transit/world'; CAP=250e6
import zipfile
def packtex():
    d=f'{H}/outbox/tex'
    if not os.path.isdir(d): return
    fs=sorted(x for x in os.listdir(d) if x.endswith('.png'))
    tot=0;sel=[]
    for x in fs:
        sel.append(x); tot+=os.path.getsize(f'{d}/{x}')
        if tot>=22e6: break
    if tot<22e6 and not os.path.exists(f'{H}/flush_tex'): return
    if not sel: return
    os.makedirs(f'{H}/outbox/texpacks',exist_ok=True)
    n=len(os.listdir(f'{H}/outbox/texpacks'))+int(open(f'{H}/texpack_n').read() if os.path.exists(f'{H}/texpack_n') else 0)
    name=f'tex_{int(time.time()*1000)}.zip'
    with zipfile.ZipFile(f'{H}/outbox/texpacks/{name}.tmp','w',zipfile.ZIP_STORED) as z:
        for x in sel: z.write(f'{d}/{x}',x)
    os.replace(f'{H}/outbox/texpacks/{name}.tmp',f'{H}/outbox/texpacks/{name}')
    with open(f'{H}/shipped_tex.txt','a') as o:
        for x in sel: o.write(x[:-4]+'\n'); os.remove(f'{d}/{x}')
while True:
  try:
    for _ in range(20): packtex()
    have=0;cur=[]
    for dp,_,fn in os.walk(T):
        for f in fn:
            p=os.path.join(dp,f)
            try: have+=os.path.getsize(p); cur.append(p)
            except OSError: pass
    for m in sorted(os.listdir(f'{H}/outbox')):
        d=f'{H}/outbox/{m}'
        if not os.path.isdir(d) or m=='tex' or m.startswith('.'): continue
        if any(x.endswith('.tmp') for x in os.listdir(d)): continue
        for f in sorted(os.listdir(d)):
            p=f'{d}/{f}'; s=os.path.getsize(p)
            if have+s>CAP: break
            os.makedirs(f'{T}/{m}',exist_ok=True); shutil.copy(p,f'{T}/{m}/{f}'); os.remove(p); have+=s
            if m=='tex': open(f'{H}/shipped_tex.txt','a').write(f[:-4]+'\n')
        if not os.listdir(d) and m!='tex': os.rmdir(d)
    time.sleep(10)
  except Exception as e:
    print('ERR',repr(e),flush=True); time.sleep(5)
