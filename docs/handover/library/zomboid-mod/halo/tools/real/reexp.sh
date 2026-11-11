#!/bin/bash
# usage: reexp.sh <map> <bsps...>  (re-export glbs with the shared texture library, keep bakes)
cd /tmp/claude-0/halo; m=$1; shift; export ZC_TEXLIB=/tmp/claude-0/halo/real/tex
for b in "$@"; do
  timeout 2400 python3 -c "
import sys;sys.path.insert(0,'.');import lexport2 as lexport
lexport.run('maps/keep/$m.map','levels\\\\solo\\\\$m\\\\$b','real/$m/$b.glb.new',1024)" > real/$m/$b.reexp.log 2>&1 && mv real/$m/$b.glb.new real/$m/$b.glb
  echo "re $b $(tail -1 real/$m/$b.reexp.log)"
done
