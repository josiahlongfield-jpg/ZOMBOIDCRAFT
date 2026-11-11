---
name: zomboid-base-customisation-plan
description: Skill-gated base customisation (move furniture, knock/add walls, extend bases) plan doc 2026-10-31, picks answered, wave 1 built
metadata:
  type: project
  modified: 2026-10-31T03:20:53.159Z
---

Josiah 2026-10-31 03:14 (via LOOT thread): full base customisation (enlarge bases, remove/add walls, move every interior piece) gated by incremental Zomboid-style skills; "everything needs purpose, detailed and methodical"; also wants a Zomboid attribute-by-attribute opinion. Cars/auto shops/hoists part is with VEHICLES.

Plan doc https://claude.ai/artifact/TufN8zkGBfc8Tq7wZTBmQY (5 picks, answered below).

Ladders: Carpentry/Masonry/Metalworking/Electrical unlock tables in the doc; knock-through steps by wall type (outside brick Masonry 5, concrete 8, heavy steel 8). Work from origin/playtest (main is far behind). See [[zomboid-base-building-tech]].

ANSWERS (Josiah 2026-10-31 03:41, notes in the doc): 1 add BOTH Electrical and Plumbing skills. 2 vanilla blocks off; dirt only as soil, never an easy build material. 3 claim: others can't edit your claimed building (grows with extensions) unless you give them authority; other shelters around the map still allowed besides one primary claim base, which can be moved to another building if nobody else claimed it. 4 furniture into the inventory like Zomboid, its real weight added to the character (carry/Weights). 5 waves approved. Water: like Zomboid, every tap/outlet gives drinkable water until the water shuts off; shutoff is a setting (toggle + adjustable).
Wave 4 hooks: SURVIVAL thread (cmsg_01LvQiYGyHLhwyRNcM6wP1DpW4Z7nEV5bGssDNkM4Pzo7n) owns water shutoff timing, LOOT owns power grid shutoff; plumbing/electrical read their state, agree hooks first (coordinator 2026-10-31 03:43).
From the review thread (Josiah approved 2026-10-31 03:46): YES to the beam rule (floors sag, then fall without support). Note on section 11: Zomboid is the inspiration, but in Minecraft we can make incredible structures, as long as they cost time spent learning skills. This plan also owns B42 crafting stations (review s10), farming beds and grow lights (s9), and Base Claim, safehouses and factions as settings (s17). Standing rule: Zomboid's version of every feature unless he confirmed a change. Review doc https://claude.ai/artifact/7rkGg3jdUDUZuHf1QeEvVE
WAVE 1 BUILT (2026-10-31, branch claude/project-thread-91cq2a, IN PLAYTEST 0b4c3f4 06:21 with the break-chance lifting): building/Moving (furniture lift: size by kg 8/25, material skill, plumbed=pipe wrench+Plumbing, wired=screwdriver+Electrical, fittings must be searched/empty; server-only loot check), DigRules.Wall (WOOD/PARTITION/OUTSIDE 5/CONCRETE 8/STEEL/HEAVY_STEEL 8; outside = air beside with sky), building/Placing + mixin BlockItemPlaceMixin (vanilla blocks off, soil only ≤ ground), Skill ELECTRICAL+PLUMBING + books, SkillsPage rows 18px. BaseMoveTest + .github/workflows/basetest.yml push frames+preview.mp4 to shots/91cq2a (software renderer lags: wait 30 ticks before each screenshot). Furniture weights lightened 04:57.
LIFTING CHANGED (Josiah 2026-10-31 04:59-05:00): no skill gates on lifting. Zomboid model: with the right tool most interior items move; skill only lowers the chance it breaks. Built (bb942526d): hammer+Carpentry furniture, screwdriver+Electrical appliances/wired lights, 75% at 0 minus 7.5%/level (0 at 10); pipe wrench plumbing flat 25% (ours: -2.5%/Plumbing level); small loose things and fridges no tool, never break; broken piece drops scraps; crouch+strip tool = scrap on purpose. Figures from community guides, not game files. New preview due before playtest merge.
G menu (IN PLAYTEST 2026-10-31 08:10, merge 67eb5ac): Pick up line with kg + break chance, Scrap with strip tool, both timed via building/MoveNet; SkillUnlocks lines added in DigRules.unlocks().
WAVE 2 (wood building) started on his go 08:44: see [[zomboid-base-wave2]].
