---
name: zomboid-hospital-tech
description: Mercy General Hospital builder (Hospital/HospitalDepts/HospitalWard.java, hs_ kit in tools/city/hospital.py), lot, preview coords, access results
metadata:
  type: project
  modified: 2026-10-28T01:57:21.036Z
---

Built 2026-10-28 by LANDSCAPE; in playtest via merge 4117c612. Variant "mercy" in LandmarkSite, a 2x2 lot
(133x133) that Layout.fixedPlace nudges to 2880,-320 so the subway's Mercy General platform stays under
the street at z -400 (Glass Line deeper). No basement: the subway and the Glass Line run under it.

Files (world/county): Hospital (shell: grounds, brick podium 3 floors S=5, white precast tower of 9 ward
floors PROOF 15..TROOF 60, core with 2 Stairwells + 3 LiftBuild shafts, roof helipad, service wing,
outbreak dressing), HospitalDepts (podium floors: ground lobby/chapel/cafe/lab/radiology/ER; second
pre-op + 10 theatres (8 wide, two per bay) + sterile core + recovery + ICU; third admin/records/clinics,
gym), HospitalWard (Kind enum per ward floor, 13 rooms a side via turned frames).

hs_ kit: tools/city/hospital.py (registered in photo_textures.py after mall). Theatre floor block is
hs_slab_green (green vinyl up, ceiling under); never put a hs_lino* block in an upper floor's slab.
Hospital.solid(x0,x1,y,z) = wall row whole; corridor gurneys/carts only go against solid walls.

Preview: `SitePreview DIR landmark mercy 133 133 SEED 8`; mesh with /tmp/claude-0/plan/mercy/mk.sh (its
extra.json has lift_door/button/machine boxes). Checks 2026-10-28 seeds 1-3: 0 blocked doors, 0 blocked
treads, 0 head-knockers; leftover pockets are lift cars and furniture tops.
No hospital photos in refs yet (asked REFERENCES); redo when they land.
Merge tip: playtest conflicts on generated files are solved by a 3-way JSON merge of blocks.json and
en_us.json, then gen_blocks.py run in the playtest worktree (texsync from the worktree's textures first).
