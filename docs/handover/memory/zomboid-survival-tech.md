---
name: zomboid-survival-tech
description: SURVIVAL wave 1 code map (Nutrition/Foods/Needs/Sleep, food bar held at 17, skills enum, preview test) and gotchas
metadata:
  type: project
---

Branch claude/project-thread-onqb2b (SURVIVAL). Wave 1 SHIPPED to playtest 2026-10-31 (468676e; mood moodles e917208). MoodleHud: 12px plates, names cap-aligned to plate top, bars to bottom (Josiah); moodles must keep the current look and cover every state. Wave 1 code:
- survival/Nutrition: attachment, Zomboid hunger 0..1 (<0 = Full/Stuffed), kcal bank -2200..3700, macros, weight kg.
- survival/Foods + data/zomboidcraft/survival/foods.txt: per-item hunger/kcal/carbs/fats/proteins for all 116 roster foods + vanilla (vanilla apple/carrot/potato rows use _minecraft suffix). Values approximate.
- survival/Needs: needs clock (survival.dayMinutes=60 -> hour 150 s), holds vanilla food bar at 17/sat 0 every tick (no vanilla regen/starve), own regen (regenPerHour), moodle effects as attribute modifiers, Fitness/Strength XP + wasting; hooks Stamina regen, Carry capacity, Drinks.onConsumed.
- survival/Sleep: G (CarClient chain -> client/survival/SleepClient) on bed-like blocks by id (bedOf), crouch+G floor; bed quality rest/hour; wakes on damage/move/zombie/hunger; tick sprint when all asleep (unverifiable in client gametest, ticks are test-driven).
- Skill enum: my B42 skills inserted at the TOP of the enum to avoid conflicts with VEHICLES (MECHANICS) and CLOTHING (TAILORING), which append at the end. Skill.passive(): FITNESS/STRENGTH default xp = level 5.
- SkillsPage regrouped (Passive/Agility/Combat/Firearm/Crafting/Farming/Survivalist) + wheel scroll; MoodleHud owned by SURVIVAL for wave 1 (agreed with UI).
- Preview: src/gametest/.../kit/SurvivalLookTest + .github/workflows/survivalshots.yml push frames to branch shots/survival; stitch with ffmpeg (PIL captions). Use `git archive origin/shots/survival | tar x -C dir` (rm -rf in cd'd dirs is blocked).

- Other threads' hooks and contracts: [[zomboid-survival-contracts]].
## Wave 3 character creation (2026-10-31, commit 21963310)
- survival/character/: CharacterData (reads data/zomboidcraft/character/occupations.json + traits.json; occupation "points" add to budget, trait "cost" comes off), Traits (Profile attachment "character" persistent+synced, not kept on death; has(player,id), points(), blocked(), bonuses(), apply(), xpScale + per-need scales), CharacterNet (Open/Create).
- Data is PROVISIONAL (B41 values + known B42 changes): REFERENCES asked 2026-10-31 21:20 to copy B42 MainCreationMethods.lua / scripts / EN translations into /mnt/project-files/zomboid-mod/survival/pz-b42-data/. Swap the JSON when it lands.
- B42 data LANDED 2026-11-01 02:15 (42.20.3, 9 files, README there; never commit them).
- Runs only in the planned county (Traits.county); client CharacterScreen (client/kit) opens first, SpawnScreen.show waits on CharacterScreen.pending().
- Skills.addXp now rounds fractional XP by chance (so x0.7/x1.3 matter).
- Trait hooks handed out: zombie behaviour (hearing/sight/stealth), UI (sight, cat's eyes, nutritionist, dextrous, traits section), VEHICLES (speed demon/sunday driver, burglar, amateur_mechanic), LOOT (smoker, stomach, organized, colds; I edited their SkillBookItem for reading), WEAPONS NEW (axpert). Still to do: NPC occupation picker + hobby spread.

- Trait senses (sight/hearing/noise) live only in zombie/TraitSenses.java (zombie behaviour thread, playtest 73d6780a); edit there to apply B42 numbers.
- Moodles (UI, playtest d234ba89e): Zomboid icons via PzMoodles.ICONS (key->PZ file, local config/zomboidcraft/moodles); Moodle record (key, icon, level, label, stage 1-4, pz); .as(pzKey) overrides icon. New moodle key = add an ICONS entry. Pre-mapped wave-4 keys: hot, chilly, windchill, zombie, dead, angry, uncomfortable, smell, cant_sprint, hungover, dizzy, scared, sedated, wired, happy.
- Wave 4 hook: when the dam water is restored call com.zomboidcraft.gear.HuntBoard.objectiveDone(server, "dam") (AUDIO, branch wh9qba, not on playtest yet 2026-11-06).
- Ping LOOT (cmsg_01LvQiYGyHLhwyRNcM6wP1Dp3sPQiPRKoASa3LtUBwRxqS) when marine creation merges: they scale boredom/stress by Needs.statsSpeed and halve marine boredom.
