---
name: zomboid-university-tech
description: Grayhaven State University builder (University + UniLibrary/UniLecture/UniScience/UniUnion/UniOldMain/UniDorm.java, un_ kit), lot, preview coords, access results
metadata:
  type: project
---

Built 2026-10-28 by LANDSCAPE; in playtest via merge f3c69970. Variant "university" in LandmarkSite, a 3x3 lot
(213x213) that Layout.fixedPlace pins at 2760,-1720 (east of the subway under street x 2640, clear of its
corner curve near 2640,-2000). No basements.

Files (world/county): University (constants for every hall's box, shared helpers: walls/partitions/doors,
lamps (swaps un_slab_* / un_roof for _lamp), mess, sign/row/picture/poster, stair = Stairwell in its own
shaft with roof hut, lift = LiftBuild; grounds: walks, staff lot, railings, gate, quad, Guard camp).
Halls: UniLibrary (brutalist, 5 floors, stacks), UniLecture (two raked halls, rake(x) helper, ROOF 18, no
stairs so no roof access), UniScience (corridor + labs per PLAN table, greenhouse), UniUnion (storeys 6,
ballroom shelter, radio), UniOldMain (F0 = 1 raised floor, storeys 6, portico + low stepped pediment, clock
tower copied from Courthouse but lit with dn_glassblock_lit, no vanilla froglight), UniDorm (10 floors x 4,
one lift).

un_ kit: tools/city/university.py (registered in photo_textures.py after hospital). Campus map posts hang
under tiles 3 and 5 (bottom row). Skeletons carry a post-shaped collision (else head-knocker reports).
Camp.nest drops vanilla barrels: University swaps them for crates after the call.
Preview: `SitePreview DIR landmark university 213 213 SEED 8`; mesh with /tmp/claude-0/plan/uni/mk.sh
(m_un_ prefix). Checks 2026-10-28 seeds 1-3: 0 blocked doors, 0 blocked treads, 0 head-knockers; left
pockets are lift cars, Old Main's water-table ledge and furniture tops. No campus photos in refs yet.
