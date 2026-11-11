---
name: zomboid-population-direction
description: Josiah's 2026-10-27 rules for ZomboidCraft's zombie population (Zomboid-style density by place, no respawn by default, one shared count, towns full at world gen)
metadata:
  type: feedback
  modified: 2026-10-27T09:36:23.780Z
---

Josiah 2026-10-27 09:36, answering the horde engine plan (https://claude.ai/artifact/U5yxeaVBdABFCpq5LDond6):

1. Density like Project Zomboid, by place: downtown/cities and "landmarks" can hold THOUSANDS (ground level, every skyscraper floor, the subway); a small town a couple of hundred; zombies scattered between major areas but far thinner. "Its really dependant on the capabilities you have."
2. No respawn by default. A setting lets the player pick how many in-game days/months before respawning. With it off, cleared houses stay cleared, unless a door is open or broken and a zombie wanders in.
3. Every building gets its dead at world generation (towns start full).
4. ONE zombie count shared by all players on the map; every zombie is (technically) visible to every player the same way. No per-player caps.
5. OK to remove the steady spawner and vanilla night spawns for walkers once populations and herds take over.
6. Build order: "do what you think is best".

Josiah 09:37: "all major areas need to be the hotspots for zombies and they scale in zombie count depending on their function and size with downtown having the largest population of the whole map". Plan: size per place type + footprint from the county plan; show him the numbers table before spawning.

Sizing doc sent 2026-10-27 ~09:45: https://claude.ai/artifact/TgfqRgPiao5wQ5cxVWbLSU ("Zombie population: how many, where"): ~115,000 per world (seed 12345: 25 downtowns; metro 3,000, city 2,000, old city 1,400, county seat 450, main street 250, hamlet 80, suburbs 90-240, farms 5-12, ~10 per empty 512 cell), hotspot building bonuses, ~1/4 houses empty, population.density 0.25-4, shared real cap 250, real within 96 / fold past 112. Four questions at the end; awaiting answers. Counting tool: javac -sourcepath src/main/java County.java + a small main using County.placesNear (CountPlaces in old scratchpad).

Zomboid reference (looked up 09:50, community counts): whole Knox map ~75k-100k at 1x, Louisville 20k-40k; population multiplier 0-4 (Insane 4) with start/peak multipliers (~16x maxed).

DECISION Josiah 09:54-09:56: 20,000 for the GRAYHAVEN ISLAND CITY only ("our biggest landmark and most intricate", built by LANDSCAPE; every new part of it must be meticulously filled). Mainland procedural downtowns stay 3,000/2,000/1,400; world ~135k. Asked LANDSCAPE how code identifies the island. Earlier reading (superseded): downtown 20,000 ("massive"), on condition they fill buildings and skyscrapers, not just roads. Applied: metro 20,000, city 13,000, old city 9,400 (area-scaled; my reading), world ~430k. Split: ~55% towers/flats by floor space, 15% street, 15% shops/offices, 15% underground; records pinned to rooms. Indoor ones materialise only for your building/adjacent and nearby floors (+-2); downtown real cap ~400.

Josiah 10:01: table defaults OK as a base (scale later). HALF of houses empty. Zomboid-style zombies shut inside houses banging on doors when they hear you. Doors much stronger; break time scales with how many zombies bang on a door or window (shared hit points; solo ~2 min, 5 ~25 s, 10 ~12 s), settings to tune.

**Why:** a finite, Zomboid-like world where the first wave is huge and clearing an area means something.

**How to apply:** the initial population must be substantial; size it by place type (county plan, [[zomboid-county-plan]]), store most as records ([[zomboid-performance-tech]]), and keep a real-entity cap for performance, filled nearest-first around all players together. Roaming herds: [[zomboid-hordes-death-base-direction]].
