---
name: zomboid-landscape-direction
description: Josiah's rules for ZomboidCraft world generation (new landscape, many variants of every place type, match the concept sheets, finite urban county)
metadata:
  type: feedback
  modified: 2026-10-25T13:38:00.000Z
---

(2026-10-25, thread "Build the ZomboidCraft landscape") Josiah: "using the references youve got access to for the landscapes, buildings, locations etc, i need you to get stuck into building the rest of the world" and "we need multiple differing versions of each location, differing by size, function, style etc. not just one of each. spare no details and produce a result as close to the references youve been supplied as possible".

**Why:** the world must feel like a full new game, not one stamped template per place. References: project files zomboid-mod/reference/areas-concepts-a.png and -b.png (port, suburbs, military base / quarantine zone, small town, countryside farms, skyscraper interiors, downtown with elevated rail, hospital, police, subway).

**How to apply:**
- Every place type gets several seeded variants that differ in size, function and style.
- Detail level and look follow the concept sheets, [[josiah-world-art-direction]] and [[zomboid-building-detail-direction]].
- Start building rather than wait on a plan, but send previews (Artifact pages) as work lands. See [[zomboid-mod-status]], [[zomboid-playtest-build]].

**Finite map (12:31-12:43):** "make it a massive map like zomboid has thats fully discoverable but isnt infinite". He PICKED one fixed hand-planned master plan with procedural buildings inside, plus a world border ("huge amounts of immersion"). The county is ~16 x 16 km, ringed by the Exclusion Zone fence. Today's County.java is still infinite (one place per 512-block cell).

**Not Zomboid's map (13:16):** "we dont have to mimic the zomboid map, i still want all of the reference locations ... the port we will need a seaside city with docks ... make sure the land and sea are made good use of".

**Urban first (13:21):** "I'm pushing for the urban environment like big cities, big port and docks with massive cargo ships, crane and the like. There can be a forest area in a mountain scape but prioritise the urban feel". So: mostly metropolis, a megaport, one mountain forest area, small farms.

The plan drafts and how to read his edits: [[zomboid-county-plan]].

- Trees (Josiah 2026-10-26 09:39): he may buy low-poly tree packs and wants ALL trees replaced with them, redesigned to our style. Agreed approach (offered, awaiting the packs): originals stay out of the repo like the bought jets; each model is turned into chunky custom blocks (trunk, branch, leaf pieces finer than a block) so it stays blocky, fast and choppable. Asked him for Kentucky species (oak, maple, hickory, sycamore, pine, cedar, birch, dead/storm-broken, bushes), several sizes, bare/autumn variants, OBJ/FBX with textures, a game-use licence. Current trees: county/Trees.java (Kind OAK, MAPLE, SYCAMORE, PINE, DEAD, SICK, BUSH, YOUNG).
