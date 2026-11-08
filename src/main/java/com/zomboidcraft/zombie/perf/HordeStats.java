package com.zomboidcraft.zombie.perf;

/**
 * Counts what the horde savings did (rested moves, collision searches skipped, paths shared or planned), for the
 * horde benchmark and /zc perf. Server thread only, so plain ints.
 */
public final class HordeStats {
    public static long rested, moved, quiet, shared, planned;

    public static void reset() {
        rested = moved = quiet = shared = planned = 0;
    }

    public static String line() {
        long moves = rested + moved, paths = shared + planned;
        return String.format(java.util.Locale.ROOT, "moves rested %d of %d (%.0f%%), collision searches skipped %d, paths shared %d of %d (%.0f%%)",
                rested, moves, moves == 0 ? 0 : 100.0 * rested / moves, quiet, shared, paths, paths == 0 ? 0 : 100.0 * shared / paths);
    }

    private HordeStats() {
    }
}
