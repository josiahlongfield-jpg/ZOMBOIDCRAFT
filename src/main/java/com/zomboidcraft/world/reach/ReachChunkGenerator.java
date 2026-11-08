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
 * Where no file reaches, the chunk is left empty. The world is deep and tall (its dimension type runs from y -1024 to
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
        List<ReachVoxels.Run>[] cols = ReachVoxels.get().columns(chunk.getPos().x(), chunk.getPos().z());
        if (cols == null) {
            return;
        }
        int min = chunk.getMinY(), max = chunk.getMaxY();
        BlockState whole = ReachWorld.SOLID.defaultBlockState();
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
                    BlockState state = y == last ? whole.setValue(ReachSolid.LAYERS, r.top()) : whole;
                    BlockState old = section.getBlockState(lx, y & 15, lz);
                    if (old.is(ReachWorld.SOLID) && old.getValue(ReachSolid.LAYERS) >= state.getValue(ReachSolid.LAYERS)) {
                        continue;   // another level file already made this block at least as tall
                    }
                    section.setBlockState(lx, y & 15, lz, state, false);
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
        return new NoiseColumn(min, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos, SamplerContext context) {
        info.add("Endless Night: Reach (" + ReachVoxels.get().files() + " level files)");
    }
}
