package com.zomboidcraft.zombie;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zomboidcraft.ZcConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * The measuring stick for big hordes. {@code /zc perf} says how long the server's ticks take and how many zombies it
 * is running (and turns the client's hotbar readout of what is drawn on or off); {@code /zc horde <count> [distance]}
 * drops that many around you, so a test can be run the same way each time.
 */
public final class PerfCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("perf").executes(PerfCommand::perf))
                        .then(Commands.literal("horde")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 2000))
                                        .executes(ctx -> horde(ctx, IntegerArgumentType.getInteger(ctx, "count"), 32))
                                        .then(Commands.argument("distance", IntegerArgumentType.integer(4, 128))
                                                .executes(ctx -> horde(ctx, IntegerArgumentType.getInteger(ctx, "count"),
                                                        IntegerArgumentType.getInteger(ctx, "distance"))))))));
    }

    private static int perf(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayer you = ctx.getSource().getPlayer();
        int walkers = 0, chasing = 0, near = 0, entities = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                entities++;
                if (!ZombieTypes.isWalker(e)) {
                    continue;
                }
                walkers++;
                if (((Mob) e).getTarget() != null) {
                    chasing++;
                }
                if (you != null && e.level() == you.level() && e.distanceToSqr(you) < 64 * 64) {
                    near++;
                }
            }
        }
        double tickMs = server.getAverageTickTimeNanos() / 1e6;
        String line = String.format("Server tick %.1f ms (budget 50) · walkers %d (%d chasing, %d within 64 of you) · entities %d",
                tickMs, walkers, chasing, near, entities);
        // the client's readout of what it draws rides on the same command: on with the first /zc perf, off with the next
        ZcConfig.Performance perf = ZcConfig.get().performance;
        perf.showStats = !perf.showStats;
        ZcConfig.save();
        String readout = perf.showStats ? "Hotbar readout on (run /zc perf again to turn it off)" : "Hotbar readout off";
        String pop = com.zomboidcraft.zombie.population.Population.stats();
        ctx.getSource().sendSuccess(() -> Component.literal(line + "\n" + pop + "\n" + readout), false);
        return walkers;
    }

    private static int horde(CommandContext<CommandSourceStack> ctx, int count, int distance) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int made = ZombieSpawner.spawnAround((ServerLevel) player.level(), player, count, distance);
        ctx.getSource().sendSuccess(() -> Component.literal("Dropped " + made + " of " + count + " zombies about "
                + distance + " blocks around you"), false);
        return made;
    }

    private PerfCommand() {
    }
}
