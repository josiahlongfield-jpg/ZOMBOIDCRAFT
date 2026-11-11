#!/bin/bash
# usage: expall.sh <map short> <bsp shorts...>
m=$1; shift; mkdir -p real/$m
for b in "$@"; do
  if [ ! -f real/$m/$b.glb ]; then timeout 2400 python3 -c "
import sys;sys.path.insert(0,'.');import lexport2 as lexport
lexport.run('maps/keep/$m.map','levels\\\\solo\\\\$m\\\\$b','real/$m/$b.glb',1024)" > real/$m/$b.exp.log 2>&1; fi
  timeout 1800 python3 lmbake.py maps/keep/$m.map "levels\\solo\\$m\\$b" real/$m/$b.glb real/$m/$b >> real/$m/$b.exp.log 2>&1
  echo "done $b $(tail -1 real/$m/$b.exp.log)"
done
