---
name: zomboid-landscape-pause-after-list
description: LANDSCAPE pause history: 2026-10-29 pause (lifted 18:59) and the 2026-10-31 10:22 "stop development for now" pause with what was left unmerged
metadata:
  type: project
  modified: 2026-10-29T12:25:06.054Z
---

On 2026-10-29 at 12:20, Josiah told the LANDSCAPE thread: "finish all the items you have listed above to the fullest extent and then pause development upon completion of them".

The list covered:
- Town leftovers: boxcars, depot, street furniture.
- Landmark and suburb leftovers: mill, cellars, military site.
- Big ship hulls.
- Park lamps and the gazebo.
- Downed power poles.
- Ladders.

**Why:** He wants the consistent-detail pass closed out, then usage freed up.

**How to apply:** After the last item ships in a playtest jar, stop LANDSCAPE work. Don't start new items. Don't resolve the thread ([[zomboid-no-self-resolve]]).

**Lifted 2026-10-29 18:59:** Josiah: "Resume work and begin the airport". LANDSCAPE is active again on Grayhaven International Airport ([[zomboid-airport-tech]]).

**Paused again 2026-10-31 10:22:** Josiah: "stop development for now". State at the stop:
- Road stories wave 1, house alarms and the yard hum/arc were merged to playtest (48bf4147). CI wasn't confirmed.
- Substation repair (his pick "fix parts round the yard", commit 7ee9b42b) is on branch claude/project-thread-d61kbn only and is NOT in playtest.
- Queued for when he resumes: world stories wave 2, WEAPONS' reloading_bench placement, LOOT's crusader_kit crate + museum_armour placements.

**Resumed 2026-10-31 20:27:** Josiah: "You may resume". LANDSCAPE picks up the queued list above.

**2026-11-06 02:57:** Josiah: "Just keep pushing through all the work, I won't be at my pc for a while". Keep building back to back without asking. Harbour fort waits on PC photos (pc_spec45); meanwhile zone stories (RZS) from refs/stories/zone.

**2026-11-06 04:19:** Josiah: "Anything that requires more photos just push it aside for now". Harbour fort parked. Next (photo-free): county roof pass (Bldg.gable vanilla stairs -> house roof kit, 14 builders), leftover vanilla blocks in structures (froglights, wool, deepslate tile, copper), farm tractor/wagon remake.
**2026-11-06 04:47:** vanilla cleanup done (e0083040d, playtest 17b410028). The roof pass was already done via Bldg.kitRoof, and the tractor and wagon were already .zcar models. Next: Zomboid's non-house building stories (RB bar/cafe/clinic/gun store siege/office/school/burnt/looted/trashed) for shops and civic buildings.
