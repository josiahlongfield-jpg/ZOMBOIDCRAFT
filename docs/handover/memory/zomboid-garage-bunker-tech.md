---
name: zomboid-garage-bunker-tech
description: How the downtown tower parking levels and hidden skyscraper bunker are generated, previewed and found in game (Garage.java, park_ kit, /zc bunker)
metadata:
  type: project
  modified: 2026-10-26T00:51:01.383Z
---

(2026-10-26, landscape thread) Commit 01f85b7 on branch claude/project-thread-d61kbn, CI green; merged into playtest 3eb9193 on 2026-10-26 01:25 with the weathering (283eba5), jar sent.

- src/main/java/com/zomboidcraft/world/county/Garage.java: 2-3 parking levels under a Tower (H=4, floorY(k) = -4k), own RNG from Noise.hash(seed, box). Fits only when w >= 30, d >= 28, the tower core leaves room for the ramp lanes (x >= w-9) and cz0 >= 8. Skipped near the subway gate (within 16 of p.gateZ()).
- Bottom level has the service corner: freight lift, electrical room, mesh cage. DowntownSite.hideout() picks ONE garage per downtown for the bunker (SEALED 45%, LOOTED 25%, BREACHED 30%); the rest get cageJunk().
- Bunker hatch at (6, bottom, d-2) in the cage; bunker floor = min(bottom-10, -22); a 2-wide vestibule and ladder shaft lead to the blast door. Bunker frame tries 4 turns to fit the tower plot.
- Marks: BlockSink.mark(what, x, y, z) is recorded by Voxels.marks ("bunker <state>" at the hatch, "bunker door"). In game, /zc bunker and /zc bunker door teleport to the nearest one (3 nearest downtowns within 8000).
- park_ block kit lives in tools/city/bunker.py (PREFIXES includes "park_", texture seeds 6400-6432).
- Preview: java -cp build/county GaragePreview OUT seed w d levels STATE ylo yhi [xlo xhi]. mesh.py now rotates cube top/bottom textures (arrows and lines turn correctly).
- Sheet sent: /mnt/project-files/zomboid-mod/previews/tower-garage-bunker.jpg.
- Photon mock-up (commit 2449adb): view.html &photon=1 (&dark=1 underground, &hand=1 held torch, &ev=N), mesh.py --flicker-on; scratch helper furn/garf.sh. Sheet sent 2026-10-26: previews/tower-garage-bunker-photon.jpg. It is an approximation, not real Photon. Finding: asphalt/sidewalk LabPBR smoothness is about 0.55, so roads look damp and glossy under Photon; offered to make them rougher.
- Next in wave one: quay cargo ship, civil-defence shelter, forest hatch.

Related: [[zomboid-bunker-kit-tech]], [[zomboid-bunkers-direction]], [[zomboid-world-tech]].
