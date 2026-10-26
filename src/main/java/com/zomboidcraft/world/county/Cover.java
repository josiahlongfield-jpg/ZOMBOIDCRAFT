package com.zomboidcraft.world.county;

/**
 * Swaps the vanilla grass, ferns and dry grass the builders ask for for the ground cover cut from
 * Josiah's grass pack (tools/nature/groundcover.py): a dozen grass clumps, green and gone to straw,
 * plumed grass and reeds two blocks high, seedlings, and clover, weeds and fallen twigs lying on the
 * ground. The city's painted tufts and weeds go the same way. The pick hangs on the spot, so a
 * tall plant's two halves always match.
 */
final class Cover implements ChunkSink {
    private static final int GRASS = 12, DRY = 6, TALL = 3, SPRIGS = 3, FLAT = 3;
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private final ChunkSink out;
    private final long seed;

    private Cover(ChunkSink out, long seed) {
        this.out = out;
        this.seed = seed;
    }

    static ChunkSink wrap(ChunkSink out, long seed) {
        return out instanceof Cover ? out : new Cover(out, seed);
    }

    @Override
    public void set(int x, int y, int z, String block) {
        out.set(x, y, z, block.startsWith("minecraft:") ? swap(x, z, block) : weeds(x, z, block));
    }

    /** The city's own painted tufts and weeds give way to the pack's too. */
    private String weeds(int x, int z, String block) {
        if (!block.startsWith("weeds") && !block.equals("grass_tuft")) {
            return block;
        }
        long h = Noise.hash(seed ^ 0x3EED5L, x, z);
        double r = Noise.unit(h);
        int v = (int) (h >>> 40);
        return switch (block) {
            case "grass_tuft" -> r < 0.6 ? "gc_grass_" + (1 + v % GRASS) : "gc_grass_dry_" + (1 + v % DRY);
            case "weeds" -> r < 0.4 ? "gc_sprig_" + (1 + v % SPRIGS) : r < 0.75 ? "gc_grass_dry_" + (1 + v % DRY)
                    : "gc_grass_" + (1 + v % GRASS);
            // the tallest clumps, 7 to 10
            case "weeds_tall" -> "gc_grass_" + (7 + v % 4);
            default -> block;
        };
    }

    private String swap(int x, int z, String block) {
        long h = Noise.hash(seed ^ 0x6C0FE5L, x, z);
        double r = Noise.unit(h);
        int v = (int) (h >>> 40);
        switch (block) {
            case "minecraft:short_grass" -> {
                if (r < 0.66) {
                    return "gc_grass_" + (1 + v % GRASS);
                } else if (r < 0.84) {
                    return "gc_grass_dry_" + (1 + v % DRY);
                } else if (r < 0.92) {
                    return flat((v & 1) == 0 ? "gc_clover_" : "gc_leaves_", v);
                }
                return "gc_sprig_" + (1 + v % SPRIGS);
            }
            case "minecraft:short_dry_grass", "minecraft:tall_dry_grass" -> {
                return "gc_grass_dry_" + (1 + v % DRY);
            }
            case "minecraft:tall_grass[half=lower]", "minecraft:tall_grass[half=upper]" -> {
                String half = block.endsWith("lower]") ? "_lower" : "_upper";
                return (r < 0.3 ? "gc_tall_dry_" : "gc_tall_") + (1 + v % TALL) + half;
            }
            case "minecraft:fern" -> {
                return r < 0.5 ? "gc_sprig_" + (1 + v % SPRIGS) : block;
            }
            default -> {
                if (block.startsWith("minecraft:leaf_litter") && r < 0.3) {
                    return flat("gc_twigs_", v);
                }
                return block;
            }
        }
    }

    private static String flat(String kind, int v) {
        return kind + (1 + v % FLAT) + "[facing=" + DIRS[(v >>> 8) & 3] + "]";
    }

    @Override
    public void column(int x, int z, int y0, int y1, String block) {
        out.column(x, z, y0, y1, block);
    }

    @Override
    public void car(double x, int y, double z, float yaw, double minLength, double maxLength, long seed) {
        out.car(x, y, z, yaw, minLength, maxLength, seed);
    }

    @Override
    public void vehicle(double x, int y, double z, float yaw, String models, long seed) {
        out.vehicle(x, y, z, yaw, models, seed);
    }

    @Override
    public int minY() {
        return out.minY();
    }
}
