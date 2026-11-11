---
name: zomboid-survival-plan
description: SURVIVAL thread plan (2026-10-31): Zomboid needs, 17-part health, cooking/farming/fishing/animals, occupations+traits, water shutoff; split with LOOT; awaiting go
metadata:
  type: project
  modified: 2026-10-31T03:43:16.520Z
---

Thread "Build Zomboid survival systems" (SURVIVAL) started 2026-10-31. Plan doc https://claude.ai/artifact/T8T4SFwD6sEXFCAwgcEP8F sent; approved order 03:46, building wave 1.

Waves: 1 food (calories/weight replace vanilla food bar, Zomboid hunger/thirst/tired moodles, vanilla potion moodles removed, sleep + bed quality, Fitness/Strength); 2 17-part health + treatment chain + crutches + treating others + panic; 3 occupations/traits (take list + costs from B42 game data, fan wikis disagree 25 vs 26 jobs); 4 cooking + water shutoff/tainted water; 5 farming/foraging/fishing/trapping; 6 B42 animals + body temperature setting.

Split agreed with LOOT 2026-10-31: LOOT keeps medicine item effects (survival/Ailments, Medicine, MedSpecs, MedicalItem, Downs; branch claude/project-thread-jd27hs; per-part keys use Body.Region ordinal), downed+revive, stress/boredom/unhappiness/comfort, power grid, generators, fuel; will give district-power query + mood API. SURVIVAL owns panic, body model (Body/Treatment/SurvivalStats untouched by LOOT), crutches, treating others, water shutoff. Ten new skills mine (Fitness, Strength, Cooking, Agriculture, Foraging, Fishing, Trapping, Butchering, Animal Care, Tracking); Mechanics=VEHICLES, Electrical/crafting=BASES, Tailoring=CLOTHING.

ANSWERS (Josiah 2026-10-31 03:46, notes in doc s15): body temperature ON by default with a toggle setting; sleep needed = a toggle setting (default needed); keep the start-at-workplace extra; wave order OK (= go, wave 1 started); Q4 (04:36): the dam CAN bring water back but it must not be easy. Water settings: toggle = water always on; if off, adjustable time until it shuts off.

Related: [[zomboid-pz-review]], [[zomboid-match-zomboid-rule]].

MARINE DIRECTION (Josiah 2026-11-06 11:22, brief UxHyNZNfCqGbJFzxXd67VJ W1-W5): squad of marines purging the world. Player creation becomes marine base (Fit7 Str7 Aim4 Rel4 Sneak2 Maint2 FA1 Blade2) + one of 8 specialist roles (B42 job boosts, larger value when two jobs mix) + 2 positive traits, no negatives. Needs Slow, near panic-proof, slow boredom, fatal bite. NPC survivors keep civilian occupations (keep occupations.json). Appearance = operator picker in the Tarkov thread. Preview canvas BNZJoFL4JYaNMMr3iZus57. All 4 questions yes (Hunter trait for marksman, engineer +Metalworker 4, cap 10, RTO overlap ok). BUILT + in playtest df396bb 2026-11-06 20:52: marine.json, MarineData, Traits.marineLevels/marinePicks/occupationName, client/kit/MarineScreen, Open.marine, zc.marines toggle, StatsDecrease choice survival.statsDecreaseSpeed default slow (Needs.statsSpeed), marine panic 0.25 (brave 0.125, desensitized 0.05). LOOT pinged for boredom.

PAUSED 2026-11-08 (purge re-steer, [[zomboid-purge-direction]] P20): survival keeps only health, injuries, bleeding, stamina. Needs, traits, skills, marine creation, cooking/farming/water (wave 4+) are retired: pause, remove nothing yet. Wave 4 not started.
