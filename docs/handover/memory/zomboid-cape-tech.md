---
name: zomboid-cape-tech
description: Cape Merrow Lighthouse (coast group site 2) build tech: natural place like the Dam, drawn cliff line, cm_ kit, round.py xrail/glazex, preview go.sh
metadata:
  type: project
---

Built 2026-10-30 by LANDSCAPE (coast-and-industry group, see [[zomboid-landscape-queue]]). Refs: refs/coast (lighthouse photos).

- Cape.java: a natural Place like [[zomboid-dam-tech]]. Layout special-cases spot id "lighthouse" (county_plan spot 6750,-5150) to Cape.place; Builders dispatches LANDMARK variant "cape". Frame x -90..200, z -150..150 around the spot, LEVEL = WATER+12 (75). Tower at frame (110, 0), watch room floor y 38, lantern floor 45.
- Cliff: NOT from natural height (the shore slope is so flat that a height threshold left a comb of stone pillars). cliffLine(z) = 140 - (z/72)^2*34 + noise: columns east of it keep the land as it lies (beach, sea), west of it are raised to 0. Shingle and fallen blocks within 5 of the foot.
- Station class (extends Grounds): land, cliff, stacks, drive + car park, fieldstone enclosure (x -6..128, z +-62), tower (Round.wall lhw/lhr, newel, square-ring steel stair FLIGHT), watch room + gallery (cm_corbel ring, xrail), lantern (glazex, cm lens lit), vestibule, keepers' House x2 + front pickets + gardens (farmland crops, scarecrow, privy, woodpile, hives), oil house, fog signal (cm_horn), yard (mast, wtw tank: no hoop variant for wtw, hoopEvery 0), coast path rail along the whole cliff edge, landing gully stair (stairs_concrete facing west) at z -41..-40 with wrecked skiff, heath (bush/berry/dry grass/rock outcrops, BUSH/DEAD/PINE trees landward), story.
- Kit: tools/city/cape.py (cm_ textures + 37 blocks, signs cm_sgn_<key>_0/1 incl. "steps"), registered in photo_textures.py. round.py gained xrail (X-lattice gallery rail) and glazex (diagonal astragal glazing) as rd_xrail_*/rd_glazex_*.
- Missing names found on compile: bunk_cot -> ch_cot_head/foot, jerry_can -> bunk_jerry_cans[facing], pw_generator -> tn_genset (bunk_st_generator is only a stencil).
- Preview: /tmp/claude-0/plan/lh/go.sh a 6810 -5150 150 (runs vanilla.py for new vanilla stand-ins like tuff first); mesh m_lh_a; tower local (204, y, 162), ground local y 29.
