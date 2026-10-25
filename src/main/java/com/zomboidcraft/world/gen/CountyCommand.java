package com.zomboidcraft.world.gen;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * /zc places [type]: the nearest towns, farms, ports and bases with their variants and where
 * they are. /zc goto type: teleports to the nearest one (for trying the world out).
 */
public final class CountyCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("places")
                                .executes(ctx -> places(ctx, null))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(types(), b))
                                        .executes(ctx -> places(ctx, StringArgumentType.getString(ctx, "type")))))
                        .then(Commands.literal("goto")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(types(), b))
                                        .executes(ctx -> go(ctx, StringArgumentType.getString(ctx, "type")))))));
    }

    private static List<String> types() {
        return Arrays.stream(PlaceType.values()).map(t -> t.name().toLowerCase()).toList();
    }

    private static County county(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        if (level.getChunkSource().getGenerator() instanceof CountyChunkGenerator) {
            return CountyChunkGenerator.gen(level.getSeed()).county();
        }
        src.sendFailure(Component.literal("This world isn't a ZomboidCraft County world (pick that world type when creating it)."));
        return null;
    }

    private static List<Place> nearest(County county, Vec3 at, String type, int radius) {
        int x = (int) at.x, z = (int) at.z;
        return county.placesNear(x - radius, z - radius, x + radius, z + radius, 0).stream()
                .filter(p -> type == null || p.type.name().equalsIgnoreCase(type) || p.variant.equalsIgnoreCase(type))
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.centreX() - at.x, p.centreZ() - at.z)))
                .toList();
    }

    private static int places(CommandContext<CommandSourceStack> ctx, String type) {
        CommandSourceStack src = ctx.getSource();
        County county = county(src);
        if (county == null) {
            return 0;
        }
        Vec3 at = src.getPosition();
        List<Place> list = nearest(county, at, type, 3000);
        if (list.isEmpty()) {
            src.sendSuccess(() -> Component.literal("Nothing like that within 3000 blocks."), false);
            return 0;
        }
        for (Place p : list.subList(0, Math.min(12, list.size()))) {
            double dx = p.centreX() - at.x, dz = p.centreZ() - at.z;
            String dir = compass(dx, dz);
            src.sendSuccess(() -> Component.literal(String.format("%s (%s): %d blocks %s, at %d %d %d",
                    p.type.name().toLowerCase(), p.variant.replace('_', ' '), (int) Math.hypot(dx, dz), dir,
                    p.centreX(), p.level + 1, p.centreZ())), false);
        }
        return list.size();
    }

    private static int go(CommandContext<CommandSourceStack> ctx, String type) {
        CommandSourceStack src = ctx.getSource();
        County county = county(src);
        if (county == null || src.getEntity() == null) {
            return 0;
        }
        List<Place> list = nearest(county, src.getPosition(), type, 6000);
        if (list.isEmpty()) {
            src.sendFailure(Component.literal("No " + type + " within 6000 blocks."));
            return 0;
        }
        Place p = list.get(0);
        int[] gate = p.gate(2);
        src.getServer().getCommands().performPrefixedCommand(src, "tp @s " + gate[0] + " " + (p.level + 3) + " " + (gate[1] + 6));
        src.sendSuccess(() -> Component.literal("At the south gate of " + p), false);
        return 1;
    }

    private static String compass(double dx, double dz) {
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        int i = (int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4));
        return names[Math.floorMod(i, 8)];
    }

    private CountyCommand() {
    }
}
