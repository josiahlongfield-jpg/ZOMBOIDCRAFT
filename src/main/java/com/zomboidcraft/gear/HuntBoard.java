package com.zomboidcraft.gear;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.creature.HarvestmanEntity;
import com.zomboidcraft.power.PowerGrid;
import com.zomboidcraft.world.county.Districts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The hunt board (Josiah 2026-11-06, W21 "grand hunt" and C3/C6/C7 of the command net plan): everything the purge
 * still needs, fed in over the {@link CommandNet} as the squad goes, and never an end to the sandbox. Four kinds:
 * <ul>
 *   <li>district clears: a district goes on the board the first time a marine walks into it, and is done when its
 *   walkers fall to {@code hunt.districtClear} of what it started with (the walker count comes from the
 *   population thread through {@link #walkers});</li>
 *   <li>high-value targets: each Harvestman, the first time a marine comes near it, done when it dies;</li>
 *   <li>objectives: the lab data at Ridgeview and the dam from the start, and a district's substation once its mains
 *   go down; other threads report them done ({@link #objectiveDone}, or the grid coming back on);</li>
 *   <li>escorts: a survivor to bring to an extraction point ({@link #addEscort}, {@link #escortDelivered}).</li>
 * </ul>
 * Each done entry adds its credit to the purge total. The board is kept on the overworld and sent to every player
 * as JSON ({@link Sync}); the UI thread draws it, and marks entries seen with {@link Seen}.
 */
public final class HuntBoard {
    public static final String DISTRICT = "district", TARGET = "target", OBJECTIVE = "objective", ESCORT = "escort";

    /** One entry. Fields are public for Gson. */
    public static final class Entry {
        public String id, kind, title, where, doneWhen, gives, caption;
        public String status = "open";
        public int credit;
        public long time, doneAt = -1;
        public List<String> seen = new ArrayList<>();
    }

    /** Everything kept with the world. */
    static final class Board {
        List<Entry> entries = new ArrayList<>();
        List<String> inserted = new ArrayList<>();
    }

    /** Remaining walkers in a district as a share (0 to 1) of what it started with, or a negative number when not known. */
    @FunctionalInterface
    public interface Walkers {
        double share(ServerLevel level, String districtId);
    }

    /** Set by the population thread; until then district clears stay open. */
    public static volatile Walkers walkers = (level, id) -> -1;

    /** The board for one player, as JSON: {total, hears, entries:[...], log:[{text,time}]}. */
    public record Sync(String json) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ZomboidCraft.id("hunt_board"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Sync::json, Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The player has seen this entry (drops its "New" tag). */
    public record Seen(String id) implements CustomPacketPayload {
        public static final Type<Seen> TYPE = new Type<>(ZomboidCraft.id("hunt_board_seen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Seen> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Seen::id, Seen::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final Gson GSON = new Gson();
    private static final AttachmentType<String> STATE = AttachmentRegistry.create(ZomboidCraft.id("hunt_board"),
            builder -> builder.persistent(Codec.STRING).initializer(() -> ""));
    private static final int DISTRICT_CREDIT = 100, TARGET_CREDIT = 250, OBJECTIVE_CREDIT = 150, ESCORT_CREDIT = 50;
    private static Board board;
    private static boolean dirty;

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Seen.TYPE, Seen.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Seen.TYPE, (payload, context) -> context.server().execute(() -> {
            Entry e = find(context.server(), payload.id());
            String who = context.player().getUUID().toString();
            if (e != null && !e.seen.contains(who)) {
                e.seen.add(who);
                save(context.server());
                send(context.player());
            }
        }));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> join(server, handler.getPlayer())));
        ServerTickEvents.END_SERVER_TICK.register(HuntBoard::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof HarvestmanEntity && entity.level() instanceof ServerLevel level) {
                done(level.getServer(), "target:" + entity.getUUID(), "Overwatch confirms the kill. Tall contact down. Good work, Reaper.");
            }
        });
        PowerGrid.OFF.register((level, id) -> {
            Districts.District d = Districts.byId(id);
            if (d != null && find(level.getServer(), "district:" + id) != null) {
                add(level.getServer(), "substation:" + id, OBJECTIVE, "Restore the " + d.name() + " substation", "In " + d.name(),
                        "Its mains are back on", "The district's power, and purge credit", OBJECTIVE_CREDIT,
                        "Mains are down in " + d.name() + ". Get its substation running. Overwatch out.");
            }
        });
        PowerGrid.ON.register((level, id) -> done(level.getServer(), "substation:" + id, "Power's back in the district. Nice work, Reaper."));
    }

    // ---- what other threads call ----

    /** An objective is done: "lab_data", "dam", or a "substation:<district>". */
    public static void objectiveDone(MinecraftServer server, String id) {
        done(server, id.contains(":") ? id : "objective:" + id, "Objective complete. Overwatch copies.");
    }

    /** A survivor to bring out: they go on the board as an escort. */
    public static void addEscort(MinecraftServer server, UUID survivor, String name, BlockPos where) {
        add(server, "escort:" + survivor, ESCORT, "Bring " + name + " to an extraction point", near(server, where),
                "They reach the airport, the carrier or Camp Black Pine alive", "Purge credit and a resupply crate", ESCORT_CREDIT,
                "Reaper, you've got a civilian with you. Get " + name + " to an extraction point. There's a crate in it for you.");
    }

    /** That survivor reached an extraction point. */
    public static void escortDelivered(MinecraftServer server, UUID survivor) {
        done(server, "escort:" + survivor, "Civilian's in. Resupply crate is on the pad. Overwatch out.");
    }

    /** The board's entries (a copy), for anyone who needs them server-side. */
    public static List<Entry> entries(MinecraftServer server) {
        return List.copyOf(board(server).entries);
    }

    /** The purge total: credit from every done entry. */
    public static int total(MinecraftServer server) {
        return board(server).entries.stream().filter(e -> "done".equals(e.status)).mapToInt(e -> e.credit).sum();
    }

    /** Something on the board or the net changed: send it out at the end of the tick. */
    static void changed(MinecraftServer server) {
        dirty = true;
    }

    // ---- the board ----

    private static void join(MinecraftServer server, ServerPlayer player) {
        Board b = board(server);
        String who = player.getUUID().toString();
        if (!b.inserted.contains(who)) {
            b.inserted.add(who);
            String town = districtName((ServerLevel) player.level(), player.blockPosition());
            String at = town == null ? "the county" : town;
            UUID id = player.getUUID();
            CommandNet.sayLater(server, 100, id, "Reaper, Overwatch. Do you copy? We lost your bird over " + at + ".");
            CommandNet.sayLater(server, 220, id, "Your kit went down with it. You were going in light anyway. Use what you find.");
            CommandNet.sayLater(server, 340, id, "Your orders stand: purge the county. Extraction points are the airport, the carrier and Camp Black Pine.");
            CommandNet.sayLater(server, 460, id, "Taskings will come over this net. Overwatch out.");
            save(server);
        }
        send(player);
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (ZcConfig.get().broadcast.enabled && now % 40 == 0) {
            scan(server);
        }
        if (dirty) {
            dirty = false;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                send(p);
            }
        }
    }

    private static void scan(MinecraftServer server) {
        Board b = board(server);
        if (b.entries.isEmpty()) {
            add(server, "objective:lab_data", OBJECTIVE, "Recover the lab data", "At Ridgeview Labs",
                    "The lab's research drive is in your hands", "Purge credit, and answers", OBJECTIVE_CREDIT, null);
            add(server, "objective:dam", OBJECTIVE, "Retake the dam", "At the dam",
                    "The dam's control room is running again", "Water, and purge credit", OBJECTIVE_CREDIT, null);
        }
        double clear = ZcConfig.get().hunt.districtClear;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) {
                continue;
            }
            ServerLevel level = (ServerLevel) p.level();
            String id = PowerGrid.district(level, p.blockPosition());
            Districts.District d = Districts.byId(id);
            if (d != null && find(server, "district:" + id) == null) {
                add(server, "district:" + id, DISTRICT, "Purge " + d.name(), "In " + d.name(),
                        "Its walkers are down to " + Math.round(clear * 100) + "% of what it started with",
                        "Purge credit, and a safe spot for an outpost", DISTRICT_CREDIT,
                        "New tasking, Reaper: purge " + d.name() + ". Overwatch out.");
            }
            for (HarvestmanEntity h : level.getEntitiesOfClass(HarvestmanEntity.class, p.getBoundingBox().inflate(48))) {
                if (h.isAlive() && find(server, "target:" + h.getUUID()) == null) {
                    String near = districtName(level, h.blockPosition());
                    add(server, "target:" + h.getUUID(), TARGET, "Kill the tall contact", near == null ? "Last seen near you" : "Last seen in " + near,
                            "It's dead", "Big purge credit", TARGET_CREDIT,
                            "New tasking: tall contact sighted" + (near == null ? "" : " in " + near) + ". High-value target. Aim for the joints. Overwatch out.");
                }
            }
        }
        // district clears, from the population thread's walker counts
        ServerLevel overworld = server.overworld();
        for (Entry e : new ArrayList<>(b.entries)) {
            if (DISTRICT.equals(e.kind) && "open".equals(e.status)) {
                String id = e.id.substring("district:".length());
                double share = walkers.share(overworld, id);
                if (share >= 0 && share <= clear) {
                    Districts.District d = Districts.byId(id);
                    done(server, e.id, (d == null ? "That district" : d.name()) + " is clear. Set up an outpost if you want it. Good work, Reaper.");
                }
            }
        }
    }

    private static void add(MinecraftServer server, String id, String kind, String title, String where, String doneWhen,
                            String gives, int credit, String caption) {
        Board b = board(server);
        if (find(server, id) != null) {
            return;
        }
        Entry e = new Entry();
        e.id = id;
        e.kind = kind;
        e.title = title;
        e.where = where;
        e.doneWhen = doneWhen;
        e.gives = gives;
        e.credit = credit;
        e.caption = caption == null ? "" : caption;
        e.time = server.overworld().getGameTime();
        b.entries.add(e);
        save(server);
        if (caption != null) {
            CommandNet.say(server, caption);
        }
    }

    private static void done(MinecraftServer server, String id, String caption) {
        Entry e = find(server, id);
        if (e == null || "done".equals(e.status)) {
            return;
        }
        e.status = "done";
        e.doneAt = server.overworld().getGameTime();
        save(server);
        CommandNet.say(server, caption + " Purge total " + total(server) + ".");
    }

    private static Entry find(MinecraftServer server, String id) {
        for (Entry e : board(server).entries) {
            if (e.id.equals(id)) {
                return e;
            }
        }
        return null;
    }

    private static String near(MinecraftServer server, BlockPos pos) {
        String n = districtName(server.overworld(), pos);
        return n == null ? "With you" : "In " + n;
    }

    private static String districtName(ServerLevel level, BlockPos pos) {
        Districts.District d = Districts.byId(PowerGrid.district(level, pos));
        return d == null ? null : d.name();
    }

    private static Board board(MinecraftServer server) {
        if (board == null) {
            String json = server.overworld().getAttachedOrElse(STATE, "");
            Board b = null;
            try {
                b = json.isEmpty() ? null : GSON.fromJson(json, Board.class);
            } catch (RuntimeException ex) {
                ZomboidCraft.LOGGER.error("Couldn't read the hunt board", ex);
            }
            board = b == null ? new Board() : b;
        }
        return board;
    }

    private static void save(MinecraftServer server) {
        server.overworld().setAttached(STATE, GSON.toJson(board(server)));
        dirty = true;
    }

    private static void send(ServerPlayer player) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        String who = player.getUUID().toString();
        JsonObject root = new JsonObject();
        root.addProperty("total", total(server));
        root.addProperty("hears", CommandNet.hears(player));
        JsonArray list = new JsonArray();
        for (Entry e : board(server).entries) {
            JsonObject o = GSON.toJsonTree(e).getAsJsonObject();
            o.remove("seen");
            o.addProperty("seen", e.seen.contains(who));
            list.add(o);
        }
        root.add("entries", list);
        JsonArray log = new JsonArray();
        for (CommandNet.Said s : CommandNet.log()) {
            JsonObject o = new JsonObject();
            o.addProperty("text", s.text());
            o.addProperty("time", s.time());
            log.add(o);
        }
        root.add("log", log);
        ServerPlayNetworking.send(player, new Sync(root.toString()));
    }

    /** A new world (or the server stopping): forget the cached board. */
    public static void reset() {
        board = null;
    }

    private HuntBoard() {
    }
}
