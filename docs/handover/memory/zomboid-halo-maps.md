---
name: zomboid-halo-maps
description: Halo MCC maps as LOCAL-ONLY world segments (Josiah 2026-11-08 06:55): where his MCC install is, file formats, Reach focus
metadata:
  type: project
  modified: 2026-11-08T11:40:00.000Z
---

**The ask (Josiah 2026-11-08 06:55):** "in exactly the same way we are approaching this game currently in terms of it being entirely for personal use and never being reuplished or sold": add maps from his Halo MCC folder to ours as segments. At 07:00: "id like to concentrate on" the **Halo Reach** maps.
FULL ASK (07:03-07:30): Sword Base map, ALL Reach weapons, Spartan body plus every armor piece and cosmetic, Noble Team (campaign files) made into PLAYABLE characters (like the Tarkov operator), and Elites, Jackals, Hunters and Grunts in every class as future ENEMIES. Output to C:\Dev\Zomboidcraft-Desktop\additions\halo-reach.
RULE: same as the Warthog and Tarkov. Never put anything in the repo, jar or CI. Converted segments live on his PC (config/zomboidcraft/...), and world gen places them only when the file is present.

**Install (Xbox app, folder access granted 06:58):** C:\XboxGames\Halo- The Master Chief Collection\Content\{halo1,halo2,groundhog,halo3,halo3odst,halo4,haloreach}. The per-game DLC folders C:\XboxGames\The Master Chief Collection- <game> hold only package stubs.
- Reach maps: haloreach\maps\*.map (44 files, 37-496 MB). Codenames:
  - Campaign: m05 Noble Actual, m10 Winter Contingency, m20 ONI Sword Base, m30 Nightfall, m35 Tip of the Spear, m45 Long Night of Solace, m50 Exodus, m52 New Alexandria, m60 The Package, m70 Pillar of Autumn.
  - Multiplayer: 20_sword_slayer Sword Base, 30_settlement Powerhouse, 35_island Spire, 45_aftship Zealot, 45_launch_station Countdown, 50_panopticon Boardwalk, 52_ivory_tower Reflection, 70_boneyard Boneyard, condemned, trainingpreserve Highlands, dlc_* (Map Pack), cex_* (Anniversary), ff* (Firefight), forge_halo, ff_unearthed.
- Reach format: header 'head' v13 (MCC build Jun 21 2023). Resource pages are RAW DEFLATE (zlib wbits -15; a test page at 0x167000 inflated to 406 KB). The geometry/collision for gen3 BSPs sits in resource pages, so a full cache reader is needed (written in the cloud; pip reclaimer was blocked by the classifier).
- READER details: [[zomboid-halo-reach-format]].
- DELIVERED 2026-11-08 08:45 to C:\Dev\Zomboidcraft-Desktop\additions\halo-reach: weapons\ (27), equipment\ (4), noble\ (spartan_{carter,kat,jun,emile,jorge,john117,default} + heads + spartan_variants.json, outfits from the game's own hlmt variants; Kat = female body, air assault helmet + FC-I), covenant\ (every class: 11 elite, 5 grunt, 8 jackal/skirmisher, hunter + *_variants.json, colours from the game's change-colour data), spartan-armory\ (158 per-piece GLBs region__perm + spartan_armory_all_pieces.json), split\campaign.map.part1/2 (copy for staging). Tools backed up in zomboid-mod/halo/tools/. NEXT: animations (jmad), FP arms, Noble playable (like Tarkov operator), Covenant enemies, Sword Base render geometry, where it sits in the county (his call).
- Halo CE (halo1\maps, v13, uncompressed) WORKS: /tmp/claude-0/halo/cemap.py + coll.py. Scenario +0x5A4 = structure_bsps (32 B: file off, size, magic). sbsp +0xA4 = collision materials (20 B), +0xB0 = collision bsp (8 reflexives; surfaces 12 B, edges 24 B, vertices 16 B). Blood Gulch = 5940 tris, about 126x145 world units (1 wu = 3.048 m).

- Weapons completion + weapon sounds: [[zomboid-halo-weapons]].
- Vehicles (delivered) + Sword Base level build: [[zomboid-halo-swordbase]].
Related: [[zomboid-pc-storage-rule]] (save to C:\Dev\Zomboidcraft-Desktop\additions, never OneDrive), [[zomboid-warthog-tech]], [[tarkov-assets-pipeline-tech]].
- Voices: [[zomboid-halo-voices]].
