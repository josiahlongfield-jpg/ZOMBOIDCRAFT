---
name: zomboid-road-stories-tech
description: How LANDSCAPE's Zomboid road stories (world stories wave 1) are placed, built and hooked to LOOT, VEHICLES and the corpses system (2026-10-31)
metadata:
  type: project
  modified: 2026-10-31T09:17:31.347Z
---

World stories wave 1, part of Josiah's approved LANDSCAPE plan [[zomboid-landscape-review-plan]]. Branch claude/project-thread-d61kbn.

- **world/county/RoadStories.java** (public): Zomboid's 13 RVS stories. Every highway and county road is cut into 256-block stretches. Each stretch rolls from hash(road.seed ^ 0x5701, w, 313) with chance BASE 0.3 x rate. Rates: never 0, extremely_rare 0.2, rare 0.5, sometimes 1, often 2, very_often 3, always 1000.
- **Fits:** the road is level (no asphalt half steps), not on a bridge, at least 12 blocks clear of a jam (Jams.jammed), clear of places, junctions and the railway, and 48 or more blocks from the road's ends. About 32% of stretches fit. Seed 12345 has 329 km of road, so "always" gives about 408 stories and "rare" about 60.
- **Rendering:** scenes are laid out in the road's frame (s ahead, l right; facing AHEAD/RIGHT/BACK/LEFT, axis ALONG/ACROSS) and emitted per chunk through CountyGen.stories, after the jams. Results are cached by road seed and stretch.
- **Bodies and walkers:** "!body:outfit;limbs;pose" and "!walkers:outfit;count;radius" go through ChunkSink.vehicle. The ChunkWriter passes them to **world/gen/StoryMarks** (a chunk attachment). Within 4 chunks of a player (and entity ticking), it lays Corpses.lay(..., kept=true) and spawns EntityTypes.ZOMBIE with ZombieLook.OUTFIT. StoryMarks.register also sets RoadStories.setRate from Sandbox.value("VehicleStoryChance").
- **LOOT:** the searchables are tagged loot=story_<name>. Tables live at data/zomboidcraft/loot_table/shop/story_*.json (LOOT, 2026-10-31). Chance config keys are stories.vehicleChance, zoneChance, survivorHouseChance and annotatedMapChance. LOOT owns AnnotatedMapChance.
- **VEHICLES:** poses "@wrecked^roof" and "^side" are used for the flipped crash and the ambulance. There is no hitched trailer, so the trailer crash uses two cars (pickup + horse_trailer). mesh.py previews the poses.
- **rv_ kit:** tools/city/roadstory.py (the rs_ prefix was already taken). It has 16 props: flare, flare_out, jack (loot), lug_wrench, skid, fluid, debris, trunk, trunk_crown, sign_work, closed, closed_sign, spool, tape, case (loot), tin (loot). Photos are in refs/stories/road/<story>/.
- **Preview:** `bash /tmp/claude-0/plan/st/go.sh NAME CX CZ R` dumps the county with rate 1000. StoryList (in /tmp/claude-0/st) lists every story's spot.
- **Next waves:** zone stories (RZS: camps, BBQ, fishing trip...) read ZoneStoryChance; building stories (RB / SurvivorHouseChance); annotated map stashes.

**Way past (Josiah 2026-10-31 20:38: "make sure road stories have room to drive around them or the vehicles can be pushed"):** every story must leave a corridor a 3-wide vehicle can drive on the road plus a 1-block shoulder. RoadStories.PAST = 3 is the clear width kept in from the far edge. Changed in 5800c63d:
- the felled tree stops short of the far lane;
- county police blockades sit half on the shoulders and leave the checkpoint gap;
- the county trailer crash spills its load on the verge;
- the county pile-up keeps to one lane plus the ditch.
Check any new story with the StoryGap harness (/tmp/claude-0/st/gap; args: seed, models.json, block heights, half width 1.3-1.5, shoulder 1). Car pushing: see the update below.
UPDATE 21:30: LANDSCAPE added car pushing for Josiah (world/cars/CarPush, "Push it" in the car's G menu, playtest 6c466258). VEHICLES has been told.
