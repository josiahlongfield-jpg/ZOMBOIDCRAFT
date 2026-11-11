---
name: zomboid-brita-wave3-tech
description: Brita wave 3 (the 88 new guns of the refined list): conversion pipeline, review page QcWrQW8iUpZ4sysoCx5Vhb, open calls awaiting Josiah
metadata:
  type: project
  modified: 2026-11-06T05:48:55.511Z
---

2026-11-06 05:30 Josiah: "back on my pc, you can get started" on the refined list ([[zomboid-brita-refined-list]]).

State 2026-11-06: all 88 new guns are pulled from his Brita + Arsenal workshop folders, converted to toon meshes, given stats (scaled against an anchor plus a Zomboid reference gun, same as stats2) and their Arsenal-designated attachments. Review page (private artifact): https://claude.ai/artifact/QcWrQW8iUpZ4sysoCx5Vhb. This is LOCAL ONLY data and must never go in the repo, jar or CI.

Pipeline notes:
- Folded-stock ids map to source models: Model55, Bizon2_01, EVO, KRISS, MAC10, UMP9, UMP45 and SPAS12 use their _Stock model, and KS23_S uses _Pistol. Attachment fits check both ids, because MountOn uses the source id.
- GP100_6 and the M9/92F grips come from Arsenal. Grip92F_Ivory has no texture.
- A gun is upright (no flip) when its muzzle offset z < 0.
- The artifact holds about 262 files, so it is published in two passes (more than 255 files per publish is refused).

DECIDED by Josiah 2026-11-06 06:06: no folding ammo, so add every new calibre as its own type; the flamethrower proposal is approved; the KAC M203 fires one 40mm round; the B93R gets its 3-round burst.

Calls that were put to him:
- New calibres: 5.45x39, .223, 4/10/20 gauge, .410.
- M2A1 flamethrower needs a new mechanic. Proposed: an ~8-block stream that sets zombies alight, from a 100-unit fuel tank, and never breaks blocks.
- KAC_M203 fires one round of our 40mm grenade.
- B93R fires single shots only in Arsenal.
- Ots14 uses 9mm per Arsenal.

Game meshes (2026-11-06 06:4x): local/build_local3.py (copy in brita/tools/wave3/ with the other wave-3 scripts) packed all 88 into local-pc/guns-orig/<id>.zmesh + .json. Up comes from the page's upright flag (the old count guessed the AK-74 Alpha, FAMAS and Vz58 upside down). The json carries B93R stats.burst 3, M2A1 anchor 'flamethrower' + flame {reach 8, tank 100}, and proposed new ammo ids (r545_ammo, r223_ammo, g4/g10/g20/g410_shell, flame_fuel; M203 uses grenade_round). The game code for burst, flamethrower and new ammo is NOT written yet.
Irons: brita/tools/irons/irons3.py, ALL 88 built 2026-11-06 into local-pc/guns (one @gun builder per gun, docstring = what it models; helpers ak, rifle, ar_handle, fsb_rear, railflips, scoped, pistol, bead, open_rear, surface). Factory irons from refs; pack flip-ups only on no-irons guns; M2A1 has only an aim line. Preview: brita/previews/brita-wave3-iron-sights.mp4 (scratchpad vf4.py renders, mkvid4.py; pic view clips mesh behind the rear sight).
07:06 Josiah approved the sights ("yes move on"); the video looks flat only because of the quick renderer (he knows). Hold poses: asked the survivor bodies thread (owns the posing tool) to add all 88, data + follow templates in brita/tools/wave3/posing-wave3.json. Reload prep: tools/wave3/magframes3.json (47 guns with a mag part; pistols/shotguns/bolt guns have none, make prims as wave-2 specs2.py). Reloads wait for the posed holds.
Fixed 2026-11-06: wave-2 AK47 (irons2.py) had the leaf notch at its FRONT end; the AK photo shows it at the REAR end. Now rebuilt in local-pc/guns (backup irons2_before_aknotch.py).

Next, after his OK: real-life irons ([[zomboid-brita-irons-tech]]), sounds, hold poses, then reloads for all 88 following [[zomboid-reload-rules]] and the wave-2 port pipeline ([[zomboid-reload-wave2]]).
