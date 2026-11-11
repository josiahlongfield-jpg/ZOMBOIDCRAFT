---
name: zomboid-house-stories-tech
description: Zomboid's building stories (SurvivorHouseChance / Randomized Building Chance) in county houses: where the code is, the 12 tales, how bodies/walkers and the rate are wired
metadata:
  type: project
  modified: 2026-11-06T07:21:00.000Z
---

Built 2026-10-31 by LANDSCAPE after Josiah's "Move on with other work" (21:34), commit 7a671196 on claude/project-thread-d61kbn.

- Code: world/county/Interior.java, end of class (enum Tale, stories(f) called from furnish after story(f)). One roll a house on its own dice (seedBits * 0x632BE59BD9B4E019L + 0x5704), so houses without a story generate exactly as before. Flat, BURNT and RUIN houses never get one. BOARDED: 75% SAFEHOUSE. Others: weighted pick of POKER_NIGHT, HOUSE_PARTY, FOOTBALL_NIGHT, DEAD_DRUNK, GUNMAN_IN_BATHROOM, LOCKED_BATHROOM, BEDROOM_ZED, ZOMBIES_EATING, POLICE_AT_HOUSE, PRISON_ESCAPE, STUDENT_NIGHT (Zomboid's RBSafehouse + RDS* names). Retries per floor until a scene fits; describe() prints "story <tale>".
- Rate: TALE_BASE 0.1 x RoadStories.rate(SurvivorHouseChance) (rare = 5% of houses). StoryMarks.register calls House.setStoryRate. Tools: -Dhouses.stories (default 0.5; 1000 = every house).
- Bodies: p.vehicle "!body:outfit;limbs;0"; walkers "!walkers:outfit;n;-radius". NEGATIVE radius = indoors: StoryMarks.walkers spawns at the mark's y on clear floor, not on the heightmap (roof).
- Safehouse loot bag: wf_bags_a[loot=basement_supplies]. scatter() now merges facing into a bracketed name.
- Preview: /tmp/claude-0/hs/StoryPreview.java (HousePreview + 5th arg tale; bodies drawn as bunk_bodybag stand-ins). Cut 2 view, camera 11.5,19,25 -> 11.5,4,13.5.

- Wave 2 (2026-11-06, commit 0fa5b05ef): BANDIT_RAID, BATHROOM_ZED, CORPSE_PSYCHO, SKELETON_PSYCHO, GUNSLINGER, STAG_DO, TIN_FOIL_HAT, PRISON_ESCAPE_POLICE added (methods bandits, bathroomZed, psycho(f,list,bones), gunslinger, tinFoil; stag do reuses party()). Left out ON PURPOSE: RDSBleach and RDSSuicidePact (self-harm scenes), plus HockeyPsycho and SpecificProfession. All of Zomboid's other RDS house tales are now in.

Related: [[zomboid-road-stories-tech]], [[zomboid-landscape-review-plan]], [[zomboid-houses-tech]].
