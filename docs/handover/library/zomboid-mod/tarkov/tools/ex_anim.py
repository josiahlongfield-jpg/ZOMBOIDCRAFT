import sys,json; sys.path.insert(0,'/tmp/claude-0/tk')
from anim import load,bones
OUT='/tmp/claude-0/tk/web/data/'
env,tos,clips=load('assets__content__weapons__m4a1__client_assets.bundle')
want=sys.argv[1:] or ['m4a1_idle_armed','m4a1_fire','m4a1_mag_out_0','m4a1_mag_in_0','m4a1_reload_charge_0','m4a1_hands_in','m4a1_look','m4a1_charge_0','m4a1_mag_out_all_0','m4a1_reload_start','m4a1_reload_end']
import math
def qmul(a,b):
    ax,ay,az,aw=a;bx,by,bz,bw=b
    return (aw*bx+ax*bw+ay*bz-az*by, aw*by-ax*bz+ay*bw+az*bx, aw*bz+ax*by-ay*bx+az*bw, aw*bw-ax*bx-ay*by-az*bz)
def euler(v):
    x,y,z=[math.radians(a)/2 for a in v]
    qx=(math.sin(x),0,0,math.cos(x)); qy=(0,math.sin(y),0,math.cos(y)); qz=(0,0,math.sin(z),math.cos(z))
    return qmul(qmul(qz,qy),qx)
res={}
for n in want:
    layers=[]
    for c in clips[n]:
        r,nf,dur,_,_=bones(c,tos)
        L={}
        for path,ch in r.items():
            e={}
            if 'p' in ch: e['p']=[[round(-v[0],5),round(v[1],5),round(v[2],5)] for v in ch['p']]
            if 'q' in ch: e['q']=[[round(v[0],5),round(-v[1],5),round(-v[2],5),round(v[3],5)] for v in ch['q']]
            if 'e' in ch and 'q' not in ch:
                qs=[]
                for v in ch['e']:
                    q=euler(v); qs.append([round(q[0],5),round(-q[1],5),round(-q[2],5),round(q[3],5)])
                e['q']=qs
            if 's' in ch: e['s']=[[round(x,5) for x in v] for v in ch['s']]
            if e: L[path]=e
        layers.append({'nf':nf,'dur':dur,'bones':L,'nb':len(r)})
    res[n]=layers
    print(n,[(l['nb'],round(l['dur'],2)) for l in layers])
json.dump(res,open(OUT+'anims.json','w'))
