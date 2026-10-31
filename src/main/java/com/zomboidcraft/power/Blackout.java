package com.zomboidcraft.power;

import com.zomboidcraft.world.blocks.LightSwitch;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The lights going out with the mains: when a district's power goes off, every room lamp left on in it (and its
 * switch) goes dark, in the chunks round the players then and in every chunk loaded after. A spot a running generator
 * reaches keeps its lights. Traffic signals see to themselves (world/blocks/Signal).
 */
public final class Blackout {
    private static final int RADIUS = 8, PER_TICK = 4;
    private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();

    private Blackout() {
    }

    public static void register() {
        PowerGrid.OFF.register((level, id) -> {
            for (ServerPlayer p : level.players()) {
                int px = p.blockPosition().getX() >> 4, pz = p.blockPosition().getZ() >> 4;
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                        queue(px + dx, pz + dz);
                    }
                }
            }
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (level.dimension() == Level.OVERWORLD) {
                queue(chunk.getPos().getMinBlockX() >> 4, chunk.getPos().getMinBlockZ() >> 4);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Blackout::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            QUEUE.clear();
            QUEUED.clear();
        });
    }

    private static void queue(int x, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        if (QUEUED.add(key)) {
            QUEUE.add(key);
        }
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int i = 0; i < PER_TICK && !QUEUE.isEmpty(); i++) {
            long key = QUEUE.poll();
            QUEUED.remove(key);
            int cx = (int) (key >> 32), cz = (int) key;
            if (level.getChunkSource().getChunkNow(cx, cz) instanceof LevelChunk chunk) {
                sweep(level, chunk);
            }
        }
    }

    private static void sweep(ServerLevel level, LevelChunk chunk) {
        ChunkPos c = chunk.getPos();
        BlockPos middle = new BlockPos(c.getMiddleBlockX(), 64, c.getMiddleBlockZ());
        if (PowerGrid.mainsOn(level, middle)) {
            return;
        }
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section.hasOnlyAir() || !section.maybeHas(LightSwitch::powered)) {
                continue;
            }
            int baseY = chunk.getSectionYFromSectionIndex(si) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState s = section.getBlockState(x, y, z);
                        BlockState off = LightSwitch.unpowered(s);
                        if (off == null) {
                            continue;
                        }
                        at.set(c.getMinBlockX() + x, baseY + y, c.getMinBlockZ() + z);
                        if (!Generators.powers(level, at)) {
                            level.setBlock(at, off, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }
}
