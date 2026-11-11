---
name: zomboid-throwables-direction
description: ZomboidCraft throwables (Josiah picked them as next weapons work 2026-10-27 22:17): plan doc, roster, open questions, reference photo request
metadata:
  type: project
---

Josiah 2026-10-27 22:17 picked "Throwables" (over gun wave two, gun care/repair, more attachments) on the WEAPONS card.

- Plan doc (Claude Doc): https://claude.ai/artifact/8TBkissdo4THMexsw8QYxy "ZomboidCraft throwables plan". Roster of 10 (pipe bomb OUT FOR GOOD 2026-10-29, never model/name it): glass bottle, tin can, brick, firecracker string, road flare, wind-up alarm clock (Common/Scarce lures), molotov, smoke grenade, flashbang, frag grenade (Prized, military only). Hold right-click to wind up with arc preview; fuse starts at wind-up; quick-use wheel throws from belt; noise via zombie/Noise.emit.
- APPROVED 2026-10-27 22:25: all my picks (all 11, hold-to-aim arc, friendly fire with config switch, craftable homemade ones once WORLD LOOT has ingredients, build order lures -> molotov/smoke -> blasts) EXCEPT blast damage: blasts break NO blocks; they start fires across the blast radius. RULE: every fire (blast, molotov, flare) is CONTAINED: spreads only a few blocks past its origin and burns out after a set time (can gut a room/house, never the world); rain, water, extinguisher put it out; limits in /zc config.
- Reference photos requested 22:18 into refs/items/throwables/{molotov,frag-grenade,smoke-grenade,road-flare,alarm-clock,firecrackers,thrown-junk,flashbang}; WORLD LOOT thread fetches them (its container reaches Wikimedia). See [[zomboid-photo-reference-rule]].
- Crafting ingredients (fuel, rags, pipes, fertiliser) belong to the WORLD LOOT thread ([[zomboid-rolls-tech]] notes the split).
- BUILT 2026-10-27 on guns-wave1 (5e11dc1a2, CI green, NOT in playtest): combat/throwing/{Throwing,Thrown,ThrowKind,ThrowableItem,ContainedFire(+Block),Smoke}, client/throwing/{ThrownRenderer,ThrowEffects}. fireReach default 2. tin_can and alarm_clock are throwable (clock: sneak+RMB sets timer, thrown clock waits its timer). Items use vanilla stand-in models until photo-based models exist. Asked PLAYERS for Dismember.blast, AUDIO for weapon.throw.* takes (fire_burn repeats every 3 s), WORLD LOOT places items once in playtest.
- Models: tools/weapons/throwables.py (1 unit = 1.5 cm, display sizes from real cm; preview via scratchpad itemview/shot.js). Frag (M26 lemon shape, photo 04) and molotov done 2026-10-28; rest wait on refs/items/throwables/* folders. MERGE RULE (AUDIO 00:06): when guns-wave1 meets AUDIO's branch in playtest, sounds.json keeps AUDIO's 16 weapon.throw.* entries, ours only for pin_pull and ear_ring.
- PAUSED 2026-10-28 01:17 by Josiah: throwables. Resume from the zomboid-throwables-direction state (frag+molotov models done, 7 waiting on photos, then merge into playtest).
- RESUMED 2026-10-28 08:06 (Josiah). Bottle (beer bottle shape, drinks photo 07) + brick (standard US red brick) models done ea1e7fac2. Remaining 5 (smoke, flashbang, pipe bomb, flare, firecrackers) wait on refs. Thread messaging tools were down at 08:10; resume message to Josiah NOT yet sent.

- 2026-10-29 (thread "Finish the weapons work", branch claude/project-thread-5nbhdi): pipe bomb removed from code/config/lang/items (8097aa0e7, 4f4d3f771); smoke grenade, flashbang, road flare, firecracker models added (shared fuze_top helper); tin can + alarm clock keep their building models. All throwables MERGED INTO PLAYTEST 818c2d570 (sounds.json kept AUDIO's throw entries + ours pin_pull/ear_ring). Preview renderer: scratch render.py (software render of item model JSON).
