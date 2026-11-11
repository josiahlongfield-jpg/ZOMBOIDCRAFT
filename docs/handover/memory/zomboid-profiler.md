---
name: zomboid-profiler
description: /zc profile sampling profiler + GPU timers, and what each FPS profile of Josiah's city showed (root causes, fixes)
metadata:
  type: project
  modified: 2026-11-01T09:33:31.383Z
---

`/zc profile [seconds]` (zombie/Profiler.java, PerfCommand) samples Render/Server/Worker/Sodium threads every 3 ms and writes zomboidcraft-profile-<time>.txt to the game dir. Since 2026-11-01 it also prints "Graphics card time per frame" from client/perf/GpuTimer (GL timestamp queries on GameRenderer.render/renderLevel, LevelRenderer.executeSolid, Iris renderShadows/beginTranslucents/finalizeLevelRendering, Sodium DefaultChunkRenderer.render). Related: [[zomboid-performance-tech]].

History (2026-10-30..11-01):
- Our custom geometry cut 60% -> ~9%. Lamp flicker off by default (performance.flickerLamps, /zc flicker). MeshGuns quad pairing, no ink in HUD PiP, far lifts/cars cast no shadow.
- Upload stall ROOT CAUSE: 26.3 GlDevice turns GL_ARB_buffer_storage off on NVIDIA -> Sodium fallback glNamedBufferSubData. GlBufferStorageMixin (@ModifyVariable on BufferStorage.create's avoid flag) fixes it; config performance.nvidiaBufferStorage (restart). Verified 19:21 profile + F3 shows "TO: Mapped". SodiumUploadMixin caps fallback to 4 MB/frame; detects fallback by toString "Fallback".
- Harvestman off (performance.harvestman=false, Josiah "get rid of it for now").
- 2026-11-01 09:2x (his clock 19:2x): still ~9 FPS in the city with NO zombies, even in the Esc menu. F3: GPU 99%, 2560x1369, RTX 4070, Photon custom. Shaders OFF = ~60 FPS, Photon ON = ~9. So it's Photon's GPU cost on this city (suspects: lit windows/colored lights, glass, shadow pass over tall buildings, wet-rain reflections). GPU timers built (playtest 00b2d04fd). 20:32 timed profile: frame 262 ms GPU, shadow pass 171 (blocks 67, OBJECTS 104), main solid 31, Photon deferred/composite ~9. Fixes: ShadowCasterMixin (EntityRenderer.shouldRender false in shadow pass past performance.shadowObjectDistance=24); zombie thread deleted ZSprites (picture sheets built on render thread = 100-250 ms freezes). Playtest fc3b31e0e. Josiah 10:47: stop and wait for his tests on PC; asked for crash-reports file (crash at shutdown) + new profile.

- 2026-11-02 16:35 profile (suburb, low pop, jar fc3b31e0e): GPU 50 ms/frame (was 262): shadow terrain 23.4, main terrain 13.8, deferred 4.1, composite 1.7, objects ~1.3 each pass. CPU: NpcSkinDraw 18% + CrowdFx 5% + Iris fillExtendedData 13% (zombie body verts). Next: halve terrain cost (shadow pass draws hidden interiors/trees). Asked for his video + Photon shadow settings.

TARGET (Josiah 2026-11-02 04:19): a smooth, stable ~40 FPS in the city with his full Photon look; he'd be "more than happy" with that. It must hold with FAR MORE than 200 zombies attacking at once inside big landmarks (04:20). Last horde profile: server 41% busy, Zombie.tick 70% of it (pathfinding + block collision); likely next levers are shared flow-field pathing and cheap far-zombie ticking.

Rules: never suggest changing Photon's Profile or turning off Colored Lights; keep his look, change the city/mod instead.

- 2026-11-02 card-memory work (atlases, padding, DH, shared looks): [[zomboid-vram-tech]].
