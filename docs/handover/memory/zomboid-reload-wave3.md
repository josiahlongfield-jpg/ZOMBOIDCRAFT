---
name: zomboid-reload-wave3
description: Wave-3 Brita reloads (88 guns) ported from Josiah's 14 edited templates: status, scripts, families, what is left (2026-11-06)
metadata:
  type: project
  modified: 2026-11-06T08:28:37.858Z
---

ON HOLD 2026-11-06 10:56: Josiah decided (loot plan doc YQBXTTQ1C98wNZaxjzgzhJ, his notes 10:51) that all Brita guns and pack attachments go and the game switches to Tarkov weapons. This wave-3 reload work is superseded; nothing deleted; the Tarkov thread does removals after previews. Ask him before any more Brita work.

Status 2026-11-06 08:55: 87 of 88 wave-3 reloads in the reload editor artifact T7GqDNuxrnjgeSJZbEM4fL (version 18, kit/index.json lists 128 guns). Awaiting Josiah's review (one gun per family). Only M2A1 (flamethrower) is left, waiting on his call (suggested: fuel-tank swap).

specs5.py (no direct template): break-actions ROYAL_SXS, Coachgun, NEF_Handi_410, Thompson_Center_3006 from his S&W 500 with the barrel group tipping -35 deg about the hinge (x axis); KAC_M203 barrel slides forward 3 px; WA2000 (L86, mag body comp 12); M1208 (M14, 4-tube mag body comp 3); Colt1855 is a PLACEHOLDER swing-out (real gun loads from the front; his call).

Scripts (LOCAL ONLY, copies in /mnt/project-files/zomboid-mod/brita/tools/wave3/reload3/):
- specs3.py: AR family (his AR-15 bc), AK/M14 family (right handle), bullpups (L86), MGs, HK-style left pull.
- specs4.py: pistols (prim mags from pframe(), slide by box above slide-bottom y), SMGs, revolvers (REV with box sel), M870 pumps (port + pump box), semi tubes (M1014 bolt), levers (Winchester1866 gate+lever anchors), bolt guns (SV_98 mag+bolt, M40A3 internal loading), MAS49.
- export_kit3.py: SPECMOD=specs4 env picks the spec module; runs port.js, then armfit0.js (eases armfit sho so both ends land on the posed grip), then fit.js.
- chk2.js: hand misses and finger depth versus the template.

Known rough spots to mention in review: belt-fed MGs (M249E3, RPD) use a box swap like wave-2 M60/PKM; MG hold poses already bury fingers ~6 cm (posing thread's first-guess holds); bolt knob, handle and port points for several guns are estimates from side views.

Related: [[zomboid-reload-wave2]], [[zomboid-reload-rules]], [[zomboid-brita-wave3-tech]], [[zomboid-reloads-ingame-tech]].

In-game sound cues (2026-11-06): reloads/<id>.cues.json beside a local gun's clip ({dur, mag_out, mag_in, charge, charge_sound:"release"}) makes Guns.cueSounds play each sound at his clip's moment; written for AAC_Honey, AssaultRifle2, L86, M2Carbine, American180, Bush_AR15_MOE. The AR-15 release sound needs the Brita pack's sounds.json entry weapon.local/bush_ar15_moe/weapon.rifle.bolt_release (asked him; not yet approved).
