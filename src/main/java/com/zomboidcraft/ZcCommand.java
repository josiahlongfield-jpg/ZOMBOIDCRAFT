package com.zomboidcraft;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.SurvivalStats;
import com.zomboidcraft.world.CityPlacer;
import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.Cars;
import com.zomboidcraft.world.weather.Weather;
import com.zomboidcraft.world.weather.WeatherDirector;
import com.zomboidcraft.world.weather.WeatherKind;
import com.zomboidcraft.zombie.ZombieLook;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** /zc: inspect and set survival stats, and change settings. Ops only. */
public final class ZcCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("skills").executes(ZcCommand::skills)));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("stats").executes(ZcCommand::show))
                        .then(Commands.literal("city")
                                .executes(ctx -> city(ctx, 1L))
                                .then(Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(ctx -> city(ctx, IntegerArgumentType.getInteger(ctx, "seed")))))
                        .then(Commands.literal("car")
                                .executes(ctx -> car(ctx, null, null))
                                .then(Commands.argument("model", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(CarModels.all().keySet(), b))
                                        .executes(ctx -> car(ctx, StringArgumentType.getString(ctx, "model"), null))
                                        .then(Commands.argument("variant", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(CarModels.variants(), b))
                                                .executes(ctx -> car(ctx, StringArgumentType.getString(ctx, "model"),
                                                        StringArgumentType.getString(ctx, "variant"))))))
                        .then(Commands.literal("zombie")
                                .executes(ctx -> zombie(ctx, null, "walker"))
                                .then(Commands.argument("outfit", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ZombieLook.outfits(), b))
                                        .executes(ctx -> zombie(ctx, StringArgumentType.getString(ctx, "outfit"), "walker"))
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ZOMBIE_KINDS, b))
                                                .executes(ctx -> zombie(ctx, StringArgumentType.getString(ctx, "outfit"),
                                                        StringArgumentType.getString(ctx, "kind"))))))
                        .then(Commands.literal("weather")
                                .executes(ZcCommand::weather)
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(WeatherKind.values()).map(k -> k.id), b))
                                        .executes(ctx -> setWeather(ctx, StringArgumentType.getString(ctx, "kind")))))
                        .then(Commands.literal("config")
                                .executes(ZcCommand::listConfig)
                                .then(Commands.literal("reload").executes(ctx -> {
                                    ZcConfig.load();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Reloaded config/zomboidcraft.json"), true);
                                    return 1;
                                }))
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ZcConfig.keys(), builder))
                                        .executes(ctx -> showConfig(ctx, StringArgumentType.getString(ctx, "key")))
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .executes(ctx -> setConfig(ctx, StringArgumentType.getString(ctx, "key"),
                                                        StringArgumentType.getString(ctx, "value"))))))
                        .then(Commands.literal("infect").executes(ctx -> update(ctx, s -> s.withInfection(0))))
                        .then(Commands.literal("cure").executes(ctx -> {
                            com.zomboidcraft.survival.Body.set(ctx.getSource().getPlayerOrException(), com.zomboidcraft.survival.Body.FRESH);
                            return update(ctx, s -> s.withInfection(-1).withWounds(0));
                        }))
                        .then(Commands.literal("reset").executes(ctx -> update(ctx, s -> SurvivalStats.FRESH)))
                        .then(Commands.literal("thirst")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0, 100))
                                        .executes(ctx -> update(ctx, s -> s.withThirst(FloatArgumentType.getFloat(ctx, "value"))))))
                        .then(Commands.literal("fatigue")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0, 100))
                                        .executes(ctx -> update(ctx, s -> s.withFatigue(FloatArgumentType.getFloat(ctx, "value"))))))
                        .then(Commands.literal("wounds")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 10))
                                        .executes(ctx -> update(ctx, s -> s.withWounds(IntegerArgumentType.getInteger(ctx, "value"))))))
                        .then(Commands.literal("injure")
                                .then(Commands.argument("part", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(com.zomboidcraft.survival.Body.Region.values())
                                                        .map(r -> r.name().toLowerCase(java.util.Locale.ROOT)), builder))
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        java.util.List.of("scratch", "cut", "bite", "burn", "break"), builder))
                                                .executes(ctx -> injure(ctx, StringArgumentType.getString(ctx, "part"),
                                                        StringArgumentType.getString(ctx, "kind"))))))
                        .then(Commands.literal("roll")
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> reroll(ctx, IntegerArgumentType.getInteger(ctx, "tier")))))
                        .then(Commands.literal("heal").executes(ctx -> {
                            com.zomboidcraft.survival.Body.set(ctx.getSource().getPlayerOrException(), com.zomboidcraft.survival.Body.FRESH);
                            return show(ctx);
                        }))));
    }

    /** Rolls the weapon in your hand again at a tier, to try one out. */
    private static int reroll(CommandContext<CommandSourceStack> ctx, int tier) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (com.zomboidcraft.combat.rolls.Rarity.of(held) == null) {
            ctx.getSource().sendFailure(Component.literal("Hold a weapon to roll it"));
            return 0;
        }
        var old = com.zomboidcraft.combat.rolls.Rolls.get(held);
        String place = old != null ? old.place() : com.zomboidcraft.combat.rolls.Rolls.UNKNOWN;
        com.zomboidcraft.combat.rolls.Rolls.set(held,
                com.zomboidcraft.combat.rolls.Rolls.rolled(held, tier, place, player.getRandom()), player.getRandom());
        ctx.getSource().sendSuccess(() -> Component.literal("Rolled your " + held.getHoverName().getString() + " at tier " + tier), false);
        return 1;
    }

    private static int city(CommandContext<CommandSourceStack> ctx, long seed) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!CityPlacer.start((ServerLevel) player.level(), player.blockPosition(), seed)) {
            ctx.getSource().sendFailure(Component.literal("A city is already being built"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Building the test city around you; it takes a minute or two. Best in a superflat world."), true);
        return 1;
    }

    private static final java.util.List<String> ZOMBIE_KINDS = java.util.List.of("walker", "crawler", "legless");

    /** Puts a zombie a few blocks in front of you: any outfit or the one named, walking, crawling or with no legs. */
    private static int zombie(CommandContext<CommandSourceStack> ctx, String outfit, String kind) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if ((outfit != null && !ZombieLook.outfits().contains(outfit)) || !ZOMBIE_KINDS.contains(kind)) {
            ctx.getSource().sendFailure(Component.literal("Outfits: " + String.join(", ", ZombieLook.outfits())
                    + "; then walker, crawler or legless"));
            return 0;
        }
        double yaw = Math.toRadians(player.getYRot());
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(
                player.getX() - Math.sin(yaw) * 4, player.getY(), player.getZ() + Math.cos(yaw) * 4);
        net.minecraft.world.entity.monster.zombie.Zombie zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.spawn(
                (ServerLevel) player.level(), pos, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (zombie == null) {
            ctx.getSource().sendFailure(Component.literal("No room for a zombie there"));
            return 0;
        }
        if (outfit != null) {
            zombie.setAttached(ZombieLook.OUTFIT, outfit);
        }
        switch (kind) {
            case "legless" -> com.zomboidcraft.combat.Downed.makeLegless(zombie);
            case "crawler" -> {
                zombie.setAttached(com.zomboidcraft.combat.Downed.LEGLESS, false);
                com.zomboidcraft.combat.Downed.makeCrawler(zombie);
            }
            default -> com.zomboidcraft.combat.Downed.makeWalker(zombie);
        }
        String name = ZombieLook.outfit(zombie);
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned a " + name + " zombie (" + kind + ")"), false);
        return 1;
    }

    /** Drops a car side-on a few blocks in front of you: a random one, or the model and state named. */
    private static int car(CommandContext<CommandSourceStack> ctx, String model, String variant) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        java.util.Random rng = new java.util.Random();
        CarModels.Model m = model == null ? CarModels.pick(rng, c -> true) : CarModels.get(model);
        if (m == null || (variant != null && !CarModels.variants().contains(variant))) {
            ctx.getSource().sendFailure(Component.literal("Unknown car: " + model + (variant == null ? "" : " " + variant)));
            return 0;
        }
        String look = variant != null ? variant : Cars.pickVariant(rng);
        double yaw = Math.toRadians(player.getYRot());
        double reach = m.size[0] / 2 + 2.5;
        double x = player.getX() - Math.sin(yaw) * reach, z = player.getZ() + Math.cos(yaw) * reach;
        Cars.spawn((ServerLevel) player.level(), x, player.getY(), z, player.getYRot() + 90, m.id, look, true);
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + m.id + " (" + look + ")"), false);
        return 1;
    }

    private static int weather(CommandContext<CommandSourceStack> ctx) {
        Weather w = WeatherDirector.current();
        if (w == null) {
            ctx.getSource().sendFailure(Component.literal("The mod's weather is off (weather.enabled)"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Weather: %s for %d more seconds | rain %.2f thunder %.2f fog %.2f wind %.2f moon %.2f hail %.2f",
                WeatherDirector.kind().id, WeatherDirector.secondsLeft(), w.rain(), w.thunder(), w.fog(), w.wind(),
                w.moon(), w.hail())), false);
        return 1;
    }

    private static int setWeather(CommandContext<CommandSourceStack> ctx, String id) {
        WeatherKind kind = WeatherKind.byId(id);
        if (kind == null || WeatherDirector.current() == null) {
            ctx.getSource().sendFailure(Component.literal(kind == null ? "Unknown weather: " + id
                    : "The mod's weather is off (weather.enabled)"));
            return 0;
        }
        WeatherDirector.force(kind);
        ctx.getSource().sendSuccess(() -> Component.literal("Weather set to " + kind.id), true);
        return 1;
    }

    private static int listConfig(CommandContext<CommandSourceStack> ctx) {
        StringBuilder text = new StringBuilder("ZomboidCraft settings (/zc config <name> <value> to change):");
        for (String key : ZcConfig.keys()) {
            text.append(String.format("%n  %s = %s", key, ZcConfig.getValue(key)));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static int showConfig(CommandContext<CommandSourceStack> ctx, String key) {
        try {
            String value = ZcConfig.getValue(key);
            ctx.getSource().sendSuccess(() -> Component.literal(key + " = " + value), false);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int setConfig(CommandContext<CommandSourceStack> ctx, String key, String value) {
        try {
            ZcConfig.setValue(key, value);
            ctx.getSource().sendSuccess(() -> Component.literal(key + " set to " + ZcConfig.getValue(key)), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int skills(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StringBuilder text = new StringBuilder("Skills:");
        for (Skill skill : Skill.values()) {
            text.append(String.format("%n  %s %d (%d xp)", skill.displayName(), Skills.level(player, skill), Skills.xp(player, skill)));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SurvivalStats s = SurvivalStats.get(player);
        com.zomboidcraft.survival.Body body = com.zomboidcraft.survival.Body.get(player);
        String infection = s.infected() ? Math.round(s.infectionProgress() * 100) + "%" : "none";
        StringBuilder hurt = new StringBuilder();
        for (com.zomboidcraft.survival.Body.Region r : com.zomboidcraft.survival.Body.Region.values()) {
            com.zomboidcraft.survival.Body.Part p = body.part(r);
            if (!p.fine()) hurt.append(hurt.isEmpty() ? "" : ", ").append(com.zomboidcraft.survival.Body.describe(r, p))
                    .append(p.bleeding() ? " (bleeding)" : "");
        }
        String injuries = hurt.isEmpty() ? "none" : hurt.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Thirst %.0f | Fatigue %.0f | Infection %s | Injuries: %s",
                s.thirst(), s.fatigue(), infection, injuries)), false);
        return 1;
    }

    /** /zc injure left_leg break: try out an injury and how the HUD's figure shows it. */
    private static int injure(CommandContext<CommandSourceStack> ctx, String part, String kind) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        com.zomboidcraft.survival.Body.Region region;
        try {
            region = com.zomboidcraft.survival.Body.Region.valueOf(part.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("No body part called " + part));
            return 0;
        }
        com.zomboidcraft.survival.Body body = com.zomboidcraft.survival.Body.get(player);
        body = switch (kind) {
            case "scratch" -> body.wound(region, com.zomboidcraft.survival.Body.Wound.SCRATCH, true);
            case "cut" -> body.wound(region, com.zomboidcraft.survival.Body.Wound.LACERATION, true);
            case "bite" -> body.wound(region, com.zomboidcraft.survival.Body.Wound.BITE, true);
            case "burn" -> body.wound(region, com.zomboidcraft.survival.Body.Wound.BURN, false);
            case "break" -> body.fracture(region);
            default -> null;
        };
        if (body == null) {
            ctx.getSource().sendFailure(Component.literal("Injuries: scratch, cut, bite, burn or break"));
            return 0;
        }
        com.zomboidcraft.survival.Body.set(player, body);
        return show(ctx);
    }

    private static int update(CommandContext<CommandSourceStack> ctx, UnaryOperator<SurvivalStats> change)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SurvivalStats.set(player, change.apply(SurvivalStats.get(player)));
        return show(ctx);
    }

    private ZcCommand() {
    }
}
