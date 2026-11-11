import os,time,subprocess
H='/tmp/claude-0/halo'
while True:
    for f in sorted(os.listdir(f'{H}/world')):
        if f.endswith('.DONE'):
            m=f[:-5]
            if not os.path.exists(f'{H}/world/{m}.PACKED') and os.path.isdir(f'{H}/world/{m}'):
                subprocess.call(['python3',f'{H}/wpack2.py',m]); open(f'{H}/world/{m}.PACKED','w').write('1')
    time.sleep(15)
