---
name: zomboid-harvestman-route-tech
description: Harvestman route-finding round walls (HarvestmanRoute A*, gates for doorways, crawl hooks stay/slow, Python port to test changes) - 2026-10-28
metadata:
  type: project
---

Josiah 2026-10-28 11:46: it ran straight at prey behind a wall and stopped; it must recognise structures and find the entrances, and map them fast.

- creature/HarvestmanRoute: A* over cells its body can be in (air with a solid floor/wall/ceiling and the cell off it open; cost 1 floor, 1.4 wall, 2 ceiling; 18-neighbour, no flat corner cutting; floor-edge drops up to 16). MAX_NODES 12000, RANGE 48, replans when the goal moves >2.5 or stuck>10 (at most every 10 ticks); within 6 and clear it goes straight.
- Following: farthest point in sight with room for its body (3 rays, 0.7 apart), points pushed off walls and out round outside corners; a doorway/window/corridor ("narrow" cell) gets a gate 2.6 before it: slow (0.7) and faster turning, then along the line through its middle until its centre is 2.1 past (tail through).
- Crawl hooks in HarvestmanEntity: aim = route.aim(...) replaces goal for steering/drops; route.stay = don't climb what it meets (carry on); height() feels for lintels at foot level ahead and over its tail; creeping into an edge its legs can't pass starts the pivot; it only turns where fits().
- Test changes first in the Python port: tools/creatures/harvestman/crawlsim/ (simcore.py crawl, route.py, walls.py scenarios: door to one side, house back door, one-wide 2-high door in a tall wall, low wall, sealed box; old.py: room/roof/ground). Known rough: block stairs inside a two-storey house (it climbs the stair sides into the stairwell).

Related: [[zomboid-harvestman-game-tech]], [[zomboid-harvestman-den-tech]].
