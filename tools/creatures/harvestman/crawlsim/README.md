# Harvestman crawl sim

A Python port of the Harvestman's crawl (HarvestmanEntity.crawl and its leg support) and its route-finding
(HarvestmanRoute), in voxel worlds, so changes to either can be tried before CI and a game. Keep it in step with the Java.

    python3 walls.py     walls, doorways, a house's back door, a one-wide door in a tall wall, over a low wall, a sealed box
    python3 old.py       the climbing tests: a room's corner, wall and ceiling, roof to ground and back, prey upstairs

Each scenario prints whether it reached the goal, where it ended, and how many ticks it spent floating or inside a block.
