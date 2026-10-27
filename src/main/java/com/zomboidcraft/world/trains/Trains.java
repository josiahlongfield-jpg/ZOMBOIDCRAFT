package com.zomboidcraft.world.trains;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Driverless trains: the county railway and the downtown subway. Lines are laid by the world (see
 * {@link #line}) or by /zc train test; each runs its trains round and round, and they're kept in the
 * world's folder (zomboidcraft_trains.json) between sessions. A line's track is the same on both
 * sides: the client is sent every line and lays the same route, so it can draw the rails and run the
 * cars smoothly itself.
 */
public final class Trains {
    private static final ResourceKey<EntityType<?>> TRAIN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ZomboidCraft.id("train"));
    public static final EntityType<TrainEntity> TRAIN = Registry.register(BuiltInRegistries.ENTITY_TYPE, TRAIN_KEY,
            EntityType.Builder.<TrainEntity>of(TrainEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f).clientTrackingRange(16).updateInterval(1).fireImmune().noLootTable()
                    .build(TRAIN_KEY));

    private static final ResourceKey<Block> BUTTON_KEY = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id("train_call_button"));
    public static final Block CALL_BUTTON = Registry.register(BuiltInRegistries.BLOCK, BUTTON_KEY, new CallButtonBlock(
            BlockBehaviour.Properties.of().strength(3f, 6f).sound(SoundType.METAL).noOcclusion().setId(BUTTON_KEY)));

    private static final Map<String, Line> SERVER = new LinkedHashMap<>(), CLIENT = new LinkedHashMap<>();
    /** Where each player last said they stood in a train, and when. */
    private static final Map<UUID, Seen> RIDING = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MinecraftServer server;
    private static boolean dirty;

    private record Seen(TrainNet.Ride ride, long tick) {
    }

    public static Map<String, Line> lines(boolean client) {
        return client ? CLIENT : SERVER;
    }

    /**
     * Lays a line (or keeps it as it is when it's already there with the same track): a closed loop
     * through points {x, y, z} (y the top of the track bed), its stops (name and any point on the
     * stop's platform). Safe to call every time the server starts.
     */
    public static Line line(ServerLevel level, String id, Line.Kind kind, String name, List<int[]> points, List<StopSpec> stops) {
        List<double[]> pts = new ArrayList<>();
        for (int[] p : points) {
            pts.add(new double[] {p[0], p[1], p[2]});
        }
        return lineExact(level, id, kind, name, pts, stops);
    }

    /**
     * {@link #line} with points that needn't sit on whole blocks: the county lays its curves as
     * points a few blocks apart along true arcs (and its grades as fractions), so the corners
     * left between them are slight enough for the trains to take at speed.
     */
    public static Line lineExact(ServerLevel level, String id, Line.Kind kind, String name, List<double[]> points, List<StopSpec> stops) {
        List<double[]> pts = new ArrayList<>();
        for (double[] p : points) {
            pts.add(new double[] {p[0], p[1], p[2]});
        }
        Line old = SERVER.get(id);
        if (old != null && same(old.points, pts) && old.stops.size() == stops.size()) {
            return old;
        }
        Line line = build(id, kind, name, pts, stops.stream().map(s -> new TrainNet.StopData(s.name(), s.x(), s.z())).toList(), List.of());
        if (old != null) {
            line.crossings.addAll(old.crossings);
        }
        startTrains(line, old);
        SERVER.put(id, line);
        changed();
        return line;
    }

    /** A stop as the world gives it: its name and any point on its platform. */
    public record StopSpec(String name, double x, double z) {
    }

    /** Level crossings on a line (points on or near the track): the train sounds its horn coming up to each. */
    public static void crossings(String id, List<int[]> points) {
        Line line = SERVER.get(id);
        if (line == null) {
            return;
        }
        line.crossings.clear();
        for (int[] p : points) {
            line.crossings.add(line.route.project(p[0], p[2]));
        }
        changed();
    }

    public static boolean remove(String id) {
        Line line = SERVER.remove(id);
        if (line != null) {
            Beacons.clear(line);
            changed();
        }
        return line != null;
    }

    static Line build(String id, Line.Kind kind, String name, List<double[]> pts, List<TrainNet.StopData> stops, List<Double> crossings) {
        ZcConfig.Trains cfg = ZcConfig.get().trains;
        int cars = kind == Line.Kind.SUBWAY ? cfg.subwayCars : cfg.railCars;
        Line line = new Line(id, kind, name, pts, cars, cfg.curveGrip);
        for (TrainNet.StopData st : stops) {
            line.addStop(st.name(), st.x(), st.z());
        }
        line.crossings.addAll(crossings);
        return line;
    }

    private static void startTrains(Line line, Line old) {
        ZcConfig.Trains cfg = ZcConfig.get().trains;
        int n = Math.max(1, line.kind == Line.Kind.SUBWAY ? cfg.subwayTrains : cfg.railTrains);
        for (int i = 0; i < n; i++) {
            double s = old != null && i < old.trains.size() ? old.trains.get(i).s : line.route.length() * i / n;
            line.trains.add(new TrainRun(line, i, s));
        }
    }

    private static boolean same(List<double[]> a, List<double[]> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!java.util.Arrays.equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    static boolean running(TrainRun run) {
        Line line = SERVER.get(run.line.id);
        return line == run.line && line.trains.contains(run);
    }

    /** Whether the server last heard this player was in or on a train (in the last couple of seconds). */
    public static boolean riding(ServerPlayer player) {
        Seen seen = RIDING.get(player.getUUID());
        return seen != null && seen.ride.train() >= 0 && player.level().getGameTime() - seen.tick < 40;
    }

    /** A rider pressed a stop button: the train they're on pulls up where it can and opens up. */
    private static void requestStop(ServerLevel level, int trainId, ServerPlayer by) {
        if (by.isSpectator() || !(level.getEntity(trainId) instanceof TrainEntity train) || !riders(level, train).contains(by)) {
            return;
        }
        for (Line line : SERVER.values()) {
            for (TrainRun run : line.trains) {
                if (train.getUUID().equals(run.entity)) {
                    run.requestStop(level, by);
                    return;
                }
            }
        }
    }

    /** A gunshot from where it left to where it stopped: OTTO notices one at him or through his cab's glass. */
    public static void shot(ServerLevel level, ServerPlayer shooter, Vec3 from, Vec3 to) {
        for (Line line : SERVER.values()) {
            for (TrainRun run : line.trains) {
                if (run.entity == null) {
                    continue;
                }
                TrainCar.Pose front = run.pose(0);
                Otto.shot(level, run, shooter, front.toLocal(from), front.toLocal(to), front);
            }
        }
    }

    /** The players riding a train (by its entity id). */
    static List<ServerPlayer> riders(ServerLevel level, TrainEntity train) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            Seen seen = RIDING.get(p.getUUID());
            if (seen != null && seen.ride.train() == train.getId() && level.getGameTime() - seen.tick < 40) {
                out.add(p);
            }
        }
        return out;
    }

    public static void register() {
        Beacons.register();
        TrainSounds.register();
        PayloadTypeRegistry.clientboundPlay().register(TrainNet.Lines.TYPE, TrainNet.Lines.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TrainNet.Riders.TYPE, TrainNet.Riders.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TrainNet.OttoSay.TYPE, TrainNet.OttoSay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TrainNet.Ride.TYPE, TrainNet.Ride.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TrainNet.Call.TYPE, TrainNet.Call.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TrainNet.StopRequest.TYPE, TrainNet.StopRequest.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TrainNet.StopRequest.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> requestStop((ServerLevel) player.level(), payload.train(), player));
        });
        ServerPlayNetworking.registerGlobalReceiver(TrainNet.Ride.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> RIDING.put(player.getUUID(), new Seen(payload, player.level().getGameTime())));
        });
        ServerPlayNetworking.registerGlobalReceiver(TrainNet.Call.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (!player.isSpectator() && player.blockPosition().distSqr(payload.pos()) < 6 * 6
                        && player.level().getBlockState(payload.pos()).is(CALL_BUTTON)) {
                    call((ServerLevel) player.level(), payload.pos(), player);
                }
            });
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            load(s);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            save(s);
            SERVER.clear();
            RIDING.clear();
            server = null;
        });
        ServerTickEvents.START_SERVER_TICK.register(Trains::tick);
        ServerTickEvents.END_SERVER_TICK.register(Trains::afterTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> s.execute(() -> ServerPlayNetworking.send(handler.getPlayer(), payload())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> RIDING.remove(handler.getPlayer().getUUID()));
    }

    private static void changed() {
        dirty = true;
        if (server != null) {
            TrainNet.Lines p = payload();
            for (ServerPlayer player : PlayerLookup.all(server)) {
                ServerPlayNetworking.send(player, p);
            }
        }
    }

    private static TrainNet.Lines payload() {
        List<TrainNet.LineData> out = new ArrayList<>();
        for (Line l : SERVER.values()) {
            out.add(new TrainNet.LineData(l.id, l.kind.id(), l.name, l.cars, l.points,
                    l.stops.stream().map(s -> new TrainNet.StopData(s.name(), s.x(), s.z())).toList(), l.crossings));
        }
        return new TrainNet.Lines(out);
    }

    /** Client: the lines the server sent, laid the same way. */
    public static void received(TrainNet.Lines lines) {
        CLIENT.clear();
        for (TrainNet.LineData d : lines.lines()) {
            CLIENT.put(d.id(), new Line(d.id(), Line.Kind.of(d.kind()), d.name(), d.points(), d.cars(), ZcConfig.get().trains.curveGrip));
            Line line = CLIENT.get(d.id());
            for (TrainNet.StopData st : d.stops()) {
                line.addStop(st.name(), st.x(), st.z());
            }
            line.crossings.addAll(d.crossings());
        }
    }

    // ------------------------------------------------------------------ running

    private static void tick(MinecraftServer s) {
        if (SERVER.isEmpty()) {
            return;
        }
        ServerLevel level = s.overworld();
        ZcConfig.Trains cfg = ZcConfig.get().trains;
        for (Line line : SERVER.values()) {
            for (TrainRun run : line.trains) {
                run.tick(level, cfg);
                place(level, run);
            }
        }
        if (level.getGameTime() % 1200 == 0 && dirty) {
            save(s);
        }
        dirty = true;   // positions change all the time
    }

    /** Keeps the train's entity there while its middle is in a running chunk, and lets it go when it isn't. */
    private static void place(ServerLevel level, TrainRun run) {
        TrainCar.Pose mid = run.pose((run.line.cars - 1) / 2);
        BlockPos at = BlockPos.containing(mid.x(), mid.y(), mid.z());
        Entity existing = run.entity == null ? null : level.getEntity(run.entity);
        if (!level.isPositionEntityTicking(at)) {
            if (existing != null) {
                existing.discard();
            }
            run.entity = null;
            return;
        }
        TrainEntity train = existing instanceof TrainEntity t && !t.isRemoved() ? t : null;
        if (train == null) {
            train = TRAIN.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (train == null) {
                return;
            }
            train.follow(run);
            level.addFreshEntity(train);
            run.entity = train.getUUID();
        }
        train.follow(run);
        if (run.v > 0.05) {
            hits(level, run, train);
        }
    }

    /** Anything standing in a moving train's way that isn't riding it gets hit and thrown aside. */
    private static void hits(ServerLevel level, TrainRun run, TrainEntity train) {
        ZcConfig.Trains cfg = ZcConfig.get().trains;
        for (int car = 0; car < run.line.cars; car++) {
            TrainCar.Pose pose = run.pose(car);
            AABB box = new AABB(pose.x() - 12, pose.y() - 1.5, pose.z() - 12, pose.x() + 12, pose.y() + 4, pose.z() + 12);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                if (e.isSpectator() || ((TrainRider) e).zomboidcraft$train() == train
                        || e instanceof ServerPlayer p && (riding(p) || p.isCreative())) {
                    continue;
                }
                Vec3 local = pose.toLocal(e.position());
                if (Math.abs(local.x) > TrainCar.HALF_WIDTH - 0.1 || Math.abs(local.z) > TrainCar.PITCH / 2
                        || local.y < -1.3 || local.y > TrainCar.ROOF - 0.2) {
                    continue;
                }
                double side = local.x >= 0 ? 1 : -1;
                Vec3 out = pose.toWorld(side * (TrainCar.HALF_WIDTH + 0.7), Math.max(local.y, -1), local.z);
                Vec3 fling = pose.dirToWorld(new Vec3(side * 0.9, 0, 0.6)).add(0, 0.45, 0);
                float damage = e instanceof Player ? (float) Math.max(4, run.speedPerSecond() * cfg.hitPlayerPerSpeed) : cfg.hitZombie;
                e.hurtServer(level, level.damageSources().flyIntoWall(), damage);
                if (e instanceof ServerPlayer p) {
                    // a player's position is their own game's to say: move them there and tell it so
                    p.teleportTo(level, out.x, out.y, out.z, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
                    p.setDeltaMovement(fling);
                    p.connection.send(new ClientboundSetEntityMotionPacket(p));
                } else {
                    e.setPos(out);
                    e.setDeltaMovement(fling);
                }
                level.playSound(null, out.x, out.y, out.z, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.2f, 0.6f);
                Otto.bump(level, run);
            }
        }
    }

    /** After everything's moved: tell everyone near each train who's aboard and where. */
    private static void afterTick(MinecraftServer s) {
        ServerLevel level = s.overworld();
        Beacons.tick(level);
        for (Line line : SERVER.values()) {
            for (TrainRun run : line.trains) {
                Entity e = run.entity == null ? null : level.getEntity(run.entity);
                if (e instanceof TrainEntity train) {
                    sendRiders(level, run, train);
                    Zombies.steer(level, run, train);
                }
            }
        }
    }

    private static void sendRiders(ServerLevel level, TrainRun run, TrainEntity train) {
        List<Integer> ids = new ArrayList<>(), cars = new ArrayList<>();
        List<Float> at = new ArrayList<>();
        for (ServerPlayer p : riders(level, train)) {
            TrainNet.Ride r = RIDING.get(p.getUUID()).ride();
            ids.add(p.getId());
            cars.add(r.car());
            at.add(r.x());
            at.add(r.y());
            at.add(r.z());
            at.add(r.yaw());
        }
        for (int car = 0; car < run.line.cars; car++) {
            TrainCar.Pose pose = run.pose(car);
            AABB box = new AABB(pose.x() - 12, pose.y() - 1, pose.z() - 12, pose.x() + 12, pose.y() + 6, pose.z() + 12);
            for (Entity e : level.getEntities((Entity) null, box, x -> !(x instanceof Player) && ((TrainRider) x).zomboidcraft$train() == train)) {
                TrainRider r = (TrainRider) e;
                if (r.zomboidcraft$car() != car) {
                    continue;
                }
                Vec3 local = pose.toLocal(e.position());
                ids.add(e.getId());
                cars.add(car);
                at.add((float) local.x);
                at.add((float) local.y);
                at.add((float) local.z);
                at.add(e.getYRot() - pose.yawDegrees());
            }
        }
        if (ids.isEmpty() && level.getGameTime() % 10 != 0) {
            return;     // nobody aboard: an empty list now and then clears stale riders
        }
        int[] idArr = ids.stream().mapToInt(Integer::intValue).toArray(), carArr = cars.stream().mapToInt(Integer::intValue).toArray();
        float[] atArr = new float[at.size()];
        for (int i = 0; i < atArr.length; i++) {
            atArr[i] = at.get(i);
        }
        TrainNet.Riders payload = new TrainNet.Riders(train.getId(), idArr, carArr, atArr);
        for (ServerPlayer p : PlayerLookup.tracking(train)) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    // ------------------------------------------------------------------ call buttons

    /** A call button pressed: the nearest stop to it (on any line) gets a train, at the price of a flare or a siren. */
    public static void call(ServerLevel level, BlockPos pos, ServerPlayer by) {
        Line best = null;
        int stop = -1;
        double bestD = Double.MAX_VALUE;
        for (Line line : SERVER.values()) {
            int i = line.nearestStop(pos.getX() + 0.5, pos.getZ() + 0.5, 48);
            if (i >= 0) {
                Line.Stop st = line.stops.get(i);
                double d = Math.abs(line.route.distance(st.s(), pos.getX(), pos.getZ()));
                if (d < bestD) {
                    bestD = d;
                    best = line;
                    stop = i;
                }
            }
        }
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1f, 0.7f);
        if (best == null) {
            by.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Nothing happens. No line stops here.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        Line.Stop st = best.stops.get(stop);
        for (TrainRun run : best.trains) {
            if (run.phase == TrainRun.Phase.DWELL && run.atStop == stop) {
                Otto.alreadyHere(level, run, by);
                return;
            }
        }
        if (best.calls.contains(stop)) {
            by.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Already called. The train's on its way.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        best.calls.add(stop);
        Beacons.start(level, best, stop, pos);
        Otto.called(level, best, st, by);
    }

    // ------------------------------------------------------------------ saving

    private static Path file(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("zomboidcraft_trains.json");
    }

    private static void save(MinecraftServer s) {
        if (SERVER.isEmpty() && !Files.exists(file(s))) {
            return;
        }
        JsonObject root = new JsonObject();
        JsonArray lines = new JsonArray();
        for (Line l : SERVER.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", l.id);
            o.addProperty("kind", l.kind.id());
            o.addProperty("name", l.name);
            JsonArray pts = new JsonArray();
            for (double[] p : l.points) {
                JsonArray a = new JsonArray();
                a.add(p[0]);
                a.add(p[1]);
                a.add(p[2]);
                pts.add(a);
            }
            o.add("points", pts);
            JsonArray stops = new JsonArray();
            for (Line.Stop st : l.stops) {
                JsonObject so = new JsonObject();
                so.addProperty("name", st.name());
                so.addProperty("x", st.x());
                so.addProperty("z", st.z());
                stops.add(so);
            }
            o.add("stops", stops);
            JsonArray crossings = new JsonArray();
            l.crossings.forEach(crossings::add);
            o.add("crossings", crossings);
            JsonArray trains = new JsonArray();
            l.trains.forEach(t -> trains.add(t.s));
            o.add("trains", trains);
            lines.add(o);
        }
        root.add("lines", lines);
        try {
            Files.writeString(file(s), GSON.toJson(root));
            dirty = false;
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't save the train lines", e);
        }
    }

    private static void load(MinecraftServer s) {
        SERVER.clear();
        Path f = file(s);
        if (!Files.exists(f)) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(f), JsonObject.class);
            for (var el : root.getAsJsonArray("lines")) {
                JsonObject o = el.getAsJsonObject();
                List<double[]> pts = new ArrayList<>();
                for (var p : o.getAsJsonArray("points")) {
                    JsonArray a = p.getAsJsonArray();
                    pts.add(new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()});
                }
                List<TrainNet.StopData> stops = new ArrayList<>();
                for (var p : o.getAsJsonArray("stops")) {
                    JsonObject so = p.getAsJsonObject();
                    stops.add(new TrainNet.StopData(so.get("name").getAsString(), so.get("x").getAsDouble(), so.get("z").getAsDouble()));
                }
                List<Double> crossings = new ArrayList<>();
                if (o.has("crossings")) {
                    o.getAsJsonArray("crossings").forEach(c -> crossings.add(c.getAsDouble()));
                }
                Line line = build(o.get("id").getAsString(), Line.Kind.of(o.get("kind").getAsString()), o.get("name").getAsString(),
                        pts, stops, crossings);
                startTrains(line, null);
                if (o.has("trains")) {
                    JsonArray t = o.getAsJsonArray("trains");
                    for (int i = 0; i < Math.min(t.size(), line.trains.size()); i++) {
                        line.trains.get(i).s = line.route.wrapS(t.get(i).getAsDouble());
                    }
                }
                SERVER.put(line.id, line);
            }
            ZomboidCraft.LOGGER.info("Train lines: {}", SERVER.keySet());
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't read the train lines", e);
        }
    }

    private Trains() {
    }
}
