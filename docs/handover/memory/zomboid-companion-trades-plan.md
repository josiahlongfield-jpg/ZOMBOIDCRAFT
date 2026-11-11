---
name: zomboid-companion-trades-plan
description: PZ review section 18 plan (2026-10-31): companion moves to the player's Zomboid skill list, learns by doing/books/occupation, trades per new system; doc 7CzuWTzRBUNiGB6t6FsmQP answered, building
metadata:
  type: project
  modified: 2026-10-31T03:53:15.197Z
---

NPC thread owns PZ review section 18 (companion learns trades from the new systems), handed out 2026-10-31 03:48. Plan doc https://claude.ai/artifact/7CzuWTzRBUNiGB6t6FsmQP (Claude Docs id 3243b2e7-8bae-40fe-9573-cef39c6a058c) sent ~04:00, NOT built yet, awaiting Josiah's notes.

Plan: drop NpcSkills' own 6 skills (aim, first aid, cooking, carpentry, scavenging->Foraging, farming->Agriculture) for the shared player skill list/XP curve SURVIVAL is growing to B42; old saves carry levels over. Learning = Zomboid's: XP per action from one shared table, skill books read in free hours under light (x3/5/8/12/16 bands), magazines teach recipes, VHS/TV later, Zomboid occupation + traits rolled (Brave/Cowardly feed panic), same 2/4/6/8/10 unlock ladder. Trades as Work-page jobs, each waiting on its system: nurse (SURVIVAL w2), cook (w4), farmer/forager/fisher/trapper (w5), butcher/herder (w6), mechanic at hoist (VEHICLES), electrician (LOOT/BASES), builder (BASES), tailor (CLOTHING), fighter skills (w1).

Questions in the doc (blank = first option): occupation random vs picked; learn-by-watching XP share (addition) yes/no; occupation levels only vs plus random spread; reads any stored book vs only handed books.

ANSWERS (Josiah 04:06): 1 occupation = HE PICKS it on her page; 2 learn-by-watching YES (proposed 1/4 XP share); 3 asked my view, I recommended occupation levels + small +1 hobby spread off-trade; 4 BIG ASK: every bonded NPC gets an autonomous needs/wants brain, does everything the player can on her own (study, eat, heal, equip backpacks/weapons/attachments, NEVER clothing), uses only the real world loot (no own pool), asks him before taking anything important/valuable. My brain design added to the doc 04:10 (utility scoring of safety/health/needs/kit/learning/mood/job; asks via talk page, holds item a day; follow-mode pickup only nearby and safe; companions claim items; real ammo proposed with endless-ammo setting). 3 questions there ANSWERED 04:13: spread YES; brain YES (go); real ammo + endless-ammo setting YES; valuables: ALWAYS ASK each time (no always-want list), and he can check what she carries at any time. Building from 04:14. Brain wave merged to playtest 7c5d4c3a8 (release 90f487fbf) and jar sent 05:20 after video preview approval (artifact JYWYAvWhuRhUWy32xpJLY6). Next: occupation picker (he picks) + hobby spread when SURVIVAL wave 3 lands; trade jobs as each system lands.

Asked SURVIVAL to keep skills usable by a non-player holder and XP in one shared table. SURVIVAL 05:42: wave 1 in playtest 468676e (skills.Skill B42 list with passive() Fitness/Strength; Skills xp/level/addXp/loseXp; survival.Nutrition attachment; survival.Needs eat/lose/gain; survival.Sleep). Skills still Player-only; LivingEntity overloads + id lookups come with wave 3 (occupations/traits) = my trigger for the occupation picker.

TRADES 2026-10-31 ~09:00: tailor + mechanic jobs and car helping hands built in npc/NpcTrades (commit e2c5f5bd9, film CompanionTradesTest), preview approved; merged to playtest c06896a1d, released in e168d0442, jar sent ~09:55. Thread done apart from closing (needs Josiah's OK). Remaining trades handed off via /mnt/project-files/zomboid-mod/npc/companion-trades-handoff.md: SURVIVAL agreed 08:59 to build nurse (w2), occupation picker + hobby spread (w3), cook (w4), farm/forage/fish/trap (w5), butcher/herder (w6) companion sides itself; SURVIVAL built the nurse 09:15 on claude/project-thread-onqb2b f9425790 (NpcWork.medic wound branch on top, merges with wave 2); base customisation thread asked for electrician + builder. Job row on her page is full at 10 (NpcPanel.JOB 10..19).

OCCUPATION PICKER 2026-10-31 21:45: built on my branch (77ef48ce0 + b55a560f9) with SURVIVAL's wave 3 branch onqb2b merged in; NpcSkills.occupy/start (occupation + Zomboid hobby traits off-trade, 2-3 levels, passives 5, startBoost x1.75/2/2.25), page: click her past -> occupation list, 12 best skills; /zc npc occupation <id>. Preview https://claude.ai/artifact/71NefEydsnsbp261u7Wq4A sent, APPROVED by Josiah 21:52; merged to playtest 6c5b7493e after SURVIVAL wave 3 (b52a483) landed; CI green, jar sent 22:40.

Related: [[zomboid-companion-work-tech]], [[zomboid-survival-plan]], [[zomboid-pz-review]].
