# Companion trades still to build (hand-off from the NPC thread, 2026-10-31)

Josiah's confirmed answers for PZ review section 18 (companion trades):
- He picks her occupation on her page.
- Learn by watching: yes, 0.25 share (`ZcConfig.companions.watchShare`).
- Starting levels: the occupation's levels, plus a +1 hobby spread in 2 or 3 skills outside her trade.
- Every bonded NPC has a needs and wants brain (`NpcCare`). It uses only real world loot, always asks before it takes
  anything valuable, never wears clothing, and studies, eats and heals by itself. Real ammo is on, with an
  endless-ammo setting.

## What's already in the code (branch claude/project-thread-pd5a26, merged to playtest)
- `npc/NpcSkills`: her skills are keyed by the shared `skills.Skill` name and use the player's XP curve. The enum
  `NpcSkills.Skill` lists the skills her jobs use; add an entry to show a new one on her page.
  `npc.skills.practise(key, xp)` applies the world XP multiplier and her books.
- `npc/NpcWork`: home jobs (`Job` enum, `plan()` switch). A job returns a `Task(at, ticks, action, reach, done)`.
  The helpers are `fetchTask`, `depositTask`, `storeWith`, `packHas`, `takeFromPack`, `practise` and `grumble`.
  The page shows 10 job slots (`NpcPanel.JOB` 10..19), and all 10 are used now. Adding another job needs
  `NpcPanel.DAY_SHIFT` moved first.
- `npc/NpcTrades`: the pattern to copy for a new trade.
  - Tailor: patches holes using Tailoring's own odds.
  - Mechanic: tops up fluids and swaps worn universal parts on cars at home.
  - Helping hands: hooks into `Mechanics.ticks`, `start` and `finish`.
  - `NpcTrades.keeps()`: the trade tools she holds on to.

## Waiting on Build Zomboid survival systems
1. **Nurse (wave 2, treat others)**: she needs Treatment's actions with a helper `LivingEntity`, not a `ServerPlayer`.
   - Treatments: bandage, disinfect, stitch, splint, remove glass or bullet.
   - Targets: the player's 17 body parts, or her own body.
   - The helper's First Aid level sets the time and the odds.
   - Her `NpcWork.medic()` would then treat the worst wound first instead of plain healing.
2. **Occupation picker and hobby spread (wave 3)**:
   - She needs `LivingEntity` overloads or id lookups for occupations and traits.
   - Her page gets an Occupation chip row. Picking one sets her levels to that occupation's, then adds +1 to 2 or 3
     random skills outside her trade.
   - Brave or Cowardly should feed `NpcMind` panic.
   - `NpcSkills.roll()` is today's stand-in.
3. **Cook (wave 4)**: `NpcWork.cook()` should use the shared recipes, cook-level spoil and burn, and the stoves.
4. **Farmer, forager, fisher, trapper (wave 5)**:
   - Farming: Agriculture plots, plus watering and disease.
   - Foraging: zones.
   - Fishing: a rod and spots.
   - Trapping: set traps and check them.
5. **Butcher and herder (wave 6)**: feeding the chickens and cows, milking, collecting eggs, slaughtering.

## Waiting on other threads
- **Electrician** (LOOT generators and fuel, Build skill-gated base customisation wiring): fuel the generators at home,
  repair them at her Electrical level, and connect them.
- **Builder** (Build skill-gated base customisation): run base-building jobs from the stores with her Carpentry and
  Metalworking, using the same chance-to-break model.
