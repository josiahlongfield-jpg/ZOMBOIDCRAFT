---
name: zomboid-hordes-never-vanish-rule
description: Josiah 2026-11-04 RULE: a horde a player met must never disappear when they leave and return; vanilla despawn is off for walkers (MobMixin)
metadata:
  type: feedback
  modified: 2026-11-04T06:08:39.043Z
---

Josiah 2026-11-04 06:07 (FPS thread): "we dont want a horde being encountered and having it disappear the next time a player returns."

Cause found: vanilla Mob.checkDespawn deleted walkers for good (instant past 128 blocks, 1/800 per tick past 32 when idle). His 600-zombie test came back as a handful.
Fix: MobMixin.zomboidcraft$walkersStay makes removeWhenFarAway false for ZombieTypes.isWalker (playtest merge 6ed5d2db6). Do NOT use setPersistenceRequired for this: Population.fold() skips persistent zombies.

**Why:** hordes are a gameplay thing he wants to stay real; folding into chunk records (Population.fold, countBeforeDelete 300 folds extras, never deletes) is the only allowed way to take them off the field.

**How to apply:** any new walker removal path must fold to records, never discard. The real cap (autoCap 120-800) still limits how many come back live at once; the rest wait as records. Related: [[zomboid-population-tech]], [[zomboid-population-direction]].
