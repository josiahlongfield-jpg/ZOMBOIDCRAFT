package com.zomboidcraft.creature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZomboidCraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the Harvestmen of a world: how many there are (four to six, each with a den in the dark of some big building,
 * made the first time a player goes deep into one), which one is hunting and whom, and what they've learned of the
 * players. Only one hunts at a time, server-wide. It picks one player and keeps them across log-offs (it notices when
 * they come back); it lets go after a week offline, or for someone it holds a worse grudge against. The hunt ends with
 * the target's death or the hunter's, and then the world is left alone for at least two in-game days.
 *
 * <p>A hunter left behind in unloaded ground keeps travelling toward its target at a real speed and comes out of the
 * dark near them; one whose den is broken into comes back to it the same way. While it hunts, its target alone hears
 * it now and then: far-off calls, taps above them, a scrape behind. Saved to zomboidcraft_harvestmen.json.
 */
public final class HarvestmanDirector {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** After a hunt, at least two in-game days of peace. */
    private static final long GRACE = 48_000;
    /** A target offline this long (real time) is let go. */
    private static final long LET_GO_MS = 7L * 24 * 3600 * 1000;
    /** How fast it travels through ground no one is in, blocks a second. */
    private static final double TRAVEL = 3.5;

    /** One Harvestman, loaded or not. */
    static final class Record {
        String id;
        BlockPos lair;
        int gen, lost, stage = 1;
        double health = HarvestmanEntity.MAX_HEALTH;
        /** Where it is while no one's near enough to have it loaded (or null: it's in its chunk). */
        Vec3 away;
        /** Where it's travelling to while away: its target, or home. */
        boolean homeward;
        String state = "";
    }

    private static final Map<String, Record> RECORDS = new LinkedHashMap<>();
    private static final Map<String, HarvestmanEntity> LOADED = new HashMap<>();
    private static final Map<UUID, Double> GRUDGES = new HashMap<>();
    private static final Map<UUID, Long> LAST_SEEN = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();
    /** Where each player spends their time: 16x16 columns, seconds spent, and the floor height there. */
    private static final Map<UUID, Map<Long, int[]>> HAUNTS = new HashMap<>();
    private static String hunter;
    private static UUID target;
    private static long graceUntil, huntStart, nextLair, nextScare;
    /** Its call, a few minutes after its target logs back in (it knows they're back); 0 for none due. */
    private static long loginCall;
    private static int population;
    /** Harvestmen lost while enemy respawning is on, each waiting on the next breach surge to come through. */
    private static int waiting;
    private static boolean loaded;
    private static int tick;
    private static MinecraftServer server;

    public static void register() {
        HarvestmanNights.register();
        HarvestmanGlimpse.register();
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            load(s);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            save(s);
            RECORDS.clear();
            LOADED.clear();
            loaded = false;
            server = null;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof HarvestmanEntity h) {
                loadedIn(level, h);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof HarvestmanEntity h && LOADED.get(h.mind.hid) == h) {
                LOADED.remove(h.mind.hid);
                Record r = RECORDS.get(h.mind.hid);
                if (r != null && h.isAlive() && entity.getRemovalReason() != null && !entity.getRemovalReason().shouldDestroy()) {
                    copy(h, r);
                    r.away = h.position();
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer p && (source.getEntity() instanceof HarvestmanEntity
                    || source.is(HarvestmanEntity.DRAINED)) && p.level() instanceof ServerLevel level) {
                // it'll bring them back to their door
                HarvestmanNights.killed(level, p);
            }
            if (entity instanceof ServerPlayer p && p.getUUID().equals(target)) {
                endHunt(true);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> s.execute(() -> {
            ServerPlayer p = handler.getPlayer();
            NAMES.put(p.getUUID(), p.getName().getString());
            if (p.getUUID().equals(target)) {
                // it knows they're back: its call, far off, within their first few minutes
                loginCall = p.level().getGameTime() + 20 * (45 + p.getRandom().nextInt(120));
            }
        }));
        ServerTickEvents.END_SERVER_TICK.register(HarvestmanDirector::tick);
    }

    // ------------------------------------------------------------------------------------------------ the creatures

    private static void loadedIn(ServerLevel level, HarvestmanEntity h) {
        String id = h.mind.id();
        Record r = RECORDS.get(id);
        if (r == null) {
            r = new Record();
            r.id = id;
            r.gen = h.mind.gen;
            if (h.mind.lair == null) {
                h.mind.lair = h.blockPosition();
            }
            r.lair = h.mind.lair;
            RECORDS.put(id, r);
        } else if (r.gen != h.mind.gen) {
            // an old copy of one that has since travelled on: it's gone from here
            server.execute(h::discard);
            return;
        }
        r.away = null;
        LOADED.put(id, h);
    }

    private static void copy(HarvestmanEntity h, Record r) {
        r.health = h.getHealth();
        r.lost = h.lost();
        r.stage = h.mind.stage;
        r.lair = h.mind.lair;
        r.state = h.mind.doing();
    }

    /** Brings one out of the dark at a place, as the next copy of itself. */
    private static HarvestmanEntity materialize(ServerLevel level, Record r, Vec3 at) {
        HarvestmanEntity h = Harvestmen.HARVESTMAN.create(level, EntitySpawnReason.EVENT);
        if (h == null) {
            return null;
        }
        r.gen++;
        h.mind.hid = r.id;
        h.mind.gen = r.gen;
        h.mind.lair = r.lair;
        h.mind.stage = r.stage;
        h.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360f, 0f);
        h.setHealth((float) Math.max(1, r.health));
        for (int i = 0; i < 4; i++) {
            if ((r.lost >> i & 1) != 0) {
                h.legHealth[i] = 0;
            }
        }
        r.away = null;
        level.addFreshEntity(h);
        if (r.lost != 0) {
            for (int i = 0; i < 4; i++) {
                if ((r.lost >> i & 1) != 0) {
                    h.loseLegQuietly(i);
                }
            }
        }
        return h;
    }

    static void died(ServerLevel level, HarvestmanEntity h, Entity killer) {
        RECORDS.remove(h.mind.hid);
        LOADED.remove(h.mind.hid);
        if (h.mind.hid.equals(hunter)) {
            endHunt(true);
        }
        // the next one is a while coming: it comes through the breach on its next surge (world/Breach); with
        // enemy respawning off nothing more comes through, and the world has one fewer for good
        nextLair = Math.max(nextLair, level.getGameTime() + 24_000);
        if (com.zomboidcraft.ZcConfig.get().population.respawnDays > 0) {
            waiting++;
        } else {
            population = Math.max(0, population - 1);
        }
    }

    /** A surge of the breach: one lost Harvestman comes through and goes to make its den. */
    public static void breachSurge(ServerLevel level) {
        if (waiting > 0) {
            waiting--;
            nextLair = level.getGameTime();
        }
    }

    static void killed(ServerLevel level, HarvestmanEntity h, ServerPlayer victim) {
        if (victim.getUUID().equals(target)) {
            endHunt(true);
        }
    }

    static void grudge(ServerLevel level, HarvestmanEntity h, ServerPlayer by, double amount) {
        GRUDGES.merge(by.getUUID(), amount, Double::sum);
        NAMES.put(by.getUUID(), by.getName().getString());
    }

    private static void endHunt(boolean grace) {
        hunter = null;
        target = null;
        if (grace && server != null) {
            graceUntil = server.overworld().getGameTime() + GRACE;
        }
    }

    /** The player this Harvestman hunts, if it's the hunter and they're here; else null. */
    static ServerPlayer targetFor(ServerLevel level, HarvestmanEntity h) {
        if (target == null || !h.mind.hid.equals(hunter)) {
            return null;
        }
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(target);
        return p != null && p.level() == level && !p.isSpectator() && !p.isCreative() && p.isAlive() ? p : null;
    }

    /** A noise: every Harvestman in earshot hears it (they hear further than zombies). */
    public static void noise(ServerLevel level, Vec3 pos, double radius) {
        double r = radius * 1.5;
        for (HarvestmanEntity h : LOADED.values()) {
            if (h.level() == level && h.isAlive() && h.centre().distanceToSqr(pos) < r * r) {
                h.mind.heard(level, pos, radius);
            }
        }
    }

    /** Where a player spends the most time near where they are now (for an ambush, or a body left for them), or null. */
    static Vec3 favourite(ServerPlayer p, ServerLevel level) {
        Map<Long, int[]> cells = HAUNTS.get(p.getUUID());
        if (cells == null) {
            return null;
        }
        long best = 0;
        int most = 60;
        for (Map.Entry<Long, int[]> e : cells.entrySet()) {
            long k = e.getKey();
            int cx = (int) (k >> 32), cz = (int) k;
            double dx = cx * 16 + 8 - p.getX(), dz = cz * 16 + 8 - p.getZ();
            if (e.getValue()[0] > most && dx * dx + dz * dz < 160 * 160) {
                most = e.getValue()[0];
                best = k;
            }
        }
        if (most <= 60) {
            return null;
        }
        int cx = (int) (best >> 32), cz = (int) best;
        return new Vec3(cx * 16 + 8, cells.get(best)[1] + 1, cz * 16 + 8);
    }

    // ------------------------------------------------------------------------------------------------ the hunt

    private static void tick(MinecraftServer s) {
        if (!loaded) {
            return;
        }
        ServerLevel level = s.overworld();
        long now = level.getGameTime();
        tick++;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            LAST_SEEN.put(p.getUUID(), System.currentTimeMillis());
            NAMES.put(p.getUUID(), p.getName().getString());
        }
        if (tick % 100 == 0) {
            learn(s);
        }
        if (tick % 20 == 0) {
            hunt(s, level, now);
            HarvestmanNights.tick(level, target != null ? s.getPlayerList().getPlayer(target) : null);
            travel(s, level);
            lairs(s, level);
            for (Map.Entry<String, HarvestmanEntity> e : LOADED.entrySet()) {
                Record r = RECORDS.get(e.getKey());
                if (r != null) {
                    copy(e.getValue(), r);
                }
            }
        }
        if (tick % 40 == 0 && RECORDS.size() + waiting < population && now >= nextLair) {
            List<ServerPlayer> players = s.getPlayerList().getPlayers();
            if (!players.isEmpty()) {
                ServerPlayer p = players.get((tick / 40) % players.size());
                if (p.level() == level && !p.isSpectator()) {
                    tryLair(level, p);
                }
            }
        }
        scare(s, now);
        if (tick % (20 * 60 * 5) == 0) {
            save(s);
        }
    }

    /** Picks a hunt when there's none (and the grace is over); lets a target go after a week away, or for a worse grudge. */
    private static void hunt(MinecraftServer s, ServerLevel level, long now) {
        if (hunter != null && !RECORDS.containsKey(hunter)) {
            endHunt(false);
        }
        if (target != null) {
            Long seen = LAST_SEEN.get(target);
            if (seen != null && System.currentTimeMillis() - seen > LET_GO_MS) {
                endHunt(false);
            } else {
                // someone it holds a much worse grudge against takes their place
                double held = GRUDGES.getOrDefault(target, 0.0);
                for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                    if (!p.getUUID().equals(target) && GRUDGES.getOrDefault(p.getUUID(), 0.0) > held * 2 + 200) {
                        target = p.getUUID();
                        break;
                    }
                }
            }
        }
        if (target == null && now >= graceUntil && !RECORDS.isEmpty()) {
            List<ServerPlayer> can = new ArrayList<>();
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                if (!p.isSpectator() && !p.isCreative() && p.level() == level) {
                    can.add(p);
                }
            }
            if (!can.isEmpty()) {
                // the worst grudge, or else whoever strays furthest from the rest
                ServerPlayer pick = can.get(level.getRandom().nextInt(can.size()));
                double apart = -1;
                for (ServerPlayer p : can) {
                    double nearest = 1e6;
                    for (ServerPlayer q : can) {
                        if (q != p) {
                            nearest = Math.min(nearest, q.distanceTo(p));
                        }
                    }
                    if (can.size() > 1 && nearest > apart) {
                        apart = nearest;
                        pick = p;
                    }
                }
                double worst = 0;
                for (ServerPlayer p : can) {
                    double g = GRUDGES.getOrDefault(p.getUUID(), 0.0);
                    if (g > worst) {
                        worst = g;
                        pick = p;
                    }
                }
                target = pick.getUUID();
                huntStart = now;
                hunter = null;
            }
        }
        if (target != null && hunter == null) {
            hunter = nearestTo(s, target);
        }
    }

    /** The Harvestman nearest a player (the loaded ones by where they are, the rest by where they're away or their den). */
    private static String nearestTo(MinecraftServer s, UUID who) {
        ServerPlayer p = s.getPlayerList().getPlayer(who);
        Vec3 at = p != null ? p.position() : Vec3.ZERO;
        String best = null;
        double bd = Double.MAX_VALUE;
        for (Record r : RECORDS.values()) {
            HarvestmanEntity h = LOADED.get(r.id);
            Vec3 pos = h != null ? h.position() : r.away != null ? r.away : r.lair != null ? Vec3.atCenterOf(r.lair) : Vec3.ZERO;
            double d = pos.distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = r.id;
            }
        }
        return best;
    }

    /** A hunter, or one called home to its broken-into den, travels through unloaded ground, then comes out of the dark. */
    private static void travel(MinecraftServer s, ServerLevel level) {
        for (Record r : RECORDS.values()) {
            if (LOADED.containsKey(r.id) || r.away == null) {
                continue;
            }
            Vec3 dest = null;
            ServerPlayer t = r.id.equals(hunter) && target != null ? s.getPlayerList().getPlayer(target) : null;
            if (r.homeward && r.lair != null) {
                dest = Vec3.atCenterOf(r.lair);
            } else if (t != null && t.level() == level && !t.isCreative() && !t.isSpectator()) {
                dest = t.position();
            }
            if (dest == null) {
                continue;
            }
            Vec3 to = dest.subtract(r.away);
            double d = to.length();
            r.away = d <= TRAVEL ? dest : r.away.add(to.scale(TRAVEL / d));
            boolean arrived = r.homeward ? d < 12 : d < 56;
            if (!arrived) {
                continue;
            }
            Vec3 at = r.homeward ? denSpot(level, r.lair) : hiddenNear(level, t);
            if (at != null && level.isLoaded(BlockPos.containing(at))) {
                r.homeward = false;
                materialize(level, r, at);
            }
        }
    }

    /** A den broken into calls its Harvestman home, at a real speed, wherever it is. */
    private static void lairs(MinecraftServer s, ServerLevel level) {
        for (Record r : RECORDS.values()) {
            if (r.lair == null) {
                continue;
            }
            Vec3 den = Vec3.atCenterOf(r.lair);
            ServerPlayer in = null;
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                if (p.level() == level && !p.isSpectator() && !p.isCreative() && p.position().distanceToSqr(den) < 10 * 10) {
                    in = p;
                }
            }
            if (in == null) {
                continue;
            }
            HarvestmanEntity h = LOADED.get(r.id);
            if (h != null && h.isAlive() && h.centre().distanceToSqr(den) > 30 * 30 && h.mind.plan != HarvestmanMind.LAIR
                    && h.held() == null) {
                h.mind.queue(HarvestmanMind.LAIR);
            } else if (h == null && r.away != null && !r.homeward) {
                r.homeward = true;
            }
        }
    }

    /** Somewhere 20-40 blocks from a player, out of their sight and dark if it can be. */
    private static Vec3 hiddenNear(ServerLevel level, ServerPlayer t) {
        if (t == null) {
            return null;
        }
        Vec3 eye = t.getEyePosition(), best = null;
        double bestScore = -1e9;
        RandomSource rnd = level.getRandom();
        for (int k = 0; k < 40; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 20 + rnd.nextDouble() * 20;
            Vec3 top = eye.add(Math.cos(a) * r, 6, Math.sin(a) * r);
            if (!level.isLoaded(BlockPos.containing(top))) {
                continue;
            }
            BlockHitResult down = level.clip(new ClipContext(top, top.add(0, -20, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t));
            if (down.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 p = down.getLocation().add(0, HarvestmanEntity.STAND - HarvestmanEntity.MID, 0);
            if (!level.noCollision(Harvestmen.HARVESTMAN.getDimensions().makeBoundingBox(p))) {
                continue;
            }
            boolean seen = level.clip(new ClipContext(eye, p.add(0, HarvestmanEntity.MID, 0), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, t)).getType() == HitResult.Type.MISS;
            double score = (seen ? -5 : 0) + 15 - level.getMaxLocalRawBrightness(BlockPos.containing(p)) + rnd.nextDouble();
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return bestScore > -2 ? best : null;
    }

    // ------------------------------------------------------------------------------------------------ dens

    /**
     * A player deep in the dark of a big building, with no den near: a Harvestman makes its den somewhere dark nearby,
     * out of their sight (it was always there).
     */
    private static void tryLair(ServerLevel level, ServerPlayer p) {
        BlockPos at = p.blockPosition();
        if (level.canSeeSky(at.above()) || level.getBrightness(LightLayer.SKY, at) > 4 || level.getBrightness(LightLayer.BLOCK, at) > 6) {
            return;
        }
        if (p.position().distanceToSqr(Vec3.atCenterOf(level.getRespawnData().pos())) < 160 * 160) {
            return;
        }
        for (Record r : RECORDS.values()) {
            if (r.lair != null && r.lair.distSqr(at) < 220 * 220) {
                return;
            }
        }
        // big: a lot of room all round, and a roof well over it
        Vec3 eye = p.getEyePosition();
        double room = 0;
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8;
            Vec3 to = eye.add(Math.cos(a) * 32, 0, Math.sin(a) * 32);
            BlockHitResult hit = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            room += hit.getType() == HitResult.Type.MISS ? 32 : hit.getLocation().distanceTo(eye);
        }
        BlockHitResult roof = level.clip(new ClipContext(eye, eye.add(0, 30, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double roofAt = roof.getType() == HitResult.Type.MISS ? 99 : roof.getLocation().y - eye.y;
        if (room / 16 < 9 || roofAt < 2 || roofAt > 26) {
            return;
        }
        Vec3 den = darkFloor(level, p);
        if (den == null) {
            return;
        }
        nextLair = level.getGameTime() + 20 * 60 * 10;
        makeDen(level, den, p.position());
    }

    /** A new Harvestman and its den on the floor at den, built for someone coming in from `from`; it rests there. */
    static HarvestmanEntity makeDen(ServerLevel level, Vec3 den, Vec3 from) {
        BlockPos lair = BlockPos.containing(den);
        Record r = new Record();
        r.id = UUID.randomUUID().toString();
        r.lair = lair;
        RECORDS.put(r.id, r);
        furnish(level, lair, from);
        HarvestmanEntity h = materialize(level, r, den.add(0, HarvestmanEntity.STAND - HarvestmanEntity.MID, 0));
        if (h != null) {
            h.mind.queue(HarvestmanMind.REST);
        }
        ZomboidCraft.LOGGER.info("A Harvestman made its den at {}", lair.toShortString());
        return h;
    }

    /** A dark floor 14-40 blocks from them that they can't see from where they stand. */
    private static Vec3 darkFloor(ServerLevel level, ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), best = null;
        double bestScore = -1e9;
        RandomSource rnd = level.getRandom();
        for (int k = 0; k < 48; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 14 + rnd.nextDouble() * 26;
            Vec3 top = eye.add(Math.cos(a) * r, 2, Math.sin(a) * r);
            BlockHitResult down = level.clip(new ClipContext(top, top.add(0, -10, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (down.getType() == HitResult.Type.MISS || down.getDirection() != net.minecraft.core.Direction.UP) {
                continue;
            }
            Vec3 floor = down.getLocation();
            BlockPos air = BlockPos.containing(floor.add(0, 0.5, 0));
            if (!level.isEmptyBlock(air) || !level.isEmptyBlock(air.above()) || !level.isEmptyBlock(air.above(2))) {
                continue;
            }
            boolean seen = level.clip(new ClipContext(eye, floor.add(0, 1, 0), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
            int light = level.getMaxLocalRawBrightness(air);
            if (light > 4) {
                continue;
            }
            double score = (seen ? -4 : 0) - light + rnd.nextDouble();
            if (score > bestScore) {
                bestScore = score;
                best = floor;
            }
        }
        return best;
    }

    /**
     * Its den: the room torn clear, scrap heaped round the walls and a nest in the middle, the bodies it has dragged in
     * hung up or set round the walls facing the way in (HarvestmanDens), and now and then something one of them carried.
     */
    private static void furnish(ServerLevel level, BlockPos lair, Vec3 from) {
        RandomSource rnd = level.getRandom();
        HarvestmanDens.carve(level, lair, from);
        HarvestmanDens.bodies(level, lair, from, 3 + rnd.nextInt(4));
        if (rnd.nextFloat() < 0.3f) {
            Vec3 p = Vec3.atBottomCenterOf(lair).add(rnd.nextDouble() - 0.5, 0.2, rnd.nextDouble() - 0.5);
            ItemEntity map = new ItemEntity(level, p.x, p.y, p.z, new ItemStack(ModItems.BUNKER_MAP));
            map.setDeltaMovement(Vec3.ZERO);
            map.setUnlimitedLifetime();
            level.addFreshEntity(map);
        }
    }

    private static Vec3 denSpot(ServerLevel level, BlockPos lair) {
        if (lair == null) {
            return null;
        }
        return Vec3.atBottomCenterOf(lair).add(0, HarvestmanEntity.STAND - HarvestmanEntity.MID, 0);
    }

    // ------------------------------------------------------------------------------------------------ learning, and fear

    /** Where each player spends their time, a column at a time. */
    private static void learn(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p.isSpectator()) {
                continue;
            }
            Map<Long, int[]> cells = HAUNTS.computeIfAbsent(p.getUUID(), u -> new HashMap<>());
            long key = (long) (p.getBlockX() >> 4) << 32 | (p.getBlockZ() >> 4) & 0xFFFFFFFFL;
            int[] c = cells.computeIfAbsent(key, k -> new int[] {0, p.getBlockY()});
            c[0] += 5;
            c[1] = (c[1] * 3 + p.getBlockY()) / 4;
            if (cells.size() > 96) {
                long least = 0;
                int few = Integer.MAX_VALUE;
                for (Map.Entry<Long, int[]> e : cells.entrySet()) {
                    if (e.getValue()[0] < few && e.getKey() != key) {
                        few = e.getValue()[0];
                        least = e.getKey();
                    }
                }
                cells.remove(least);
            }
        }
    }

    /** Its target alone hears it now and then: a far-off call, taps above them, a scrape behind. */
    private static void scare(MinecraftServer s, long now) {
        boolean login = loginCall > 0 && now >= loginCall;
        if (target == null || hunter == null || now < nextScare && !login) {
            return;
        }
        ServerPlayer t = s.getPlayerList().getPlayer(target);
        if (t == null || t.isCreative() || t.isSpectator()) {
            return;
        }
        RandomSource rnd = t.getRandom();
        if (login) {
            loginCall = 0;
        } else {
            nextScare = now + 20 * (60 + rnd.nextInt(180));
        }
        HarvestmanEntity h = LOADED.get(hunter);
        if (h != null && (h.mind.plan == HarvestmanMind.STRIKE || h.held() != null)) {
            return;
        }
        // now and then, instead of a sound, it lets them alone see it for a moment at the edge of their sight
        if (!login && rnd.nextInt(4) == 0 && t.level() instanceof ServerLevel tl && (h == null || h.distanceTo(t) > 30)
                && HarvestmanGlimpse.show(tl, t)) {
            return;
        }
        int kind = login ? 0 : rnd.nextInt(3);
        double a = rnd.nextDouble() * Math.PI * 2;
        Vec3 at;
        SoundEvent sound;
        float volume;
        if (kind == 0) {
            at = t.position().add(Math.cos(a) * 60, 10, Math.sin(a) * 60);
            sound = HarvestmanSounds.CALL;
            volume = 4f;
        } else if (kind == 1) {
            at = t.position().add(Math.cos(a) * 3, 5, Math.sin(a) * 3);
            sound = HarvestmanSounds.TAP;
            volume = 0.35f;
        } else {
            Vec3 back = t.getLookAngle().scale(-6);
            at = t.position().add(back.x, 1, back.z);
            sound = HarvestmanSounds.SCRAPE;
            volume = 0.4f;
        }
        t.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE,
                at.x, at.y, at.z, volume, 0.85f + rnd.nextFloat() * 0.3f, rnd.nextLong()));
    }

    // ------------------------------------------------------------------------------------------------ commands

    /** Sets who's hunted (the nearest Harvestman takes it up at once), or clears the hunt with no grace after. */
    static String setTarget(MinecraftServer s, ServerPlayer who) {
        if (who == null) {
            endHunt(false);
            graceUntil = 0;
            return "No one is hunted now.";
        }
        if (RECORDS.isEmpty()) {
            return "There's no Harvestman in this world to hunt them (/zc harvestman makes one).";
        }
        target = who.getUUID();
        NAMES.put(target, who.getName().getString());
        huntStart = s.overworld().getGameTime();
        hunter = nearestTo(s, target);
        graceUntil = 0;
        HarvestmanEntity h = LOADED.get(hunter);
        if (h != null && h.held() == null) {
            h.mind.queue(HarvestmanMind.STALK);
            h.mind.forced = -1;
        }
        return "A Harvestman is hunting " + who.getName().getString() + " now.";
    }

    static String report(MinecraftServer s) {
        long now = s.overworld().getGameTime();
        StringBuilder b = new StringBuilder();
        b.append(RECORDS.size()).append(" of ").append(population).append(" Harvestmen in this world");
        if (waiting > 0) {
            b.append(", ").append(waiting).append(" waiting on the breach's next surge");
        }
        if (target != null) {
            b.append("\nHunting ").append(NAMES.getOrDefault(target, target.toString()))
                    .append(s.getPlayerList().getPlayer(target) == null ? " (offline)" : "");
        } else if (now < graceUntil) {
            b.append(String.format("\nNo hunt: left alone for %.1f more in-game hours", (graceUntil - now) / 1000.0));
        } else {
            b.append("\nNo hunt yet");
        }
        if (HarvestmanNights.patient(s.overworld())) {
            b.append("\nA patient night: the hunter only watches until the next dusk");
        }
        for (Record r : RECORDS.values()) {
            HarvestmanEntity h = LOADED.get(r.id);
            String where = h != null ? String.format("at %d %d %d", h.getBlockX(), h.getBlockY(), h.getBlockZ())
                    : r.away != null ? String.format("away near %.0f %.0f %.0f", r.away.x, r.away.y, r.away.z) : "unloaded";
            b.append(String.format("\n- %s%s, %s: %s, health %.0f/1200, legs %d/4, stage %d, den %s",
                    r.id.equals(hunter) ? "[hunter] " : "", r.id.substring(0, 8), where,
                    h != null ? h.mind.doing() : r.state.isEmpty() ? "out of reach" : r.state,
                    h != null ? h.getHealth() : r.health, 4 - Integer.bitCount(h != null ? h.lost() : r.lost),
                    h != null ? h.mind.stage : r.stage, r.lair == null ? "none" : r.lair.toShortString()));
        }
        return b.toString();
    }

    /** Takes every Harvestman out of the records (the clear command). */
    static void forgetAll() {
        RECORDS.clear();
        LOADED.clear();
        endHunt(false);
    }

    static void forget(HarvestmanEntity h) {
        RECORDS.remove(h.mind.hid);
        LOADED.remove(h.mind.hid);
        if (h.mind.hid.equals(hunter)) {
            endHunt(false);
        }
    }

    // ------------------------------------------------------------------------------------------------ saving

    private static Path file(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("zomboidcraft_harvestmen.json");
    }

    private static void load(MinecraftServer s) {
        RECORDS.clear();
        GRUDGES.clear();
        LAST_SEEN.clear();
        HAUNTS.clear();
        NAMES.clear();
        hunter = null;
        target = null;
        population = 4 + s.overworld().getRandom().nextInt(3);
        waiting = 0;
        try {
            Path f = file(s);
            if (Files.exists(f)) {
                JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                population = root.has("population") ? root.get("population").getAsInt() : population;
                waiting = root.has("waiting") ? root.get("waiting").getAsInt() : 0;
                graceUntil = root.has("graceUntil") ? root.get("graceUntil").getAsLong() : 0;
                hunter = root.has("hunter") ? root.get("hunter").getAsString() : null;
                target = root.has("target") ? UUID.fromString(root.get("target").getAsString()) : null;
                huntStart = root.has("huntStart") ? root.get("huntStart").getAsLong() : 0;
                for (JsonElement e : root.getAsJsonArray("records")) {
                    JsonObject o = e.getAsJsonObject();
                    Record r = new Record();
                    r.id = o.get("id").getAsString();
                    r.gen = o.get("gen").getAsInt();
                    r.lost = o.get("lost").getAsInt();
                    r.stage = o.get("stage").getAsInt();
                    r.health = o.get("health").getAsDouble();
                    r.lair = o.has("lair") ? BlockPos.of(o.get("lair").getAsLong()) : null;
                    r.away = o.has("ax") ? new Vec3(o.get("ax").getAsDouble(), o.get("ay").getAsDouble(), o.get("az").getAsDouble()) : null;
                    r.homeward = o.has("homeward") && o.get("homeward").getAsBoolean();
                    r.state = o.has("state") ? o.get("state").getAsString() : "";
                    RECORDS.put(r.id, r);
                }
                JsonObject players = root.getAsJsonObject("players");
                if (players != null) {
                    for (Map.Entry<String, JsonElement> e : players.entrySet()) {
                        UUID u = UUID.fromString(e.getKey());
                        JsonObject o = e.getValue().getAsJsonObject();
                        if (o.has("name")) {
                            NAMES.put(u, o.get("name").getAsString());
                        }
                        if (o.has("grudge")) {
                            GRUDGES.put(u, o.get("grudge").getAsDouble());
                        }
                        if (o.has("seen")) {
                            LAST_SEEN.put(u, o.get("seen").getAsLong());
                        }
                        if (o.has("haunts")) {
                            Map<Long, int[]> cells = new HashMap<>();
                            for (JsonElement c : o.getAsJsonArray("haunts")) {
                                JsonArray a = c.getAsJsonArray();
                                cells.put(a.get(0).getAsLong(), new int[] {a.get(1).getAsInt(), a.get(2).getAsInt()});
                            }
                            HAUNTS.put(u, cells);
                        }
                    }
                }
            }
        } catch (Exception e) {
            ZomboidCraft.LOGGER.error("Couldn't read the Harvestmen", e);
        }
        loaded = true;
    }

    private static void save(MinecraftServer s) {
        if (!loaded) {
            return;
        }
        for (Map.Entry<String, HarvestmanEntity> e : LOADED.entrySet()) {
            Record r = RECORDS.get(e.getKey());
            if (r != null) {
                copy(e.getValue(), r);
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty("population", population);
        root.addProperty("waiting", waiting);
        root.addProperty("graceUntil", graceUntil);
        if (hunter != null) {
            root.addProperty("hunter", hunter);
        }
        if (target != null) {
            root.addProperty("target", target.toString());
        }
        root.addProperty("huntStart", huntStart);
        JsonArray records = new JsonArray();
        for (Record r : RECORDS.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id);
            o.addProperty("gen", r.gen);
            o.addProperty("lost", r.lost);
            o.addProperty("stage", r.stage);
            o.addProperty("health", r.health);
            if (r.lair != null) {
                o.addProperty("lair", r.lair.asLong());
            }
            if (r.away != null) {
                o.addProperty("ax", r.away.x);
                o.addProperty("ay", r.away.y);
                o.addProperty("az", r.away.z);
            }
            o.addProperty("homeward", r.homeward);
            o.addProperty("state", r.state);
            records.add(o);
        }
        root.add("records", records);
        JsonObject players = new JsonObject();
        java.util.Set<UUID> all = new java.util.HashSet<>(NAMES.keySet());
        all.addAll(HAUNTS.keySet());
        for (UUID u : all) {
            JsonObject o = new JsonObject();
            o.addProperty("name", NAMES.getOrDefault(u, ""));
            o.addProperty("grudge", GRUDGES.getOrDefault(u, 0.0));
            o.addProperty("seen", LAST_SEEN.getOrDefault(u, 0L));
            JsonArray haunts = new JsonArray();
            for (Map.Entry<Long, int[]> e : HAUNTS.getOrDefault(u, Map.of()).entrySet()) {
                JsonArray a = new JsonArray();
                a.add(e.getKey());
                a.add(e.getValue()[0]);
                a.add(e.getValue()[1]);
                haunts.add(a);
            }
            o.add("haunts", haunts);
            players.add(u.toString(), o);
        }
        root.add("players", players);
        try {
            Files.writeString(file(s), GSON.toJson(root));
        } catch (Exception e) {
            ZomboidCraft.LOGGER.error("Couldn't save the Harvestmen", e);
        }
    }

    private HarvestmanDirector() {
    }
}
