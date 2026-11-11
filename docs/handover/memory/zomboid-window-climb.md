---
name: zomboid-window-climb
description: Zombies breaking in: door/window banging and climb-through-window animations, Acts sync, WindowClimbGoal, WayInGoal, the split with the performance thread
metadata:
  type: project
  modified: 2026-10-27T11:08:08.103Z
---

**Ask (Josiah 2026-10-27 10:09):** zombies break through windows; animation of climbing through a window and a better one for beating on doors and windows. Characters thread (branch claude/project-thread-wfvzb7). Preview v22 (kits.html, artifact Mnik6r9hRkYoARfsLTdBxU) has the three buttons; source scratchpad breakin.js, copy in /mnt/project-files/zomboid-mod/characters/preview-src/.

**Split with the performance thread:** PERF owns hit points and breaking in BashGoal (glass/lit → _broken after zombies.windowHits blows → _empty after a third; doors zombies.doorHits; shared per block, reset after 5 min). Mapping `BashGoal.broken(state)` (package-private). CHARACTERS owns the animations, climbing, and going round to a way in.

**Game side (built 2026-10-27):**
- `zombie/Acts` synced attachment (not saved): kind BASH_DOOR/BASH_WINDOW/CLIMB, block, facing, tick, climb start offsets. BashGoal calls Acts.bash in start, Acts.blow after hits++, Acts.stop in stop. Client swing: tt = (now - tick)/20 + 0.55, blow lands at 0.55. Bash body is drawn shifted square onto the door/glass face (Acts.bashShift, same on server in BodyTrack); server nudges the zombie to stand there.
- `zombie/ClimbPath` (pure, MC-free): preview climb hips track; game climb runs u 1.1 (rake) to 7.9 (standing); entity kept under the hips (place()), noPhysics while climbing, travel never moves it.
- `zombie/WindowClimbGoal` priority 0: lo window block at feet+1 must be an *_empty* frame whose FACING/AXIS matches the way through; top half open or rakeable (rakes it to _empty at u 1.33); landing needs room. Claims per window. Downed knockdown/hold ignored while climbing; death clears noPhysics.
- `zombie/WayInGoal` priority 1: stuck zombie (path can't reach) paths to the nearest door/window/boards round its goal (scan cached 5 s, 4 paths a tick total).
- Client: `ZBreak` (port of breakin.js, checked joint-for-joint against it), ZMove modes BASH_DOOR 13, BASH_WIN 14, CLIMB 15; ZBodyAnimator blends facing + shift by act weight; WalkerRenderer.acting fills Facts.
- Hit shapes: BodyCaps BASH_DOOR 19, BASH_WIN 20, CLIMB 21..27 (ClimbPath.STEPS). oneLeg() is HOP..STAND1_REACH only. hitcaps run.sh now also compiles ZBreak, ClimbPath and a ZDetail stub.
- Config: zombies.climbWindows, zombies.seekWayIn (new fields, no defaults bump).

**Geometry:** floor y%4==0, window lo at floor+2 (1-block sill), hi floor+3. Opening 0.69 x 1.75, so nothing walks through. Single-block windows (window_empty AXIS with wall above) are not climbed.

Related: [[zomboid-movement-v2]], [[zomboid-zbody-tech]], [[zomboid-hitbox-tech]], [[zomboid-population-direction]].

**Status (2026-10-27 13:16, work paused by Josiah):** in playtest since 18cffd5 (jar sent). Not yet seen in game: check climb alignment and WayInGoal pathing first if he reports problems. Open question to him: drop the sledgehammer/fire-axe knockdown cripple roll (CombatEvents heavy -> Downed.crippleChance)? Unanswered.
