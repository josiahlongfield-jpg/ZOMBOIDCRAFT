---
name: zomboid-companion-work-tech
description: Companion skills, base jobs, day/night routine, sleep and mood posture: classes, rules, commands and the test room (built 2026-10-30)
metadata:
  type: project
  modified: 2026-10-30T01:44:52.693Z
---

Built 2026-10-30 01:00-01:50 from Josiah's picks "2, 3 (lean heavily), 8, and 4" in [[zomboid-living-companion]]. Merged to playtest 04e5652be.

- NpcSkills: aim, first aid, cooking, carpentry, scavenging, farming, 0..10. XP for level n is 30*n^2. A new companion rolls 0-2 each, with one skill at 3-4. Practice: aim 1/shot and 3/kill; carpentry 6; farming 6/4/2/3; cooking 3+meals; scavenging 4; first aid 6 (bandage) or 3. Level 5 and 10 go in her diary.
- NpcWork: home (/zc npc home); stores are chests within 12 blocks; a 9-slot pack. Jobs: guard (door/barricade posts), repair (planks, barricade strength by carpentry: 4+=1, 8+=2), farm (planters: ripe > dry > empty), cook (lit campfire or furnace), scavenge (containers 20-64 blocks out, 3 per run), medic (players under 75%, herself under 60%), haul.
- Clock = the wristwatch: game tick 0 is 09:00, so her day uses (gameTime+3000)%24000. Do not use getDayTime, which doesn't exist in 26.3.
  - Day shift: work 06-17, free 17-21, sleep after.
  - Night shift: work 19-05, free 05-09, sleep 09-17.
- Bedtime always sends her to bed (not only when she's tired). She lies down at the bed foot +0.75 block toward the pillow. Nightmares follow a strong bad memory. Asleep she neither shoots nor flees. A zombie within 6, her alarm, or getting hurt wakes her.
- Evening goal covers free time, bedtime before she lies down, and work hours with no job. She eats from the stores, then sits with the player.
- Client: synced ACTION and MOOD ints.
  - Action clips: sleep, sit, kneel, hammer, dig, search, lookout. Mood idles: nervous, sad, tired, happy. All play only if the model has them; otherwise she plays idle, and lies down with the end of the "die" clip.
  - posture() adds a mood slump, shrug or glance on top of every clip.
- Test room (same URL, v2): https://claude.ai/artifact/YXACnbqGTpnovxxNH8bCsS. It has the bed, chest, planters, campfire, barricaded window and three containers outside, plus job, shift and skip buttons. ?sim=N runs N sim steps per frame for headless checks. Source is in project files zomboid-mod/assets/woman/tools/room/.
- Still waiting: Mixamo clips from Josiah (the names above), then step 2 (opinions, non-explicit romance, her own decisions).
