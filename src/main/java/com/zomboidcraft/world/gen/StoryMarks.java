package com.zomboidcraft.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.RoadStories;
import com.zomboidcraft.zombie.Corpse;
import com.zomboidcraft.zombie.Corpses;
import com.zomboidcraft.zombie.ZombieLook;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The people of the county's world stories (world/county/RoadStories): the bodies they left and the walkers they
 * became, kept on the chunk as the generator wrote them ("!body:outfit;limbs;pose", "!walkers:outfit;count;radius")
 * until a player comes within {@link #REACH} chunks, when the bodies are laid through the corpses system (kept,
 * so they never time out) and the walkers stand up there, dressed for the story. Each mark goes once.
 * <p>
 * Also hands the sandbox's Randomized Road Stories Chance (VehicleStoryChance, LOOT's wiring) to the generator.
 */
public final class StoryMarks {
    /** A body or a knot of walkers at (x, y, z). */
    public record Mark(boolean walkers, String outfit, int count, int limbs, int pose, double x, int y, double z, float yaw,
                       int radius) {
        static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("walkers").forGetter(Mark::walkers),
                Codec.STRING.fieldOf("outfit").forGetter(Mark::outfit),
                Codec.INT.fieldOf("count").forGetter(Mark::count),
                Codec.INT.fieldOf("limbs").forGetter(Mark::limbs),
                Codec.INT.fieldOf("pose").forGetter(Mark::pose),
                Codec.DOUBLE.fieldOf("x").forGetter(Mark::x),
                Codec.INT.fieldOf("y").forGetter(Mark::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Mark::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(Mark::yaw),
                Codec.INT.fieldOf("radius").forGetter(Mark::radius)).apply(i, Mark::new));
    }

    public static final AttachmentType<List<Mark>> MARKS = AttachmentRegistry.create(ZomboidCraft.id("story_marks"),
            builder -> builder.persistent(Mark.CODEC.listOf()).initializer(List::of));

    /** How near (in chunks) a player comes before a story's people are put out. */
    private static final int REACH = 4;
    private static final int EVERY = 40;

    private StoryMarks() {
    }

    public static void register() {
        RoadStories.setRate(() -> RoadStories.rate(com.zomboidcraft.sandbox.Sandbox.value("VehicleStoryChance")));
        ServerTickEvents.END_SERVER_TICK.register(StoryMarks::tick);
    }

    /** A mark from the chunk writer, kept on the chunk being generated. */
    static void add(ChunkAccess chunk, double x, int y, double z, float yaw, String spec) {
        boolean walkers = spec.startsWith("!walkers:");
        String[] f = spec.substring(spec.indexOf(':') + 1).split(";");
        if (f.length < 3) {
            return;
        }
        Mark m;
        try {
            m = walkers
                    ? new Mark(true, f[0], Integer.parseInt(f[1]), 0, 0, x, y, z, yaw, Integer.parseInt(f[2]))
                    : new Mark(false, f[0], 1, Integer.parseInt(f[1]), Integer.parseInt(f[2]), x, y, z, yaw, 0);
        } catch (NumberFormatException e) {
            return;
        }
        List<Mark> list = new ArrayList<>(chunk.getAttachedOrElse(MARKS, List.of()));
        list.add(m);
        chunk.setAttached(MARKS, List.copyOf(list));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % EVERY != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer p : level.players()) {
                int cx = p.getBlockX() >> 4, cz = p.getBlockZ() >> 4;
                for (int dx = -REACH; dx <= REACH; dx++) {
                    for (int dz = -REACH; dz <= REACH; dz++) {
                        LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                        if (chunk != null) {
                            putOut(level, chunk);
                        }
                    }
                }
            }
        }
    }

    private static void putOut(ServerLevel level, LevelChunk chunk) {
        List<Mark> marks = chunk.getAttached(MARKS);
        if (marks == null || marks.isEmpty()) {
            return;
        }
        List<Mark> kept = new ArrayList<>();
        for (Mark m : marks) {
            BlockPos at = BlockPos.containing(m.x, m.y, m.z);
            if (!level.isPositionEntityTicking(at)) {
                kept.add(m);    // it would only stand frozen; it waits
                continue;
            }
            if (m.walkers) {
                walkers(level, m);
            } else if (ZcConfig.get().corpses.enabled) {
                Corpse body = Corpses.lay(level, new Vec3(m.x, m.y, m.z), m.yaw, m.outfit, m.limbs, true);
                if (body != null && m.pose != Corpse.FALLEN) {
                    body.pose(m.pose);
                }
            }
        }
        if (kept.isEmpty()) {
            chunk.removeAttached(MARKS);
        } else if (kept.size() != marks.size()) {
            chunk.setAttached(MARKS, List.copyOf(kept));
        }
    }

    private static void walkers(ServerLevel level, Mark m) {
        if (ZcConfig.get().zombiePopulation.count == ZcConfig.ZombiePopulation.Count.NONE) {
            return;
        }
        RandomSource r = level.getRandom();
        for (int i = 0; i < m.count; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * m.radius;
            int x = (int) Math.floor(m.x + Math.cos(a) * d), z = (int) Math.floor(m.z + Math.sin(a) * d);
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.isPositionEntityTicking(pos) || !level.getFluidState(pos.below()).isEmpty()) {
                continue;
            }
            Zombie zombie = EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.EVENT);
            if (zombie != null && !"any".equals(m.outfit)) {
                zombie.setAttached(ZombieLook.OUTFIT, m.outfit);
            }
        }
    }
}
