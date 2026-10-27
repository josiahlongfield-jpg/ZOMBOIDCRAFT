package com.zomboidcraft.spawn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.Plan;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import com.zomboidcraft.zombie.population.Population;
import com.zomboidcraft.zombie.population.Spots;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Zomboid's start: before you're in the world you choose where you wake up (a town, a city, a district, or beside a
 * friend), then wake inside a building there with none of the dead in it. Until you choose you're held out of the
 * world as a spectator. Death starts a new survivor, who chooses again. Only in the planned county.
 */
public final class SpawnPicker {
    /** Set once you've woken up somewhere; not kept through death, so the next survivor chooses again. */
    public static final AttachmentType<Boolean> SPAWNED = AttachmentRegistry.create(ZomboidCraft.id("spawned"),
            builder -> builder.persistent(Codec.BOOL));
    /** While you're choosing: the game mode to give back once you wake. */
    public static final AttachmentType<String> HELD = AttachmentRegistry.create(ZomboidCraft.id("spawn_held"),
            builder -> builder.persistent(Codec.STRING));

    private record Entry(String id, String name, String kind, String group, int danger, String zombies, String wake,
                         String loot, String note) {
    }

    /** One survivor's search for a way in: chunks round the place, nearest rings first, and the best spot so far. */
    private static final class Search {
        final UUID player;
        final String where;
        final List<long[]> chunks;
        int next, score = Integer.MIN_VALUE;
        BlockPos best;
        final BlockPos fallback;

        Search(UUID player, String where, List<long[]> chunks, BlockPos fallback) {
            this.player = player;
            this.where = where;
            this.chunks = chunks;
            this.fallback = fallback;
        }
    }

    /** A spot with walls all round, on the ground floor or near it: good enough to stop looking. */
    private static final int GOOD = 45;
    /** A chunk needs this many indoor spots to count as a building rather than a porch or a bus shelter. */
    private static final int BUILDING = 24;
    private static final long BUDGET_NS = 25_000_000;

    private static List<Entry> entries;
    private static final Map<UUID, Search> SEARCHES = new HashMap<>();

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(SpawnNet.Open.TYPE, SpawnNet.Open.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SpawnNet.Status.TYPE, SpawnNet.Status.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SpawnNet.Pick.TYPE, SpawnNet.Pick.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SpawnNet.Pick.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> pick(player, payload));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> offer(handler.getPlayer(), false)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> SEARCHES.remove(handler.getPlayer().getUUID())));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive) {
                // back through an end portal: the same survivor
                if (oldPlayer.getAttachedOrElse(SPAWNED, false)) newPlayer.setAttached(SPAWNED, true);
                return;
            }
            if (ZcConfig.get().start.afterDeath) {
                offer(newPlayer, true);
            } else {
                newPlayer.setAttached(SPAWNED, true);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(SpawnPicker::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SEARCHES.clear());
        // /zc wakeup: choose again (to try another start without dying first)
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(net.minecraft.commands.Commands.literal("zc")
                        .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .then(net.minecraft.commands.Commands.literal("wakeup").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (!applies(player)) {
                                ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                        "The spawn picker only runs in a planned ZomboidCraft County world (and start.picker on)."));
                                return 0;
                            }
                            player.removeAttached(SPAWNED);
                            offer(player, false);
                            return 1;
                        }))));
    }

    /** Whether the picker runs for this player's world: the planned county's overworld. */
    public static boolean applies(ServerPlayer player) {
        ServerLevel level = player.level();
        return ZcConfig.get().start.picker && level == level.getServer().overworld()
                && level.getChunkSource().getGenerator() instanceof CountyChunkGenerator gen && gen.planned();
    }

    /** Holds a survivor who hasn't woken anywhere yet and opens the picker on their screen. */
    public static void offer(ServerPlayer player, boolean again) {
        if (!applies(player)) {
            release(player);
            return;
        }
        if (player.getAttachedOrElse(SPAWNED, false)) {
            release(player);    // left mid-way after waking: nothing to choose
            return;
        }
        if (!player.hasAttached(HELD)) {
            player.setAttached(HELD, player.gameMode.getGameModeForPlayer().getName());
        }
        player.setGameMode(GameType.SPECTATOR);
        ServerPlayNetworking.send(player, new SpawnNet.Open(places(), friends(player), again));
        if (SEARCHES.containsKey(player.getUUID())) {
            ServerPlayNetworking.send(player, new SpawnNet.Status(SEARCHES.get(player.getUUID()).where, false));
        }
    }

    /** Gives back the game mode a held survivor had. */
    private static void release(ServerPlayer player) {
        String held = player.getAttached(HELD);
        if (held == null) {
            return;
        }
        player.removeAttached(HELD);
        player.setGameMode(GameType.byName(held, GameType.SURVIVAL));
    }

    // ---------------------------------------------------------------- what's on offer

    private static List<Entry> entries() {
        if (entries == null) {
            List<Entry> list = new ArrayList<>();
            try (InputStream in = SpawnPicker.class.getResourceAsStream("/data/zomboidcraft/spawn_places.json")) {
                if (in != null) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (JsonElement e : root.getAsJsonArray("places")) {
                        JsonObject o = e.getAsJsonObject();
                        list.add(new Entry(o.get("id").getAsString(), o.get("name").getAsString(), o.get("kind").getAsString(),
                                o.get("group").getAsString(), o.get("danger").getAsInt(), o.get("zombies").getAsString(),
                                o.get("wake").getAsString(), o.get("loot").getAsString(), o.get("note").getAsString()));
                    }
                }
            } catch (Exception e) {
                ZomboidCraft.LOGGER.error("Couldn't read the spawn places", e);
            }
            entries = list;
        }
        return entries;
    }

    /** The places, where the plan puts them; any the plan doesn't have are left out. */
    public static List<SpawnNet.Place> places() {
        Plan plan = Plan.get();
        List<SpawnNet.Place> out = new ArrayList<>();
        for (Entry e : entries()) {
            Plan.Spot spot = plan.byId.get(e.id());
            if (spot != null) {
                out.add(new SpawnNet.Place(e.id(), e.name(), e.kind(), e.group(), spot.x(), spot.z(), e.danger(), e.zombies(),
                        e.wake(), e.loot(), e.note()));
            }
        }
        return out;
    }

    private static List<SpawnNet.Friend> friends(ServerPlayer player) {
        List<SpawnNet.Friend> out = new ArrayList<>();
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other != player && other.getAttachedOrElse(SPAWNED, false) && !other.isSpectator() && other.isAlive()
                    && other.level() == player.level()) {
                out.add(new SpawnNet.Friend(other.getUUID().toString(), other.getName().getString(), nearest(other.getBlockX(), other.getBlockZ())));
            }
        }
        return out;
    }

    /** The name of the offered place nearest a spot, for "near Seabright". */
    private static String nearest(int x, int z) {
        String name = "the county";
        double best = Double.MAX_VALUE;
        for (SpawnNet.Place p : places()) {
            double d = (double) (p.x() - x) * (p.x() - x) + (double) (p.z() - z) * (p.z() - z);
            if (d < best) {
                best = d;
                name = p.name();
            }
        }
        return name;
    }

    // ---------------------------------------------------------------- choosing and looking for a way in

    private static void pick(ServerPlayer player, SpawnNet.Pick pick) {
        if (!player.hasAttached(HELD) || player.getAttachedOrElse(SPAWNED, false) || SEARCHES.containsKey(player.getUUID())) {
            return;
        }
        List<SpawnNet.Place> places = places();
        if (places.isEmpty()) {
            finish(player, player.blockPosition());
            return;
        }
        RandomSource random = player.getRandom();
        int x, z, rings;
        String where;
        ServerPlayer friend = null;
        if (pick.kind() == SpawnNet.Pick.FRIEND) {
            try {
                friend = player.level().getServer().getPlayerList().getPlayer(UUID.fromString(pick.id()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (friend != null && friend.level() == player.level() && friend.getAttachedOrElse(SPAWNED, false)) {
            x = friend.getBlockX();
            z = friend.getBlockZ();
            rings = 3;
            where = "near " + friend.getName().getString();
        } else {
            SpawnNet.Place place = null;
            for (SpawnNet.Place p : places) {
                if (pick.kind() == SpawnNet.Pick.PLACE && p.id().equals(pick.id())) place = p;
            }
            if (place == null) place = places.get(random.nextInt(places.size()));
            // a little way off the pin, so two survivors choosing the same town don't wake in the same room
            x = place.x() + random.nextInt(97) - 48;
            z = place.z() + random.nextInt(97) - 48;
            rings = 8;
            where = place.name();
        }
        List<long[]> chunks = new ArrayList<>();
        int cx = x >> 4, cz = z >> 4;
        for (int r = 0; r <= rings; r++) {
            List<long[]> ring = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) ring.add(new long[]{cx + dx, cz + dz});
                }
            }
            Collections.shuffle(ring, new java.util.Random(random.nextLong()));
            chunks.addAll(ring);
        }
        BlockPos fallback = friend != null ? friend.blockPosition() : null;
        Search search = new Search(player.getUUID(), where, chunks, fallback);
        SEARCHES.put(player.getUUID(), search);
        ServerPlayNetworking.send(player, new SpawnNet.Status(where, false));
    }

    private static void tick(MinecraftServer server) {
        if (SEARCHES.isEmpty()) {
            return;
        }
        long end = System.nanoTime() + BUDGET_NS;
        int limit = Math.max(1, ZcConfig.get().start.searchChunks);
        for (Search s : new ArrayList<>(SEARCHES.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(s.player);
            if (player == null) {
                SEARCHES.remove(s.player);
                continue;
            }
            ServerLevel level = server.overworld();
            // one new chunk a tick at least (making it can take a while), more while there's time
            do {
                if (s.next >= s.chunks.size() || s.next >= limit || s.score >= GOOD) {
                    SEARCHES.remove(s.player);
                    BlockPos at = s.best != null ? s.best : s.fallback != null ? s.fallback : outside(level, s.chunks.get(0));
                    finish(player, at);
                    break;
                }
                long[] c = s.chunks.get(s.next++);
                look(level, (int) c[0], (int) c[1], s);
            } while (System.nanoTime() < end);
        }
    }

    /** Scores the indoor spots of one chunk: walls on every side and close to the ground floor are best. */
    private static void look(ServerLevel level, int cx, int cz, Search s) {
        ChunkAccess chunk = level.getChunk(cx, cz);
        int bx = cx << 4, bz = cz << 4;
        int minY = level.getMinY(), maxY = level.getMaxY() + 1;
        LevelChunkSection[] sections = chunk.getSections();
        Spots.Blocks blocks = (lx, y, lz) -> sections[(y - minY) >> 4].getBlockState(lx, y & 15, lz);
        County county = CountyChunkGenerator.gen(level.getSeed()).county();
        List<Place> near = county.placesNear(bx, bz, bx + 15, bz + 15, County.SKIRT);
        Spots.Ground ground = (x, z) -> CountyChunkGenerator.surface(county, x, z, near);
        List<BlockPos> indoor = new ArrayList<>();
        Spots.scan(blocks, bx, bz, minY, maxY, ground, (lx, y, lz, in) -> {
            if (in) {
                int surface = ground.at(bx + lx, bz + lz);
                if (y >= surface - 1 && y <= surface + 24) indoor.add(new BlockPos(bx + lx, y, bz + lz));
            }
        });
        if (indoor.size() < BUILDING) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int i = 0; i < 24; i++) {
            BlockPos pos = indoor.get(random.nextInt(indoor.size()));
            int surface = ground.at(pos.getX(), pos.getZ());
            int score = walls(level, pos) * 10 + (pos.getY() - surface <= 4 ? 5 : 0) + random.nextInt(3);
            if (score > s.score) {
                s.score = score;
                s.best = pos;
            }
        }
    }

    /** How many of the four ways out hit a wall within six blocks. */
    private static int walls(ServerLevel level, BlockPos pos) {
        int n = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= 6; i++) {
                at.setWithOffset(pos, d.getStepX() * i, 0, d.getStepZ() * i);
                if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    /** Nowhere indoors turned up: the street at the middle of the search. */
    private static BlockPos outside(ServerLevel level, long[] chunk) {
        int x = ((int) chunk[0] << 4) + 8, z = ((int) chunk[1] << 4) + 8;
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Wakes the survivor at the spot: the building is cleared and kept clear, and they're back in the world. */
    private static void finish(ServerPlayer player, BlockPos at) {
        ServerLevel level = player.level().getServer().overworld();
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        Population.keepClear(level, at);
        float yaw = player.getRandom().nextFloat() * 360f - 180f;
        player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), yaw, 0f, true);
        player.setAttached(SPAWNED, true);
        release(player);
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            player.setGameMode(GameType.SURVIVAL);
        }
        ServerPlayNetworking.send(player, new SpawnNet.Status("", true));
        ZomboidCraft.LOGGER.info("{} woke up at {} {} {}", player.getName().getString(), at.getX(), at.getY(), at.getZ());
    }

    private SpawnPicker() {
    }
}
