---
name: zomboid-hordes-death-base-direction
description: Josiah's 2026-10-26 pick of three new ZomboidCraft features (roaming hordes, your body stays after death, base building); plan doc link and status
metadata:
  type: project
  modified: 2026-10-26T11:02:54.702Z
---

Josiah 2026-10-26 11:00 (UI thread), choosing from a creative brainstorm: "look into building 3, 4, 6", which were roaming hordes, your death leaving a trace, and base building.

Plan doc (Claude Doc, 11:10): https://claude.ai/artifact/UCUCueszUoTCCTDEedJ7H6 ("Hordes, body recovery and base building"). It recommends this order:
1. Base building. A Build page on the E kit screen with a placement preview and timed builds. Wave 1: boards up to 4 planks, metal sheets, tin-can tripwire, rain barrel, sandbags. Wave 2: car alarm decoy, alarm clock decoy, planter box, spiked log wall. Every board has its own HP. All custom blocks.
2. Roaming hordes. Herds are records on the county map that move along roads and become real zombies within about 96 blocks of a player. They turn toward big noises and are marked on the Map once spotted. Defaults: 6 per county, 30-200 zombies each.
3. Your body stays. The corpse keeps all your gear and is looted from the Pack page. An infected body gets up as a zombie carrying your gear. The new character's map marks the death spot. Skills reset by default, with an optional share kept.

Josiah's answers 11:11: the order is approved (base building, hordes, body). Hordes should be BIGGER than 30-200. His body gets up only if he died infected. Death resets ALL skills and progress. Base building waves approved as written (he may add more). New ask: stop players digging down to dodge everything. Proposed 11:15: (1) a hard dig floor about 8 blocks under natural ground, except inside built places; (2) block toughness tiers by tool + skill (hand for loose; Carpentry + crowbar/hammer for wood; new Masonry + sledgehammer for brick/concrete; new Metalworking + torch for metal); (3) breaking is loud, costs stamina and tool wear, and drops rubble/scrap rather than the block. Placing stays free. He said yes 11:20. SHIPPED in playtest ddb6cdbd (11:47): building/DigRules.java + mixin BlockStateDigMixin (getDestroyProgress), config section digging, skills MASONRY/METALWORKING (SkillsPage Building group), items rubble_chunks (the city already has a rubble BLOCK), scrap_metal, propane_torch (tools/items/gen_scrap.py). Walls = full CityBlocks without block entity, tier by SoundType; furniture strips with its tool for XP. Wave 1 (Build page, Placer, boards with HP, metal sheet, tripwire, rain barrel, sandbags, weld sheet) merged to playtest a663b5de 2026-10-26; see [[zomboid-base-building-tech]]. Wave 2 (alarm clock + car alarm decoys, planter box, spiked log wall) in playtest 10f69661 on 2026-10-27. Next: roaming hordes, then body. Existing pieces: barricade/ (hammer + plank barricade), zombie/Hordes.java (only the crowd count for sound), ZombieSpawner, Noise/Sight. See [[zomboid-ui-systems-tech]] and [[zomboid-spawn-select-direction]].
