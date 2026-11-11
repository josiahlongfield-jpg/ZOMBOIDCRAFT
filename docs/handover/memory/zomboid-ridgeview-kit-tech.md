---
name: zomboid-ridgeview-kit-tech
description: Ridgeview Labs breach lab kits: rl_ (tools/city/lab.py, round ring + plug layouts in data/zomboidcraft/ridgeview) and sg_ red growth (tools/city/spore.py); preview yard pipeline (2026-10-29)
metadata:
  type: project
---

Built 2026-10-29 in thread "Build the Nether origin and ground zero", branch claude/project-thread-nch2s7 (commit f7655335). Preview page: https://claude.ai/artifact/CuTeHbha6ZRezp7kdsZLTV.

- rl_ kit, tools/city/lab.py (registered in photo_textures.py after ramp): lab panels/epoxy/shotcrete, the breach ring, plug, pipes/cable/duct/beacon (flicker), consoles, 3x3 specimen tanks (plinth, post, glass, body, broken, empty, cap, spill), glove box, box-built pressure suits (rl_suit + rl_suit_top), eyewash, hazard strip (axis, drawn along z), files, archive boxes, signs rl_sgn_KEY_0..1 (tile 0 = viewer's LEFT: facing north, _0 goes at the higher x).
- The ring stands in the x-y plane, 3 deep in z. ring_layout() (R_OUT 10.5, R_IN 7.75, 8 collars) picks per cell rl_ring / rl_ring{c}_{o|i}{base}_{turn}_{depth} (turned slab like the rd_ silos, rotation about z). Inner pieces in the middle/back layers use the `_p` variant (plug fills the rest). lab.py writes data/zomboidcraft/ridgeview/ring.txt and plug.txt ("dx dy block", y up) for the Java builder. Plug cracks: rl_plug_{h,v,a,b}[_glow|_surge]; surge = light 15, swap glow->surge during a breach surge.
- sg_ kit, tools/city/spore.py: sg_crust_0..2 (ONE seed so stages tile into each other), sg_vein_0..2 (wall plates), sg_hang, sg_blister, sg_mass, sg_stalk(+_top), sg_pods (light 6), sg_ash, sg_ash_drift. Place stages from a smooth noise field, never per-cell random (reads as a checkerboard).
- Sounds must be in gen_blocks GAME_SOUNDS (no SLIME; growth uses PACKED_MUD).
- Regenerating: photo_textures.py needs CC0=/mnt/project-files/zomboid-mod/cc0 (F.steel uses Metal009). For gen_blocks, copy ALL of textures/block/city into a dir with find|xargs cp (a glob is too long), add the new textures, then gen_blocks.py DIR.
- Preview yard: scratch yard.py writes a SitePreview-format dump (vox uint16 [y][z][x] + json size/palette), then mesh.py DUMP TEX OUT, view.html served on :8777, tools/county/shot.js "name=mesh=mesh&cam=ex,ey,ez,ax,ay,az,fov&dark=1&amb=0.12".
- Files (lore papers): blocks rl_doc_<id> from data/zomboidcraft/files/files.json (24 texts; 22 placed by Ridgeview.file(), survivor01/02 unplaced for county/bunkers). LabFiles (server, FOUND attachment), FilesPage + FilesClient (kit Files page, G reads).
- Breach surges: world/Breach.java (schedule every respawnDays days at night, else a flicker every 1-3 days; swaps rl_plug_*_glow <-> _surge via BreachSite marks; Tremor payload -> client/camera/BreachTremor rig 90). Population.respawnDue needs Breach.lastSurgeDay() > pop.day(). HarvestmanDirector `waiting` count: lost ones return on surges only when respawn is on. /zc breach [surge|flicker].

Related: [[zomboid-nether-origin-idea]], [[zomboid-round-kit-tech]], [[zomboid-world-tech]].
