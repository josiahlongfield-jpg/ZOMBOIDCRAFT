package com.zomboidcraft.world.gen;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.StandIns;
import com.zomboidcraft.world.blocks.CityBlocks;
import com.zomboidcraft.world.blocks.Fittings;
import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Cars;
import com.zomboidcraft.world.county.CarSpacing;
import com.zomboidcraft.world.county.ChunkSink;
import java.util.EnumSet;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Writes {@link com.zomboidcraft.world.county.CountyGen}'s blocks straight into a chunk's sections. */
public final class ChunkWriter implements ChunkSink {
    private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ChunkAccess chunk;
    private final WorldGenRegion region;
    private final int bx, bz, minY, maxY;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    ChunkWriter(ChunkAccess chunk, WorldGenRegion region) {
        this.chunk = chunk;
        this.region = region;
        this.bx = chunk.getPos().getMinBlockX();
        this.bz = chunk.getPos().getMinBlockZ();
        this.minY = chunk.getMinY();
        this.maxY = chunk.getMinY() + chunk.getHeight() - 1;
    }

    public static BlockState resolve(String name) {
        BlockState s = STATES.get(name);
        if (s == null) {
            BlockState real = name.equals("air") ? AIR : name.startsWith("minecraft:")
                    || ZcConfig.get().world.cityStandIns && !name.startsWith(ZomboidCraft.MOD_ID + ":") ? null : CityBlocks.resolve(name);
            s = real != null ? real : StandIns.resolve(name);
            STATES.put(name, s);
        }
        return s;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public void set(int x, int y, int z, String block) {
        if (y < minY || y > maxY || x < bx || x > bx + 15 || z < bz || z > bz + 15) {
            return;
        }
        BlockState state = resolve(block);
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(x & 15, y & 15, z & 15, state, false);
        if (state.hasBlockEntity() && state.getBlock() instanceof EntityBlock eb) {
            BlockPos at = new BlockPos(x, y, z);
            BlockEntity entity = eb.newBlockEntity(at, state);
            if (entity != null) {
                chunk.setBlockEntity(entity);
                String loot = lootTable(block);
                if (loot != null) {
                    Fittings.stock(entity, loot);
                }
            }
        } else if ((!state.getFluidState().isEmpty() || needsUpdate(state)) && chunk instanceof ProtoChunk proto) {
            proto.markPosForPostProcessing(pos.set(x, y, z));
        }
    }

    @Override
    public void column(int x, int z, int y0, int y1, String block) {
        if (x < bx || x > bx + 15 || z < bz || z > bz + 15) {
            return;
        }
        BlockState state = resolve(block);
        for (int y = Math.max(y0, minY); y <= Math.min(y1, maxY); y++) {
            chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x & 15, y & 15, z & 15, state, false);
        }
    }

    /** Blocks whose shape depends on their neighbours (fences, panes, walls, stairs) get fixed up later. */
    private static boolean needsUpdate(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.CrossCollisionBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.WallBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.StairBlock;
    }

    @Override
    public void car(double x, int y, double z, float yaw, double minLength, double maxLength, long seed) {
        spawn(x, y, z, yaw, filter(minLength, maxLength, null), seed);
    }

    @Override
    public void vehicle(double x, int y, double z, float yaw, String models, long seed) {
        spawn(x, y, z, yaw, filter(0, 99, models), seed);
    }

    /**
     * The models a builder's car may be: any non-aircraft between the lengths when no names are
     * given, else the named models or kinds, or exactly one model when pinned as "=id".
     */
    static java.util.function.Predicate<CarModels.Model> filter(double minLength, double maxLength, String models) {
        if (models == null) {
            return m -> !m.jet() && m.length() >= minLength && m.length() <= maxLength;
        }
        if (models.startsWith("=")) {
            String id = models.substring(1);
            return m -> m.id.equals(id);
        }
        // a builder may name a model twice to favour it; Set.of would throw on the repeat
        java.util.Set<String> names = new java.util.HashSet<>(java.util.Arrays.asList(models.split(",")));
        return m -> names.contains(m.id) || names.contains(m.kind);
    }

    /** The car models' bodies, for {@link CarSpacing}: picked from the same seed and filter as {@link #spawn}. */
    static final CarSpacing.Bodies BODIES = new CarSpacing.Bodies() {
        @Override
        public String pick(double minLength, double maxLength, String models, long seed) {
            CarModels.Model m = CarModels.pick(new SplittableRandom(seed), filter(minLength, maxLength, models));
            return m == null ? null : m.id;
        }

        @Override
        public boolean covers(String model, double lx, double lz) {
            return CarModels.get(model).heightAt(lx, lz) > 0.15f;
        }

        @Override
        public double reach(String model) {
            CarModels.Model m = CarModels.get(model);
            return Math.hypot(m.size[0], m.size[2]) / 2;
        }

        @Override
        public boolean tight(String model) {
            return CarModels.get(model).jet();
        }
    };

    private void spawn(double x, int y, double z, float yaw, java.util.function.Predicate<CarModels.Model> filter, long seed) {
        if (!ZcConfig.get().world.streetCars || !(chunk instanceof ProtoChunk proto)) {
            return;
        }
        SplittableRandom rng = new SplittableRandom(seed);
        CarModels.Model model = CarModels.pick(rng, filter);
        if (model == null) {
            return;
        }
        CarProp car = Cars.CAR.create(region.getLevel(), EntitySpawnReason.STRUCTURE);
        if (car == null) {
            return;
        }
        // stand it on whatever is under it: roads are 14/16 of a block tall
        double ground = y;
        int bxx = (int) Math.floor(x), bzz = (int) Math.floor(z);
        if (bxx >= bx && bxx <= bx + 15 && bzz >= bz && bzz <= bz + 15 && y - 1 >= minY) {
            BlockState below = chunk.getBlockState(pos.set(bxx, y - 1, bzz));
            VoxelShape shape = below.getCollisionShape(EmptyBlockGetter.INSTANCE, pos);
            if (!shape.isEmpty()) {
                ground = y - 1 + shape.max(Direction.Axis.Y);
            }
        }
        car.setLook(model.id, Cars.pickVariant(rng));
        car.snapTo(x, ground, z, yaw, 0f);
        proto.addEntity(car);
        int base = (int) Math.ceil(ground - 0.05);
        int lift = (int) Math.round((base - ground) * 100);
        for (int[] col : Cars.hullColumns(model, x, z, yaw)) {
            int[] layers = Cars.layers(col, lift);
            for (int i = 0; i < layers.length; i++) {
                if (layers[i] != 0 && col[0] >= bx && col[0] <= bx + 15 && col[1] >= bz && col[1] <= bz + 15) {
                    BlockState there = chunk.getBlockState(pos.set(col[0], base + i, col[1]));
                    if (there.canBeReplaced()) {
                        chunk.getSection(chunk.getSectionIndex(base + i)).setBlockState(col[0] & 15, (base + i) & 15, col[1] & 15,
                                Cars.hull(layers[i]), false);
                    }
                }
            }
        }
    }

    void finish() {
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
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
}
