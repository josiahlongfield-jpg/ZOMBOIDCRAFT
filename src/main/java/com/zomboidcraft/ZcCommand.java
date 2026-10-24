package com.zomboidcraft;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zomboidcraft.survival.SurvivalStats;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /zc: inspect and set survival stats, for testing. Ops only. */
public final class ZcCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("stats").executes(ZcCommand::show))
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
