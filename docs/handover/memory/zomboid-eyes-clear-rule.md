---
name: zomboid-eyes-clear-rule
description: RULE from Josiah - ZomboidCraft face items rest below the eyes and headwear just above them; only eyewear covers the eyes
metadata:
  type: feedback
  modified: 2026-10-29T02:48:49.687Z
---

Josiah 2026-10-29 02:48 (clothing thread): "make sure that for all facial items that the eyes aren't covered like for the bandana, it covers the eyes completely, this should rest below the eyes and any headwear just above them".

**Why:** the first bandana was modelled up over the eye row and hid the eyes.

**How to apply:** a skin's eyes are the head's fifth pixel row (head coords y -4 to -3, head top at -8). Bandanas, masks, balaclavas, scarves and collars stay below y -3. Hats, helmets and cuffs stay above y -4 at the front. Only eyewear (glasses, goggles) and anything that's meant to (a gas mask's lenses sit over the eyes) goes over them. The constants are EYES_HIGH/EYES_LOW in tools/items/gen_clothes.py. Applies to zombie kits and any other character gear too. Related: [[zomboid-wearable-clothing]], [[zomboid-clothing-style]].
