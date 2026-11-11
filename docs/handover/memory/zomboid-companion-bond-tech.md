---
name: zomboid-companion-bond-tech
description: Companion step 2: NpcBond opinions (trust/affection/attraction), flirty/romantic/jealous moods, sneak+G talk panel, and the no-sexual-content line (2026-10-30)
metadata:
  type: project
  modified: 2026-10-30T02:51:22.817Z
---

2026-10-30 02:40. Josiah asked for flirty, romantic and "horny" moods. The horny mood was DECLINED for the second time (see [[zomboid-living-companion]]): no sexual content for the companion, ever. Romance stops at flirting, teasing, hugs, holding hands, kissing and jealousy. If he asks again, give the same short answer and don't lecture.

Built (branch claude/project-thread-pd5a26, commit 47cbf0b + flower fix 8acbaa1; merged to playtest f366f1bc7, jar sent 2026-10-30 03:15):
- NpcBond (saved as "npcBond"; synced as entity data BOND bits):
  - Bits: trust 0-6, affection 7-13, attraction 14-20; flags together 21, holding 22, flirty 23, romantic 24, jealous 25.
  - Feelings are about one player, `with`, set from born(by) or her leader.
- What raises or lowers them:
  - Raise: time within 6 blocks, shared meals, a zombie killed near her by him (Npcs AFTER_DEATH hook, SAVED line), gifts, compliments.
  - Lower: loneliness above 80, being hurt by him, pushing after a no (3 pushes = a diary memory).
- Moods (NpcEntity.mood() → NpcBond.mood) override only steady/good spirits/lonely/low:
  - Jealous: another NpcEntity within 3 of him for 20 s.
  - Romantic: together, close, and a tender act within 3 min, or holding hands, or sitting together.
  - Flirty: attraction 55+, or 30+ after a recent flirt.
- Sneak + G on her opens client TalkScreen: compliment, flirt, give what's in your hand, hug, hold hand, kiss. Sends NpcNet.Talk(npc, act).
- Gates:
  - Kiss: needs together, or affection 70+, attraction 65+ and trust 50+ (that first kiss makes them together).
  - Hold hands: together, or affection 60+ and attraction 45+.
  - Hug: affection 45+, or she's scared and trust is 40+.
- Holding hands: she follows 0.7 block to his right. She lets go when a zombie is within 12, he's more than 4 away, or she isn't following.
- Pose actions HUG=9 and KISS=10 (NpcWork ints). She faces him; client clips "hug"/"kiss"/"flirty" play if present, otherwise posture fakes them (tilt/lean, lids shut for a kiss).
- Breakup when affection drops below 35 while together.
- /zc npc bond [trust|affection|attraction <0-100> | together | apart].
- ItemTags.FLOWERS does not exist in 26.3. Flowers are found as BlockItem of FlowerBlock/TallFlowerBlock.
- Test room v3 has the "Her and you" panel: https://claude.ai/artifact/YXACnbqGTpnovxxNH8bCsS

2026-10-30 22:30 (Josiah's picks; merged to playtest c97f2c26c):
- Friendly start: config companions.startTrust 60, startAffection 45, startAttraction 35, startSpread 10. LOYAL and KIND tilt trust and affection.
- Bonds saved before this (6 fields; the new save adds a 7th "2") are lifted to at least start minus spread.
- Gains scale by (1.6 - value/100): faster when low. Losses are unchanged.
- New gates: hug at affection 35; hands at affection 50 and attraction 35; first kiss at affection 60, attraction 55 and trust 45.
- "Calm her down" is NpcNet.CALM = 51 (TalkScreen row 8), handled by NpcBond.calm. Fear drops by 20 + trust*0.4, and NpcMind.steadied = 60 s scales the fear target by 0.6.
- Panic (NpcBrain.Flee rewrite):
  - Armed, she never flees. She only steps back from a zombie within 3 and fights.
  - Unarmed, she runs 1.8 blocks behind her player (if he's within 24), on his side away from the zombie, and stays there.
  - Otherwise she picks one scored escape (roof +6, near home), with a 40-tick re-pick cooldown.
  - She jogs (1.0), and sprints (1.6, capped by setSpeed) only with a zombie within 4.
  - When panicking, she cowers 3-5 s: NpcWork.COWER = 14, clip "cower", else "kneel".
- NpcBrain.Attend (priority 1): she holds still and faces the player while talking or while her page is open (npc.attend, 30 ticks), within 4 blocks, with no zombie within 5. It skips for 10 s after a page order (npc.ordered).
