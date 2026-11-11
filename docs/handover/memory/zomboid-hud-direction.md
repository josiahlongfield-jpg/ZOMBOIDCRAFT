---
name: zomboid-hud-direction
description: Josiah's ZomboidCraft HUD redesign - chose "field kit that fades" (2026-10-25); rundown doc and open questions
metadata:
  type: project
  modified: 2026-10-25T10:09:25.572Z
---

Josiah (2026-10-25): "lets work on a new HUD". On the decision card he picked **Field kit that fades** and asked for a full rundown before anything is built.

Rundown doc: https://claude.ai/artifact/8d3jqHFdbRyWKHr2zjvo6h
- The pieces use the same kit as the inventory. Six pieces:
  - Belt hotbar on webbing, bottom middle.
  - Moodle tiles down the right edge, yellow/orange/red.
  - Body outline replacing the hearts, bottom left.
  - Weapon tag with ammo on tape, bottom right.
  - Skill-up tape, top left.
  - Grabbed warning under the crosshair.
- Vanilla hearts, hunger, armour, XP and air are removed. Hunger and air become moodles.
- Each piece fades after a few quiet seconds. It stays up while dangerous (bleeding, below half health, bad moodles, aiming). Left Alt peeks the whole HUD (the key is free; the mod binds R, V and K).
- His answers (2026-10-25): whole-body health for now; potion effects become our own moodles; wristwatch yes (time and day, only while wearing a watch); Zomboid belts and holsters decide the quick slots. The first slot is always HANDS, then HIP L/R, HOLSTER, SHEATH and BACK, and the inventory's Belt & Hands row matches.
- Style (his words): "a clean design but equally immersive with finite details". Keep pieces small with no heavy panels, and get the immersion from fine material detail.

Mockup v1 (2026-10-25): https://claude.ai/artifact/5gbKWAMkyYf4FDDtWbihuv. Source is in project files zomboid-mod/hud-ui/v1/ (template.html, assets.py). It uses the real game sprites and 12px pixel moodle icons. Feedback on v1 (10:36): he likes the belt and other pieces vanishing until used. Belt and holster squares should have NO backdrop strap, just the squares. He wants a high-quality v2 "on par with our level of realism and moody design", and it doesn't have to match the inventory UI. Mockup 2 is published at the same link (source in zomboid-mod/hud-ui/v2/). It has bare belt squares with the real 3D item models, enamel moodle plates with sprayed stencil icons, a 7-segment wristwatch and a lit blocky body figure. 11:01 he asked for the body slot to be the player model, as in the inventory mirror (InventoryScreen.extractEntityInInventoryFollowsMouse), in the bottom left, turning to follow look direction. The mockup now does this, with health shown as a red underlight, wounds and a state word. 11:10 he asked that the head aim at the mouse so that hovering over its head makes it look straight at you; mockup v4 does this (body turns half, head turns the rest; 11:11 he asked to halve the head turn, so the angles are scaled by 0.5). In game, drive it from the player's look direction the same way. Next: his feedback, then build. See [[zomboid-inventory-ui-direction]]. Current HUD code: client/MoodleHud, SkillsHud, combat/CombatHud, combat/Crosshair.

BUILT 2026-10-25 (he tapped Build it now at 11:12): code in client/hud (Hud registers it and hides vanilla bars in survival; BeltHud, MoodleHud, BodyHud, WeaponHud, WatchHud, SkillTape, GrabHud). Art comes from tools/ui/hud/gen_hud.js (Playwright) into textures/gui/sprites/hud; the fonts are hud/hud_big (Barlow Condensed). Config look.fieldKitHud. New item digital_watch: the watch shows while it's anywhere in your inventory. In playtest 596ac50. NOT done yet: Zomboid belt/holster/sheath items that gate quick slots with HANDS/HIP/HOLSTER/SHEATH/BACK names (for now the belt shows the hotbar slots with numbers), a wrist slot for the watch, and wounds drawn on the figure. Game names: the clock is Level.getDefaultClockTime(); LivingEntity has no getAttackAnim/swinging, so use options.keyAttack.

**Why:** he likes options and a full explanation before work starts. **How to apply:** don't build HUD code until he approves the mockup.

2026-10-26 01:35 (UI thread): Josiah wants a subtle "water on your face" HUD effect when exposed to rain (the game is always rainy). Proposed: drops gather at the screen edges (centre clear), occasional runs down, more in heavy rain / looking up, dry off 10-20 s under cover, sprinting pushes them sideways, hats/hoods reduce, setting to tune. Decision card asked: HUD-drawn drops vs shader refraction vs HUD now + refraction later (recommended). Check rain exposure with level.isRainingAt(eye pos). Keep it consistent with the weather thread's thin-rain direction ([[zomboid-weather-direction]]).
SHIPPED 2026-10-26 02:0x: he chose "HUD drops, plus refraction later". Code client/hud/RainHud (drawn in Hud.under before the active() check), sprites hud/raindrop_round|tall|wide + rain_streak, setting HudSettings.rainOnFace (Video Settings: Off/Light/Normal/Heavy, saved in zomboidcraft-hud.json). In playtest e274e69, untested in game. Refraction (shader) still to do as an option.
Update: drops made bigger/brighter after the browser preview (https://claude.ai/artifact/8YVbj4akbaQCSq8KBa1sm8, source in thread scratchpad rain/), in playtest 9b2c7e7.
Exposure (playtest 9241c55): level.isRainingAt(eye block) (same MOTION_BLOCKING test as falling rain), none in a CarProp, first person only, 0.4 s settle before drops land.
Preview (2026-10-26 07:5x): artifact 8YVbj4akbaQCSq8KBa1sm8 v2 is now the FULL HUD (hud2 mockup + face rain + action bar + senses meters), built by scratchpad rain2/build.py from hud2/template.html. He liked the rain ("rain is good").
