---
name: zomboid-access-rule
description: RULE (Josiah 2026-10-27): every stairwell, doorway and roof must be walkable by players and zombies; roof access huts on buildings with stairs
metadata:
  type: feedback
  modified: 2026-10-27T23:40:00.000Z
---

Josiah 2026-10-27 03:51: most stairwells could not be used. Block heights (half-height
furniture/fit-out blocks, ceilings) and collision stopped players and zombies, and no building
had roof access.

RULE for every building, structure, bunker and ship, old and new:
- Stairwells at least 2 wide where possible and full headroom: 4 CLEAR blocks over every
  tread (Josiah 2026-10-27 22:59: no head collisions on stairs; a jump on a stair reaches ~4.05
  over the tread block, so 3 clear bumps). Includes roofs, ceilings, beams AND the flight above:
  never stack flights one storey (4) apart.
- Entrances bigger by default (2-wide doors where the facade allows), no collision lips.
- Every building with stairs gets a stair up to the roof ending in a small roof hut sealed
  by a door, so players and zombies can walk onto roofs.
- Nothing at head height over any walkway: awnings, canopies, signs and lamps leave 2 full
  blocks clear (a 04:07 screenshot: a sagging blue awning fold at 1.75 trapped players and zombies).
- Every interior and exterior doorway walkable from both sides.
- Check new builds with the offline walk checker before shipping (see [[zomboid-world-tech]]).

**Why:** he found stairwells unusable in game; zombies must path through buildings too.
**How to apply:** audit every builder that places stairs; never ship a stair without the
headroom check passing.

HATCH RULE (Josiah 04:24): a trapdoor/hatch over a ladder hinges on the wall OPPOSITE the
ladder (trapdoor facing = opposite of the ladder's facing), so the open leaf never blocks the
climb out. Builders.fitHatches enforces it for every place; vanilla trapdoors over ladders
become bunk_hatch_wood (climbable). Roofs: RoofHut.build (county) puts the hut over a stair's
head; Flats.stairwell(hutWall) and Flats.porches(toRoof) already use it.

ORIENTATION RULE (Josiah 2026-10-27 04:14, screenshot of a gallery rail): every linear piece
(rails, banisters, bar rails, fences, windows, panes, kerbs) must run along its line: a row of
them is turned to the row's direction and joins up, never a row of stubs turned across it (the
same bug as windows turned the wrong way). tools/county/orientcheck.py DUMP lists offenders;
run it on every build before shipping.
