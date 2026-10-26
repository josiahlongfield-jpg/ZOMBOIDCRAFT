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
 * they are. /zc goto type: teleports to the nearest one (for trying the world out). /zc bunker
 * [tower|ship|shelter|forest] [door]: teleports to the way into the nearest bunker (of that kind),
 * or with "door" to its blast door.
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
                                .executes(ctx -> bunker(ctx, null, false))
                                .then(Commands.literal("door").executes(ctx -> bunker(ctx, null, true)))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(BUNKERS, b))
                                        .executes(ctx -> bunker(ctx, StringArgumentType.getString(ctx, "kind"), false))
                                        .then(Commands.literal("door")
                                                .executes(ctx -> bunker(ctx, StringArgumentType.getString(ctx, "kind"), true)))))));
    }

    private static final List<String> BUNKERS = List.of("tower", "ship", "shelter", "forest");

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
        List<Place> all = new java.util.ArrayList<>(county.placesNear(x - radius, z - radius, x + radius, z + radius, 0));
        all.addAll(county.hideoutsNear(x - radius, z - radius, x + radius, z + radius, 0));
        return all.stream()
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
        if (p.type == PlaceType.HIDEOUT) {
            // nothing is levelled round a hideout: stand by the hatch, the only ground it knows
            src.getServer().getCommands().performPrefixedCommand(src, "tp @s " + p.centreX() + ".5 " + (p.level + 2) + " " + (p.centreZ() + 3) + ".5");
            src.sendSuccess(() -> Component.literal("By the hatch of " + p), false);
            return 1;
        }
        int[] gate = p.gate(2);
        src.getServer().getCommands().performPrefixedCommand(src, "tp @s " + gate[0] + " " + (p.level + 3) + " " + (gate[1] + 6));
        src.sendSuccess(() -> Component.literal("At the south gate of " + p), false);
        return 1;
    }

    /**
     * The places that may hold a bunker of a kind (null: any), nearest first: downtowns (towers, and
     * the old cities' shelters), ports (ships), towns (shelters) and hideouts (forest hatches).
     */
    private static List<Place> bunkerPlaces(County county, Vec3 from, String kind) {
        List<Place> search = new java.util.ArrayList<>();
        if (kind == null || kind.equals("tower") || kind.equals("shelter")) {
            search.addAll(first(nearest(county, from, PlaceType.DOWNTOWN.name(), 8000), 3));
        }
        if (kind == null || kind.equals("ship")) {
            search.addAll(first(nearest(county, from, PlaceType.PORT.name(), 8000), 3));
        }
        if (kind == null || kind.equals("shelter")) {
            search.addAll(first(nearest(county, from, PlaceType.TOWN.name(), 6000), 6));
        }
        if (kind == null || kind.equals("forest")) {
            int x = (int) from.x, z = (int) from.z, r = 8000;
            search.addAll(first(county.hideoutsNear(x - r, z - r, x + r, z + r, 0).stream()
                    .sorted(Comparator.comparingDouble(p -> Math.hypot(p.centreX() - from.x, p.centreZ() - from.z)))
                    .toList(), 3));
        }
        search.sort(Comparator.comparingDouble(p -> Math.hypot(p.centreX() - from.x, p.centreZ() - from.z)));
        return search;
    }

    private static <T> List<T> first(List<T> list, int n) {
        return list.subList(0, Math.min(n, list.size()));
    }

    /**
     * The places that may hold one are built (or taken from the cache) and searched for the bunker
     * marks, nearest first. Each bunker leaves "bunker STATE KIND" where you get in (a tower's hatch
     * in its electrical room, a ship's false plate, the top of a shelter's stairs, a forest hatch)
     * and "bunker door" at its blast door straight after.
     */
    private static int bunker(CommandContext<CommandSourceStack> ctx, String kind, boolean door) {
        CommandSourceStack src = ctx.getSource();
        County county = county(src);
        if (county == null || src.getEntity() == null) {
            return 0;
        }
        if (kind != null && !BUNKERS.contains(kind)) {
            src.sendFailure(Component.literal("Bunker kinds: " + String.join(", ", BUNKERS)));
            return 0;
        }
        Vec3 from = src.getPosition();
        CountyGen gen = CountyChunkGenerator.gen(src.getLevel().getSeed());
        for (Place p : bunkerPlaces(county, from, kind)) {
            List<Voxels.Mark> marks = gen.voxels(p).marks;
            for (int i = 0; i < marks.size(); i++) {
                String[] what = marks.get(i).what().split(" ");
                if (what.length < 3 || !what[0].equals("bunker") || kind != null && !what[2].equals(kind)) {
                    continue;
                }
                Voxels.Mark go = marks.get(i);
                if (door && i + 1 < marks.size() && marks.get(i + 1).what().equals("bunker door")) {
                    go = marks.get(i + 1);
                }
                src.getServer().getCommands().performPrefixedCommand(src,
                        "tp @s " + (p.x0 + go.x()) + ".5 " + (p.level + go.y()) + " " + (p.z0 + go.z()) + ".5");
                String where = door ? "at its blast door" : switch (what[2]) {
                    case "ship" -> "at the false plate in the engine room's forward bulkhead";
                    case "shelter" -> "at the top of the stairs down to the boiler room";
                    case "forest" -> "on the hatch under the leaves";
                    default -> "on the hatch in the electrical room";
                };
                String in = switch (what[2]) {
                    case "ship" -> "in the ship at ";
                    case "forest" -> "in the woods at ";
                    default -> "under ";
                };
                String state = what[1];
                String name = what[2];
                src.sendSuccess(() -> Component.literal("The " + name + " bunker " + in + p + " (" + state + "): " + where), false);
                return 1;
            }
        }
        src.sendFailure(Component.literal(kind == null ? "No bunker near here." : "No " + kind + " bunker near here."));
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
