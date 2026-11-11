---
name: zomboid-attachment-colours-tech
description: How attachment colour variants work (tan holo/magnifier/grip/light, dark/camo/green flip irons): zomboidcraft:colour component, model data indices, skin.py selects, loot paint
metadata:
  type: project
  modified: 2026-10-30T21:49:21.027Z
---

Josiah 2026-10-30 21:37: "I want all attachment variants added to the game so that loot drops have a variety". Built on claude/project-thread-5nbhdi (6a49c0f84); preview first, then playtest.

- Meshes: each colour packed as `{mesh}_{colour}.zmesh` (att_holo_tan, att_magnifier_tan, att_stubby_grip_tan, att_light_tan, att_irons_{front,rear}_{up,down}_{dark,camo,green}). Scratch pack specs were in the weapons thread scratchpad packs/att/; textures from the pack folders (holo "base color_earth_albedo", magnifier "eotech_ magnifier_earth_albedo", Magpul Grip 1001_2, surefire_earth_base_color, irons default/camo/grn).
- Data: tools/meshguns/attachments.json "colours": ["tan"] per key; flip irons' colours are skin.py IRONS_COLOURS (grey is plain).
- Java: GunState.COLOUR (String component zomboidcraft:colour). On a part it is the part's colour (it survives mounting: Attachments stores full stacks); on a gun it is its flip irons' colour. Attachments.COLOURS / IRONS_COLOURS / FLIP_IRONS (assault_rifle, hunting_rifle, precision_rifle, heavy_sniper; crossbow has NO irons since 2026-10-30; keep in step with guns.json "flip"), paint(stack, random) picks plain or a colour evenly. Called in Rolls MODIFY_DROPS loop and CaseItem.gunCase. NOT in the Carry tick (would re-roll plain stacks forever). AttachmentItem tooltip shows the colour.
- Model data strings: 0-8 slot keys, 9 finish, 10-18 each slot's part colour (Slot order), 19 irons colour; "none" = plain. writeModelData writes them.
- skin.py only (no kit.py change, no weapons.py regen needed): attachment() and irons() wrap their specials in a custom_model_data select on those indices (coloured()); swap() collapses selects with index >= 10 back to the block model so reruns are idempotent; apply_items wraps loose items in a minecraft:component select on zomboidcraft:colour. `python3 tools/meshguns/skin.py` reapplies in 3 s (then revert rounding-only *_magazine.json). Gun item JSONs roughly doubled (assault_rifle 324K -> 692K).
- GunLookTest colours(): shots colour-assault_rifle-tan-{fp-hip,tp-front}, colour-parts-ground (near row plain, far row tan), colour-irons-{grey,dark,camo,green}-{fp-hip,ads}.
See [[zomboid-irons-tech]], [[zomboid-mesh-attach-tech]], [[zomboid-rolls-tech]].
