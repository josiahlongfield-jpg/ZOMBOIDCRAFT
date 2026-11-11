#!/bin/bash
# usage: mission.sh <map>  -> real/<map>/<bsp>.glb (+ _lm.png bakes) and real/<map>/sky*.glb, textures shared in real/tex
cd /tmp/claude-0/halo; m=$1; export ZC_TEXLIB=/tmp/claude-0/halo/real/tex; mkdir -p real/$m
bsps=$(python3 -c "
import sys;sys.path.insert(0,'.')
from rres import RR
r=RR('maps/keep/$m.map');nm=r.names()
print(' '.join(sorted(set(nm[k].split('\\\\')[-1] for k,t in enumerate(r.tags) if t[0]=='sbsp' and '\\\\$m\\\\' in nm[k]))))")
echo "$m bsps: $bsps"
real/expall.sh $m $bsps
python3 real/mklm.py real/$m/*_light.npy > /dev/null
rm -f real/$m/*_light.npy real/$m/*_cov.png
python3 -c "
import sys,json;sys.path.insert(0,'.')
from rres import RR
import export
export.MATHOOK=True
r=RR('maps/keep/$m.map');nm=r.names()
out=[]
for k,t in enumerate(r.tags):
    n=nm[k]
    if t[0]=='mode' and ('\\\\sky' in n.lower()) and 'decorators' not in n:
        fn='real/$m/sky_'+n.split('\\\\')[-1]+'.glb'
        try: export.export(r,nm,n,fn,maxsz=2048); out.append(fn)
        except Exception as ex: print('sky fail',n,ex)
print('skies',out)"
echo "MISSION DONE $m"
