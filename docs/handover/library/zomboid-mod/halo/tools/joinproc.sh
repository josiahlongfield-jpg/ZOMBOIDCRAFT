#!/bin/bash
export CLIP_PREFIXES='objects\vehicles\,objects\levels\,objects\cinematics\,objects\scenery\'
# usage: joinproc.sh name size : join staged split parts into maps/<name>.map, extract clips, delete parts and joined file
S="/mnt/user-data/uploads/Zomboidcraft-Desktop--additions/halo-reach/split"
cd /tmp/claude-0/halo
n=$1; want=$2
for i in $(seq 1 30); do
  a=$(stat -c %s "$S/$n.map.part1" 2>/dev/null || echo 0); b=$(stat -c %s "$S/$n.map.part2" 2>/dev/null || echo 0)
  [ $((a+b)) -eq $want ] && break; python3 -c "import time;time.sleep(3)"
done
cat "$S/$n.map.part1" "$S/$n.map.part2" > maps/$n.map
ls -la maps/$n.map
python3 -c "import os,sys; [os.remove(os.path.join(sys.argv[1],sys.argv[2]+'.map.part'+k)) for k in '12']" "$S" "$n"
python3 clipdb.py maps/$n.map
python3 vexport.py maps/$n.map 2>&1 | tail -40
python3 -c "import os,sys; os.remove('/tmp/claude-0/halo/maps/'+sys.argv[1]+'.map')" "$n"
