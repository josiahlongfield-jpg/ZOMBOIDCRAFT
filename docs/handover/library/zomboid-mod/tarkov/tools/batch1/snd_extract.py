import UnityPy, os, sys, glob, json
src, out = sys.argv[1], sys.argv[2]
idx = {}
for f in sorted(glob.glob(src + '/*audio*')):
    tag = f.split('audio__')[-1].replace('.bundle', '')
    try: env = UnityPy.load(f)
    except Exception as e: print('ERR', tag, e); continue
    n = 0
    for o in env.objects:
        if o.type.name != 'AudioClip': continue
        try:
            a = o.read()
            for nm, data in a.samples.items():
                p = os.path.join(out, nm if nm.endswith('.wav') else nm + '.wav')
                if not os.path.exists(p):
                    open(p, 'wb').write(data)
                idx.setdefault(tag, []).append(os.path.basename(p)[:-4]); n += 1
        except Exception as e: print('clip err', tag, e)
    print(tag, n, flush=True)
json.dump(idx, open(os.path.join(out, 'index.json'), 'w'), indent=0)
