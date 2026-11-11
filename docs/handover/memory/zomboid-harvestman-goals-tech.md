---
name: zomboid-harvestman-goals-tech
description: Harvestman goals between hunts (2026-10-29): HarvestmanGoals/Territory/Choice, new plans 27-33, territory memory in Director records, encounter decisions
metadata:
  type: project
---

Josiah 2026-10-29 23:44/23:47: Harvestmen sat perched in odd places doing nothing. EVERY Harvestman must act with purpose, even if only one hunts: eat, make a territory, and decide how to deal with players who cross its path. "As thorough and detailed as you can, actively intelligent." Built on claude/project-thread-j04clk and merged into playtest at 0e0066899.

- Cause: non-hunters rolled dice between WANDER/PERCH/CEILING/HIDE to random walls 16 blocks away. Mind.pick now sends non-hunters (target null) to goals.next(). The hunter's own WANDER/PERCH weights were removed.
- HarvestmanGoals keeps one goal for minutes, re-thought every 60 s with a +0.5 stickiness for 150 s. The goals:
  - HOLD: PATROL to the stalest cell or new ground, with stops; gouges edges via signs.mark; keeps nearer its own den than others'.
  - FEED: FORAGE to the deadest cell, then ZOMBIE. Zombies are dragged home if the den is within 64 (dragHome, up to 50 s).
  - WAIT: LIE_IN_WAIT over the busiest cell for the time of day, using a ceiling, a dark corner, or a doorway fallback. It holds 90-240 s and learns cell.waits and ambushBias.
  - DEN: LAIR, then REST or TEND.
  - SHELTER: by day when more than 64 from its den, it goes somewhere covered with no sky and waits till night.
- New plan ids (PLANS names, usable with /zc harvestman do): PATROL=27 patrol, LIE_IN_WAIT=28 lie_in_wait, FORAGE=29 forage, WARN=30 warn, OBSERVE=31 observe, SHELTER=32 shelter, TEND=33 tend. Glow: LIE_IN_WAIT/FORAGE/WARN/OBSERVE are red; PATROL/SHELTER/TEND are blue.
- HarvestmanTerritory: 8-block cells track people (night/day, fading over 3 days), light, the dead, danger, waits, fed, marked, visited and a floor point. It also tracks each person met (seen, heeded, ignored, warned, hurtMe). Capped at 600 cells and 64 people. Saved as "land" in the Director Record JSON, together with "learned" (the mind's traits) and "leftAt". Time spent out of reach raises hunger and restlessness via mind.away().
- HarvestmanChoice is pure Java and tested offline (scratchpad choice/T.java, 19 scenarios). Choices: STRIKE (in the den always; otherwise starving, alone, unaware and dark, never in grace or while hunting someone else), WARN (on its core ground within 28; remembers heeded/ignored), WATCH (OBSERVE from 18-24, slips away if looked at for 2 s), AVOID (HIDE), LET_PASS (while waiting or tending). It re-decides every 8 s per player.
- Radius is 40+12*stage, shrunk to 0.55 x the distance to the nearest other den. mind.doing() adds "(goal: why; chose to X someone (why); knows N places)" for /zc harvestman info.

TEST ROOM (Josiah 00:15, playtest c4598728d): `/zc harvestman room` builds a closed deepslate hall 70 blocks above the player and teleports them in (HarvestmanTour). The hall is 31x22x7, with a partition and doorway at x20, a den beyond, pillars, a lit end and a ledge. A showcase Harvestman (mind.showcase: never strikes players, skips meet, never picked as hunter) tours 14 steps with captions: tend, patrol, lie_in_wait (doorway traffic seeded), zombie (2 zombies spawned), warn, observe, hide, shelter, stalk, ambush, feint, retreat, display, rage. `room next` skips a step; `room stop` discards the Harvestman (the room stays).

Related: [[zomboid-harvestman-game-tech]], [[zomboid-harvestman-wave1-tech]], [[zomboid-spider7-glow-hits]].

NETHER thread (2026-10-30 01:04, branch nch2s7 55d34fd0a): HarvestmanDirector got a saved `waiting` count. A lost Harvestman returns only on a breach surge (public breachSurge(level), called by world/Breach) when respawnDays > 0; otherwise population drops for good. The lair check is RECORDS.size()+waiting < population. Keep these hooks.
