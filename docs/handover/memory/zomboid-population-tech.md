---
name: zomboid-population-tech
description: ZomboidCraft world population engine (zombie/population: Population chunk records, Census floor-space count, Spots, Budgets) and the shared door/window blows
metadata:
  type: project
  modified: 2026-10-27T10:07:44.586Z
---

Built 2026-10-27 by the performance thread (branch u0is1s). Direction: [[zomboid-population-direction]]. Perf background: [[zomboid-performance-tech]].

- `zombie/population/Budgets.java`: the approved table per PlaceType/variant (total + indoor share). Wild = 10 per 512x512 per spot, x5 on roads. Grayhaven island (20,000) goes in here once LANDSCAPE adds `County.onIsland(x, z)`. LANDMARK stadium 1200.
- `Spots.java`: one spot test for real chunks and the census: floor (collision top 0.5-1.0, not tree, not wet), two clear blocks (carpets count as clear), indoor = a >=0.75-wide roof within 24 above; outdoors only within 2 of the generator surface (or on the carrier deck).
- `Census.java`: off-thread (one daemon thread) builds up to 64 sampled chunks of each place through CountyGen.generate into a sink and scales the indoor/outdoor spot counts. Rate per spot = budget / spots, so any building LANDSCAPE adds is filled automatically by floor space.
- `Population.java`: chunk attachment `zomboidcraft:population` = Pop(int[] recs, given, day). Chunks within 8 of a player are queued and filled (2.5 ms per tick). Every 10 ticks: street records wake within streetRange 96 (|dy| <= 40), indoor ones within indoorRange 32 and indoorFloors 9 (or 8 blocks and 24 up/down), nearest first, perStep 24, under realCap 250. Noise.emit calls Population.wake (radius >= 20) so records in earshot come out and head to the noise. Idle walkers (no target, nothing heard, no lost limbs, 200+ ticks old) past range + foldMargin 16 fold back into their chunk's records. Half of houses empty (12-block lot hash, TOWN/SUBURB/FARM). respawnDays 0 = never; >0 tops chunks back up to `given`.
- ZcConfig `population.*`; DEFAULTS 8 turns `spawning.enabled` off (old spawner); PathfinderMobMixin cancels NATURAL walker spawns while population is on. `/zc perf` prints Population.stats().
- Doors/windows (BashGoal): blows counted per block (door lower half) in a shared map, forgotten after 5 minutes untouched; `zombies.doorHits` 120 (was a 1-in-12 roll), `windowHits` 45; windows = GLASS sound, no light.

- Crowd (2026-10-27, commit 41dd1c6d, playtest fe17db34): Population.crowd() every 20 ticks sends each player a CrowdPayload of OUTDOOR records 40..192 blocks, nearest 3000; client CrowdFx draws them as ZSprites idle cards, one submit per outfit. Readout shows "crowd N".
- Self-tuning cap: population.autoCap, capMin 120, capMax 800, tickTarget 40 ms; Population.tune() in survey drops 5% when avg tick > target, +6 when < 0.75x target and cap-limited. /zc perf shows "real X of CAP (self-tuning)".
- Island: Budgets.ISLAND 20000 @0.85 indoors, pooled: any place whose centre is county.island() uses Population.island() = sum of Census counts of all island places (county.allPlaces filtered), logged once. Untested in game.
Still to do: hotspot building bonuses (hospital, police, mall...).

- KEEP (UI thread, 2026-10-27 22:57): spawn picker calls Population.keepClear(level, pos) -> level attachment zomboidcraft:clear_zones (<=256); Spots.scan seeding skips spots within 14 sideways, -4..+10 vertically; drops records and live zombies there. Preserve this skip in any seeding/record rework.
PAUSED again 2026-10-28 01:18 by Josiah. All work merged (playtest 20f8241e+). Next when resumed: hotspot building bonuses.

- 2026-10-28 stall fix (branch ma5ppf): Census used the world's CountyGen and its 10-place PlaceCache, evicting places near players (rebuilt under the global BUILD_LOCK while chunks waited: lag with FPS fine). Census now runs its own CountyGen (Census.side) with PlaceCache(4, borrow = world cache): reuses world-built places, never evicts them, standBack() waits up to 15 s while world-gen builds want the lock. Keep this if Census is reworked.
