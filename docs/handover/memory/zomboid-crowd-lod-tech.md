---
name: zomboid-crowd-lod-tech
description: Far crowd (CrowdFx) cost 2026-11-08: view-cone cull shipped; level-3 crowd mesh SHIPPED (playtest 03871067a, packs installed on his PC 09:34Z), awaiting his profile
metadata:
  type: project
  modified: 2026-11-08T08:59:06.673Z
---

FPS thread, 2026-11-08 (new world, normal city population):
- 10:59 profile: CrowdFx/ZedBodies.submitCrowd 17% of the render thread. The crowd (records 40-192 blocks, up to population.crowdMax 3000) was drawn all round the player. Fix aa0369007 (playtest 21bafef60): view cone = screen diagonal at fov*1.15 + 10 degrees, from player.getViewVector. 18:50 profile: crowd 4%.
- What remains is body vertex volume: FastVerts.copy + StagedVertexBuffer upload ~25% of CPU, GPU objects ~7 ms main + 7 ms shadow. In the profiler, samples on FastVerts.ids line 192 are safepoint bias from the memCopy just before it, not cost in ids().
- Level-3 crowd mesh preview: /mnt/project-files/zomboid-mod/performance/crowd-lod/crowd-lod-preview.png. Thinning (zombie_lod.thin) body 0.045, clothes max(0.045, 0.2), least 24. Result 600 -> 367 tris (F jacket+trousers+Bob hair), 762 -> 563 (M hoodie+trousers). Body 0.03 / clothes 0.15 broke hands and heads. Renderer: scratchpad crowdlod/prev.py (skins idle frame 0 from survivor packs; cuts body tris under clothes by dominant bone, the way the game's mask textures do).
- Josiah OK 09:05Z, on condition: no visible difference through scopes or in game, and approached zombies must become detailed (they do: records wake into real zombies with level 0-2). Shipped: ZedBodies.submitCrowd uses level 3 only when ZDetail.zoomed(distSq) >= look.crowdLightDistance (48)^2, so scopes keep level 2. zombie_lod.py keeps existing levels 1/2 and only adds 3. Packs rebuilt (f sha256 7eeb411c..., m b2eec497...); originals backed up to additions\pack-backups-20261108 by RC.
- device_commit_files from /mnt/project-files is capped at 25 MB per file: split into 24 MB parts, commit to additions, have an RC session join them (copy /b) and check SHA-256.

Related: [[zomboid-render-distance-limit]], [[zomboid-pz-zombies-tech]], [[zomboid-vram-tech]].
