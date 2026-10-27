package com.zomboidcraft;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.zomboidcraft.world.weather.WeatherDirector;
import com.zomboidcraft.world.weather.WeatherKind;
import com.zomboidcraft.zombie.Noise;
import com.zomboidcraft.zombie.ZombieSpawner;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * /zc cinematic: a film set for trailers. Clears the HUD and the green dots off your screen (the gun stays in your
 * hands), lets zombies ignore you or stand frozen mid-step, brings a horde up the street, holds the weather and the
 * clock where you put them, and slows flying down for steady camera moves. /zc cinematic off puts it all back.
 */
public final class Cinematic {
    /** What a filming player's client hides or changes: bit CLEAN clears the HUD and dots, SMOOTH eases the mouse. */
    public static final int CLEAN = 1, SMOOTH = 2;
    public static final AttachmentType<Integer> VIEW = AttachmentRegistry.create(
            ZomboidCraft.id("cinematic"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
    /** On zombies the freeze stopped, kept in the save so a world left frozen wakes them again next time. */
    private static final AttachmentType<Boolean> FROZEN = AttachmentRegistry.create(
            ZomboidCraft.id("cinematic_frozen"),
            builder -> builder.persistent(Codec.BOOL));

    private static final Map<String, Integer> TIMES = Map.of(
            "day", 1000, "noon", 6000, "dusk", 12000, "night", 13000, "midnight", 18000, "dawn", 23000);
    private static final float NORMAL_FLY = 0.05f;

    /** Players zombies can't see (they still hear the shots and come to look). */
    private static final Set<UUID> GHOSTS = new HashSet<>();
    private static boolean frozen;
    private static boolean timeHeld;

    public static boolean ghost(Player player) {
        return GHOSTS.contains(player.getUUID());
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Cinematic::tick);
        // a zombie loaded still frozen from an earlier session walks again unless the set is frozen now
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!frozen && entity instanceof Zombie z && z.hasAttached(FROZEN)) {
                thaw(z);
            }
        });
        // abilities are saved with the player, so leaving mid-shot mustn't keep you unhurtable
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (ghost(handler.getPlayer())) {
                setGhost(handler.getPlayer(), false);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("cinematic")
                                .executes(Cinematic::on)
                                .then(Commands.literal("on").executes(Cinematic::on))
                                .then(Commands.literal("off").executes(Cinematic::off))
                                .then(Commands.literal("help").executes(Cinematic::help))
                                .then(Commands.literal("hud")
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("clean", "full"), b))
                                                .executes(ctx -> hud(ctx, StringArgumentType.getString(ctx, "mode")))))
                                .then(Commands.literal("zombies")
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("ghost", "freeze", "normal"), b))
                                                .executes(ctx -> zombies(ctx, StringArgumentType.getString(ctx, "mode")))))
                                .then(Commands.literal("lure")
                                        .executes(ctx -> lure(ctx, 64))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 256))
                                                .executes(ctx -> lure(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                                .then(Commands.literal("horde")
                                        .executes(ctx -> horde(ctx, 40, 28))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 300))
                                                .executes(ctx -> horde(ctx, IntegerArgumentType.getInteger(ctx, "count"), 28))
                                                .then(Commands.argument("distance", IntegerArgumentType.integer(6, 96))
                                                        .executes(ctx -> horde(ctx, IntegerArgumentType.getInteger(ctx, "count"),
                                                                IntegerArgumentType.getInteger(ctx, "distance"))))))
                                .then(Commands.literal("time")
                                        .then(Commands.argument("when", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                        List.of("dusk", "night", "midnight", "dawn", "day", "noon", "free"), b))
                                                .executes(ctx -> time(ctx, StringArgumentType.getString(ctx, "when")))))
                                .then(Commands.literal("weather")
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                        java.util.stream.Stream.concat(java.util.Arrays.stream(WeatherKind.values()).map(k -> k.id),
                                                                java.util.stream.Stream.of("free")), b))
                                                .executes(ctx -> weather(ctx, StringArgumentType.getString(ctx, "kind")))))
                                .then(Commands.literal("fly")
                                        .executes(ctx -> fly(ctx, 0.015f))
                                        .then(Commands.literal("normal").executes(ctx -> fly(ctx, NORMAL_FLY)))
                                        .then(Commands.argument("speed", FloatArgumentType.floatArg(0.002f, 0.5f))
                                                .executes(ctx -> fly(ctx, FloatArgumentType.getFloat(ctx, "speed")))))
                                .then(Commands.literal("smooth")
                                        .then(Commands.literal("on").executes(ctx -> view(ctx, SMOOTH, true, "Smooth mouse on")))
                                        .then(Commands.literal("off").executes(ctx -> view(ctx, SMOOTH, false, "Smooth mouse off")))))));
    }

    /** Clean screen and zombies that ignore you: the usual setup for a shot. */
    private static int on(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        setView(player, viewOf(player) | CLEAN);
        setGhost(player, true);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Cinematic on: HUD and dots hidden, zombies ignore you, you can't be hurt. /zc cinematic help for the rest,"
                        + " /zc cinematic off to finish (chat still shows while it's open)"), false);
        return 1;
    }

    private static int off(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        setView(player, 0);
        setGhost(player, false);
        if (frozen) {
            setFrozen(ctx.getSource().getServer(), false);
        }
        WeatherDirector.hold = false;
        if (timeHeld) {
            holdTime(ctx.getSource(), false);
        }
        Abilities a = player.getAbilities();
        a.setFlyingSpeed(NORMAL_FLY);
        if (!player.isCreative() && !player.isSpectator()) {
            a.mayfly = false;
            a.flying = false;
        }
        player.onUpdateAbilities();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Cinematic off: HUD back, zombies normal, weather and clock running, flying back to normal"), false);
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(String.join("\n",
                "/zc cinematic - clean HUD + zombies ignore you",
                "/zc cinematic hud clean|full - hide or show the HUD and green dots (the gun stays)",
                "/zc cinematic zombies ghost|freeze|normal - ignore you / stand still / back to normal",
                "/zc cinematic horde [count] [distance] - a horde up ahead, coming your way",
                "/zc cinematic lure [radius] - every zombie in range comes to you",
                "/zc cinematic time dusk|night|midnight|dawn|day|free - set and hold the clock",
                "/zc cinematic weather <kind>|free - set and hold the weather",
                "/zc cinematic fly [speed|normal] - slow flying for camera moves (default 0.015)",
                "/zc cinematic smooth on|off - eased mouse look",
                "/zc cinematic off - everything back")), false);
        return 1;
    }

    private static int hud(CommandContext<CommandSourceStack> ctx, String mode) throws CommandSyntaxException {
        if (!mode.equals("clean") && !mode.equals("full")) {
            ctx.getSource().sendFailure(Component.literal("hud clean or hud full"));
            return 0;
        }
        boolean clean = mode.equals("clean");
        return view(ctx, CLEAN, clean, clean ? "HUD and dots hidden" : "HUD back");
    }

    private static int view(CommandContext<CommandSourceStack> ctx, int bit, boolean set, String said) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int v = viewOf(player);
        setView(player, set ? v | bit : v & ~bit);
        ctx.getSource().sendSuccess(() -> Component.literal(said), false);
        return 1;
    }

    private static int zombies(CommandContext<CommandSourceStack> ctx, String mode) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        switch (mode) {
            case "ghost" -> {
                setGhost(player, true);
                ctx.getSource().sendSuccess(() -> Component.literal("Zombies ignore you (they still come to noises)"), false);
            }
            case "freeze" -> {
                setFrozen(server, true);
                ctx.getSource().sendSuccess(() -> Component.literal("Every zombie frozen where it stands (normal to let them go)"), false);
            }
            case "normal" -> {
                setGhost(player, false);
                setFrozen(server, false);
                ctx.getSource().sendSuccess(() -> Component.literal("Zombies back to normal"), false);
            }
            default -> {
                ctx.getSource().sendFailure(Component.literal("zombies ghost, freeze or normal"));
                return 0;
            }
        }
        return 1;
    }

    private static int lure(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        // a noise loud enough for the weather not to muffle it, as far as asked
        Noise.emit(level, player.position(), radius / Math.max(0.2, WeatherDirector.hearing(level) * ZcConfig.get().senses.hearing));
        ctx.getSource().sendSuccess(() -> Component.literal("Zombies within " + radius + " blocks are coming to you"), false);
        return 1;
    }

    private static int horde(CommandContext<CommandSourceStack> ctx, int count, int distance) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        int made = ZombieSpawner.spawnAhead(level, player, count, distance);
        if (made == 0) {
            ctx.getSource().sendFailure(Component.literal("No room for a horde up ahead: face down an open street"));
            return 0;
        }
        if (!frozen) {
            Noise.emit(level, player.position(), (distance * 1.5 + 8)
                    / Math.max(0.2, WeatherDirector.hearing(level) * ZcConfig.get().senses.hearing));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(made + " zombies up ahead"
                + (frozen ? " (frozen: zombies normal to set them off)" : ", coming your way")), false);
        return made;
    }

    private static int time(CommandContext<CommandSourceStack> ctx, String when) {
        CommandSourceStack src = ctx.getSource();
        if (when.equals("free")) {
            holdTime(src, false);
            src.sendSuccess(() -> Component.literal("The clock runs again"), false);
            return 1;
        }
        Integer ticks = TIMES.get(when.toLowerCase(Locale.ROOT));
        if (ticks == null) {
            src.sendFailure(Component.literal("time dusk, night, midnight, dawn, day, noon or free"));
            return 0;
        }
        src.getServer().getCommands().performPrefixedCommand(src.withSuppressedOutput(), "time set " + ticks);
        holdTime(src, true);
        src.sendSuccess(() -> Component.literal("Clock set to " + when + " and held"), false);
        return 1;
    }

    private static int weather(CommandContext<CommandSourceStack> ctx, String id) {
        if (id.equals("free")) {
            WeatherDirector.hold = false;
            ctx.getSource().sendSuccess(() -> Component.literal("The weather moves on again"), false);
            return 1;
        }
        WeatherKind kind = WeatherKind.byId(id);
        if (kind == null || WeatherDirector.current() == null) {
            ctx.getSource().sendFailure(Component.literal(kind == null ? "Unknown weather: " + id
                    : "The mod's weather is off (weather.enabled)"));
            return 0;
        }
        WeatherDirector.force(kind);
        WeatherDirector.hold = true;
        ctx.getSource().sendSuccess(() -> Component.literal("Weather set to " + kind.id + " and held"), false);
        return 1;
    }

    private static int fly(CommandContext<CommandSourceStack> ctx, float speed) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Abilities a = player.getAbilities();
        a.mayfly = true;
        a.setFlyingSpeed(speed);
        player.onUpdateAbilities();
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Flying at %.3f (normal is 0.05): double-tap jump to take off", speed)), false);
        return 1;
    }

    private static int viewOf(ServerPlayer player) {
        return player.getAttachedOrElse(VIEW, 0);
    }

    private static void setView(ServerPlayer player, int v) {
        if (v == 0) {
            player.removeAttached(VIEW);
        } else {
            player.setAttached(VIEW, v);
        }
    }

    private static void setGhost(ServerPlayer player, boolean on) {
        Abilities a = player.getAbilities();
        if (on) {
            GHOSTS.add(player.getUUID());
            a.invulnerable = true;
            // whatever was already after you loses interest
            for (Zombie z : player.level().getEntitiesOfClass(Zombie.class, new AABB(player.position(), player.position()).inflate(128))) {
                if (z.getTarget() == player) {
                    z.setTarget(null);
                }
            }
        } else {
            GHOSTS.remove(player.getUUID());
            a.invulnerable = player.isCreative() || player.isSpectator();
        }
        player.onUpdateAbilities();
    }

    private static void setFrozen(MinecraftServer server, boolean on) {
        frozen = on;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Zombie z) {
                    if (on) {
                        freeze(z);
                    } else if (z.hasAttached(FROZEN)) {
                        thaw(z);
                    }
                }
            }
        }
    }

    private static void freeze(Zombie z) {
        if (ZombieTypes.isWalker(z) && z.isAlive() && !z.hasAttached(FROZEN) && !z.isNoAi()) {
            z.setAttached(FROZEN, true);
            z.setNoAi(true);
            z.setTarget(null);
            z.getNavigation().stop();
            z.setDeltaMovement(0, z.getDeltaMovement().y, 0);
        }
    }

    private static void thaw(Zombie z) {
        z.removeAttached(FROZEN);
        z.setNoAi(false);
    }

    /** Stops or restarts the day going by. The rule's name changed in 1.21.11, so both are tried; the wrong one fails quietly. */
    private static void holdTime(CommandSourceStack src, boolean hold) {
        timeHeld = hold;
        CommandSourceStack quiet = src.withSuppressedOutput();
        String value = hold ? " false" : " true";
        src.getServer().getCommands().performPrefixedCommand(quiet, "gamerule advance_time" + value);
        src.getServer().getCommands().performPrefixedCommand(quiet, "gamerule doDaylightCycle" + value);
    }

    private static void tick(MinecraftServer server) {
        // zombies that wake or wander in while the set is frozen freeze too
        if (frozen && server.getTickCount() % 10 == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity e : level.getAllEntities()) {
                    if (e instanceof Zombie z) {
                        freeze(z);
                    }
                }
            }
        }
    }

    private Cinematic() {
    }
}
