---
name: zomboid-reach-sky-direction
description: Reach world sky/atmosphere (Josiah 2026-11-09): missions' ships in orbit, fog, light, battle effects in the shared world; each region keeps its mission's sky (chosen 04:06)
metadata:
  type: project
---
Josiah 2026-11-09 03:45: all campaign missions with ships in orbit and atmospheric elements (New Alexandria, Exodus, Lone Wolf etc.) must be in the world so the environment reads the same as the actual game.

Told him: each level's sky pieces (supercarrier/burning towers over New Alexandria, cruisers/dropships over Exodus, glassing + PoA launch sky, clouds, fog, light colour) are currently CUT by ReachLandMesh.clip (edges > 1.5 km). Plan: one shared sky, big ships as real objects at fixed spots visible world-wide, per-region fog/cloud/light/battle effects (Banshee flybys, flak, glassing beams).

Decision card 03:46, ANSWERED 04:06 = A (each region keeps its mission's sky and war stage, blended gradually): A each region keeps its mission's sky and war stage, blended gradually like the climate (RECOMMENDED); B one late-invasion moment everywhere; C sky escalates on a story timeline.

**Why:** he wants it to feel like the real game; flow rule [[zomboid-reach-terrain-rule]] applies to sky too.
**How to apply:** survey sky pieces per mission first (cloud background), preview before building, endless rain look still applies.

Next: sky regions map preview (which mission sky where, blend zones), then PC export of sky tags.
Sky regions map published 04:12 (build log Preview 7, #sky-regions; src scratch skymap2.py, backed up img in halo/land-build/07-sky-regions.jpg). FOG (Josiah 04:11: fog must allow supercarriers/distant cities to be seen): proposed layered fog = thick low ground fog thinning with height, sky objects/far skylines with own light haze (Halo-style), density by region. APPROVED by Josiah 04:15.
Josiah 04:13: landscape must never end suddenly leaving floating cities; land mesh covers whole 16 km, keep Halo far horizon beyond the edge. Vanilla projection far plane ~4x render distance (~500 m at his rd 8) cuts far meshes -> FAR DRAW PASS needed (like DH); Photon border fog uses render distance, check on PC. Cost answer 04:16: drawn meshes far cheaper than blocks; watch VRAM (LOD far towers/land, smaller far textures, DH off in Reach world).
FULL DETAIL (Josiah 04:20): no LOD/smaller textures for now, judge cost in game (he thinks old VRAM cost was blocks + Photon redraws). Land coarse-far LOD exists but OFF by default (-Dzomboidcraft.reach.landLod=<blocks> to enable), commit 958295040.
FAR PASS + HORIZON BUILT (2026-11-09 05:20, commits c30f97ad4 + 594e0f0d3, build log Preview 8 #horizon): ReachFarPlaneMixin (@ModifyArg on Camera.update -> setupPerspective(near, far, fov, w, h); far 48000, near 0.2 in Reach; depthFar raised before createProjectionMatrixForCulling) + Reach branch in FogRendererMixin (fog 900..22000; water/lava/blindness keep vanilla). Props -Dzomboidcraft.reach.far/near/fogStart/fogEnd. ReachHorizon: land past map edge 22 km (ranges west/south, sea north/east), zipper strip makes it watertight with the map's 8 m edge. Client mixins not checked by CI (injectors defaultRequire 0, silent if a target misses): look for log "Reach world: far plane" on PC. PC checks: Photon fog hiding far land, far z-fighting.
REGION SKIES BUILT 2026-11-09 06:05 (commit 55c307063, CI green, build log Preview 9 #region-skies): ReachSkies (assets/zomboidcraft/reach/sky_regions.zsk = skymap2 weights, 250x250 @64 m, 8 regions; stand-in colours/fog/dark per region until PC sky tags), ReachSkyState + ReachSkyMixin (EnvironmentAttributeProbe.getValue RETURN: SKY_COLOR, FOG_COLOR, SKY_LIGHT_FACTOR*(1-0.8dark), STAR_BRIGHTNESS), FogRendererMixin env fog = region fog (lifted with height). ReachLandMesh.clip drops FAR_SCENERY materials (glacier/background_mountain/distant_mountain/crater_mountain/distant_ridge/far_ridge) unless >=10% of tris touch ground.
