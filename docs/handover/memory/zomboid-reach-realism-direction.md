---
name: zomboid-reach-realism-direction
description: Josiah 2026-11-09 10:52-10:55: after the playtest fixes, top priority is Reach realism, "indistinguishable detail" from the real game
metadata:
  type: project
---
Josiah 2026-11-09 10:52-10:55: once the playtest bug fixes are done, move straight to adding all the real textures and everything needed so walking the Reach world feels like walking through the actual game. "indistinguishable detail is the key here".
**Why:** the first playtest looked white, foggy and flat (missing ground pictures + Halo's hidden lid at spawn); he wants it to match Reach exactly.
**How to apply:** use Halo's own data for everything visible (full-res diffuse, normal/bump, specular, detail maps, baked lighting, decals, foliage, props, skies, atmosphere, water). Halo files live only on his PC (MCC install + additions\halo-reach\split); cloud disk is ~3-4 GB free, so pull maps one at a time while his PC is on. He gave PC access windows (10:54: "another hour").
- Hidden collision fix 10:50: voxelize.py --materials drops shaders\invalid except ramps on the ground; spawn moved to y 48.5 (Sword Base courtyard). ENDLESS NIGHT\config folder connected 10:53 (his OK).
Related: [[zomboid-reach-world-tech]], [[zomboid-reach-world-direction]].

- 2026-11-09 11:28 build tour: https://claude.ai/artifact/UjWqKsSi6FKNWnyNK44RPP (shots in endless-night/reach-world/previews/tour-2026-11-09/). Issues seen: dark fog band hides land, backdrop undersides show, Winter Contingency trees white, Exodus untextured, PoA building blown white, flat lighting. Josiah wants shots viewable on phone (use an Artifact page; thread attachment cap is full).
- Screenshot trick: a fullscreen MC capture goes stale; toggle F11 twice before each screenshot. Use /gamemode spectator + /tp x y z yaw pitch, F1 hides the HUD.
- Josiah 11:34: "I want to not being able to tell the difference between what you've put together and the actual game"; work OVERNIGHT section by section with great time and detail, post previews as I go. Only LANDSCAPE (this thread) keeps working while others are paused (11:31).
- 2026-11-09 12:40 OVERNIGHT PREVIEW PAGE (keep updating, same URL): https://claude.ai/artifact/1YWUtHBKxMq6zA2MLw7xjt (source scratchpad reach-realism.html; Sword Base update 1 posted).
- Cloud reference renderer: /tmp/claude-0/halo/real/web/real.html (+lmdec.py, lmbake.py, lexport2.py, lmat.py, real/reexp.sh; shared textures real/tex via ZC_TEXLIB). Findings: bitmap curve byte at image+0x11 (1 xRGB, 3 linear); terrain base/detail maps look right only as gamma; sky detail honours linear. Bake needs crease-aware normals (thin fins went black). Sky = self-illum only, drawn at camera. Fixed exposure 2, sks 0.5.

- Josiah 2026-11-09 19:19: "Levels are looking great" (preview page). Wants the ENTIRE landscape (the land between/around levels, land.zrh + backdrops) brought to the same intricacy as the levels. PC on until ~21:15 that night for the level re-export.
- Josiah 2026-11-10 08:10 (decision card): Reach world is built around HALO'S OWN LOOK with Photon OFF (Photon cut far skyline/orbit ships and washed out Halo's baked light). Photon stays for the county world. Rain/fog for Reach to be done in the mod. With a pack on, the mod falls back to the plain look (commit 998727fe1). Next asked: detailed land between missions (land is still the 4 m first pass).
- Josiah 2026-11-11 07:19: "I want as much of the actual reach in the world as possible" -> picked bringing back Winter Contingency's real snowy valley (m10_000) over plain land, Noble Actual stays put; then the same around other missions (real _000 land preferred over generated land).
