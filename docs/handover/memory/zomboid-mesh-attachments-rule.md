---
name: zomboid-mesh-attachments-rule
description: RULE (Josiah 2026-10-29 09:17) no old block parts on bought-mesh guns; attachments come only from the guns' own pieces plus the 10-piece attachment pack, swappable
metadata:
  type: feedback
  modified: 2026-10-29T09:21:45.288Z
---

Josiah (2026-10-29 09:17): "i dont want you to use any of the old parts from the previous weapons. all attachments are being replaced too. just use the attachments that came with the weapons but make them interchangeable with all the attachments from the pack i uploaded (if they are applicable to the weapon)".

Josiah (2026-10-29 09:34): "i want you to add these weapons like the old ones never existed, dont reuse anything except for where they sat on the screen and were held by the player as a reference". So muzzle, rails, sights, mags, mount points all come from the bought mesh; only the on-screen placement and the hold poses are kept from the old guns.

**Why:** the first mesh swaps kept old block irons, block mags and block attachments over the real mesh, which looked odd and disconnected.

**How to apply:**
- A mesh gun draws nothing from block models (muzzle flash effects aside): its own sights, mag, furniture.
- Pieces a bought gun came with (pbr scopes, red dots, bipods, rifle 6 suppressor, grips on 1 and 14, launcher on 2, M200 scope/bipod) become attachment items, fitted as standard on that gun, swappable.
- The attachment pack (att_*.obj in scratch packs/pbrobj: eotech holo, Aimdot red dot, Prism Acog 4x, Foregrip RVG, surefire light, ironsight flip irons, magnifier, Fortis angled grip, Magpul grip, RMR2 mini red dot) fits every gun with a matching slot; magnifier gets its own slot.
- Old block attachment models go away on every gun, block guns too.
- Laser sight: no model in either pack; Josiah (09:35) "ill get a laser light pack". Laser stays as an item with no gun model until that pack arrives.
Related: [[zomboid-mesh-guns-tech]], [[zomboid-wave2-guns]], [[zomboid-gun-art-style]].
