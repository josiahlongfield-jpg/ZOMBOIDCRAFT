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
                        .then(Commands.literal("hoist").executes(ZcCommand::hoist))
                        .then(Commands.literal("shoptool")
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(
                                                com.zomboidcraft.world.cars.ShopTool.Kind.values()).map(k -> k.name().toLowerCase(java.util.Locale.ROOT)), b))
                                        .executes(ctx -> shopTool(ctx, StringArgumentType.getString(ctx, "kind")))))
                        .then(Commands.literal("carparts").executes(ctx -> carParts(ctx, null, -1))
                                .then(Commands.argument("part", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(com.zomboidcraft.world.cars.CarParts.names(), b))
                                        .then(Commands.argument("condition", IntegerArgumentType.integer(-1, 100))
                                                .executes(ctx -> carParts(ctx, StringArgumentType.getString(ctx, "part"),
                                                        IntegerArgumentType.getInteger(ctx, "condition"))))))
                        .then(Commands.literal("aircraft")
                                .executes(ctx -> aircraft(ctx, "jet_corvid"))
                                .then(Commands.argument("model", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(AIRCRAFT.keySet(), b))
                                        .executes(ctx -> aircraft(ctx, StringArgumentType.getString(ctx, "model")))))
                        .then(Commands.literal("event")
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                com.zomboidcraft.world.events.WorldEvents.kinds(), b))
                                        .executes(ctx -> worldEvent(ctx, StringArgumentType.getString(ctx, "kind")))))
                        .then(Commands.literal("zombie")
                                .executes(ctx -> zombie(ctx, null, "walker"))
                                .then(Commands.argument("outfit", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ZombieLook.outfits(), b))
                                        .executes(ctx -> zombie(ctx, StringArgumentType.getString(ctx, "outfit"), "walker"))
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ZOMBIE_KINDS, b))
                                                .executes(ctx -> zombie(ctx, StringArgumentType.getString(ctx, "outfit"),
                                                        StringArgumentType.getString(ctx, "kind"))))))
                        .then(Commands.literal("lore").executes(ZcCommand::lore))
                        .then(Commands.literal("weather")
                                .executes(ZcCommand::weather)
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(WeatherKind.values()).map(k -> k.id), b))
                                        .executes(ctx -> setWeather(ctx, StringArgumentType.getString(ctx, "kind")))))
                        .then(Commands.literal("config")
                                .executes(ZcCommand::listConfig)
                                .then(Commands.literal("reload").executes(ctx -> {
                                    com.zomboidcraft.sandbox.Sandbox.reloadConfig();
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

    private static final java.util.List<String> ZOMBIE_KINDS = java.util.List.of("walker", "crawler", "legless", "hopper", "holed");

    /**
     * Puts a zombie a few blocks in front of you: any outfit or the one named, walking, crawling, with no legs,
     * hopping on one (the other shot off at the knee), or walking with its chest blown open.
     */
    private static int zombie(CommandContext<CommandSourceStack> ctx, String outfit, String kind) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if ((outfit != null && !ZombieLook.outfits().contains(outfit)) || !ZOMBIE_KINDS.contains(kind)) {
            ctx.getSource().sendFailure(Component.literal("Outfits: " + String.join(", ", ZombieLook.outfits())
                    + "; then walker, crawler, legless, hopper or holed"));
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
            case "hopper" -> {
                com.zomboidcraft.combat.Downed.makeWalker(zombie);
                com.zomboidcraft.combat.Dismember.restore(zombie);
                com.zomboidcraft.combat.Dismember.cut(zombie, zombie.getRandom().nextBoolean()
                        ? com.zomboidcraft.combat.Dismember.KN_R : com.zomboidcraft.combat.Dismember.KN_L);
            }
            case "holed" -> {
                com.zomboidcraft.combat.Downed.makeWalker(zombie);
                com.zomboidcraft.combat.Dismember.restore(zombie);
                com.zomboidcraft.combat.Dismember.blowOpen(zombie);
            }
            default -> {
                com.zomboidcraft.combat.Downed.makeWalker(zombie);
                com.zomboidcraft.combat.Dismember.restore(zombie);
            }
        }
        String name = ZombieLook.outfit(zombie);
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned a " + name + " zombie (" + kind + ")"), false);
        return 1;
    }

    /** Drops a car side-on a few blocks in front of you: a random one, or the model and state named. */
    /**
     * A two-post car lift set down in front of you, its bay running the way you face (squared to the
     * compass), centred on the block line so its posts stand in whole blocks: for trying it out, and
     * for building your own garage.
     */
    private static int hoist(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
        float yaw = Math.round(player.getYRot() / 90f) * 90f;
        net.minecraft.world.phys.Vec3 ahead = net.minecraft.world.phys.Vec3.directionFromRotation(0, yaw).scale(4.5);
        com.zomboidcraft.world.cars.CarHoist hoist = com.zomboidcraft.world.cars.Cars.HOIST.create(player.level(),
                net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (hoist == null) {
            return 0;
        }
        double x = Math.round(player.getX() + ahead.x), z = Math.round(player.getZ() + ahead.z);
        hoist.snapTo(x, Math.floor(player.getY() + 0.01), z, yaw, 0);
        player.level().addFreshEntity(hoist);
        ctx.getSource().sendSuccess(() -> Component.literal("A car lift, its bay running the way you face. Drive a car in between the posts, "
                + "then G on the control box."), false);
        return 1;
    }

    /** An auto shop floor tool (jack, crane, tyre_machine, washer) set down two blocks ahead, facing you. */
    private static int shopTool(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
        com.zomboidcraft.world.cars.ShopTool.Kind kind = com.zomboidcraft.world.cars.ShopTool.Kind.of(name);
        com.zomboidcraft.world.cars.ShopTool tool = com.zomboidcraft.world.cars.Cars.TOOL.create(player.level(),
                net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (kind == null || tool == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown tool: jack, crane, tyre_machine or washer."));
            return 0;
        }
        float yaw = Math.round(player.getYRot() / 90f) * 90f;
        net.minecraft.world.phys.Vec3 ahead = net.minecraft.world.phys.Vec3.directionFromRotation(0, yaw).scale(2);
        tool.setKind(kind);
        tool.snapTo(player.getX() + ahead.x, Math.floor(player.getY() + 0.01), player.getZ() + ahead.z, yaw + 180, 0);
        player.level().addFreshEntity(tool);
        ctx.getSource().sendSuccess(() -> Component.literal("A " + kind.title.toLowerCase(java.util.Locale.ROOT) + ". G works it."), false);
        return 1;
    }

    /**
     * The nearest car's parts (within 8 blocks), listed; or one set (part name, "tire" for all four, "all" for
     * everything) to a condition, -1 to take it off. For trying out worn and missing parts without driving for days.
     */
    private static int carParts(CommandContext<CommandSourceStack> ctx, String part, int condition) throws CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
        com.zomboidcraft.world.cars.CarProp car = player.level().getEntitiesOfClass(com.zomboidcraft.world.cars.CarProp.class,
                        player.getBoundingBox().inflate(8), c -> !c.parts().isEmpty()).stream()
                .min(java.util.Comparator.comparingDouble(c -> c.distanceToSqr(player))).orElse(null);
        if (car == null) {
            ctx.getSource().sendFailure(Component.literal("No car with parts within 8 blocks."));
            return 0;
        }
        String parts = car.parts();
        if (part != null) {
            java.util.List<Integer> slots = com.zomboidcraft.world.cars.CarParts.slots(part);
            if (slots.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("No part called " + part + "."));
                return 0;
            }
            for (int slot : slots) {
                parts = com.zomboidcraft.world.cars.CarParts.set(parts, slot, condition);
            }
            car.setParts(parts);
        }
        String shown = parts;
        ctx.getSource().sendSuccess(() -> Component.literal(com.zomboidcraft.world.cars.CarParts.describe(car.model(), shown)), false);
        return 1;
    }

    private static int car(CommandContext<CommandSourceStack> ctx, String model, String variant) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        java.util.Random rng = new java.util.Random();
        CarModels.Model m = model == null ? CarModels.pick(rng, c -> !c.boat() && !c.farm() && !c.military()) : CarModels.get(model);
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

    /** The flyable aircraft, by the name the command takes (the model id works too). */
    private static final java.util.Map<String, String> AIRCRAFT = java.util.Map.of(
            "f22", "jet_corvid", "su27", "jet_gyrfalcon", "apache", "heli_hornbill",
            "jet_corvid", "jet_corvid", "jet_gyrfalcon", "jet_gyrfalcon", "heli_hornbill", "heli_hornbill");

    /**
     * An aircraft in front of you, nose the way you're facing, ready to fly: full tanks, a good battery,
     * in full repair, and you've read its flight manual.
     */
    private static int aircraft(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = AIRCRAFT.get(name.toLowerCase(java.util.Locale.ROOT));
        CarModels.Model m = id == null ? null : CarModels.get(id);
        if (m == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown aircraft: " + name + " (f22, su27 or apache)"));
            return 0;
        }
        // far enough ahead that its wings and nose clear you; you stand behind its tail
        double yaw = Math.toRadians(player.getYRot());
        double reach = m.size[2] / 2 + 4;
        double x = player.getX() - Math.sin(yaw) * reach, z = player.getZ() + Math.cos(yaw) * reach;
        com.zomboidcraft.world.cars.CarProp car = Cars.spawn((ServerLevel) player.level(), x, player.getY(), z, player.getYRot(), m.id,
                "usable", true);
        if (car == null) {
            ctx.getSource().sendFailure(Component.literal("Couldn't spawn " + m.id));
            return 0;
        }
        car.readyToFly();
        var manual = com.zomboidcraft.world.cars.AirReadiness.manualFor(car.helicopter()
                ? com.zomboidcraft.world.cars.Flight.Kind.HELI : com.zomboidcraft.world.cars.Flight.Kind.JET);
        java.util.List<String> read = new java.util.ArrayList<>(player.getAttachedOrCreate(com.zomboidcraft.world.cars.AirReadiness.READ));
        if (!read.contains(manual.name())) {
            read.add(manual.name());
            player.setAttached(com.zomboidcraft.world.cars.AirReadiness.READ, read);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + m.id + ", fuelled and ready: G to get in, C to start"), false);
        return 1;
    }

    /**
     * /zc event jet|heli|siren|explosion|strafe|bomb|hunt: starts one of the shared world events around you now (a
     * strike with no crowd of zombies about makes its run at the ground ahead of you).
     */
    private static int worldEvent(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        com.zomboidcraft.world.events.WorldEvents.Kind kind;
        try {
            kind = com.zomboidcraft.world.events.WorldEvents.Kind.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Unknown event: " + name + " (jet, heli, siren, explosion, strafe, bomb or hunt)"));
            return 0;
        }
        var e = com.zomboidcraft.world.events.WorldEvents.start(player.level().getServer().overworld(), kind, player, true);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("Started %s at %d %d %d", name, (int) e.x(), (int) e.y(),
                (int) e.z())), false);
        return 1;
    }

    private static int weather(CommandContext<CommandSourceStack> ctx) {
        Weather w = WeatherDirector.current();
        if (w == null) {
            ctx.getSource().sendFailure(Component.literal("The mod's weather is off (weather.enabled)"));
            return 0;
        }
        double air = com.zomboidcraft.world.weather.Climate.airTemp(ctx.getSource().getLevel(),
                net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Weather: %s for %d more seconds | rain %.2f thunder %.2f fog %.2f wind %.2f moon %.2f hail %.2f | air %.0f°C",
                WeatherDirector.kind().id, WeatherDirector.secondsLeft(), w.rain(), w.thunder(), w.fog(), w.wind(),
                w.moon(), w.hail(), air)), false);
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

    /** What the zombie you're looking at rolled under Zomboid's Zombie Lore settings (zombie/Lore). */
    private static int lore(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        net.minecraft.world.entity.monster.zombie.Zombie best = null;
        double bestCos = Math.cos(Math.toRadians(8));
        for (net.minecraft.world.entity.monster.zombie.Zombie z : player.level().getEntitiesOfClass(
                net.minecraft.world.entity.monster.zombie.Zombie.class, player.getBoundingBox().inflate(48),
                com.zomboidcraft.zombie.ZombieTypes::isWalker)) {
            net.minecraft.world.phys.Vec3 to = z.getBoundingBox().getCenter().subtract(eye);
            double cos = to.normalize().dot(look);
            if (cos > bestCos) {
                bestCos = cos;
                best = z;
            }
        }
        if (best == null) {
            ctx.getSource().sendFailure(Component.literal("Look at a zombie first."));
            return 0;
        }
        String text = com.zomboidcraft.zombie.Lore.describe(best) + ", armour "
                + Math.round(com.zomboidcraft.zombie.ZombieArmor.share(best) * 100) + "%, health "
                + Math.round(best.getHealth()) + "/" + Math.round(best.getMaxHealth());
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
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
            com.zomboidcraft.sandbox.Sandbox.configChanged(key);
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
