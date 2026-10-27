package com.zomboidcraft.zombie.population;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where in a chunk a zombie could be standing: a floor under it, room for its body, and either a roof over it
 * (indoors, at any height: every floor of a tower) or open sky near the ground the world was made with (a street, a
 * yard, a field). Roofs, treetops and wall tops are never spots. The same test reads a chunk in the world and one the
 * census builds off to the side, so the counts agree.
 */
public final class Spots {
    /** Blocks of a chunk by local x and z and world y. */
    public interface Blocks {
        BlockState at(int lx, int y, int lz);
    }

    /** The first block above the ground the world was made with, by world x and z. */
    public interface Ground {
        int at(int x, int z);
    }

    public interface Visitor {
        void spot(int lx, int y, int lz, boolean indoor);
    }

    /** How far overhead a roof makes a spot indoors. */
    public static final int ROOF_REACH = 24;

    private static final byte STAND = 1, CLEAR = 2, ROOF = 4, TREE = 8, DECK = 16;
    private static final Map<BlockState, Byte> FLAGS = new ConcurrentHashMap<>();

    public static void scan(Blocks blocks, int bx, int bz, int minY, int maxY, Ground ground, Visitor visitor) {
        int h = maxY - minY;
        byte[] f = new byte[h];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int top = -1;
                for (int i = 0; i < h; i++) {
                    f[i] = flags(blocks.at(lx, minY + i, lz));
                    if ((f[i] & CLEAR) == 0) {
                        top = i;
                    }
                }
                if (top < 0) {
                    continue;
                }
                int surface = ground.at(bx + lx, bz + lz);
                // down the column, remembering the nearest roof above the head
                int roof = Integer.MAX_VALUE;
                for (int i = Math.min(top + 1, h - 2); i >= 1; i--) {
                    if (i + 2 < h && (f[i + 2] & ROOF) != 0) {
                        roof = i + 2;
                    }
                    if ((f[i - 1] & STAND) == 0 || (f[i] & CLEAR) == 0 || (f[i + 1] & CLEAR) == 0) {
                        continue;
                    }
                    boolean indoor = roof - i <= ROOF_REACH;
                    int y = minY + i;
                    if (!indoor && y > surface + 2 && (f[i - 1] & DECK) == 0) {
                        continue;   // up on a roof or a wall in the open
                    }
                    visitor.spot(lx, y, lz, indoor);
                }
            }
        }
    }

    /** Whether there's a roof over this spot (within reach), so it counts as indoors. */
    public static boolean roofed(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = 2; dy <= ROOF_REACH; dy++) {
            if ((flags(level.getBlockState(at.set(pos.getX(), pos.getY() + dy, pos.getZ()))) & ROOF) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Room for a zombie to stand here now (nothing built in the way since it was placed). */
    public static boolean free(BlockGetter level, BlockPos pos) {
        return (flags(level.getBlockState(pos.below())) & STAND) != 0 && (flags(level.getBlockState(pos)) & CLEAR) != 0
                && (flags(level.getBlockState(pos.above())) & CLEAR) != 0;
    }

    private static byte flags(BlockState state) {
        Byte f = FLAGS.get(state);
        if (f == null) {
            f = compute(state);
            FLAGS.put(state, f);
        }
        return f;
    }

    private static byte compute(BlockState state) {
        if (state.isAir()) {
            return CLEAR;
        }
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        boolean tree = state.is(BlockTags.LEAVES) || name.startsWith("tree_");
        boolean wet = !state.getFluidState().isEmpty();
        VoxelShape shape;
        try {
            shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            shape = null;   // a block whose shape needs a real world: treat it as solid
        }
        int f = 0;
        if (shape == null || !shape.isEmpty()) {
            double top = shape == null ? 1 : shape.max(net.minecraft.core.Direction.Axis.Y);
            double wide = shape == null ? 1 : Math.min(shape.bounds().getXsize(), shape.bounds().getZsize());
            if (!wet && !tree && top >= 0.5 && top <= 1.0) {
                f |= STAND;
            }
            if (!tree && wide >= 0.75) {
                f |= ROOF;
            }
            if (!wet && top <= 0.2) {
                f |= CLEAR;   // a carpet, a rug, a pressure plate: still room to stand
            }
        } else if (!wet) {
            f |= CLEAR;
        }
        if (tree) {
            f |= TREE;
        }
        if (name.startsWith("navy_fl") || name.startsWith("navy_catapult") || name.startsWith("navy_cable")) {
            f |= DECK;   // the carrier's flight deck is out in the open and well above the water, but it's a deck
        }
        return (byte) f;
    }

    private Spots() {
    }
}
