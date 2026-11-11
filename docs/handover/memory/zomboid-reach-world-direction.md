---
name: zomboid-reach-world-direction
description: Halo Reach world direction (2026-11-08): brand new separate 16 km open world, county world kept; New Alexandria city; endless rain; no digging; corvette
metadata:
  type: project
---
Josiah 2026-11-08 12:19 + 12:27:
- "happy to have the entire map changed to suit a halo reach landscape and have the downtown and city be turned into a new Alexandria style city internally and externally but still have the entire world encapsulated in the endless rain aesthetic we currently have".
- "I won't be wanting to dig into anything, there wont be any need to on this build now" -> no block terrain needed; real Halo meshes + invisible collision are fine for land too.
- Campaign missions (m05..m70) as places in the world; Covenant squad spots from mission AI placements could drive spawns.
- PLAN DOC (2026-11-08 12:45): https://claude.ai/artifact/AAttusu1HZswrkuKKx1tvX. Choices R1-R20 with my recs, answers in CAPITALS + summary table column. Build order: 1 Sword Base in game (render/Photon test) -> 2 survey all 44 maps -> 3 layout preview -> 4 first New Alexandria block -> 5 districts -> 6 gameplay layer -> 7 switch-over (old county kept as fallback).
- 19:06-19:07: pull every Reach map; build asap; ask PLANS what fills it.
- PLANS reply: placement list in /mnt/project-files/zomboid-mod/purge/purge-world-placements.md (base sites, pods, Covenant camps, Noble rescues, Powerhouse purge goal). OPEN: no-digging vs base building (ask with R12).
- Josiah 2026-11-08 20:39 COVENANT CRUISER: the m45 Long Night of Solace corvette must be in the world as a ship visible in the sky, with a ground beam-up point; fully end-to-end walkable, filled with everything the mission shows. Original is only partly walkable, so missing decks are to be built from m45 interiors + 45_aftship (Zealot) + ff45_corvette pieces. m45 needed a re-pull (collision missing, BSPs 020/025/090 failed).
- Josiah 21:05 'let's begin, I want maximum detail'; 21:08: maps are combined so landmarks will differ, but make everything FLOW TOGETHER as well as possible (blend seams, consistent terrain/sky/rain).
- Josiah 2026-11-08 21:30 SEPARATE WORLD (overrides 'entire map changed'): the current county world stays complete as ITS OWN package/world, untouched; the Reach world is a BRAND NEW world (own layout, not mapped onto county spots). R19 county-spot mapping and R16 fallback are superseded by this. Layout page: https://claude.ai/artifact/F94zL9sYymxQA7eN5Fu6Av (Anniversary layer off per R3). Scripts in /mnt/project-files/zomboid-mod/halo/tools/worldlay/. Atlas: https://claude.ai/artifact/2MKmXgXtvXcVDWz73z4es7.
- Josiah 21:57-21:59 OPEN WORLD: make it BIGGER (16 km agreed in my reply) if land is filled with Reach-style activities, settlements and mini bases; keep an open-world feel, NOT funnelled along mission routes; mission layouts still in full, complete size and detail. Plan: use each level's own backdrop landscape (15-35 km, coarse) as in-between land with an up-detail pass, remove the missions' invisible walls and kill zones, make backdrop collision walkable.
- Josiah 22:03: place ALL mission enemy spawn spots incl. vehicles, plus Zomboid zombies; get creative on the open ground and PITCH GRAND DESIGN IDEAS: intricate structures of human OR Covenant making (Covenant-erected bases, UNSC bases/areas) matching Reach aesthetic/build quality (22:13).
- Pipeline/PC layout: see [[zomboid-reach-pipeline]].
Related: [[zomboid-halo-maps]], [[zomboid-halo-swordbase]], [[zomboid-purge-direction]], [[zomboid-county-plan]]. Places: [[zomboid-reach-places-direction]]. Sky: [[zomboid-reach-sky-direction]].
- LAYOUT v3 16 km published 2026-11-08 22:20: each mission in its real backdrop land (164 km2), 421 squads (m10/m20/m45), 22 settlements/bases, grand-design starter list of 8 (to brainstorm with him). Package placed as snowy NE twin of Sword Base valley. Src zomboid-mod/halo/layout-page/.
