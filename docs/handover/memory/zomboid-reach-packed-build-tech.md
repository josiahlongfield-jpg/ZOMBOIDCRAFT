---
name: zomboid-reach-packed-build-tech
description: How the approved tight packed Reach world (2026-11-11) is built: tools, outputs, md5s, what moved and what still has to follow
metadata:
  type: project
  modified: 2026-11-11T11:57:04.930Z
---
Tight packed layout approved 2026-11-11 21:43 (his clock), see [[zomboid-reach-layout-question]].

Moves (deltas_tight.json, metres x,z; every coordinate inside a mission's territory moves by its unit's delta):
m10+m05 [2080,1600], m20 [-864,3584], m60 [-5376,2528], m35+m30 [-640,-2176], m52 [-992,864], m50 [-4928,1536],
m70+m70_bonus [-1120,-2592], m45 [-1312,3008]. New centres (packed_tight.json): m10 (-4089,-2241), m20 (-1112,-1738),
m60 (920,-3595), m35 (-3434,1319), m52 (3167,2378), m50 (1442,-1042), m70 (-748,3317), m45 (-303,727).
Territory = argmin(dist to new centre - r80 radius); 160 m seams of our land between territories.

Pipeline in /tmp/claude-0/pack (cloud, dies with the container): packland.py -> fillhalo.py -> holefix v5.py -> overlap.py
= land_pack4.zrh md5 94be623769d2d7a55d6e9a9d135a2ade; reach_pack.json; zrv/ (shiftzrv.py, all collision moved,
reach_towers split to m52/m50); sky/packsky.py -> sky/sky_regions.zsk (old sky weights moved per territory, ~330 m blur).
Game (commit 6f285fbac on playtest): world/sky_regions.zsk beside land.zrh wins over the jar's, so install it WITH the land.

Install set (nothing installed yet as of 2026-11-11 22:00): land.zrh, reach.json, sky_regions.zsk, every .zrv in world\,
backups first in additions\jar-backups; needs a fresh world/chunks.
Still to tell other threads after install: Covenant spawn-sites.json and MP placements.json coordinates move by the deltas.
