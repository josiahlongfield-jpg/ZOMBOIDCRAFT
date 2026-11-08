package com.zomboidcraft.zombie.population;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import com.zomboidcraft.world.gen.PowerDistricts;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * How many of each power district's dead are still about, against what it started with: the purge's hunt board
 * reads it to call a district cleared. A district's start is everything its chunks were given the first time
 * they got their dead; what's left is its records plus the real walkers that came out of them, so a walker
 * killed counts down where it came from, and one that goes back to being a record counts where it lies. The
 * share is only known once the dead have been placed round nine in ten of the district's places: before that
 * most of its dead haven't been counted.
 */
public final class DistrictDead {
    record Tally(int start, int left, List<Long> seen) {
        static final Codec<Tally> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("start").forGetter(Tally::start),
                Codec.INT.fieldOf("left").forGetter(Tally::left),
                Codec.LONG.listOf().fieldOf("seen").forGetter(Tally::seen)).apply(i, Tally::new));
    }

    /** On the overworld: each district's tally. */
    static final AttachmentType<Map<String, Tally>> TALLY = AttachmentRegistry.create(ZomboidCraft.id("district_dead"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Tally.CODEC)).initializer(Map::of));
    /** On a real walker that came out of a record: the district it's counted in. */
    static final AttachmentType<String> FROM = AttachmentRegistry.create(ZomboidCraft.id("district_from"),
            builder -> builder.persistent(Codec.STRING));

    /** Share of its places a district must have had its dead placed round before its count means anything. */
    private static final double COVERED = 0.9;
    private static final Map<String, Integer> PLACES = new HashMap<>();
    private static long placesSeed = Long.MIN_VALUE;

    static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Zombie zombie && entity.level() instanceof ServerLevel level) {
                leave(level, zombie);
            }
        });
    }

    /**
     * Remaining walkers in a district as a share (0 to 1) of what it started with, or -1 while that isn't known (no
     * county, an unknown district, or too little of it visited yet).
     */
    public static double share(ServerLevel level, String districtId) {
        Tally t = level.getAttachedOrElse(TALLY, Map.of()).get(districtId);
        if (t == null || t.start() <= 0) {
            return -1;
        }
        int places = places(level).getOrDefault(districtId, 0);
        if (places > 0 && t.seen().size() < places * COVERED) {
            return -1;
        }
        return Math.max(0, Math.min(1, t.left() / (double) t.start()));
    }

    /** For /zc and the log: each district's start, what's left and how much of it has been placed. */
    public static String stats(ServerLevel level) {
        StringBuilder b = new StringBuilder();
        Map<String, Integer> places = places(level);
        level.getAttachedOrElse(TALLY, Map.of()).forEach((id, t) -> b.append(String.format("%s %d/%d places %d/%d; ", id,
                t.left(), t.start(), t.seen().size(), places.getOrDefault(id, 0))));
        return b.toString();
    }

    /**
     * A chunk got its dead for the first time: they're its district's start (the records count adds them to what's
     * left), and every place it touches has had its dead placed, in the district its middle is in.
     */
    static void firstGiven(LevelChunk chunk, int given, List<Place> places) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }
        String id = district(level, chunk);
        if (id != null) {
            update(level, id, given, 0, List.of());
        }
        for (Place p : places) {
            String pid = PowerDistricts.id(level, new BlockPos(p.centreX(), level.getSeaLevel(), p.centreZ()));
            if (pid != null) {
                update(level, pid, 0, 0, List.of(key(p)));
            }
        }
    }

    /** A chunk's records went up or down by this many (placed, topped up, made real, folded back, drifted). */
    static void records(LevelChunk chunk, int delta) {
        if (delta == 0 || !(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }
        String id = district(level, chunk);
        if (id != null) {
            update(level, id, 0, delta, List.of());
        }
    }

    /** A record came out as this walker: it's counted in its district until it dies or folds back. */
    static void cameOut(ServerLevel level, Zombie zombie) {
        String id = PowerDistricts.id(level, zombie.blockPosition());
        if (id != null) {
            zombie.setAttached(FROM, id);
            update(level, id, 0, 1, List.of());
        }
    }

    /** A counted walker died or went back to being a record (which the records count then takes on). */
    static void leave(ServerLevel level, Zombie zombie) {
        String id = zombie.getAttached(FROM);
        if (id != null) {
            zombie.removeAttached(FROM);
            update(level, id, 0, -1, List.of());
        }
    }

    private static void update(ServerLevel level, String id, int start, int left, List<Long> seen) {
        ServerLevel overworld = level.getServer().overworld();
        if (level != overworld) {
            return;
        }
        Map<String, Tally> all = new HashMap<>(overworld.getAttachedOrElse(TALLY, Map.of()));
        Tally t = all.getOrDefault(id, new Tally(0, 0, List.of()));
        List<Long> s = t.seen();
        if (!seen.isEmpty()) {
            Set<Long> set = new HashSet<>(s);
            if (!set.addAll(seen) && start == 0 && left == 0) {
                return;   // nothing new
            }
            s = List.copyOf(set);
        }
        all.put(id, new Tally(t.start() + start, Math.max(0, t.left() + left), s));
        overworld.setAttached(TALLY, Map.copyOf(all));
    }

    private static String district(ServerLevel level, LevelChunk chunk) {
        return PowerDistricts.id(level, new BlockPos((chunk.getPos().x() << 4) + 8, level.getSeaLevel(), (chunk.getPos().z() << 4) + 8));
    }

    private static long key(Place p) {
        return ((long) p.x0 << 32) ^ (p.z0 & 0xFFFFFFFFL);
    }

    /** How many places each district has (by where their middles are). */
    private static synchronized Map<String, Integer> places(ServerLevel level) {
        if (placesSeed != level.getSeed()) {
            PLACES.clear();
            placesSeed = level.getSeed();
            if (level.getChunkSource().getGenerator() instanceof CountyChunkGenerator) {
                County county = CountyChunkGenerator.gen(level.getSeed()).county();
                for (Place p : county.allPlaces()) {
                    String id = PowerDistricts.id(level, new BlockPos(p.centreX(), level.getSeaLevel(), p.centreZ()));
                    if (id != null) {
                        PLACES.merge(id, 1, Integer::sum);
                    }
                }
            }
        }
        return PLACES;
    }

    private DistrictDead() {
    }
}
