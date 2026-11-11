import os,json,time,subprocess,sys
H='/tmp/claude-0/halo'
rej=set(open(f'{H}/rej.txt').read().split('\n'))-{''} if os.path.exists(f'{H}/rej.txt') else set()
for e in json.load(open(f'{H}/cur.json')):
    p=e['stagedPath']
    if p in rej or os.path.basename(p) in rej:
        try:
            b=open(p,'rb').read(); os.remove(p); time.sleep(1); open(p,'wb').write(b)
        except FileNotFoundError: pass
        continue
    try: os.remove(p)
    except FileNotFoundError: pass
if os.path.exists(f'{H}/rej.txt'): os.remove(f'{H}/rej.txt')
time.sleep(60)
subprocess.call(['python3',f'{H}/cj.py'])
