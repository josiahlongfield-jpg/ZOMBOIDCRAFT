---
name: zomboid-reach-land-terrain-tech
description: How our generated Reach land + horizon wear real Reach terrain (in playtest 2026-11-11, commits cd41b7fcb..dfb9767c0) and the matching cloud preview tools
metadata:
  type: project
  modified: 2026-11-11T01:30:35.493Z
---
Built 2026-11-11 after Josiah's whole-world quality rule ([[zomboid-reach-whole-world-quality-rule]]). In ENDLESS-NIGHT playtest, CI green (runs 209-211), NOT yet installed/seen in game (PC off).
- Game: ReachLandTerrain (client/reach) builds 5 band materials (snow, tundra, green, dry, desert) from bundled reach/land_terrain.json (texture NAMES + reals only; pictures found by name in the PC's exported level tex folders, largest size), world blend map 2048² from slope/noise, vertex light like Halo far land. Materials 0-4 solid, 5-9 the same see-through (BLEND, vertex alpha = band weight, 5x5 climate samples 32 m apart) laid over neighbours near climate edges, 10 = sea (ReachLandMesh.SEA_MATERIAL). ReachHorizon uses the same (climate sampled 250 m apart, snow by height >950 m in cold climates).
- Shader reach_level.fsh F_TRIPLANAR(512): rock weight from vertex normal (vUp), triplanar sides, MACRO anti-tiling (base texture x0.23 and x0.061 rotated modulates brightness). Terrain alpha = vColor.a when F_BLEND.
- Tiling: near-ground layers at Halo's own size (D/scale, min 3 m) if D<600, else walking REP; detail/bump keep Halo's ratio (landtex.material).
- Bands with missing pictures fall back to plain colour (log line "land band X keeps its plain colour").
- Cloud preview: scn/landtex.py + scene.py (LANDTEX=1; COLOR_0 light, _edge overlay prims) + real.html (MC macro, valpha, LEQUAL only for _edge mats; backup real_bak6.html). Renders: scn/m35_tex5, m10_t, m45_g; page artifact YbkLMF1VC3VepFf65oNWmd v3.
- Container disk is tight (~1 GB free 2026-11-11): scene dirs scn/<m>/ are ~200 MB each, delete when done.
