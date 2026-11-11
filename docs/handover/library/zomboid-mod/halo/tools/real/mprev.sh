#!/bin/bash
# usage: mprev.sh <map...>: export+bake (if needed), render candidate previews, keep the best, then free the big glbs
cd /tmp/claude-0/halo
for m in "$@"; do
  f=$(df --output=avail -m /tmp | tail -1)
  if [ $f -lt 600 ]; then echo "STOP low disk $f MB before $m"; break; fi
  n=$(ls real/$m/*.glb 2>/dev/null | grep -v '/sky_' | wc -l)
  if [ $n -eq 0 ]; then rm -f real/$m/sky_*.glb; real/mission.sh $m > real/mission_$m.log 2>&1; fi
  cd real/web; rm -f cand/${m}_*.jpg
  U=$(python3 ../cams2.py $m 10) && timeout 3000 node multi.js "$U" cand/$m > ../prev_$m.log 2>&1
  python3 pick.py $m 6 >> ../prev_$m.log 2>&1
  ok=$?
  cd /tmp/claude-0/halo
  [ $ok -eq 0 ] && rm -f real/$m/*.glb
  echo "PREVIEW DONE $m ok=$ok $(tail -1 real/prev_$m.log)"
done
