---
name: zomboid-dam-tech
description: Black Pine Dam (NW zone site 4) build tech: raised reservoir water model, natural place, Dam.java frame, dm_ kit, real-terrain preview pipeline
metadata:
  type: project
  modified: 2026-10-30T09:14:45.516Z
---

Built 2026-10-30 after Josiah picked "Raise the reservoir behind it" (08:31). Part of [[zomboid-nw-military-plan]].

- Water model: Terrain.WATER 62 everywhere except the reservoir: Terrain.RESERVOIR = WATER + 24 (86), per column via terrain.water(x,z) / terrain.wet(x,z,h). Region = plan lake "reservoir" + an arm capsule to the dam, clipped at the dam's upstream face z = damZ - FACE (FACE 8). Terrain.dammed() shapes the lake floor, rim (RIM 280) and the gorge below. Every water consumer (CountyGen, CountyChunkGenerator, CountyBiomeSource, PaperMaps, County fit/hatch/road bridges) uses the per-column level.
- Place.natural: land left as it lies (County.ground skips its levelling). Layout special-cases spot "dam" -> Dam.place (variant "dam", level = RESERVOIR + 4 = 90, crest road at y 0); Builders dispatches LANDMARK "dam" to new Dam(p, county, out) because it reads county.ground/terrain.water per column.
- Voxels.MIN_Y lowered -32 -> -48 for the dam's foundations.
- Dam.java frame: spot at (0,0), x -150..149, z -63..57. Upstream face z -7, crest z -7..3, downstream face 0.8 slope (face(y)). Spillway piers x 8/16/24/32/40, ogee y -9, gates in z -6, gantry y 5, basin floor -35, sill z 46. Units/intake towers x -52, -32 (towers z -24). Powerhouse x -66..-18, z 30..48, floor -27, roof -12; yard cut x -74..-14, z 22..52; stair shaft in the bank west. Gallery floor -20 at z 1..3; lift shaft x -44, z 5 (stops -27, -20, 0). Overlook + keeper's house west, checkpoint east (the county road arrives at the east gate).
- Kit: tools/city/dam.py (dm_conc*, parapet, gate, hoist, rack, penstock, valve, gauge, floats, signs dm_sgn_*, dm_name_0..7, sprays). Registered in photo_textures after prison.
- Preview: /tmp/claude-0/plan/dam/go.sh (real county chunks via tools/county/CountyPreview, origin rewritten to 0 because world coords overflow the mesher; local = frame + (168, 50, 178)). Check water leaks by scanning the dump for water next to air: 0 at ship.
- Preview sheet: /mnt/project-files/zomboid-mod/black-pine-dam/black-pine-dam-preview.jpg
