package com.zomboidcraft.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * The farm animals the county's farms were built for (Josiah 2026-10-31: start small, chickens and cows), as
 * records for SURVIVAL's animals to spawn from. The farms (world/county/FarmSite, Farmstead) build the pens and
 * coops and call {@link com.zomboidcraft.world.city.BlockSink#herd}; the chunk writer attaches each herd to the
 * chunk that holds its pen's middle. When that chunk loads, SURVIVAL reads {@link #HERDS}, rolls the sandbox's
 * chance, spawns the animals inside the pen box and sets {@link Herd#spawned} (or drops the record) so it never
 * happens twice.
 * <p>
 * Species are SURVIVAL's entity names without the namespace: "chicken" (a coop and its run), "cow" (a dairy
 * barn, a corral, a pasture). The pen's furniture is found by the block tags zomboidcraft:animal/feed_trough,
 * water_trough, nest_box, hutch, coop_perch, milking_stall and pen_gate.
 */
public final class FarmHerds {
    /** One pen's animals: its species, how many, the box (inclusive) and the floor they stand on. */
    public record Herd(String species, int count, int x0, int y, int z0, int x1, int z1, boolean spawned) {
        static final Codec<Herd> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("species").forGetter(Herd::species),
                Codec.INT.fieldOf("count").forGetter(Herd::count),
                Codec.INT.fieldOf("x0").forGetter(Herd::x0),
                Codec.INT.fieldOf("y").forGetter(Herd::y),
                Codec.INT.fieldOf("z0").forGetter(Herd::z0),
                Codec.INT.fieldOf("x1").forGetter(Herd::x1),
                Codec.INT.fieldOf("z1").forGetter(Herd::z1),
                Codec.BOOL.optionalFieldOf("spawned", false).forGetter(Herd::spawned)).apply(i, Herd::new));

        /** This herd once its animals are out. */
        public Herd done() {
            return new Herd(species, count, x0, y, z0, x1, z1, true);
        }
    }

    /** The herds whose pens' middles are in a chunk. */
    public static final AttachmentType<List<Herd>> HERDS = AttachmentRegistry.create(ZomboidCraft.id("farm_herds"),
            builder -> builder.persistent(Herd.CODEC.listOf()).initializer(List::of));

    private FarmHerds() {
    }

    /** Loads the attachment type with the mod (it has to be registered before any world is). */
    public static void register() {
    }

    /**
     * A herd from the chunk writer: "!herd:species;count;w;d" at the pen's middle (x, z), the box w by d before
     * the frame turned it by yaw.
     */
    static void add(ChunkAccess chunk, double x, int y, double z, float yaw, String spec) {
        String[] f = spec.substring("!herd:".length()).split(";");
        if (f.length < 4) {
            return;
        }
        int count = Integer.parseInt(f[1]);
        int w = Integer.parseInt(f[2]), d = Integer.parseInt(f[3]);
        if (Math.floorMod(Math.round(yaw / 90f), 2) == 1) {
            int t = w;
            w = d;
            d = t;
        }
        int x0 = (int) Math.round(x - w / 2.0), z0 = (int) Math.round(z - d / 2.0);
        List<Herd> list = new ArrayList<>(chunk.getAttachedOrElse(HERDS, List.of()));
        list.add(new Herd(f[0], count, x0, y, z0, x0 + w - 1, z0 + d - 1, false));
        chunk.setAttached(HERDS, List.copyOf(list));
    }
}
