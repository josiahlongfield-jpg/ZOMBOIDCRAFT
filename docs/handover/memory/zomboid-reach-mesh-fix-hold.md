---
name: zomboid-reach-mesh-fix-hold
description: Reach world "nothing here" texture-mip fix + baked terrain hookup, pushed to ENDLESS-NIGHT playtest 2026-11-09 09:40 after Josiah's "push"
metadata:
  type: project
---
- "Nothing here" bug (08:18): non-square texture mip chains went one level past width>>n/height>>n, the upload threw, and most pieces never drew. Fixed in ReachMeshes.mips().
- ReachGlb: rmtr (Halo terrain) materials now load terrain/<shader>.png next to the level folder or one up (world/terrain/). Bakes are made by /tmp/claude-0/halo/terrainbake.py (cloud), from the map files staged off the PC.
- Pushed to josiahlongfield-jpg/endless-night as branch claude/reach-mesh-fixes (d2d250343) and merged into playtest 6f5f7d9bb (Josiah said "push" 09:38). Jar published in CI run 13. The run is red only at the anonymous download-link check, which also failed on the base: the repo is private. Reported to the coordinator.
- Clone: /home/user/endless-night (shallow). Default branch on the remote shows as staging; main and playtest must be fetched explicitly.
- The push classifier blocks pushes based only on coordinator relays when Josiah's last word was a hold: get his direct OK.
**Why:** keep the track of what is in the jar. **How to apply:** baked terrain PNGs still have to be previewed, then written to the PC under additions\halo-reach\world\terrain\.
