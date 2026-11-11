"""Kit specs: {slot: [item name, {subslots}] } resolved against SPT filters. python3 kit.py <gun> prints the
resolved kit and, for every slot of every chosen part, the options (short) so gaps can be filled."""
import sys, json; sys.path.insert(0,'.')
from db import *
def resolve(t, spec, path='', out=None, show=False):
    out = out if out is not None else []
    for sn,f,req in slots(t):
        if sn in spec:
            nm, sub = (spec[sn], {}) if isinstance(spec[sn], str) else spec[sn]
            hit=[c for c in f if c in I and name(c)==nm]
            if not hit:
                hit=[c for c in f if c in I and nm.lower() in name(c).lower()]
            if not hit:
                print('!! no', repr(nm), 'in', path+'/'+sn, '->', sorted({name(c) for c in f if c in I})[:60]); continue
            c=hit[0]; out.append((path+'/'+sn, c))
            resolve(c, sub, path+'/'+sn, out, show)
        elif show:
            opts=sorted({name(c) for c in f if c in I})
            print('   ..', path+'/'+sn, ('REQUIRED ' if req else '')+'(%d)'%len(opts), '; '.join(opts[:25]))
    return out
if __name__=='__main__':
    K=json.load(open('kits.json')); g=sys.argv[1]
    for p,c in resolve(K[g]['id'], K[g]['kit'], show=True): print(p, '=', name(c), '['+kind(c)+']')
