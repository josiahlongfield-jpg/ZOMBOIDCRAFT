package com.zomboidcraft.loot;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Where a container is, for Zomboid's building-based loot settings (RuralLooted, MaximumLootedBuildingRooms).
 * Zomboid knows each building's rooms from its map; ours reads the county plan: a landmark place (its own
 * footprint, not a piece of the street grid) counts one room for every {@link #ROOM_AREA} square blocks of it,
 * and anything else counts as a normal-sized building. Outside a planned county nothing is rural or big.
 */
final class LootArea {
    /** Footprint per room for a landmark, grounds and corridors included. */
    private static final int ROOM_AREA = 100;

    private LootArea() {
    }

    /** Zomboid's Months since the Apocalypse, 0-12. */
    static int monthsSinceApo(ZcConfig.Loot cfg) {
        try {
            return Math.max(0, Math.min(12, Integer.parseInt(cfg.timeSinceApo.trim())));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Farms, roadside places and the open country outside the city and the towns' sprawl. */
    static boolean rural(ServerLevel level, BlockPos pos) {
        County county = county(level);
        if (county == null) {
            return false;
        }
        Place p = place(county, pos);
        if (p != null && (p.type == PlaceType.FARM || p.type == PlaceType.ROADSIDE)) {
            return true;
        }
        if (p != null && p.type != PlaceType.HIDEOUT && p.type != PlaceType.MILITARY && p.type != PlaceType.LANDMARK) {
            return false;
        }
        double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
        return !county.terrain.plan.inCore(x, z) && !county.terrain.plan.inSprawl(x, z);
    }

    /** About how many rooms the building holding this spot has (0 for a normal building or none). */
    static int rooms(ServerLevel level, BlockPos pos) {
        County county = county(level);
        if (county == null) {
            return 0;
        }
        Place p = place(county, pos);
        if (p == null || p.type != PlaceType.LANDMARK || p.tile != null) {
            return 0;
        }
        long area = (long) (p.x1 - p.x0 + 1) * (p.z1 - p.z0 + 1);
        return (int) Math.min(Integer.MAX_VALUE, area / ROOM_AREA);
    }

    private static Place place(County county, BlockPos pos) {
        int x = pos.getX(), z = pos.getZ();
        Place best = null;
        for (Place p : county.placesNear(x, z, x, z, 0)) {
            if (x >= p.x0 && x <= p.x1 && z >= p.z0 && z <= p.z1) {
                // the smallest footprint holding the spot: a landmark inside a district tile wins
                if (best == null || area(p) < area(best)) {
                    best = p;
                }
            }
        }
        return best;
    }

    private static long area(Place p) {
        return (long) (p.x1 - p.x0 + 1) * (p.z1 - p.z0 + 1);
    }

    private static County county(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof CountyChunkGenerator)) {
            return null;
        }
        County county = CountyChunkGenerator.gen(level.getSeed()).county();
        return county.planned() ? county : null;
    }
}
