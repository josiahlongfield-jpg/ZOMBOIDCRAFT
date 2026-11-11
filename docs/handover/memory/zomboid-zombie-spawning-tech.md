---
name: zomboid-zombie-spawning-tech
description: Where ZomboidCraft zombies spawn from and the roof/tree perch rule (ZombieSpawner.perched, PathfinderMobMixin) for anyone touching spawns
metadata:
  type: project
  modified: 2026-10-27T03:58:07.889Z
---

Two sources of walkers in the overworld (2026-10-27, characters thread, branch claude/project-thread-wfvzb7):
- `zombie/ZombieSpawner.java`: the mod's own groups near players (EVENT reason). `findSpot` tries the player's height first, then the MOTION_BLOCKING_NO_LEAVES top; `canSpawnAt` needs a standable floor and calls `perched`.
- Vanilla night spawns (NATURAL). MobFilter only removes non-walker monsters, so vanilla zombie/husk/zombie villager still spawn. They are held to `perched` by `mixin/PathfinderMobMixin` (checkSpawnRules HEAD). Not MobMixin: PathfinderMob overrides Mob.checkSpawnRules without calling super, so a Mob hook never fires for zombies.

`ZombieSpawner.perched(level, pos)` (public, reuse it for any new spawn source such as roaming hordes):
- Standing on any tree block is a perch: BlockTags.LEAVES or a `zomboidcraft:tree_*` block. Pack trees are NOT leaves and DO count in MOTION_BLOCKING_NO_LEAVES, which fooled the old heightmap check (Josiah 03:48 "still getting zombies spawning on rooves/on top of building and trees").
- Within 2 blocks of natural ground is fine. Ground = generator `getBaseHeight(x, z, WORLD_SURFACE_WG)` (first air above ground or water; county and noise generators both), cached per column like DigRules.
- Higher up it is fine only with a real floor/roof (collision box at least 0.75 wide both ways, not a tree block) within 24 blocks overhead, or on the carrier flight deck (`navy_fl*`, catapult, cable). Open ship decks, bridges and garage roofs count as perches.

Related: [[zomboid-hordes-death-base-direction]], [[zomboid-world-tech]], [[zomboid-carrier-tech]].
