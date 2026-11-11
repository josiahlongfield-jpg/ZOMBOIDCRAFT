---
name: zomboid-mesh-attach-tech
description: How bought guns are laid out from their own meshes (guns.json, attachments.json, skin.py swap, kit.mesh_layout) and the attachment game side (Optic table, magnifier slot, reticles, bipod)
metadata:
  type: project
---

Built 2026-10-29 (thread "Finish the weapons work"), following [[zomboid-mesh-attachments-rule]]. Older pipeline: [[zomboid-mesh-guns-tech]].

- tools/meshguns/guns.json (14 bought guns): mesh, parts map (block part -> mesh part / null / {mesh, part}), flash point, irons ({part} or {flip offsets}), aperture, slots {at (item px), fits, default, keys (per-key mount), roll}. Drives BOTH kit.mesh_layout (slot mounts, Part boxes, GunSlotData fits/defaults) and skin.py (item model swap).
- tools/meshguns/attachments.json (25 keys): mesh, item, title, slot, zoom, hidden (scope picture instead of gun), reticle, per-gun mesh overrides (pistol-class cans = own_compact_suppressor x0.6; bipod per sniper). laser has no mesh until Josiah's laser pack arrives.
- Attachment meshes: att_* (attachment pack, logos painted out), own_* (pieces cut off the bought guns). Convention: clamp at origin, +y off the rail, muzzle -z; grips/bipods rolled 180. MeshGunRenderer has roll/pitch/scale.
- pack.py extras: groups/textures/only, keep/drop boxes (all/any corners), patches (paint out logos), mount rail|muzzle, sight points -> bounds.json.
- Java: Attachments.Slot.MAGNIFIER (last, model-data order), Optic record table (spread, zoom, reticle, overlay) keyed by model key, magnified(), aimZoom(), reticle(), hasBipod (crouched + aiming: cfg.bipodSpread/bipodRecoil). CombatHud draws scope overlay or a dot/ring/cross reticle. RifleView returns "magnified" optic when the magnifier is fitted.
- Loot: scratch att_loot_patch.py adds the 17 new items beside red_dot_sight in shop pools. Never run shop_loot.py wholesale.
- GunLookTest now shoots every bought gun plus a "-kit-" pass with other parts fitted.
- 2026-10-29 11:40: all of this is in playtest (53eef2eed, green). Fresh guns' item models fall back to their standard optic/muzzle; Carry tick calls Attachments.ensureModel. Bench button ids use SOCKETS=16 per gun (9 slots now).
- Attachment ref photos: /mnt/project-files/zomboid-mod/refs/items/weapons-attachments/ (each with sources.md).
- 2026-10-30 sights rework: each optic draws its reticle (textures/gui/sight from tools/meshguns/reticles.py; Optic record = spread, zoom, reticle size as share of screen height, overlay). Magnifier stays real-look: gun + front sight drawn, 3x zoom, reticle x3; X while aiming swings it aside (GunState.MAGNIFIER_ASIDE, key "magnifier_aside", skin.py rolls -90). GunLookTest "sight-*" shots aim by holding options.keyUse.
- 2026-10-30 10:50 Josiah wants the packs' own reticles, not redraws: holo ring, the Eotech spare chevron (in the 4x prism) and the RMR lit dot ship as scrambled sights/<key>.zsight (reticles.py PACKS_DIR; SightTextures.java unpacks). Other sights had no reticle in their packs, so they stay drawn (textures/gui/sight). Preview sheet: /mnt/project-files/zomboid-mod/previews/sights/sights_preview.png.
- 2026-10-30 20:00 Josiah REVERTED the 11:30 sight rework (hollowed tubes, holo window, magnifier eyepiece): "you've taken the actual body of the scope frames away". Now: tools/meshguns/hollow.py cuts only an eye-cone opening (OPENINGS: combat 0.25 of screen height = its crosshair tips, red dot 0.18 = inside of its back ring), edge faces subdivided so it's round; frames untouched. Combat reticle 0.45 (crosshairs 10% smaller, opening NOT re-adjusted). Magnifier mount x moved -0.145 px in guns.json (its lens axis sat right of the holo window). Holo + magnifier: real look, Josiah will send corrections. Magnum: red dot, no pistol scope (kept). NumPy 2 concatenate makes structured dtypes native-endian: .astype(REC) before tobytes. ALWAYS preview before merging.
