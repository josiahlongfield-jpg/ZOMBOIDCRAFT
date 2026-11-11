---
name: zomboid-nightvision-tech
description: ZomboidCraft night vision tech: headgear item models, Mounted/battery components, N flip, post-effect tube view, params texture, config, loot
metadata:
  type: project
  modified: 2026-10-26T04:28:58.779Z
---

Night vision (weapons thread, branch nightvision, 2026-10-26). Josiah loved the preview (https://claude.ai/artifact/77fKx8fvZcFXSkAKUHqPAy). See [[zomboid-guns-tech]], [[zomboid-playtest-build]].

Items (gear/ package): combat_helmet and nvg_head_mount are HeadGearItem with Equippable HEAD (no asset, so vanilla's head layer uses the item model). nvg_gen1/gen3/white are NightVisionItem (Generation enum, key = item id). aa_battery is BatteryItem. Models and lang come from `python3 tools/weapons/headgear.py`.

Components: `zomboidcraft:goggles` = Headgear.Mounted(goggles stack, up) on the headgear, only when mounted. `zomboidcraft:battery` = seconds left on the goggles; absent means never picked up, and the server tick (Headgear.settleFound) gives found goggles 20-80% (full in creative). Change headgear only through Headgear.mount/unmount/setUp so CUSTOM_MODEL_DATA strings [goggle key, "up"/"down"] stay right.

Gestures: right-click a headgear slot with goggles (mount/swap), empty cursor (take off), battery (swap battery; old one thrown away). Right-click goggles in hand while wearing headgear to clip on; right-click a battery while wearing goggles. N key = CombatNet.Action.NVG (5) -> Headgear.flip.

Server: Headgear tick gives ambient, hidden NIGHT_VISION (400 ticks, refreshed under 220) while down and powered, removes only its own ambient instance, drains 1/s, writes every nightVision.saveEverySeconds.

Client:
- HeadGearLayer (registered in CarryClient beside WornGearLayer): head.translateAndRotate, translate(0,-0.25,0), scale(-1,-1,1), then items render with NONE; goggles rotate Axis.XP by 90*eased about pivot (0, 2.3/16, -5.62/16). The item def returns empty in head context for minecraft:player.
- View = vanilla post effect `zomboidcraft:nvg_<gen>` (post_effect/*.json, shaders/post/night_vision.fsh), added each frame by GameRendererMixin @ModifyVariable on GameRenderer.preparePostEffects (26.3 has requested/applied post-effect lists). POST_PROCESSING_SNIPPET includes Globals, so GameTime animates grain and flicker.
- Dynamic settings: a 4x4 DynamicTexture registered at zomboidcraft:textures/effect/nvg_params.png (the TextureInput location "zomboidcraft:nvg_params" gets "textures/effect/" + ".png"). Its channels are r = tubeSize/1.5, g low = dying battery, b = brightness/2. A static png of the same name is the fallback.
- If getAppliedPostEffects lacks ours for 20 frames, or nightVision.tubeEffect is false, a GUI tint plus the nvg_tube(_mono).png mask is drawn instead.

Config `nightVision`: batteries, batteryMinutes 30, foundChargeMin/Max, saveEverySeconds, tubeEffect, tubeSize 0.92, brightness 0.85.

Loot: generators tools/city/shop_loot.py (NIGHT_VISION dict + rare gen3/white) and tools/cars/car_loot.py. CAUTION: committed loot JSONs carry hand edits from other threads (street_map entries etc.) that the generators don't make. Re-running a generator drops them, so patch the JSON additively instead.

Not done yet: gear/attachment tier rolls (no Rarity on gear yet; Rolls.roll would roll anything with a Rarity). Untested in game, including Photon/Iris with the post effect.
