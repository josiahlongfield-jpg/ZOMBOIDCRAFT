package com.zomboidcraft.world.gen;

import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Districts;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The county's power districts ({@link Districts}) as the game sees them, for LOOT's grid
 * (com.zomboidcraft.power.PowerGrid): which district a spot is on, and where each district's substation yard
 * stands. In a world that isn't a county (or one from before the plan) there are no districts: at() is null
 * and yards() empty, and the grid should treat the whole world as one.
 */
public final class PowerDistricts {
    private PowerDistricts() {
    }

    /** The district whose substation feeds this spot, or null outside a planned county. */
    public static Districts.District at(ServerLevel level, BlockPos pos) {
        County county = county(level);
        return county == null ? null : Districts.district(county.terrain.plan, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    /** The district's id at this spot ("midtown", "millbrook", "island", ...), or null outside a planned county. */
    public static String id(ServerLevel level, BlockPos pos) {
        Districts.District d = at(level, pos);
        return d == null ? null : d.id();
    }

    /**
     * Every district with its substation yard's middle (y just above the ground there),
     * in Districts.DISTRICTS order; a district whose yard isn't in this world is left out.
     */
    public static Map<Districts.District, BlockPos> yards(ServerLevel level) {
        Map<Districts.District, BlockPos> out = new LinkedHashMap<>();
        County county = county(level);
        if (county == null) {
            return out;
        }
        for (Districts.District d : Districts.DISTRICTS) {
            int[] c = Districts.substation(county, d);
            if (c != null) {
                int y = (int) Math.floor(county.ground(c[0], c[1], county.placesNear(c[0], c[1], c[0], c[1], County.SKIRT))) + 1;
                out.put(d, new BlockPos(c[0], y, c[1]));
            }
        }
        return out;
    }

    private static County county(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof CountyChunkGenerator)) {
            return null;
        }
        County county = CountyChunkGenerator.gen(level.getSeed()).county();
        return county.planned() ? county : null;
    }
}
