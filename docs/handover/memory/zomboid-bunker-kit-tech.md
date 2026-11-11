---
name: zomboid-bunker-kit-tech
description: How the ZomboidCraft bunker block kit is built and previewed (tools/city/bunker.py, trapdoor + climbable support, catalogue preview loop)
metadata:
  type: project
  modified: 2026-10-26T00:05:00.000Z
---

(2026-10-25, landscape thread) The bunker kit is 132 custom blocks / 144 textures, committed acd13fb on branch claude/project-thread-d61kbn, merged to playtest 66e8b38.

- Code: tools/city/bunker.py (prefixes bunk_, ship_, container_inside/floor/welded). textures() makes painters, blocks() makes specs, main() rewrites them into tools/city/blocks.json. Registered in photo_textures.py after basement.
- Pipeline: photo_textures.py CC0DIR OUTDIR names -> copy into the full texture dir -> bunker.py -> gen_blocks.py FULLDIR (writes Java specs, assets, loot tables, tags).
- gen_blocks now supports `"trapdoor": "steel"|...` (CityBlocks kind TRAPDOOR = 4, TrapDoorBlock; COPPER set for metal sound) and `"climb": true` (writes data/minecraft/tags/block/climbable.json; ladder and bunk_ladder).
- Thin wall decals (stencils) sit at z 15.75, not 15.9: 15.9 z-fought in the preview.
- stencil() auto-fits lettering via fit_text (squeezes to 62% width, then shrinks), so sign sizes can be generous.
- Avoid photo materials: rusty_metal alone reads as wood planks (blend rusty_painted_metal over painted steel instead); F.stain blobs repeat per block and look like spots.
- Preview loop: a catalogue layout (one bay per piece set, wall behind) meshed with mesh.py and shot one bay per page at 480x360 is fast (~1 s per shot) and shows detail far better than open showroom rooms, which read as empty. Sheet sent: /mnt/project-files/zomboid-mod/previews/bunker-kit-pieces.jpg.
- Containers use existing loot tables (basement_*, storage, office, kitchen, home_*) until bunker tables exist.

- Shared core (commit 888a86e, not yet in the world or playtest): src/main/java/com/zomboidcraft/world/county/Bunker.java, Kind TOWER/SHIP/SHELTER/FOREST, State SEALED/LOOTED. Blast door at x0..1 in wall z=0 facing north, floor y0, ceiling y4; decon, corridor with side rooms, storage hall, caged armoury. bounds() gives {x0,z0,x1,z1}. Preview: tools/county/BunkerPreview.java (OUT seed cut KIND[:STATE]...), sheet /mnt/project-files/zomboid-mod/previews/bunker-core.jpg sent 23:15.

- Weathering + breached state (commit 283eba5, branch only): Bunker.State SEALED/LOOTED/BREACHED; worn wall/floor/ceiling variants by noise; painters use patches()/patch_mask() (tileable fbm) because centred blobs repeat per block and read as spots. Flicker.java blocks (spec "flicker": {"light", "on"}; CityBlocks FLICKER flag 8; LIT property; untested on 26.3 until CI passes). Dying lamps are light 7-9 so rooms get pools of light, not floods.
- bunker.py PREFIXES must not include whole "garage_" (it deleted the garage doors); it owns "garage_ramp" only.
- mesh.py bakes Minecraft block light (warm/red/glow channels in the vertex AO bytes, mesh.json "baked"); view.html &dark=1 uses it, so dark previews match the game. Sheet sent: previews/bunker-weathering.jpg.

Related: [[zomboid-bunkers-direction]], [[zomboid-houses-tech]], [[zomboid-world-tech]].
