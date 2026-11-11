---
name: zomboid-union-station-tech
description: Union Station landmark: head house (UnionStation.java) + upstairs el train hall and bus terminal (UnionHall.java), lot frame, constants, tx_ kit, preview and check commands
metadata:
  type: project
---

Head house built 2026-10-27 (bd3a02b4); upstairs TRAIN HALL built 2026-10-27 evening (49f9209e, in playtest 742f6345) per Josiah's 18:43 pick ([[zomboid-trains-direction]]). Rail side: [[zomboid-rail-tech]]. Built from text knowledge; redo when refs rail/modern-train-hall and rail/station-fittings land ([[zomboid-photo-reference-rule]]).

- Lot now spans the avenue: Layout.lot(s, LANDMARK, "union_station", 2, 2, h), w = d = 133. LandmarkSite: q = lot(0,0,w,d,2); UnionStation(q,rng).build(); new UnionHall(q,rng,w,d,head.power()).build().
- Frame: world x = 3106 - lx, z = -1054 - lz, y = 66 + ly; local -z ("north") = lot front = world south. Reason in local coords only; Placer rotates facings.
- Head house: W 52 (east wall lx 51), ZF 10, ZH 49, ZC 117, ZB 130, WING_TOP 24. Mezzanine door lz 95..98, ly 12..15 opens straight onto platform 1 (old bridge removed). Subway hall/passage unchanged.
- El: straight, centre between lx 66/67; RailGen (runs AFTER places) lays ballast/deck lx 65..68 and clears ly 11..17 in the hall. Platform edges lx 64 (P1, trains stop here) and 69 (P2). Bed ly 10, slab 11, stand 12.
- UnionHall: HE 51, HW 82, Z0 1..Z1 131; CEIL 9, DECK 10, PLAT 11, FORK 20, ROOF 26. Steel trees TREE_X {58,75} x TREE_Z {10,26,42,66,90,106,122}; stair+escalator banks feet lz 43/89; gates lz 36/96; ticket office lx 52..57 lz 12..19; train call buttons ly 13 at z 30/66/102. Terminal bands: taxi 87, isle 93, bus 100, isle 108, bus 115, plaza 123 (lx); buses/taxis are Car entities.
- Kit: tools/city/transit.py (tx_ incl. tx_vault_beam/node, *_off lamps). Regenerate: transit.py -> gen_blocks.py /tmp/claude-0/texall.
- Previews: county `dpdump.sh uh1 3040 -1120 90` (mesh origin 2944,50,-1216; no cars). With cars: `SitePreview <dir> landmark union_station 133 133 12345 16` (dump x = 148-lx, z = 148-lz, y = 12+ly) then mesh.py --cars. Shots: project files zomboid-mod/previews/rail/41..47.
- Checks: orientcheck 0; walkcheck 0 blocked doors/stairs. Known harmless jump pockets: balustrade tops by the grand stair (exist without the hall too).
- Open: P2 doors only help if TRAINS opens doors on both sides (trains stop at P1).
- ZombieSpawner: union_station zombies 20% soldier, 30% patient.
