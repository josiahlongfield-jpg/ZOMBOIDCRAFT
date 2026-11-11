---
name: tarkov-icons-scope-tech
description: Tarkov gun slot icons (TarkovGunIcon), part icon base, and the scope-lens HUD bug fixed 2026-11-08
metadata:
  type: project
  modified: 2026-11-08T01:16:34.605Z
---

Fixed 2026-11-08 (commit 16fd7e7a0, branch claude/project-thread-5z3m2y) after Josiah's 00:58 report: "scopes arent working", hotbar showed the old guns, and icons not sized to the slot borders.

- Scope bug: CombatHud.scope returned before ScopeView.draw for every Tarkov gun (no sight key, Attachments.scoped false), so magnified Tarkov sights showed a tube with no zoom (with Photon on, crop mode A never zooms the FOV). ScopeView.active() now runs first.
- Hotbar: a Tarkov gun is an old GunType item with the LocalGuns.ID component, so its slot art was the old Brita/ours model. All 19 gun item JSONs now start with condition "zomboidcraft:tarkov_gun" (GUI, ground, fixed only) -> special "zomboidcraft:tarkov_gun" = client/operator/TarkovGunIcon: the fp model's "held" pose, gun pieces + bench parts, side on (from TarkovRig.mainEye; fallback weapon_ bone), muzzle right, fills 94%.
- Part icons: tarkov_part.json used the foregrip base (gui scale 4), so parts drew 4x past the slot. The new base is models/item/tarkov_icon.json (gui scale 1), and TarkovPartRenderer FILL is 0.86.
- KitDraw.tile sizes a Tarkov gun to its grid cells' width using TarkovGunIcon.aspect.
- tools/meshguns/skin.py rewrites gun item JSONs; its swap() recurses through the new top-level condition, so the condition survives a rerun. Still check after any rerun.

Not yet confirmed in game by Josiah; no local compile (CI only). Related: [[tarkov-weapon-batch3]], [[tarkov-sight-switch-tech]], [[zomboid-scope-zoom-direction]].

Batch 3 local files (gun files, zsk models, 293 parts, all sounds) were fully copied to MEDIEVAL GLOOM (1) on 2026-11-08 at 06:50 UTC.
- 2026-11-08 07:10: Josiah saw every scope lens covered by the gun (with Photon, the lens is the zoomed middle of the same frame, and the ACOG has an opaque backLens mesh). Fix 89b8ab9a7, in playtest a6ca193: once aim is at least 0.92, the looked-through sight and anything on it is skipped (bench part via TarkovRig.draw skip, kit scope via kitBones). If neither can be identified, the whole gun is hidden. Lens only shows at aim of 0.92 or more.
- 2026-11-08 10:30: with Photon, scope style A (crop) showed a BLACK lens and the gun hidden (lens copy empty; premultiplied blit over black disc, 429f662). A has never worked under Photon (3 tries). Commit on playtest 63ec75d56: shader packs default to B ("ring", whole view zoomed + dark tube); config field renamed look.scopeShaderLook (old scopeShaders "crop" not carried over); ; still toggles. Josiah 10:41 chose B as a STOPGAP default under Photon; PiP (A) stays the goal and still needs fixing.
