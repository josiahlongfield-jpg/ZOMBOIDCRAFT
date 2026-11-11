---
name: zomboid-pz-review
description: Front-to-back Project Zomboid review doc (2026-10-31): gaps, verdicts, owners, base/vehicle skill ladders, open calls
metadata:
  type: project
  modified: 2026-10-31T03:24:59.953Z
---

Doc: https://claude.ai/artifact/7rkGg3jdUDUZuHf1QeEvVE ("Project Zomboid, front to back"), thread "Review Project Zomboid systems", sent 2026-10-31.

Checked against playtest 2026-10-31: biggest gaps are character creation (no occupations/traits), hunger still vanilla food bar, vanilla potion moodles in MoodleHud, no temperature, no power/water shutoff, no cooking/farming chain, cars have fuel + battery bool + keys + alarms but NO parts, no auto shop building (Shops.Type has none; GasStation exists).

Proposals in it: new skills Mechanics, Electrical, Plumbing, Tailoring, Cooking, Farming, Body; unlocks every 2 levels; base ladder over Carpentry/Masonry/Metalworking/Electrical/Plumbing (furniture classes light/heavy/fitted, structural beam rule, extensions at Carpentry 8 / Masonry 7, Base Claim, edit mode); car parts directory (~16 part types, class-locked grades, manuals), working two/four-post lifts, jack + stands, engine crane, shop tools each with one job; auto shops scaled hamlet -> city + truck depot/scrapyard; district-by-district grid failure restorable via substations and Harbor Point.

Open calls for Josiah: new SURVIVAL thread?; temperature off-by-default or none; power failure style; part class lock; beam rule (sag-and-fall vs block); farm animals.

Related: [[zomboid-hordes-death-base-direction]], [[zomboid-world-loot-direction]], [[zomboid-cars-plan]], [[zomboid-base-building-tech]].

2026-10-31 03:25: the base thread's plan https://claude.ai/artifact/TufN8zkGBfc8Tq7wZTBmQY is the one Josiah picks from for bases (plumbing is a wrench job there, not a skill; hoist frame Metalworking 7, hoist circuit Electrical 3). It will merge this doc's beam rule, furniture classes and edit mode if he agrees, so section 11 here is input only.
