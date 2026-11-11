---
name: zomboid-iron-sights-rule
description: RULE (Josiah 2026-11-01 10:22): every future weapon gets its real-life iron sights modelled from reference photos, then the "rough up" paint/bevel pass
metadata:
  type: feedback
  modified: 2026-11-01T10:23:12.377Z
---

Josiah 2026-11-01 10:22 (Brita weapons thread): "for all future weapons you go through the same process of acquiring their true to life iron sights to model after on each weapon as well as go through the 'rough up' process".

**Why:** generic or borrowed sights looked wrong and off-centre, and flat single-colour sight parts looked "block" like next to the hand-painted gun models. He wants each gun's own real sights, in the gun's art style.

**How to apply:** for ANY new weapon (bought pack, local pack, our own):
1. Ask the REFERENCES thread for real photos of that gun's iron sights (side, from behind, from above) → refs/items/weapons/irons/<gun>/. Wikimedia only from his PC.
2. Model the real front and rear sight (post/blade/bead, notch/aperture, ears, wings, ladder), cut any solid stand-in sight blocks, dead-centre line, see-through rear. Shotguns: just a bead. Guns sold scope-only get the back-up irons that gun really wears.
3. Rough-up pass: chamfered worn edges, hand-painted tiles (mottled metal, soft highlight streak, dark painted borders, scratches, chipped light edges), painted in the gun's own metal colour, tiny hash-jitter unevenness.
4. Video preview (sight picture per gun) before any build ([[zomboid-video-preview-rule]]).
Tools: brita/tools/irons/irons.py + sightkit.py ([[zomboid-brita-irons-tech]]).
