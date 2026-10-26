package com.zomboidcraft.world.gen;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.CountyGen;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;
import com.zomboidcraft.world.county.Voxels;
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
 * they are. /zc goto type: teleports to the nearest one (for trying the world out). /zc bunker:
 * teleports to the hatch of the nearest bunker under a city tower, or with "door" to its blast door.
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
                                        .executes(ctx -> go(ctx, StringArgumentType.getString(ctx, "type")))))
                        .then(Commands.literal("bunker")
                                .executes(ctx -> bunker(ctx, false))
                                .then(Commands.literal("door").executes(ctx -> bunker(ctx, true))))));
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

    /**
     * The nearest downtowns and ports are built (or taken from the cache) and searched for the bunker
     * marks, nearest first: a tower's under its garage, a ship's behind her engine room.
     */
    private static int bunker(CommandContext<CommandSourceStack> ctx, boolean door) {
        CommandSourceStack src = ctx.getSource();
        County county = county(src);
        if (county == null || src.getEntity() == null) {
            return 0;
        }
        Vec3 from = src.getPosition();
        List<Place> towns = nearest(county, from, PlaceType.DOWNTOWN.name(), 8000);
        List<Place> ports = nearest(county, from, PlaceType.PORT.name(), 8000);
        List<Place> search = new java.util.ArrayList<>(towns.subList(0, Math.min(3, towns.size())));
        search.addAll(ports.subList(0, Math.min(3, ports.size())));
        search.sort(Comparator.comparingDouble(p -> Math.hypot(p.centreX() - from.x, p.centreZ() - from.z)));
        CountyGen gen = CountyChunkGenerator.gen(src.getLevel().getSeed());
        for (Place p : search) {
            Voxels v = gen.voxels(p);
            Voxels.Mark hatch = null, at = null;
            for (Voxels.Mark m : v.marks) {
                if (m.what().equals("bunker door")) {
                    at = door ? m : at;
                } else if (m.what().startsWith("bunker")) {
                    hatch = m;
                    at = door ? at : m;
                }
            }
            if (hatch == null || at == null) {
                continue;
            }
            Voxels.Mark go = at;
            String state = hatch.what().substring("bunker ".length());
            src.getServer().getCommands().performPrefixedCommand(src,
                    "tp @s " + (p.x0 + go.x()) + ".5 " + (p.level + go.y()) + " " + (p.z0 + go.z()) + ".5");
            boolean ship = p.type == PlaceType.PORT;
            String where = door ? "at its blast door"
                    : ship ? "at the false plate in the engine room's forward bulkhead" : "on the hatch in the electrical room";
            src.sendSuccess(() -> Component.literal("The bunker " + (ship ? "in the ship at " : "under ") + p
                    + " (" + state + "): " + where), false);
            return 1;
        }
        src.sendFailure(Component.literal("No bunker in the nearest cities or ports."));
        return 0;
    }

    private static String compass(double dx, double dz) {
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        int i = (int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4));
        return names[Math.floorMod(i, 8)];
    }

    private CountyCommand() {
    }
}
