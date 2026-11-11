---
name: zomboid-civic-buildings-tech
description: Per-building notes for the rebuilt civic set (post office, and next town hall, churches, bank...) plus the shop_loot.py wipe hazard
metadata:
  type: reference
  modified: 2026-10-26T23:10:00.000Z
---

Kit, tile order and courthouse/library notes live in [[zomboid-civic-tech]]. This file holds the later civic buildings (LANDSCAPE thread, 2026-10-26).

**PostOffice.java** (commit 25d0225f, refs/town/post-office): F=2, Z0=4 (front wall). Federal 50% (top F+6, oak doors, fanlight, cv_pofrieze_10 band, keystone arches, lantern cheeks) or sixties (top F+5, ext_win_white_six + shutters, concrete stoop with the top at F, Doors.SHOP pair, cv_poletters_6 at F+5 centred over the doors: tile i at cx+2-i). Inside: lobby (zs=z0+5 screen line of cv_po_boxes either side of counter + cv_po_grille), workroom (sorting cases, island, hampers, parcel racks, lockers, coffee bench), postmaster office + restroom behind zo=z1-4, dock at the back. TownSite.postHost() adds the shelter host (PostOffice.host(w,d), floor F). Loot kind "mail".

**HAZARD: never run tools/city/shop_loot.py wholesale.** It deletes and rewrites every loot_table/shop/*.json, wiping entries other threads added by hand (gun_oil, gun_parts...). Run it, then `git checkout --` every modified table and keep only the new file(s).

**Civic.java is now all custom**: school ([[zomboid-school-tech]]) and theatre ([[zomboid-theatre-tech]]) were the last vanilla ones. Fire station and sheriff notes: [[zomboid-civic-services-tech]].

**Church.java + tools/city/church.py** (commit 0b78e2ce, refs/town/church-*): ch_ kit 299 blocks. Civic.church() delegates to Church.build(kind, w, d, sign); sign spot comes from the busy x range each kind returns. CHAPEL: floor 1, nw 10/12/14 by lot width, tower 4x4 rising from inside the nave front (walls from y 0, plastered inside), porch with red doors. BRICK: tower in the front corner flush with the gable (z0 = 4), door through its back into the nave, battlements or slate spire, cellar hall. STONE: needs a deep lot (z0 9, nave up to 34): St. Mary's sits past the ring road on inN with a 23x50 lot; vault via fillUp() (never fill toward open sky, that made plaster pillars). TownSite.church(lot, kind, w, sign, wild) lays the lawn FIRST and adds the lot to kit.treeless (street trees skip it). Block-name gotchas: the trim block is front_trim_white (ext_trim_white is only a texture); cv_arch_* need [facing=north] or they never rotate. Preview check: /tmp/claude-0/pv/palchk.py DUMP validates every palette name and property against blockstates.

**TownHall.java + tools/city/townhall.py** (commit 762725de, no photos: text research): th_ kit 30 blocks. Civic.townHall(w, d) (old hall() removed); county seat lot 20 x depth-2; TownSite adds Host(TownHall.host, Z0=4, F1=2). F1=2, F2=8, TOP=14; clock tower T=6 at frame x 0..5, front at z0-1 (one proud), dials over the roof deck (TOP+7..), belfry, hip(...deck=false) pyramid. Hall x cx-3..cx+2, partitions cx-4/cx+3, cross walls za=z0+7, zs=z1-7 (stair foot). eoc 55%: chamber becomes emergency centre (red lamps), else polling booths.

**Bank.java + tools/city/bank.py** (commit 34b177c0, no photos: text research): bk_ kit 79 blocks (frieze_{a,b}_10_i, marble floors, counter/grille/wicket/crest, dado, wall clock, vault frame/door/shut 3x3, deposit boxes loot bank, ATM, night drop, post clock, low-pediment rakes bk_rake_{l,r}_{0,1} at 1:2). Civic.bank(w,d); lots 13 wide (built 12). F=2, M=6 mezzanine, TOP=12, Z0=4. Vault wall zv, teller line zt; stair up x1-1 into the bookkeepers' room (partition gap there). robbed 70%: door open west, boxes pried.
