---
name: zomboid-base-wave2
description: BASES wave 2 wood building status (2026-10-31): 2a pieces, beam rule, door, art refs; NPC Builder/Electrician companion jobs owed
metadata:
  type: project
---

Josiah said "go" on wave 2 at 08:44 2026-10-31. Thread: base customisation (branch claude/project-thread-91cq2a).

2a IN PLAYTEST (Josiah "merge" 09:34; playtest e168d04, jar sent 10:00): building/WoodBlocks (wall frame, wood wall with DAMAGE/STRENGTH, window frame, door frame 2-high, plank door = vanilla DoorBlock replacing the frame, post max 3 high, floor with SAG 0-3). Beam rule: a floor needs support under it or a sturdy side within floorSpan (3) boards, else sags 3 stages over floorSagSeconds then falls. Town walls are NOT under the beam rule. Art: tools/items/gen_builds3.py, from refs/building/wood/ (REFERENCES delivered 79 photos 08:54).
Window frames are NOT boardable yet (Build.isWindow would replace the frame with a barricade).
2b BUILT (2026-10-31, branch 776af43, preview sent 10:38, awaiting merge OK): wood_stairs (Carp 6), wood_ladder (2), wood_roof shingled stair-shape (5, roofHeld rule), wood_window_shutters (3, hinges; BuildBlocks.afterBash leaves the frame). Test gotcha: the mod pack has limited slots, give hinges before stacks. 2c: reinforced wall, lockable door + padlock, double gate, watchtower, hidden door, raised beds, magazines.

OWED to NPC thread (08:59): companion Builder job (base builds from stores, chance-to-break model) and Electrician (generators, shared with LOOT). Spec /mnt/project-files/zomboid-mod/npc/companion-trades-handoff.md (NpcWork Task + NpcTrades pattern). Her job row is full at 10: move NpcPanel.DAY_SHIFT first. Do it once building jobs / generator wiring land.

Related: [[zomboid-base-customisation-plan]], [[zomboid-companion-trades-plan]].
New solid building blocks must also go in data/minecraft/tags/block/blocks_motion.json and blocks_motion_no_leaves.json (playtest server smoke test "Rain cover:" fails otherwise; fixed for 2a in playtest e168d044).
PAUSE LIFTED 21:09 ("Resume"). 2c IN PLAYTEST 8b17f79 (2026-11-06): wood_wall_reinforced (x2 blows), LockDoor LOCKED + PADLOCKS level attachment + padlock_key "lock"="door:<pos long>", wood_fence/wood_gate (in minecraft fence tags), wood_hidden_door, lookout tower (WoodBlocks.tower). wood_ladder added to climbable tag (2b shipped without it). Raised beds = existing planter; magazines not built.
