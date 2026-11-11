---
name: zomboid-landmark-lighting-tech
description: Landmark coloured lighting pass (Josiah 2026-11-06): light counts per landmark, airfield runway lights lit, and how Photon colours modded lights (block.properties 10064-10079)
metadata:
  type: project
  modified: 2026-11-06T07:37:08.776Z
---

**The ask (Josiah 2026-11-06 07:23 / 07:27):** touch up landmarks with more colourful lighting. "any coloured lights id want to have that colour projected onto the surroundings, they work great for the mood". Picked "Both, landmarks first".

**Measured outdoor lights** (/tmp/claude-0/la/count.py on CountyPreview dumps; mostly exterior). Darkest: army airfield 8, Camp Black Pine 12, dam 19, shipyard 20, foundry 34, Hollis 35, boardwalk 72 globes in radius 220, tank ranges 1. Ravenrock read 0 because every lamp was dead or unlit (not a dump fault). Well lit: stadium, Mercy, broadcast, civic, carrier.

**Done:**
- Commit 6062b6587. Both airports' af_light_edge/twy/thr, af_als_bar and af_papi were UNLIT blocks. They now have light 13/11/13/15/13, with lit lens textures af_lens_{white,blue,green,red}_lit (emission is set through t.emit, which becomes the LabPBR _s alpha; the mesher's _e map reads it).
- New block af_papi_red: units k<2, nearest the runway, are red.
- The army airfield gained PAPIs.
- Preview: previews/lighting/army-papi-night.png. View with photon=1&dark=1. Mesh coords: x,z = world wrapped mod 4096 into ±2048, y relative to dump y0; .vox is (y,z,x) uint16.

- Commit 7233c51e2: an OUTDOOR LAMP PASS in RoomLights. data/zomboidcraft/lamps_out.txt (off/on pairs from tools/city/outdoor_lamps.py) is lit wherever roofed() is false. roofed() means covered here AND at 3 of the 4 cells two blocks out. The pairs are NOT in lamps.txt, so the blackout leaves them on, like the street lamps. un_/hs_roof_lamp are ceiling slabs, so they are excluded. Camp.lamp always returns lit and keeps its roll.
- Commit b5616c516:
  - rd_lens_red_lit / rd_lens_green_lit (lh_prism_*_lit). PortSite breakwater lights are green if fromWest, else red; Harbour.lighthouse(…, lens) never rolls dead.
  - Dam intake towers get an aviation_light.
  - fr_runner_hot (fr_iron_hot, light 11) runs from furnace 0's tap to the skimmer.
- Commit 41f122e2b, tools/photon/photon_lights.py:
  - It sorts lit blocks by emission-weighted texture colour (saturated pixels for neon) into Photon slots and writes tools/photon/zomboidcraft_lights.txt. White and warm white stay off it.
  - `photon_lights.py PACK.zip OUT.zip` patches a pack. It is idempotent and falls back to vanilla ids on an older Photon.
  - INSTALLED 2026-11-06 08:00 via Remote Control (cloud folder access was denied): shaderpacks/photon_v1.4_zomboid.zip (605 ids) plus a copy of his .txt. Tool copy was put in the OLD OneDrive additions/photon; from 2026-11-08 any new copy goes to C:\Dev\Zomboidcraft-Desktop\additions\photon (see zomboid-pc-storage-rule.md).

- Commit 270e1b292, Ravenrock: wallFloods() puts cp_flood_lit under the coping every 25 blocks, with no rolls, and the water tower gets an aviation_light. It now has 62 lights. Preview: previews/lighting/ravenrock-walls-night.png. Blank 'barricade' in a dump palette before mesh.py.

- Commit 3d2e06ee1: new block af_light_pad (a green taxiway-style light). Camp.helipad puts 8 round the ring (no rolls). Mercy's roof deck rim lights are now af_light_pad, with the old roll kept. The Hollis stack gets an aviation_light. Preview: previews/lighting/hollis-helipad-night.png. Pack re-patched 09:32 (606 ids) through folder access, which now works for his shaderpacks folder.
- Merged to playtest at 08:10: eb749bbd2 (everything up to Camp Black Pine).

**Photon facts** (clone /tmp/claude-0/photon/src; licence allows personal edits): colour comes from shaders/block.properties ids; block.10064-10079 are the 16 modded-light slots (white..pink, colours in include/lighting/lpv/light_colors.glsl). Unlisted zomboidcraft lamps cast his amber blocklight. raw.githubusercontent is blocked from the cloud; add_repo clone works.

Related: [[photon-shader-settings]], [[zomboid-room-lights]], [[zomboid-airport-grounds-tech]].
