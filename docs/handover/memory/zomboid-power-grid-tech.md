---
name: zomboid-power-grid-tech
description: LOOT's mains grid (PowerGrid districts, shutoff day, OFF/ON events), generators (place/fuel/connect/fix), Blackout sweep, How to Use Generators magazine (2026-10-31)
metadata:
  type: project
  modified: 2026-10-31T08:38:27.404Z
---

Built by LOOT on claude/project-thread-jd27hs (2026-10-31, stage 4 of wave 4). Not yet in playtest at time of writing (waits on the military preview OK).

- **com.zomboidcraft.power.PowerGrid**: districts come from LANDSCAPE's world/gen/PowerDistricts (id or null; null and non-county worlds = PowerGrid.WHOLE "grid"). Each district goes off at power.shutoffDays (ElecShutModifier, default 14, -1 = off from start) × 24000 + its own hour (hash of id + seed). power.alwaysOn = never. restore(level, id) = on for good (substation repair; LANDSCAPE calls it). Events OFF / ON: Changed(ServerLevel, String districtId), fired from a 40-tick sync; attachments power_down / power_restored on the overworld. hasPower = mainsOn || Generators.powers. Only the overworld has mains.
- **Generators** (power/Generators, GeneratorBlock, GeneratorEntity, GeneratorItem): portable_generator (10 L, noise 20) and quiet_generator (20 L, burn 0.8x, noise 6) set down by using the item on the ground -> blocks placed_<item> (unbreakable; pick up from the G menu keeps fuel/condition in CUSTOM_DATA). G tap toggles; hold-G menu: on/off, connect (needs MagazineItem.knows(player, "mag_generators")), add fuel from a gas can, fix (generator_parts, +10+8×Electrical, 25 XP), check, pick up. Wear: 1/12 chance per game hour -1 condition; under 20 can stall. Sandbox: power.generatorFuel / generatorRange / generatorFloors (5 blocks a floor) / generatorExterior, wired with owner LOOT.
- **Blackout**: on OFF and on every chunk load in an off district, lit room lamps (lamps.txt pairs) and lx_switch_on go dark unless a generator reaches. LightSwitch refuses to turn on without power. Traffic Signal heads go to PHASE 0 without power and re-check every 1200 ticks.
- **MagazineItem**: read once (5 s), kept in player attachment magazines_read; TEACHES map. Only mag_generators so far; the other mag_ items are still plain (world stories task).
- Not done: street lamps (plain lit blocks, no dark pair), carbon monoxide from generators run indoors (SURVIVAL), lifts stay powered by design.

Related: [[zomboid-landscape-contracts]], [[zomboid-substations-tech]], [[zomboid-room-lights]].
