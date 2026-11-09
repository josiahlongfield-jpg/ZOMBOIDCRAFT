package com.zomboidcraft.world.reach;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * The Halo Reach world (Josiah 2026-11-08: a brand new world beside the county, which stays whole as its own world
 * type). Nothing is generated from noise: every block is one of a Halo level's invisible walls, laid from the files in
 * config/zomboidcraft/reach/world (see {@link ReachVoxels}); the level's real meshes are drawn over them on the client.
 * Between the levels, the land from {@link ReachLand} is laid the same way. Where no file reaches, the chunk is left empty. The world is deep and tall (its dimension type runs from y -1024 to
 * 1535) so every level keeps its real heights: The Package's underground lab, the Aszod cliffs, all of it.
 */
public final class ReachChunkGenerator extends ChunkGenerator {
    public static final MapCodec<ReachChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, ReachChunkGenerator::new));

    public static final int MIN_Y = -1024, HEIGHT = 2560;

    public ReachChunkGenerator(BiomeSource biomes) {
        super(biomes);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState random,
            StructureManager structures, BiomeManager biomes, WorldGenRegion region, Set<Holder<Biome>> possible) {
        try {
            lay(chunk);
        } catch (RuntimeException e) {
            ZomboidCraft.LOGGER.error("Exception laying Reach chunk {}", chunk.getPos(), e);
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    private static void lay(ChunkAccess chunk) {
        layWalls(chunk);
        ReachLand land = ReachLand.get();
        if (land != null) {
            layLand(chunk, land);
        }
    }

    /**
     * The land between the levels: per column, a crust from just under the lowest neighbouring ground up to the ground
     * here, its top block shaped to the ground's height in each quarter (so slopes walk smoothly). Skipped inside a
     * level's footprint, where the level's own walls are.
     */
    private static void layLand(ChunkAccess chunk, ReachLand land) {
        int min = chunk.getMinY(), max = chunk.getMaxY();
        ReachSolid solid = (ReachSolid) ReachWorld.SOLID;
        BlockState whole = solid.defaultBlockState();
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = bx + lx, z = bz + lz;
                if (!land.covers(x, z) || ReachLand.kind(land.flagsAt(x + 0.5, z + 0.5)) == ReachLand.FOOTPRINT) {
                    continue;
                }
                int[] col = landColumn(land, x, z);
                int bottom = Math.max(min, col[0]), yTop = col[1], code = col[2];
                for (int y = bottom; y <= Math.min(max, yTop); y++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    int c = y == yTop ? code : ReachSolid.WHOLE;
                    BlockState old = section.getBlockState(lx, y & 15, lz);
                    if (old.is(ReachWorld.SOLID)) {
                        c = ReachSolid.max(c, ReachSolid.code(old));
                    }
                    section.setBlockState(lx, y & 15, lz, c == ReachSolid.WHOLE ? whole : solid.state(c), false);
                }
            }
        }
    }

    /** {lowest block, top block, top block's quarter code} of the land in block column (x, z). */
    static int[] landColumn(ReachLand land, int x, int z) {
        float nw = land.height(x + 0.25, z + 0.25), ne = land.height(x + 0.75, z + 0.25);
        float sw = land.height(x + 0.25, z + 0.75), se = land.height(x + 0.75, z + 0.75);
        float top = Math.max(Math.max(nw, ne), Math.max(sw, se));
        int yTop = (int) Math.floor(top);
        int code = eighths(nw, yTop) | eighths(ne, yTop) << 4 | eighths(sw, yTop) << 8 | eighths(se, yTop) << 12;
        if (code == 0) {
            code = ReachSolid.WHOLE;
        }
        float low = top;
        for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            low = Math.min(low, land.height(x + 0.5 + d[0], z + 0.5 + d[1]));
        }
        int bottom = Math.min(yTop - 3, (int) Math.floor(low) - 2);
        return new int[] {bottom, yTop, code};
    }

    private static int eighths(float h, int yTop) {
        return Math.max(0, Math.min(8, (int) Math.ceil((h - yTop) * 8 - 1e-3)));
    }

    private static void layWalls(ChunkAccess chunk) {
        List<ReachVoxels.Run>[] cols = ReachVoxels.get().columns(chunk.getPos().x(), chunk.getPos().z());
        if (cols == null) {
            return;
        }
        int min = chunk.getMinY(), max = chunk.getMaxY();
        ReachSolid solid = (ReachSolid) ReachWorld.SOLID;
        BlockState whole = solid.defaultBlockState();
        for (int c = 0; c < 256; c++) {
            List<ReachVoxels.Run> runs = cols[c];
            if (runs == null) {
                continue;
            }
            int lx = c & 15, lz = c >> 4;
            for (ReachVoxels.Run r : runs) {
                int last = r.y0() + r.length() - 1;
                for (int y = Math.max(min, r.y0()); y <= Math.min(max, last); y++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    int code = y == last ? r.top() : ReachSolid.WHOLE;
                    BlockState old = section.getBlockState(lx, y & 15, lz);
                    if (old.is(ReachWorld.SOLID)) {
                        code = ReachSolid.max(code, ReachSolid.code(old));   // another level file reaches this block too
                    }
                    if (code == 0) {
                        continue;
                    }
                    section.setBlockState(lx, y & 15, lz, code == ReachSolid.WHOLE ? whole : solid.state(code), false);
                }
            }
        }
    }

    /** The first empty block above the highest wall in the column (or the bottom of the world). */
    private static int top(int x, int z) {
        List<ReachVoxels.Run>[] cols = ReachVoxels.get().columns(x >> 4, z >> 4);
        int best = MIN_Y;
        if (cols != null && cols[(x & 15) + (z & 15) * 16] != null) {
            for (ReachVoxels.Run r : cols[(x & 15) + (z & 15) * 16]) {
                best = Math.max(best, r.y0() + r.length());
            }
        }
        ReachLand land = ReachLand.get();
        if (land != null && land.covers(x, z) && ReachLand.kind(land.flagsAt(x + 0.5, z + 0.5)) != ReachLand.FOOTPRINT) {
            best = Math.max(best, landColumn(land, x, z)[1] + 1);
        }
        return best;
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return MIN_Y;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return top(x, z);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int min = level.getMinY();
        BlockState[] states = new BlockState[level.getHeight()];
        java.util.Arrays.fill(states, Blocks.AIR.defaultBlockState());
        List<ReachVoxels.Run>[] cols = ReachVoxels.get().columns(x >> 4, z >> 4);
        if (cols != null && cols[(x & 15) + (z & 15) * 16] != null) {
            for (ReachVoxels.Run r : cols[(x & 15) + (z & 15) * 16]) {
                for (int y = r.y0(); y < r.y0() + r.length(); y++) {
                    if (y >= min && y - min < states.length) {
                        states[y - min] = ReachWorld.SOLID.defaultBlockState();
                    }
                }
            }
        }
        ReachLand land = ReachLand.get();
        if (land != null && land.covers(x, z) && ReachLand.kind(land.flagsAt(x + 0.5, z + 0.5)) != ReachLand.FOOTPRINT) {
            int[] col = landColumn(land, x, z);
            for (int y = col[0]; y <= col[1]; y++) {
                if (y >= min && y - min < states.length) {
                    states[y - min] = ReachWorld.SOLID.defaultBlockState();
                }
            }
        }
        return new NoiseColumn(min, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos, SamplerContext context) {
        info.add("Endless Night: Reach (" + ReachVoxels.get().files() + " level files" + (ReachLand.get() != null ? ", land" : "") + ")");
    }
}
