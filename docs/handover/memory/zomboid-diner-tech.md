---
name: zomboid-diner-tech
description: Roadside diners tech (lunch car, brick cafe, truck stop): diner.py dn_ kit, Diner.java layout rules, preview seeds, gotchas found 2026-10-27
metadata:
  type: project
  modified: 2026-10-27T07:29:59.535Z
---

Shipped in playtest 1dab37d5 (2026-10-27). Diner.java replaces RoadsideSite's old vanilla railcar/brickDiner.

**Kit:** tools/city/diner.py (294 dn_ blocks, register(ns) for photo_textures). BOARDS "c" HILLTOP CAFE, "t" ROUTE 9 TRUCK STOP. Paint with photo_textures.py on a copy of textures/block/city, then gen_blocks.py. Delete stale untracked dn_ files before regenerating.

**Diner.java entry points** (RoadsideSite.diner picks style 0/1/2, lots 40x34 / 40x36 / 64x46, shrink allowed):
- railcar(lw, ld): car(s,...) floor at y2 (raised), annex z10..15 with a 2-high room under a full-cube droptile ceiling at y4. Annex lights must be troffer() set INTO the ceiling at y4, never light() at y3 (head-knockers).
- cafe(lw, ld) and truckStop(lw, ld) both call brick(s, w, ww, v); ww=10 adds the truck stop wing (store, showers with lockers) when lw >= 56.
- The clear loops in car()/brick() start at z -3/-4: further out wipes the wheel stops laid by spaces() (bug found in preview).
- gravel(): blobby sin-noise dirt patches, grass only on the edges (per-block random looked like a patchwork).
- Trucks behind the truck stop only if ld - 6 >= cz + 23 (else they sit on the yard).

**Preview:** SitePreview dnN roadside diner 90 108 SEED 4 (seed 1,3,6 cafe; 2,5 railcar; 4 truck stop). Mesh coords = dump - 4 in x/z. Walkcheck clean except harmless counter-top "pockets" next to the restroom door.

Related: [[zomboid-civic-tech]] (sign tile order), [[zomboid-access-tech]].
