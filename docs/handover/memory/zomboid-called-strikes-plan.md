---
name: zomboid-called-strikes-plan
description: Radio-operator called air strikes (W17, marines direction): preview doc, AUDIO/UI hook agreement, A1-A8 approved 12:11; built on claude/project-thread-dcbfp2 (StrikeCalls), preview sent 14:05, merged to playtest 7f9e6db5a 2026-11-06 20:06 on his go; AUDIO+UI pinged.
metadata:
  type: project
  modified: 2026-11-06T11:49:51.275Z
---

Josiah W17 (2026-11-06, world-systems brief UxHyNZNfCqGbJFzxXd67VJ): radio operator calls air strikes on a long cooldown, "but we need a system for them to be more accurate as to where they strike". Build NOTHING until he signs off AND the first operator with the M4A1 is in the game; video preview first.

Preview doc "Calling in air strikes": https://claude.ai/artifact/GDo3hnuc3f7yobpPex2FX6 (Claude Doc 7b4261e3-0b77-48ea-bd42-fa9733a1bc81). Sent 2026-11-06 11:50, awaiting calls A1-A8:
- Marks: laser (~1 block, read at release; drop -> last spot at smoke accuracy), coloured smoke (~3, offsets "20 north of my smoke"), map grid (~10). >30 block drift = called off.
- Menu: type, mark, direction (auto down street / operator locks), danger-close confirm, time on target up to 2 min.
- Strafe 48-block line centred on mark; bomb stick middle bomb on mark; Apache orbit 20 blocks, ~40 s.
- Danger close 25/60/15 (strafe/bomb/Apache); abort until 10 s before release (half cooldown); friendly fire on (W25).
- Cooldown 1 per in-game day per base, comms post cuts a third; bonus strikes from hunt tasks, max 2.
- Random strikes kept as rare background, >=80 blocks from players (A7).

Agreed hook with AUDIO (thread cmsg_01LvQiYGyHLhwyRNcM6wP1DpT4W6GGH1BJNG9xCN2FfkG2, accepted 11:49): AirStrikes.call(level, caller, StrikeType, Mark, Vec3 direction|null, delayTicks, confirmedDangerClose) -> CallResult(accepted+etaTicks | DANGER_CLOSE_UNCONFIRMED/COOLDOWN/NO_CLEAR_RUN/MARK_LOST); listener CHECKED_IN, IN_HOT, RELEASED, ABORTED, RESULT(kills, friendlies); AirStrikes.abort(caller). AUDIO owns menu + command net voice.
UI (thread cmsg_01LvQiYGyHLhwyRNcM6wP1Dp7gZePrJawC2SWgKAK2146C): hunt board strike panel reads AirStrikes.status(base) -> ready, ticksLeft, cycleTicks (comms-post adjusted, for the bar), bonus, lastResult{type, where (BlockPos), day, kills, friendlies} (UI agreed 11:50; mock PgV8knaxyt8Mqg62gkRCrd). Laser dot + smoke are aircraft thread's.
Existing system: [[zomboid-air-strikes-tech]].

Status 2026-11-06 14:05: built (world/events/StrikeCalls + AirStrikes changes, StrikeMarksFx, CI film strikefilm.yml -> shots/dcbfp2-strikes). Preview zomboid-mod/previews/called-strikes-preview.mp4 sent; Merged 20:06 (7f9e6db5a), CI green, jar sent 20:29; AUDIO + UI pinged. No in-game way to CALL a strike until AUDIO wires its radio menu to AirStrikes.call (only the laser dot + smoke work standalone).

2026-11-09: ZOMBOIDCRAFT frozen 07:51; any further strike/aircraft work for the Reach world goes to ENDLESS-NIGHT per [[endless-night-new-repo]] (wait for its first green build). AIRCRAFT inventory sent to the repo thread 07:30.

2026-11-09 07:56 Josiah: jets/heli/air strikes are NOT used in the Reach build (it gets its own Reach aircraft). This work stays in the frozen county repo; thread closed.
