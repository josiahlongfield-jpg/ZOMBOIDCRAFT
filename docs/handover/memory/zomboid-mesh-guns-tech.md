---
name: zomboid-mesh-guns-tech
description: How bought weapon meshes go in game (pack.py .zmesh, zomboidcraft:mesh special item model, skin.py/skins.json, outlines, melee/bolts)
metadata:
  type: project
---

Built 2026-10-29 (thread "Finish the weapons work", branch claude/project-thread-5nbhdi). See [[zomboid-wave2-guns]], [[zomboid-bought-assets-plan]].

- Source FBX -> OBJ with `assimp export in.FBX out.obj` (apt assimp-utils). pbr pack is in cm; most guns point +z, up +y (Sniper_Rifle_02: forward -y, up +z; knife long axis y).
- tools/meshguns/pack.py SPEC.json -> assets/zomboidcraft/meshes/<id>.zmesh (v3; header carries outline width). Toon-painted textures stacked into one atlas, named parts (body + pieces cut by z/y/x boxes: handle, mag, slide, optic = cut away unused). Fit modes: `fit: <gun>` (muzzle on our muzzle, bore on hip/muzzle_flash, scaled to our length, or `length` px, or real size `unit_mm`), `box: <model>` (melee, bolts: longest side + centre), `axes` [x,y,z source axes] instead of forward/up, `shift`. Scrambled like cars (CarAssets.unscramble).
- Java: MeshGuns (load/cache, submitCustomGeometry entityCutout + inverted-hull ink outline), MeshGunRenderer = special item model "zomboidcraft:mesh" {id, part (default body), offset [x,y,z] px}, registered via accessor on SpecialModelRenderers.ID_MAPPER.
- tools/meshguns/skins.json: gun -> mesh id or {mesh, keep:[block parts kept], parts:{block part: mesh id}, models:{item/weapon path: mesh id}}. skin.py rewrites items/<gun>.json + <gun>_loose_*.json (body/charging_handle->handle/slide/mag/stock; *_open gets an offset; irons dropped unless kept; melee model + _worn -> whole mesh). Already-special nodes are not re-swapped, so regen from weapons.py (kit.mesh_skin runs per gun/melee and again for every skins.json entry at the end).
- Swapped so far: assault_rifle, smg, pistol (keeps block mag), magnum (cylinder offset), shotgun (pump=handle), hunting_rifle (keeps block mag, scope cut), crossbow (+bolt mesh pbr_crossbow_bolt, ammo pbr_bolt), machete. Specs live in scratch packs/pbr_*.json (not repo; regenerate with prep.py side views).
- Checking: .github/workflows/gunshots.yml runs GunLookTest (zc.gun list) and pushes shots to branch shots/5nbhdi. 08:00 shots: all meshes looked like dark silhouettes, suspected ink hull covering paint (winding); MeshGuns.inkOn/flipped test switches added.

- 2026-10-30 (perf thread): run `python3 tools/meshguns/lod.py` after any pack.py run. It cuts meshes over 6000 triangles to 1/3 with meshoptimizer (seams kept, Josiah approved the look), and tools/meshguns/lod_done.json records what has been cut.
