---
name: zomboid-guns-tech
description: How ZomboidCraft guns are built (GunType, per-gun rigs, bolt guns, preview tooling) and first-person lessons like the Iris bob bypass
metadata:
  type: project
  modified: 2026-10-25T21:40:00.000Z
---

State 2026-10-25 (branch `combat`; see [[zomboid-weapons-direction]] for what Josiah wants):
- Guns are type-driven: `combat/GunType` (ASSAULT_RIFLE, PISTOL, SHOTGUN, HUNTING_RIFLE) carries item, magazine, ammo, caliber, sounds, reload ticks (magIn, charge, tacticalDone, chargeDone, jamClear), tube guns' raise/perShell, and `cycle, cycleAfterShot` for guns worked by hand (`manual()`: pump or bolt; `bolt` = magazine gun with a cycle). Stats: one `ZcConfig.GunStats` group per gun (guns, pistol, shotgun, huntingRifle). New gun: enum value, stats group, ModItems, ModSounds, Weights, HoldPoses cases, loot in tools/city/shop_loot.py + tools/cars/car_loot.py, a Dismember.bursting case if it should burst heads (characters thread owns Dismember; Dismember.pellet now takes the BodyHit, 2026-10-27).
- First person: RifleView animates every gun on the rifle's timeline. Per-gun numbers come from generated `GunRigs.java` (record GunRig; dict order in weapons.py rig_data must match the record fields), written by tools/weapons/weapons.py from each gun's `FirstPerson(...)`. Never hand-edit GunRigs/GunSlotData/HoldPoseData; rerun `python3 tools/weapons/weapons.py` (~80 s).
- Bolt guns (hunting rifle, tools/weapons/hunting_rifle.py): GunRig.boltLift (deg) about boltAxis, then back handleTravel. RifleView bolt phases (ticks, timed to the real recording): reach 3, up 4.2, pull 6.4, back 8.4, push 11.6, home 13.9, turn 16.8, down 18.2, done 21. Bolt sound fires at the lift (cycleAfterShot 7 after a shot, charge 22 in a reload); casing sound GunType.CASE_OUT=6 ticks later. GunType.fireDelay() = max(fireDelayTicks, cycleAfterShot+cycle); use it everywhere instead of fireDelayTicks.
- Suppressor: rifle_suppressor attachment on the hunting rifle's 'muzzle' slot (weapons.py); suppressed shots use HUNTING_RIFLE_SHOT_SUPPRESSED(_DISTANT).
- Gun cases and ammo cans: see [[zomboid-cases-tech]].
- Pistol FP lesson: a forearm pointing at the eye fills the screen; run it down-left. Same for the bolt hand at ADS (bigger push/turn, elbow out).
- Walking bob: Iris draws hands through its own GameRenderer.bobView call, so GameRendererMixin injects inside bobView (scales bob by RifleView.bobScale()). Mixin defaultRequire is 0, so a wrong target fails silently: confirm with the Probe workflow (.github/probe.sh, javap -c).
- Creative tab only accepts single items: never output.accept a stack of 25 (crashed the inventory).
- Preview tooling lives in the session scratchpad (not the repo): a three.js port of RifleView rendered with Playwright; rebuild from RifleView if lost.
- Rail gear, laser, torch, full auto, hip aim: see [[zomboid-rail-lights-tech]].
- Client timers keyed to level.getGameTime() break after switching worlds: guard with now >= last.
- Double-barrel, revolver, finishes, sight lesson: see [[zomboid-opening-guns-tech]].
- Guns.hitEntity (characters thread, 2026-10-27 656b874f): Shove.push replaced by Downed.flinch(target, ticks) (bullet 5, buckshot 5 + 10*knockback*pellet share); no crippleChance roll on leg hits; bullet in no_knockback tag. Keep these when editing hitEntity.
