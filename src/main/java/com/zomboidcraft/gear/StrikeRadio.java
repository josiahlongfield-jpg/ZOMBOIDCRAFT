package com.zomboidcraft.gear;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.events.StrikeCalls;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The radio operator's side of a called air strike (wave 2 of the command net plan, Josiah 2026-11-06): the G menu
 * sends a {@link Call} (what to send and where), this builds the mark, hands it to AIRCRAFT's {@link StrikeCalls},
 * and Overwatch answers on the command net. Every moment of the strike after that (in hot, rounds away, the wave
 * off, the effect on target) is voiced from {@link StrikeCalls#listen}. Captions only, as every broadcast is.
 * <p>
 * A call with friendlies too close is held for the operator to confirm danger close (or cancel) for
 * {@link #PENDING_TICKS}; the hunt board sync carries that and the base's air support as "strike".
 */
public final class StrikeRadio {
    /** How long a call waits for "Confirm danger close". */
    private static final int PENDING_TICKS = 30 * 20;
    /** How far the operator's eye reads a grid square. */
    private static final double GRID_REACH = 400;

    /** C2S: call this strike on this mark ("laser", "smoke" or "grid"); confirm answers a danger close hold. */
    public record Call(String strike, String mark, boolean confirm) implements CustomPacketPayload {
        public static final Type<Call> TYPE = new Type<>(ZomboidCraft.id("strike_call"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Call> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Call::strike, ByteBufCodecs.STRING_UTF8, Call::mark, ByteBufCodecs.BOOL, Call::confirm, Call::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: "cancel" drops a danger close hold; "abort" waves the operator's strike off. */
    public record Cancel(String what) implements CustomPacketPayload {
        public static final Type<Cancel> TYPE = new Type<>(ZomboidCraft.id("strike_cancel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cancel> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Cancel::what, Cancel::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record Pending(StrikeCalls.StrikeType type, String mark, StrikeCalls.Mark at, List<String> names, long until) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** The strikes under way, by id, and who called them. */
    private static final Map<Integer, UUID> LIVE = new HashMap<>();
    /** How each strike under way was marked, for the lines that name it. */
    private static final Map<Integer, String> MARKED = new HashMap<>();
    private static MinecraftServer server;

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Call.TYPE, Call.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Cancel.TYPE, Cancel.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Call.TYPE, (payload, context) -> context.server().execute(() -> call(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(Cancel.TYPE, (payload, context) -> context.server().execute(() -> cancel(context.player(), payload.what())));
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            PENDING.clear();
            LIVE.clear();
            MARKED.clear();
        });
        StrikeCalls.listen(StrikeRadio::moment);
    }

    // ---- the operator's calls ----

    private static void call(ServerPlayer p, Call c) {
        ServerLevel level = (ServerLevel) p.level();
        MinecraftServer s = level.getServer();
        StrikeCalls.StrikeType type;
        StrikeCalls.Mark mark;
        String how;
        Pending held = PENDING.get(p.getUUID());
        if (c.confirm()) {
            PENDING.remove(p.getUUID());
            if (held == null || held.until < level.getGameTime()) {
                to(s, p, "Reaper, Overwatch. Say again your call. Nothing is waiting on a confirm.");
                HuntBoard.changed(s);
                return;
            }
            type = held.type;
            how = held.mark;
            // a laser is read again; a smoke or grid stays where it was
            mark = held.at instanceof StrikeCalls.Laser ? StrikeCalls.laser(p) : held.at;
        } else {
            try {
                type = StrikeCalls.StrikeType.valueOf(c.strike().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return;
            }
            how = c.mark();
            mark = mark(level, p, how);
            if (mark == null) {
                to(s, p, switch (how) {
                    case "smoke" -> "Negative, Reaper. I see no smoke near you. Pop smoke on the target and call again.";
                    default -> "Negative, Reaper. I can't make out that grid. Look at the ground you want hit.";
                });
                return;
            }
        }
        StrikeCalls.CallResult r = StrikeCalls.call(level, p, type, mark, null, 0, c.confirm());
        if (r.accepted()) {
            LIVE.put(r.id(), p.getUUID());
            MARKED.put(r.id(), how);
            int secs = Math.max(1, Math.round(r.etaTicks() / 20f));
            String close = r.dangerClose().isEmpty() ? "" : " Danger close. Heads down.";
            to(s, p, "Reaper, Overwatch. Copy " + name(type) + " on " + markWord(how) + ". " + callsign(type)
                    + " inbound, time on target " + secs + " seconds." + close);
        } else if (r.reason() == StrikeCalls.Refusal.DANGER_CLOSE_UNCONFIRMED) {
            PENDING.put(p.getUUID(), new Pending(type, how, mark, r.dangerClose(), level.getGameTime() + PENDING_TICKS));
            to(s, p, "Reaper, Overwatch. That's danger close on " + names(r.dangerClose()) + ". Confirm danger close, or cancel.");
        } else {
            to(s, p, refusal(level, p, r.reason(), how));
        }
        HuntBoard.changed(s);
    }

    private static void cancel(ServerPlayer p, String what) {
        MinecraftServer s = ((ServerLevel) p.level()).getServer();
        if (what.equals("cancel")) {
            if (PENDING.remove(p.getUUID()) != null) {
                to(s, p, "Overwatch copies, call cancelled. Standing by.");
            }
        } else if (what.equals("abort") && LIVE.containsValue(p.getUUID()) && !StrikeCalls.abort(p)) {
            // a wave off that worked is voiced by the ABORTED moment
            to(s, p, "Negative, Reaper. Too late to wave off. Get your heads down.");
        }
        HuntBoard.changed(s);
    }

    private static StrikeCalls.Mark mark(ServerLevel level, ServerPlayer p, String how) {
        return switch (how) {
            case "laser" -> StrikeCalls.laser(p);
            case "smoke" -> StrikeCalls.smoke(level, p, null, Vec3.ZERO);
            case "grid" -> {
                Vec3 eye = p.getEyePosition(), to = eye.add(p.getViewVector(1f).scale(GRID_REACH));
                BlockHitResult hit = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
                yield hit.getType() == HitResult.Type.MISS ? null : StrikeCalls.grid(level, hit.getLocation().x, hit.getLocation().z);
            }
            default -> null;
        };
    }

    private static String refusal(ServerLevel level, ServerPlayer p, StrikeCalls.Refusal why, String how) {
        return switch (why) {
            case NO_RADIO -> "Reaper, you need a field radio to call fires.";
            case COOLDOWN -> {
                if (LIVE.containsValue(p.getUUID())) {
                    yield "Negative, Reaper. Your last strike is still on station.";
                }
                StrikeCalls.Status st = StrikeCalls.status(level, StrikeCalls.BASE.apply(p));
                int mins = Math.max(1, Math.round(st.ticksLeft() / 1200f));
                yield "Negative, Reaper. No air available. Next sortie in about " + mins + (mins == 1 ? " minute." : " minutes.");
            }
            case MARK_LOST -> how.equals("laser")
                    ? "Negative, Reaper. No laser on target. Hold your designator on it and call again."
                    : "Negative, Reaper. I've lost your mark. Call again.";
            case NO_CLEAR_RUN -> "Negative, Reaper. No clear run in on that mark. Find open ground and call again.";
            case DANGER_CLOSE_UNCONFIRMED -> "Reaper, confirm danger close.";
        };
    }

    // ---- the strike as it happens ----

    private static void moment(StrikeCalls.Update u) {
        MinecraftServer s = server;
        if (s == null) {
            return;
        }
        String cs = callsign(u.type());
        switch (u.moment()) {
            case CHECKED_IN -> {
            }
            case IN_HOT -> CommandNet.say(s, cs + ", in hot.");
            case RELEASED -> CommandNet.say(s, switch (u.type()) {
                case GUN_RUN -> cs + ", guns guns guns.";
                case BOMBS -> cs + ", bombs away. Heads down.";
                case APACHE -> cs + ", engaging.";
            });
            case ABORTED -> {
                CommandNet.say(s, u.why() == StrikeCalls.Refusal.MARK_LOST ? cs + ", lost the mark. Aborting. Strike is off."
                        : cs + ", copy abort. Waving off.");
                over(s, u.id());
            }
            case RESULT -> {
                String effect = u.kills() > 0 ? "Overwatch: good effect on target, " + u.kills() + " confirmed. " + cs + " is RTB."
                        : "Overwatch: no effect observed. " + cs + " is RTB.";
                CommandNet.say(s, effect);
                if (!u.hurt().isEmpty()) {
                    CommandNet.say(s, "Overwatch: friendlies hit, " + names(u.hurt()) + ". Check your people.");
                }
                over(s, u.id());
            }
        }
    }

    private static void over(MinecraftServer s, int id) {
        LIVE.remove(id);
        MARKED.remove(id);
        HuntBoard.changed(s);
    }

    // ---- the hunt board's "strike" ----

    /** The base's air support and this operator's call, for the hunt board sync and the G menu. */
    static JsonObject json(ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        StrikeCalls.Status st = StrikeCalls.status(level, StrikeCalls.BASE.apply(p));
        JsonObject o = new JsonObject();
        o.addProperty("ready", st.ready());
        o.addProperty("readyAt", level.getGameTime() + st.ticksLeft());
        o.addProperty("bonus", st.bonus());
        o.addProperty("mine", LIVE.containsValue(p.getUUID()));
        Pending held = PENDING.get(p.getUUID());
        if (held != null && held.until >= level.getGameTime()) {
            JsonObject h = new JsonObject();
            h.addProperty("type", held.type.name().toLowerCase(Locale.ROOT));
            h.addProperty("mark", held.mark);
            JsonArray n = new JsonArray();
            held.names.forEach(n::add);
            h.add("names", n);
            o.add("pending", h);
        }
        if (st.lastResult() != null) {
            JsonObject l = new JsonObject();
            l.addProperty("type", st.lastResult().type().name().toLowerCase(Locale.ROOT));
            l.addProperty("day", st.lastResult().day());
            l.addProperty("kills", st.lastResult().kills());
            l.addProperty("friendlies", st.lastResult().friendlies());
            o.add("last", l);
        }
        return o;
    }

    // ---- words ----

    private static void to(MinecraftServer s, ServerPlayer p, String text) {
        CommandNet.sayLater(s, 20, p.getUUID(), text);
    }

    static String name(StrikeCalls.StrikeType t) {
        return switch (t) {
            case GUN_RUN -> "gun run";
            case BOMBS -> "bombs";
            case APACHE -> "Apache";
        };
    }

    static String callsign(StrikeCalls.StrikeType t) {
        return switch (t) {
            case GUN_RUN -> "Hammer two-one";
            case BOMBS -> "Anvil one-one";
            case APACHE -> "Saber three-one";
        };
    }

    private static String markWord(String how) {
        return switch (how) {
            case "laser" -> "your laser";
            case "smoke" -> "your smoke";
            default -> "your grid";
        };
    }

    private static String names(List<String> names) {
        if (names.size() <= 1) {
            return names.isEmpty() ? "friendlies" : names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    private StrikeRadio() {
    }
}
