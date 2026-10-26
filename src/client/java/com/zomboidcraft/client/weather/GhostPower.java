package com.zomboidcraft.client.weather;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Somewhere the grid still twitches. Every few minutes a stretch of the dead city round you
 * comes back: dead streetlights, signs and a scatter of windows stutter on, hum for half a
 * minute or so with the odd flicker, then die again one by one. Only on your side, and only for
 * show: every block is put back exactly as it was.
 */
final class GhostPower {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int REACH = 40, BELOW = 8, ABOVE = 30;
    /** Each switch relights the area round the block, so only so many at once. */
    private static final int MAX = 36;
    /** A dead window comes on only now and then; signs and lamps are worth more. */
    private static final float WINDOW_SHARE = 0.12f;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final List<Lamp> LAMPS = new ArrayList<>();
    private static int wait = 1200 + RANDOM.nextInt(2400);

    /** A dead lamp brought back: on and off at the listed ticks, dark for good at the end. */
    private static final class Lamp {
        final BlockPos pos;
        final BlockState dead, lit;
        final int[] switches;
        int at, step;

        Lamp(BlockPos pos, BlockState dead, BlockState lit, int[] switches) {
            this.pos = pos;
            this.dead = dead;
            this.lit = lit;
            this.switches = switches;
        }
    }

    static void tick(ClientLevel level, BlockPos center, float minutes) {
        if (LAMPS.isEmpty() && --wait <= 0) {
            wait = Math.round((2400 + RANDOM.nextInt(4800)) * Math.max(0.2f, minutes));
            surge(level, center);
        }
        LAMPS.removeIf(lamp -> {
            lamp.at++;
            while (lamp.step < lamp.switches.length && lamp.at >= lamp.switches[lamp.step]) {
                boolean on = lamp.step % 2 == 0;
                // only touch it if nobody changed it meanwhile
                if (lamp.lit != null && level.getBlockState(lamp.pos) == (on ? lamp.dead : lamp.lit)) {
                    level.setBlock(lamp.pos, on ? lamp.lit : lamp.dead, FLAGS);
                }
                lamp.step++;
            }
            if (lamp.step >= lamp.switches.length) {
                if (level.getBlockState(lamp.pos) == lamp.lit) {
                    level.setBlock(lamp.pos, lamp.dead, FLAGS);
                }
                return true;
            }
            return false;
        });
    }

    private static void surge(ClientLevel level, BlockPos center) {
        // keep the lit state from the scan: lit() rolls the window share, so asking again can say no
        List<Lamp> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                for (int y = -BELOW; y <= ABOVE; y++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState dead = level.getBlockState(pos);
                    BlockState lit = lit(dead);
                    if (lit != null) {
                        found.add(new Lamp(pos.immutable(), dead, lit, null));
                    }
                }
            }
        }
        // the power comes back all together, then each lamp holds on as long as it can
        int hold = 400 + RANDOM.nextInt(800);
        for (int i = 0; i < MAX && !found.isEmpty(); i++) {
            Lamp at = found.remove(RANDOM.nextInt(found.size()));
            LAMPS.add(new Lamp(at.pos, at.dead, at.lit, pattern(hold)));
        }
    }

    /** On, off, on... in ticks from now: a stutter coming on, a long hum with the odd blink, a stutter going out. */
    private static int[] pattern(int hold) {
        List<Integer> out = new ArrayList<>();
        int t = RANDOM.nextInt(30);
        for (int i = 0, n = 1 + RANDOM.nextInt(3); i < n; i++) {
            out.add(t);
            t += 2 + RANDOM.nextInt(5);
            out.add(t);
            t += 2 + RANDOM.nextInt(8);
        }
        out.add(t);  // on for the hum
        int end = t + hold + RANDOM.nextInt(200);
        while (RANDOM.nextFloat() < 0.5f) {
            t += 60 + RANDOM.nextInt(240);
            if (t + 6 >= end) {
                break;
            }
            out.add(t);  // a blink
            t += 2 + RANDOM.nextInt(4);
            out.add(t);
        }
        for (int i = 0, n = RANDOM.nextInt(3); i < n; i++) {
            out.add(end);  // dying: a last stutter
            end += 2 + RANDOM.nextInt(6);
            out.add(end);
            end += 2 + RANDOM.nextInt(4);
        }
        out.add(end);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The lit version of a dead lamp, sign or window, or null when the block isn't one. */
    private static BlockState lit(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("zomboidcraft")) {
            return null;
        }
        String path = id.getPath();
        String on;
        if (path.equals("window")) {
            if (RANDOM.nextFloat() >= WINDOW_SHARE) {
                return null;
            }
            on = "window_lit";
        } else if (path.endsWith("_off")) {
            on = path.substring(0, path.length() - 4);
        } else {
            return null;
        }
        Identifier onId = Identifier.fromNamespaceAndPath(id.getNamespace(), on);
        if (!BuiltInRegistries.BLOCK.containsKey(onId)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getValue(onId).withPropertiesOf(state);
    }

    /** Drops what's in hand without touching the world (it was another world's). */
    static void forget() {
        LAMPS.clear();
    }

    private GhostPower() {
    }
}
