package com.zomboidcraft;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.SurvivalStats;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
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
                        .then(Commands.literal("cure").executes(ctx -> update(ctx, s -> s.withInfection(-1).withWounds(0))))
                        .then(Commands.literal("reset").executes(ctx -> update(ctx, s -> SurvivalStats.FRESH)))
                        .then(Commands.literal("thirst")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0, 100))
                                        .executes(ctx -> update(ctx, s -> s.withThirst(FloatArgumentType.getFloat(ctx, "value"))))))
                        .then(Commands.literal("fatigue")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0, 100))
                                        .executes(ctx -> update(ctx, s -> s.withFatigue(FloatArgumentType.getFloat(ctx, "value"))))))
                        .then(Commands.literal("wounds")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 10))
                                        .executes(ctx -> update(ctx, s -> s.withWounds(IntegerArgumentType.getInteger(ctx, "value"))))))));
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
        SurvivalStats s = SurvivalStats.get(ctx.getSource().getPlayerOrException());
        String infection = s.infected() ? Math.round(s.infectionProgress() * 100) + "%" : "none";
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Thirst %.0f | Fatigue %.0f | Wounds %d | Infection %s",
                s.thirst(), s.fatigue(), s.wounds(), infection)), false);
        return 1;
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
