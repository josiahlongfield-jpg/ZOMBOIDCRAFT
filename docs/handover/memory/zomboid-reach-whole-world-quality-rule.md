---
name: zomboid-reach-whole-world-quality-rule
description: STRICT (Josiah 2026-11-11 09:49 his clock): every visible, walkable or distant piece of Reach-world land gets real Reach terrain/textures/detail; no white patches, no missing sections
metadata:
  type: feedback
---
Josiah 2026-11-11 09:49: "Any and all visible, walkable or distant land needs the same level of reach terrain, textures and detail. We can't have random white patches or missing sections, the whole world needs to be of the same quality."
**Why:** cloud previews showed our generated land as flat colour (bleached white desert band) next to Halo's real far land around Tip of the Spear.
**How to apply:** the generated land (ReachLandMesh), the horizon land past the map edge (ReachHorizon) and anything else drawn must use Halo terrain shaders with real Reach textures (per climate sets, scn/landtex.py design), never plain vertex colour; check previews for holes/blank patches before showing. He picked "real Reach textures now" on the card 09:48.
Related: [[zomboid-reach-real-surroundings]], [[zomboid-reach-land-tech]], [[zomboid-reach-realism-direction]].
