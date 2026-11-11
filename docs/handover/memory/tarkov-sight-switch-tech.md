---
name: tarkov-sight-switch-tech
description: Josiah's 2026-11-07 rule that every switchable Tarkov sight (piggyback red dots, 1x/4x modes, variable zoom) works in game, looted ones too; how it is built
metadata:
  type: project
---
RULE (Josiah 2026-11-07 04:59/05:02): any sight that can switch (e.g. the MDR's ACOG with the RMR on top) must be a working part of the attachment, for EVERY sight with the option, including sights looted and fitted at the bench.

Built (commit 259c8db1e, branch claude/project-thread-5z3m2y): gun file `alt` list [{sight mesh bone, zoom, zooms, reticleMoa, aim:[x,y,z,qx,qy,qz,qw] = alt aim camera rel to main mod_aim_camera}] + `zooms` (Tarkov Zooms = modes). Pack textures reticle_N (pack_gun.py env ALT_RETICLES=a.png,b.png). OperatorView: comma = switch sight (rig slides eye point to eye point, 0.25 s), slash = sight mode; per gun id state; hip always main sight. Eye maths checked in a joml test.
Data: MDR alt RMR (aim from web/relaim.js), SV-98 alt RMR + zooms [5,25], MP-18 zooms [6.5,20] (local + local-pc gun files; MDR/SV-98 zsks repacked).
Per-sight table for the bench: tools/batch1/sights.json (81 scopes: scopes[] with zooms+modes from SPT ScopesCount/Zooms/ModesCount, variable flag, sightSlots mod_scope/mod_mount for piggybacks); 30 switch or carry another. BENCH WORK MUST build alt/zooms from the fitted parts using this table.
Related: [[tarkov-grip-holds-tech]], [[tarkov-weapon-batch1]].
