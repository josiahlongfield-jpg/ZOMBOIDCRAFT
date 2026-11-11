---
name: zomboid-zone-stories-tech
description: How LANDSCAPE's Zomboid zone stories (RZS camps, burials, fishing, parties, sandlot) are placed, built and looted (2026-11-06)
metadata:
  type: project
  modified: 2026-11-06T03:43:35.796Z
---

World stories wave 2, after [[zomboid-road-stories-tech]]. Commit be35c8953 on claude/project-thread-d61kbn, CI green, in playtest 6bdbd31d5.

- **world/county/ZoneStories.java** (public): 11 stories in 4 zones. FOREST (t.forest > 0.6): forest camp, forest camp eaten, hunter, trapper, burying. SHORE (lake or river within 9 straight along x/z): fishing trip. BEACH (that water is the sea): beach party. FIELD (forest < 0.3, half the field rolls skipped): BBQ, music fest, baseball, field burial. No zone near town (city > 0.15 or sprawl > 0.35).
- **Rolls:** one per 160-block cell from hash(seed ^ 0x2057, gx, gz), chance BASE 0.3 x rate (RoadStories.rate scale). 75% of tries are walked to 5-6 blocks from water found within 80. Seed 12345: about 400 on "rare", about 2900 on "always".
- **Fit:** no place within radius + SKIRT, no hideout, roads more than half + 9 + radius away, rail more than radius + 30 away, ground max - min <= 1. Water is allowed only in front, and only for the water stories, which face it (local -z).
- **Building:** a Builder in the story frame, turned q. Pieces stand on their own column's top (dryTop = floor(t.height), which equals CountyGen's top away from places and roads). dy=-1 replaces the ground (worn dirt, base paths, grave holes). Tents seek a level 2x2 within 1 block, else lie down as heaps.
- **Hooks:** emitted through CountyGen.stories with the same Out as the road stories. vegetation() skips trees within radius + 3 (ZoneStories.inside on a per-chunk near() list); camp ground gets short grass and leaf litter only. StoryMarks.register sets ZoneStories.setRate from Sandbox "ZoneStoryChance".
- **zs_ kit:** tools/city/zonestory.py, 56 blocks, registered in photo_textures after foundry.
  - Tents: zs_tent_{green,blue,orange}_{door,open,end}_{l,r} plus _down. 2x2, door row north, _l is west when facing north, 45-degree panels, cutout gable textures.
  - Other pieces: zs_lean(_mid), hide_rack, hoop, trap, snare, deer_a/b (b is the head end), gun_case, rod(_down), tackle, stringer, spoil(_low), grave_a/b (placed at dy -1), cans, ruck_{forest,hunter,trapper,burying}, tote, cooler, fest_pack, kitbag, base, home, rubber, bat, glove, bench, fence(_top), log.
- **Loot:** tags are story_{forest,hunter,trapper,burying}_camp, story_fishing_trip, story_beach_party, story_bbq_party, story_music_fest and story_baseball. The 9 tables were written by LANDSCAPE in LOOT's story_loot.py format, with ids checked against playtest's known(). They are not in LOOT's STORIES dict.
- **Preview:**
  - /tmp/claude-0/zs/go.sh NAME X Z R (env CLIP cuts the canopy, Y0, H) and shots.py.
  - ZPick/ZShow/ZList in /tmp/claude-0/zs/src list spots and pieces (-Dzonestories.rate=1000).
  - Sheet: previews/zone-stories.jpg.
- **Next waves:** building stories (RB, SurvivorHouseChance) and annotated map stashes.
