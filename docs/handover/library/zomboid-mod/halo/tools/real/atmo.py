"""Per-mission camera exposure (cfxs) and atmosphere fog (fogg) values -> real/atmo.json."""
import sys, struct, json
sys.path.insert(0, '/tmp/claude-0/halo')
from reach import Reach, X
out = {}
for m in sys.argv[1:]:
    r = Reach(f'/tmp/claude-0/halo/maps/keep/{m}.map'); nm = r.names(); d = {'fog': {}}
    for k, t in enumerate(r.tags):
        if not t[0] or 'levels\\' not in nm[k]: continue
        o = r.off(X(t[2])); f = lambda a: struct.unpack_from('<f', r.d, o + a)[0]; f3 = lambda a: list(struct.unpack_from('<3f', r.d, o + a))
        if t[0] == 'cfxs':
            d['cfxs'] = dict(name=nm[k], emin=f(0x10), emax=f(0x14), target=2 ** f(0x18), raw=[f(a) for a in range(8, 0x30, 4)])
        if t[0] == 'fogg':
            d['fog'][nm[k].split('\\')[-1]] = dict(raw=[f(a) for a in range(4, 0x70, 4)], c0=f3(0x1c), c1=f3(0x3c), o0=f(0x14), d0=f(0x18), o1=f(0x34), d1=f(0x38))
    out[m] = d
    print(m, json.dumps(d.get('cfxs', {}).get('target')), {k: (v['c1'], v['o1'], v['d1']) for k, v in d['fog'].items()})
json.dump(out, open('/tmp/claude-0/halo/real/atmo.json', 'w'), indent=1)
