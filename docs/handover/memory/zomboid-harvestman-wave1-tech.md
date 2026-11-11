---
name: zomboid-harvestman-wave1-tech
description: Harvestman wave 1 mind games (2026-10-29) - HarvestmanTricks plans, HarvestmanNights (barricades, trophies, body return, patient nights), glimpses only the target sees
metadata:
  type: project
  modified: 2026-10-29T02:31:25.541Z
---

Built 2026-10-29 on branch claude/project-thread-j04clk and merged into playtest at 26f51b3ef. It covers picks 18-27, 64 and 67 from [[zomboid-harvestman-presence-tech]]'s picks doc.

- HarvestmanTricks (server): the plans DOOR=20, HERD=21, RETREAT=22, HORDE=23, SHADOW=24, SPLIT=25. Mind.pick calls tricks.weigh, and choose/act dispatch to it.
  - Learned values: herdBias and retreatBias (NBT hmHerd, hmRetreat).
  - The server has no player velocity, so tricks.track() keeps its own estimate from Mind.sense.
  - DOOR: it waits outside a shut door, breathing. If the door opens with a light, it flees; if it opens in the dark, it may strike.
  - HORDE: din() calls Noise.emit 40. madeIt() makes it ignore its own noise.
  - RETREAT: fakeRetreat comes from Mind.hurt, plays the same HURT sound, and goes to a ceiling to ambush.
- HarvestmanNights (server, ticked every second from the Director):
  - Patient nights are worked out without saved state from seed+night: about 1 in 4, never two in a row. Mind.startStrike refuses the target on those nights, and pick zeroes the striking plans.
  - Night is read with level.getDefaultClockTime(). getDayTime() doesn't exist in 26.3.
  - A player attachment `harvestman_owed` (copyOnDeath) holds bodyDue, withBody, trophies, lastPlank and lastGift.
  - Once a night it pries one barricade plank near the favourite() spot and drops the plank.
  - It leaves trophies on the doorstep. HarvestmanSigns.pocket() takes items lying near its body while stalking the target.
  - Killed by a Harvestman (AFTER_DEATH, DRAINED): 1-2 days later the body is laid in a "survivor" outfit with one non-stacking item from the death pile, posed by HarvestmanDens.setOut at their doorstep.
- Trails: HarvestmanSigns.trail adds gouges every ~5.5 blocks. It is set on 40% of AMBUSH starts and on 50% of trips home to the LAIR while hunting.
- Login call: the Director's loginCall plays a far CALL 45-165 s after the target joins.
- Glimpses (#64): the HarvestmanGlimpse payload goes to the target only (1 in 4 scares, when the hunter is >30 away). The client's HarvestmanPhantoms adds a client-only HarvestmanEntity (negative id, standPhantom sets turn and feet). It vanishes after being looked at for 8 ticks, at <9 blocks, or on timeout.
- Hunt target pick: the most isolated player when nobody holds a grudge.
- Sound: breathe was added to tools/audio/build_harvestman_presence.py. The script now takes names so it only rebuilds those; it previously re-encoded every ogg.
- CI: the county smoke test keeps hitting the watchdog in worldgen (Plan.depth), which is unrelated. Rerun the failed jobs.
- Boss reference views (front/side/rear/top) rendered from the preview are in /mnt/project-files/zomboid-mod/refs/creatures/harvestman/views/. Josiah is having another AI design the #75 lab boss from them.
- BOSS (#75) 2026-10-29: Josiah's AI concept art (green glow, heavier shingled legs, caged glowing belly) is in refs/creatures/harvestman/boss/. His rule: THE LEGS MUST MEET THE JOINTS (the AI side view had the thighs missing between the hip rings and the knees). Preview v1 is at characters/preview-src/boss/: boss_b.js is a patched r6_b with green MATs, thicker legs, root sleeves into the hip sockets, knee blocks, 22 crown spikes and a belly sac cage. v2: b7Pt fuller (env^0.8, W .64 T .36 Bt .30), crown spikes L .5-1.12, spine row h up to 1.41, scattered spikes doubled. Josiah APPROVED v2 ("That's better", 02:53). v3 adds six green eyes at the HarvestmanBody.EYES spots (offset 0.12, size x1.6, MAT.eye glow 5), close-up via shot_eyes.js. boss_post.js scales the pose by BOSS_K 1.4, and boss_d.js is r6_d with green emit. The page is cat r6_head r6_body r6_a boss_b r6_c boss_post r7_room boss_d. Renders are in boss/ours/. Not in game yet.
- PATIENT-NIGHT DISPLAY (Josiah 03:12): on patient nights it must NOT hide. It shows itself with its red lit as a show of dominance, and when players chase it the lights go out and it vanishes fast. Built as plan DISPLAY=26 "display" in HarvestmanTricks: showSpot is 22-40 blocks in front of them with line of sight, higher preferred; camo 0 via tricks.showing(); one CALL. Chased means d<14, 6 blocks closer than at the start, or running at it. It then sprints to HIDE, with a 75 s extra cooldown. Patient nights also zero AMBUSH and DOOR. Merged into playtest at fc1fa492f. 04:27 Josiah: when shot at while watching, it scurries away. tricks.shotAt() is called from heard (loud>=24 within 80) and from hurt, then vanish(). Merged at d80f34024.
