package com.zomboidcraft.world;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.blocks.CityBlocks;
import com.zomboidcraft.world.blocks.Fittings;
import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.CityDemo;
import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.Cars;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the test downtown ({@link CityDemo}) into a world with the city's own blocks (or vanilla
 * stand-ins, with the world.cityStandIns setting), a few milliseconds of work per tick so the
 * server keeps running. The generator writes into 16-block sections kept in memory; the placer
 * then copies them section by section, clearing hills and trees in the lower floors so the
 * streets aren't buried.
 */
public final class CityPlacer implements BlockSink {
    /** Where the player stands in the generator's coordinates: the south end of the main avenue. */
    private static final int START_X = 2, START_Z = 60;
    private static final int CLEAR_HEIGHT = 48;
    private static final long BUDGET_NANOS = 25_000_000L;
    private static final int FLAGS = 2 | 16;  // tell clients, but don't wake neighbours or pop plants

    private static CityPlacer running;

    private final Map<Long, short[]> sections = new HashMap<>();
    private final List<String> palette = new ArrayList<>(List.of("air"));
    private final Map<String, Short> ids = new HashMap<>(Map.of("air", (short) 0));
    private final ArrayDeque<long[]> queue = new ArrayDeque<>();
    /** Cars waiting to be spawned, by the section they stand in (it's loaded once we place it). */
    private final Map<Long, List<Parked>> cars = new HashMap<>();
    private final ServerLevel level;
    private final BlockPos origin;
    private final MinecraftServer server;
    private int total;
    private int done;
    private int lastReport;

    private CityPlacer(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
        this.server = level.getServer();
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (running != null && running.server == server) {
                running.tick();
            }
        });
    }

    /** Starts building with the player at the avenue's south end, facing north. False if one is already going. */
    public static boolean start(ServerLevel level, BlockPos feet, long seed) {
        if (running != null) {
            return false;
        }
        CityPlacer placer = new CityPlacer(level, feet.offset(-START_X, -1, -START_Z));
        new CityDemo(placer, seed).build();
        placer.plan();
        running = placer;
        return true;
    }

    private record Parked(double x, int y, double z, float yaw, String model, String variant) {
    }

    private static long key(int sx, int sy, int sz) {
        return ((long) sx & 0x1FFFFF) << 42 | ((long) sz & 0x1FFFFF) << 21 | ((long) sy & 0x1FFFFF);
    }

    @Override
    public void set(int x, int y, int z, String block) {
        if (y < 0) {
            return;
        }
        Short id = ids.get(block);
        if (id == null) {
            id = (short) palette.size();
            palette.add(block);
            ids.put(block, id);
        }
        short[] s = sections.get(key(x >> 4, y >> 4, z >> 4));
        if (s == null) {
            if (id == 0) {
                return;
            }
            s = new short[4096];
            sections.put(key(x >> 4, y >> 4, z >> 4), s);
        }
        s[(y & 15) << 8 | (z & 15) << 4 | (x & 15)] = id;
    }

    /** Picks the model and how wrecked it is now, lays its hull blocks, and spawns it with its section. */
    @Override
    public void car(double x, int y, double z, float yaw, double minLength, double maxLength, long seed) {
        if (!ZcConfig.get().world.streetCars || y < 0) {
            return;
        }
        SplittableRandom rng = new SplittableRandom(seed);
        CarModels.Model model = CarModels.pick(rng, m -> !m.jet() && m.length() >= minLength && m.length() <= maxLength);
        if (model == null) {
            return;
        }
        for (int[] col : Cars.hullColumns(model, x, z, yaw)) {
            int[] layers = Cars.layers(col, 0);
            for (int i = 0; i < layers.length; i++) {
                if (layers[i] != 0 && get(col[0], y + i, col[1]) == null) {
                    set(col[0], y + i, col[1], "car_hull[code=" + layers[i] + "]");
                }
            }
        }
        cars.computeIfAbsent(key((int) Math.floor(x) >> 4, y >> 4, (int) Math.floor(z) >> 4), k -> new ArrayList<>())
                .add(new Parked(x, y, z, yaw, model.id, Cars.pickVariant(rng)));
    }

    @Override
    public String get(int x, int y, int z) {
        short[] s = y < 0 ? null : sections.get(key(x >> 4, y >> 4, z >> 4));
        short id = s == null ? 0 : s[(y & 15) << 8 | (z & 15) << 4 | (x & 15)];
        return id == 0 ? null : palette.get(id);
    }

    /** Every section of the city's footprint in the clearing height, plus every section with blocks above it. */
    private void plan() {
        for (int sx = (CityDemo.MIN_X - 8) >> 4; sx <= (CityDemo.MAX_X + 8) >> 4; sx++) {
            for (int sz = (CityDemo.MIN_Z - 8) >> 4; sz <= (CityDemo.MAX_Z + 8) >> 4; sz++) {
                for (int sy = 0; sy < 64; sy++) {
                    if (sy * 16 < CLEAR_HEIGHT || sections.containsKey(key(sx, sy, sz))) {
                        queue.add(new long[] {sx, sy, sz});
                    }
                }
            }
        }
        total = queue.size();
    }

    private void tick() {
        long end = System.nanoTime() + BUDGET_NANOS;
        while (!queue.isEmpty() && System.nanoTime() < end) {
            long[] next = queue.poll();
            place((int) next[0], (int) next[1], (int) next[2]);
            done++;
        }
        int percent = total == 0 ? 100 : done * 100 / total;
        if (queue.isEmpty()) {
            running = null;
            sections.clear();
            cars.clear();
            broadcast("City built. Walk north up the avenue.");
        } else if (percent >= lastReport + 10) {
            lastReport = percent - percent % 10;
            broadcast("Building city: " + lastReport + "%");
        }
    }

    private void place(int sx, int sy, int sz) {
        short[] s = sections.get(key(sx, sy, sz));
        BlockState[] states = new BlockState[palette.size()];
        String[] loot = new String[palette.size()];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = level.getMaxY();
        for (int i = 0; i < 4096; i++) {
            short id = s == null ? 0 : s[i];
            int x = sx * 16 + (i & 15), z = sz * 16 + (i >> 4 & 15), y = sy * 16 + (i >> 8);
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (pos.getY() > top) {
                continue;
            }
            BlockState want;
            if (id == 0) {
                if (y >= CLEAR_HEIGHT || y == 0 || level.getBlockState(pos).isAir()) {
                    continue;
                }
                want = Blocks.AIR.defaultBlockState();
            } else {
                if (states[id] == null) {
                    String name = palette.get(id);
                    BlockState real = name.startsWith("car_hull") ? Cars.hull(Integer.parseInt(name.replaceAll("\\D", "")))
                            : ZcConfig.get().world.cityStandIns ? null : CityBlocks.resolve(name);
                    states[id] = real != null ? real : StandIns.resolve(name);
                    loot[id] = lootTable(name);
                }
                want = states[id];
            }
            level.setBlock(pos, want, FLAGS);
            if (id != 0 && loot[id] != null) {
                Fittings.stock(level.getBlockEntity(pos), loot[id]);
            }
        }
        List<Parked> here = cars.remove(key(sx, sy, sz));
        if (here != null) {
            for (Parked car : here) {
                Cars.spawn(level, origin.getX() + car.x(), origin.getY() + car.y(), origin.getZ() + car.z(), car.yaw(),
                        car.model(), car.variant(), false);
            }
        }
    }

    /** The shop loot table a fitting's name carries, as in "store_cooler[facing=west,loot=drinks]". */
    private static String lootTable(String name) {
        int at = name.indexOf("loot=");
        if (at < 0) {
            return null;
        }
        int end = at + 5;
        while (end < name.length() && name.charAt(end) != ',' && name.charAt(end) != ']') {
            end++;
        }
        return name.substring(at + 5, end);
    }

    private void broadcast(String text) {
        ZomboidCraft.LOGGER.info(text);
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}
