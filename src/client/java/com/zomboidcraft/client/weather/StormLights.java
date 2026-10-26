package com.zomboidcraft.client.weather;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The city's last power is shaky: when lightning comes down close, the lamps and neon round you
 * stutter, a few drop out for a while, and they come back one by one. Only on your side, and
 * only for show: each lamp is swapped for its dead version and put back exactly as it was.
 */
final class StormLights {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int REACH = 22, BELOW = 6, ABOVE = 16;
    /** At most this many lamps stutter at once: each swap relights the area, which isn't free. */
    private static final int MAX = 14;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final List<Lamp> LAMPS = new ArrayList<>();
    private static int cooldown;

    /** A lamp going through its stutter: off and on at the listed ticks, back to lit for good at the end. */
    private static final class Lamp {
        final BlockPos pos;
        final BlockState lit, dead;
        final int[] switches;
        int at, step;

        Lamp(BlockPos pos, BlockState lit, BlockState dead, int[] switches) {
            this.pos = pos;
            this.lit = lit;
            this.dead = dead;
            this.switches = switches;
        }
    }

    /** Lightning close by: set the lamps round center stuttering. */
    static void surge(ClientLevel level, BlockPos center) {
        if (cooldown > 0 || !LAMPS.isEmpty()) {
            return;
        }
        cooldown = 100;
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                for (int y = -BELOW; y <= ABOVE; y++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (dead(level.getBlockState(pos)) != null) {
                        found.add(pos.immutable());
                    }
                }
            }
        }
        for (int i = 0; i < MAX && !found.isEmpty(); i++) {
            BlockPos at = found.remove(RANDOM.nextInt(found.size()));
            BlockState lit = level.getBlockState(at);
            LAMPS.add(new Lamp(at, lit, dead(lit), pattern()));
        }
    }

    /** Off, on, off... in ticks from now; a fifth of them stay dark a good while before coming back. */
    private static int[] pattern() {
        int flickers = 1 + RANDOM.nextInt(3);
        boolean brownout = RANDOM.nextFloat() < 0.2f;
        int[] out = new int[flickers * 2];
        int t = RANDOM.nextInt(6);
        for (int i = 0; i < out.length; i++) {
            out[i] = t;
            boolean last = i == out.length - 1;
            t += last && brownout ? 100 + RANDOM.nextInt(200) : 2 + RANDOM.nextInt(i % 2 == 0 ? 4 : 8);
        }
        if (brownout) {
            out[out.length - 1] = t;
        }
        return out;
    }

    static void tick(ClientLevel level) {
        if (cooldown > 0) {
            cooldown--;
        }
        LAMPS.removeIf(lamp -> {
            lamp.at++;
            while (lamp.step < lamp.switches.length && lamp.at >= lamp.switches[lamp.step]) {
                boolean off = lamp.step % 2 == 0;
                BlockState now = level.getBlockState(lamp.pos);
                // only touch it if nobody changed it meanwhile
                if (now == (off ? lamp.lit : lamp.dead)) {
                    level.setBlock(lamp.pos, off ? lamp.dead : lamp.lit, FLAGS);
                }
                lamp.step++;
            }
            if (lamp.step >= lamp.switches.length) {
                if (level.getBlockState(lamp.pos) == lamp.dead) {
                    level.setBlock(lamp.pos, lamp.lit, FLAGS);
                }
                return true;
            }
            return false;
        });
    }

    /** The dead version of a lamp, or null when the block isn't one. */
    private static BlockState dead(BlockState state) {
        if (state.is(Blocks.PEARLESCENT_FROGLIGHT) || state.is(Blocks.VERDANT_FROGLIGHT) || state.is(Blocks.OCHRE_FROGLIGHT)) {
            return Blocks.SMOOTH_QUARTZ.defaultBlockState();
        }
        if (state.getLightEmission() == 0) {
            return null;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("zomboidcraft")) {
            return null;
        }
        String path = id.getPath();
        String off = path.equals("window_lit") ? "window" : path + "_off";
        Identifier offId = Identifier.fromNamespaceAndPath(id.getNamespace(), off);
        if (!BuiltInRegistries.BLOCK.containsKey(offId)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getValue(offId).withPropertiesOf(state);
    }

    /** Drops what's in hand without touching the world (it was another world's). */
    static void forget() {
        LAMPS.clear();
    }

    private StormLights() {
    }
}
