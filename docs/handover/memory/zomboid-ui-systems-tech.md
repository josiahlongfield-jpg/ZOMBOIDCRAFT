---
name: zomboid-ui-systems-tech
description: ZomboidCraft timed actions, health/skills/map pages, noise+sight meter, quick-use wheel: where the code lives and rules
metadata:
  type: reference
---

Built 2026-10-25 (UI thread) after Josiah approved the six systems ([[zomboid-kit-screen-destiny]]).

- Timed actions: survival/TimedActions (server, per player; synced attachment zomboidcraft:timed_action View(label, ticks, id)). Cancelled by walking >0.6 blocks, player.swinging, hurtTime rising, Fire/Reload/Shove packets. Items that start one must return InteractionResult.CONSUME on BOTH sides (SUCCESS swings and cancels it). Client bar: client/hud/ActionHud (progress() is also used by the Health page).
- Treatment (survival/Treatment): bandage 80-5*FA ticks (min 30), splint 140-8*FA (min 60); items are only used up at the end, from hands first then any carried slot. Health page sends menu button Bench.TREAT = 200_000_000 + kind*8 + region.
- Hammer boarding is timed too: 60-3*Carpentry ticks (min 30), prying 30.
- Quick-use wheel: client/hud/QuickWheel (hold Z), EntityTurnMixin cancels turning while open; release sends CombatNet.Action QUICK_USE(16)+slot for medical/consumables (survival/QuickUse), else selects the slot.
- Noise meter: zombie/Noise.emit sets attachment zomboidcraft:heard on players within 5 blocks (effective radius incl. weather). Sight meter uses Sight.range(Player, Senses). client/hud/SensesHud.
- Skills page numbers mirror code: Aiming 5 walking spread x1.3 (was 1.6); Blunt/Blade 5 swing cost x0.8 (Stamina.onSwing); First Aid: bound wounds +10%*level chance of an extra tick (≈5%/level), level 8 splints 4/s instead of 3. Keep SkillsPage text in step when a skill changes.
- Map: client/map/MapStore paints explored columns into 256-block PNG tiles under config/zomboidcraft/maps/<world>/<dim>/ (alpha FF = seen, FE = ink from a read map), county.png at 32 blocks/px for atlases, map.json for pins and maps read. Server world/map/PaperMaps draws street maps (nearest place's voxels at 2 blocks/cell) and road atlases (16 blocks/cell, ±2048) from the county plan and sends MapNet.Reveal (first clientbound payload in the mod). Items zomboidcraft:street_map / road_atlas in desk, drawer, office, books, pawn and car loot. Only county worlds have readable maps.

**In-game UI test (2026-10-26, UI thread):** `.github/workflows/uitest.yml` runs on every push to claude/project-thread-j1d6bj: a real 26.3 client (Fabric client gametest, `./gradlew runClientGameTest -Puitest`, source set src/gametest) opens the kit with E, left-clicks every tab and a tile through the real mouse, logs `ZCUI` lines and uploads screenshots (artifact ui-screens, fetch with `gh run download RUN -n ui-screens`). CI only has software Vulkan (OpenGL's GLX fails), which can't fit the 2048px gun skins in its 16384 atlas, so the workflow shrinks textures on the runner only. Result: left clicks work in the kit; Josiah's "only right click works" looked like OS-swapped mouse buttons, so kit hots with no right-click action now answer either button. Bench gun stage = client/kit/GunStage (3D gun + bench_ghost parts from gun_slots/*.json, drag to turn).

- Enemy health bars (Josiah 2026-10-29 12:06, "a little one above its head, as do all enemies"): client/hud/EnemyBars (LevelRenderEvents.COLLECT_SUBMITS). Thin kit-red bar for every Enemy + HarvestmanEntity EXCEPT zombies (Josiah 18:38: head shots are the only kill, so no bar) within 28 blocks in line of sight; a pale part drains after a hit. Harvestman bar sits over its skull (SpiderDraw.overHead). Hidden for apparitions (negative ids) and CinematicView.clean(). mc.options.hideGui does not exist in 26.3.
