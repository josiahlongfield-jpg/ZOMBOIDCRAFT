#!/bin/bash
# render + pick previews for one already-exported mission (glbs kept). usage: prev1.sh <map> [extra url params]
m=$1; X=$2; cd /tmp/claude-0/halo/real/web; rm -f cand/${m}_*.jpg
U=$(python3 ../cams2.py $m 10) && timeout 3000 node multi.js "$U$X" cand/$m > ../prev_$m.log 2>&1
python3 pick.py $m 6 >> ../prev_$m.log 2>&1
echo "PREVIEW DONE $m $(tail -1 ../prev_$m.log)"
