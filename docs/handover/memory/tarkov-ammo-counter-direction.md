---
name: tarkov-ammo-counter-direction
description: Josiah's ammo rule (2026-11-08): no magazine items; a hidden capped ammo counter per calibre, every reload puts in a full mag
metadata:
  type: feedback
  modified: 2026-11-08T08:53:14.260Z
---
Josiah 2026-11-08 08:46: "anytime you reload you should have a full mag loaded in", switch to a maximum carried amount instead of individual mags holding different counts. Card 08:53: he picked "Hidden ammo counter, capped" over loose round items: picked-up rounds go into a number per calibre (not inventory items), reload fills a full mag from it, HUD shows mag / carried. Cap proposed: 5 spare mags' worth per calibre, a sandbox setting.

**Why:** shooter-style flow; no juggling half-empty mags.
**How to apply:** old magazine items and loose rounds convert into the counter; loot gives rounds into it. Overrides Zomboid parity for ammo. HUD mock-up shown before the in-game build ([[zomboid-video-preview-rule]]). Related: [[tarkov-icons-scope-tech]].
- 08:53 Josiah: "a lot of the ammo building and other capabilities are going to go away as this game is now taking a different direction". Don't design around the reloading bench / ammo crafting; 08:53 he said "yes" to removing the reloading bench and ammo crafting as part of the ammo counter change.
- BUILT 2026-11-08 09:11 on branch claude/project-thread-5z3m2y (commit d30226011, CI green), NOT merged to playtest until Josiah OKs mock-up artifact NTrXMZu49FXqeqD1Heqzzr. combat/AmmoPouch (player attachment, per round item id; cap = guns.spareMagazines x biggest mag firing that round; sandbox zc.spareMagazines). Pickups via InventoryMixin.add, sweep every 10 ticks, Retired turns found mags into loose rounds. Bench, brass, powder, primers, heads, lead shot deleted outright (worldgen -> home_workbench). Survives the 09:28 purge re-steer ([[zomboid-purge-direction]]).
- 2026-11-09 07:57 Josiah: the mock-up is moot ("the hud for endless night is completely different"); card picked "Yes, mechanic only": AmmoPouch + full-mag reloads + bench/brass removal go to ENDLESS-NIGHT (re-apply d30226011 after its first green build, per SWITCH.md), WITHOUT the WeaponHud "20 / 100" counter (the new HUD reads AmmoPouch its own way). Not into old ZOMBOIDCRAFT.
- 2026-11-09 09:45 RE-APPLIED in ENDLESS-NIGHT: branch claude/project-thread-5z3m2y bea178d5d (CI green), merged to playtest 905cff5c4. Pickups via new mixin/InventoryAmmoMixin (old InventoryMixin/carry bags gone); WeaponHud untouched; SuppliesPage totals use AmmoPouch.total.
