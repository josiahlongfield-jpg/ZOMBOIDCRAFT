#!/bin/bash
export CLIP_PREFIXES='objects\vehicles\,objects\levels\,objects\cinematics\,objects\scenery\'
# usage: proc.sh name1 [name2]: symlink staged maps, extract clips, then delete the staged copies
U="/mnt/user-data/uploads/Halo- The Master Chief Collection/Content/haloreach/maps"
cd /tmp/claude-0/halo
for n in "$@"; do
  for i in 1 2 3 4 5 6 7 8 9 10; do [ -f "$U/$n.map" ] && break; python3 -c "import time;time.sleep(2)"; done
  ln -sf "$U/$n.map" maps/$n.map
  python3 clipdb.py maps/$n.map
  python3 vexport.py maps/$n.map 2>&1 | tail -40
  unlink maps/$n.map
  python3 -c "import os,sys; p=os.path.join(sys.argv[1],sys.argv[2]+'.map'); os.remove(p); print('removed',p)" "$U" "$n"
done
