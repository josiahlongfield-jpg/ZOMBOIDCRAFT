---
name: zomboid-civic-centre-tech
description: Grayhaven Civic Center builder (CivicCentre/CivicTower/CivicChamber.java, Grounds base, cc_ kit), lot, layout constants, preview coords, check results
metadata:
  type: project
  modified: 2026-10-28T03:20:16.861Z
---

Built 2026-10-28 by LANDSCAPE; playtest merge 5fb63fa6 (branch commit 0b669a63). Variant "civic" in
LandmarkSite; Layout.fixedPlace gives plan spot "civic" (2750,-850) a 2x2 lot (133x133). No basement.

Files (world/county):
- Grounds: abstract base pulled out of University (placer, rng, Bldg, power, set/fill/wall/part/door(s)/
  opening/lamps/mess/sign/row/picture/poster/stair/lift/lampPost/tent). sign() uses signs() prefix
  (un_sgn_ default, CivicCentre overrides cc_sgn_). University output is byte-identical after the move.
- CivicCentre: constants (tower TX 72..120 x TZ 70..100, S 5, 12 floors, ROOF 60, core 84..108 x 80..89;
  chamber drum CHX 36 CHZ 84 R 15; concourse 46..71 x 74..80; pool 84..108 x 20..54; relief station
  x 6..58 with hoarding at z 10), plaza/pool/flags/sculpture/planting/memorial/relief/back lot.
- CivicTower: precast facade (piers every 3, cc_fin outside them, cc_win bands, cc_spandrel), lobby
  (clerk + tax counters, detectors, vault, rooms behind core), floors 1-10 Floorplan + Floors.fit
  (GOVERNMENT x2, RECORDS, CELLULAR, OPEN_OFFICE, COMPUTERS, CAFETERIA, CELLULAR, LAW, EXECUTIVE),
  fit_ceiling/fit_troffer one under each slab, floor 11 custom EOC, roof helipad (hs_pad_h_*).
- CivicChamber: drum, saucer roof rings, portal with name, dais + th_bench, vote boards, pews or cots.

cc_ kit: tools/city/civiccentre.py (registered in photo_textures.py after university). Camp.booth
leaves vanilla sea_lantern/smooth_quartz: CivicCentre clears them after the call.
Preview: `SitePreview DIR landmark civic 133 133 SEED 8`; mesh /tmp/claude-0/plan/civic/mk.sh (m_cc_).
Checks seeds 1-3: 0 blocked doors/treads/head-knockers, orientcheck clean; left pockets are lift cars,
lift-button tops and furniture tops. No civic-center photos in refs yet.
