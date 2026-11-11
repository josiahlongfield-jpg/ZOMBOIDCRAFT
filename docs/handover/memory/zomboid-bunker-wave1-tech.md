---
name: zomboid-bunker-wave1-tech
description: How ZomboidCraft's civil-defence shelters, forest hatches (hideout layer), marked bunker maps and /zc bunker work (Shelter.java, HatchSite.java, Bunkers.java, PaperMaps.readMarked)
metadata:
  type: project
  modified: 2026-10-26T07:20:51.649Z
---

Built 2026-10-26 in the landscape thread (branch claude/project-thread-d61kbn), merged to playtest ac54502. Direction: [[zomboid-bunkers-direction]]. Core: [[zomboid-bunker-kit-tech]].

**Shelter** `world/county/Shelter.java`: `dig(host, rng, state, interior, floor, depth, area)` tries 4 turns x 4 corners of a host hall; boiler room under the hall, long stair (rise = floor-5-depth steps), landing, SHELTER bunker. `clear()` refuses spots where anything is already built (old city subway). `plate()` puts the yellow shelter plate on the front wall. TownSite registers hosts (post offices, town hall, courthouse; 55% county_seat, else 30%); DowntownSite uses CityCivic.courthouse (old_city only, 75%, depth -28).

**Forest hatch**: PlaceType.HIDEOUT on its own layer: `County.hideout(cx,cz)`/`hideoutsNear`, 5% of cells, forest>0.62, gentle ground, >=120 from places, >90 from roads; footprint hatch +-56 (HIDE_REACH), level = floor(height) at the hatch, nothing levelled. CountyGen copies hideouts after places; trees skip CLEARING+2 (9) and ground cover CLEARING (7) of the hatch. `HatchSite`: bunker frame shaft at (-1,-5), antechamber x -1..2 z -5..-1, floor 13-27 deep (11 under the lowest ground over it), clues via a turned Placer round the hatch. New kit: bunk_stump, bunk_log (axis), bunk_logpile, bunk_pole. bunk_hatch is climbable (so you step out of any ladder shaft). bunker.py now keeps kit order in blocks.json.

**Marks**: every bunker marks "bunker STATE KIND" (kind tower|ship|shelter|forest) where you get in, then "bunker door". `Bunkers.candidates/in` find them; `/zc bunker [kind] [door]`, `/zc bunkermap`, `/zc goto hideout`.

**Marked map** (item bunker_map, `BunkerMapItem.TARGET` string component "kind,state,x,z,ringX,ringZ,r"): first read picks a random bunker within 3000 (builds up to 8 candidate places async), ring r 40 town / 75 forest, offset up to 0.6r; MapPalette.MARK (15, red) inks over seen ground. Drops in Loot.java: soldier-outfit zombies 1.5%, any shop/* container inside a MILITARY place or naval PORT 3%.

**Previews**: CountyPreview dump + `mesh.py --near=X,Y,Z --radius 90` (without --near everything far is LOD and interiors vanish); view x,z = world, y = dump index. Seed 12345 hideout at (-109, 1193), level 99. Sheets: /mnt/project-files/zomboid-mod/previews/bunkers-wave1-*.jpg.

Crowbar door (2026-10-27): barricade/CrowbarItem (ModItems.CROWBAR), right-click any bunk_blast_e/w{row} block, TimedActions 200 ticks with bangs + Noise every 30 ticks, then the 2x3 leaf swings in against the decon wall (facing ccw) if the cells are clear, else it comes away. LOOTED bunkers are built already open (Bunker.pried(), called after the scatter). Bunkers.in finds a place's door mark by scanning to the next "bunker door".
